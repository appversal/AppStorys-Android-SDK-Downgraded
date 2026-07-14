package com.appversal.appstorys.api   // same package as Campaign/details models

import android.content.Context
import android.util.Log
import com.appversal.appstorys.utils.SdkJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.io.File

/**
 * AppStorys value-dump hook — DEBUG ONLY (Layer 2 / parse check).
 *
 * Dumps what the SDK *actually parsed* from campaigns.json (typed details and
 * all) back to JSON, so the testing tool can diff it against the published JSON:
 *     node Test.js <campaignId> --parse sdk_dump.json
 *
 * WHY THIS IS SDK-SPECIFIC (not a generic re-encode)
 *   Campaign is @Serializable(with = CampaignDeserializer::class), and that
 *   custom serializer's serialize() writes id/campaign_type/position/screen/
 *   trigger_event but NOT `details`. So SdkJson.encodeToString(campaign) would
 *   drop the whole details block. Instead we encode each parsed details object
 *   with ITS OWN typed serializer below — a faithful view of the parse.
 *   (SdkJson has explicitNulls = false, so null fields are omitted = clean dump.)
 *
 * WHERE TO CALL IT  (ApiRepository)
 *   1) Add a context field near sharedPreferences:
 *          private val appContext = context.applicationContext
 *   2) In getScreenCampaignsData, right after fetchCampaignsJson succeeds
 *      (where cachedCampaignsJson is first used), add:
 *          AppStorysDebugDump.dumpParsedCampaigns(cachedCampaignsJson, appContext)
 *
 *   Leave [enabled] = true while testing; set it to false (or wire it to your
 *   BuildConfig.DEBUG) so it never runs in client/production builds.
 */
object AppStorysDebugDump {

    /** Master switch — flip to false (or wire to BuildConfig.DEBUG) for release. */
    var enabled: Boolean = true

    private const val TAG = "APPSTORYS_DUMP"
    private const val CHUNK = 3500 // Logcat truncates lines around 4000 chars

    // Pretty output, but inherits SdkJson's parse rules (explicitNulls = false, etc.)
    private val pretty = Json(from = SdkJson) { prettyPrint = true }

    /**
     * Re-encode the parsed campaigns (full list is best so the tool can match by
     * id), log it to Logcat in chunks, and write it to filesDir/appstorys_dump.json.
     */
    fun dumpParsedCampaigns(campaigns: List<Campaign>?, context: Context? = null) {
        if (!enabled) return
        if (campaigns == null) {
            Log.w(TAG, "no parsed campaigns to dump")
            return
        }
        val arr = buildJsonArray {
            for (c in campaigns) {
                add(buildJsonObject {
                    c.id?.let { put("id", it) }
                    c.campaignType?.let { put("campaign_type", it) }
                    put("details", encodeDetails(c.details))
                    c.position?.let { put("position", it) }
                    c.screen?.let { put("screen", it) }
                })
            }
        }
        emit(pretty.encodeToString(JsonElement.serializer(), arr), context)
    }

    // Encode each concrete details type with its own (auto-generated) serializer.
    private fun encodeDetails(d: CampaignDetails?): JsonElement = when (d) {
        null -> JsonNull
        is BannerDetails          -> SdkJson.encodeToJsonElement(BannerDetails.serializer(), d)
        is FloaterDetails         -> SdkJson.encodeToJsonElement(FloaterDetails.serializer(), d)
        is CSATDetails            -> SdkJson.encodeToJsonElement(CSATDetails.serializer(), d)
        is WidgetDetails          -> SdkJson.encodeToJsonElement(WidgetDetails.serializer(), d)
        is ReelsDetails           -> SdkJson.encodeToJsonElement(ReelsDetails.serializer(), d)
        is TooltipsDetails        -> SdkJson.encodeToJsonElement(TooltipsDetails.serializer(), d)
        is PipDetails             -> SdkJson.encodeToJsonElement(PipDetails.serializer(), d)
        is BottomSheetDetails     -> SdkJson.encodeToJsonElement(BottomSheetDetails.serializer(), d)
        is SurveyDetails          -> SdkJson.encodeToJsonElement(SurveyDetails.serializer(), d)
        is ModalDetails           -> SdkJson.encodeToJsonElement(ModalDetails.serializer(), d)
        is StoriesDetails         -> SdkJson.encodeToJsonElement(StoriesDetails.serializer(), d)
        is ScratchCardDetails     -> SdkJson.encodeToJsonElement(ScratchCardDetails.serializer(), d)
        is MilestoneDetails       -> SdkJson.encodeToJsonElement(MilestoneDetails.serializer(), d)
        is SpinTheWheelDetails    -> SdkJson.encodeToJsonElement(SpinTheWheelDetails.serializer(), d)
        is VariantCampaignDetails -> SdkJson.encodeToJsonElement(VariantCampaignDetails.serializer(), d)
        else -> JsonNull // any future details type: add a branch above
    }

    private fun emit(json: String, context: Context?, fileName: String = "appstorys_dump.json") {
        Log.i(TAG, "----- BEGIN $fileName (${json.length} chars) -----")
        var i = 0
        while (i < json.length) {
            val end = minOf(i + CHUNK, json.length)
            Log.i(TAG, json.substring(i, end))
            i = end
        }
        Log.i(TAG, "----- END $fileName -----")
        if (context != null) {
            runCatching {
                val f = File(context.filesDir, fileName)
                f.writeText(json)
                Log.i(TAG, "wrote ${f.absolutePath}")
            }.onFailure { Log.w(TAG, "file write failed: ${it.message}") }
        }
    }
}

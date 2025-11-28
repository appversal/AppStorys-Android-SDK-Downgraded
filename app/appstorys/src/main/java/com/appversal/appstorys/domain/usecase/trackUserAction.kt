package com.appversal.appstorys.domain.usecase

import com.appversal.appstorys.api.ApiService
import com.appversal.appstorys.domain.State
import com.appversal.appstorys.domain.State.impressions
import com.appversal.appstorys.domain.model.Impression
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import timber.log.Timber

enum class ActionType {
    IMP, CLK, CNV
}

internal suspend fun trackUserAction(
    campaignId: String,
    type: ActionType,
    storySlide: String? = null,
    widgetImage: String? = null,
    reelId: String? = null
) = withContext(Dispatchers.IO) {
    val log = Timber.tag("TrackUserAction")
    try {
        val impression = Impression(
            campaign = campaignId,
            event = type.name
        )
        if (impressions.value.contains(impression)) {
            log.d("Action already tracked: $impression")
            return@withContext
        }
        State.addImpression(impression)

        ApiService.getInstance().trackUserAction(
            request = buildJsonObject {
                put("campaign_id", JsonPrimitive(campaignId))
                put("event_type", JsonPrimitive(type.name))
                if (storySlide != null) {
                    put("story_slide", JsonPrimitive(storySlide))
                }
                if (widgetImage != null) {
                    put("widget_image", JsonPrimitive(widgetImage))
                }
                if (reelId != null) {
                    put("reel_id", JsonPrimitive(reelId))
                }
            }
        )

        log.d("User action tracked")
    } catch (error: Exception) {
        log.e(error, "Error when verifying AppStorys account")
    }
}



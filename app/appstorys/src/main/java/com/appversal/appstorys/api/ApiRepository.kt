package com.appversal.appstorys.api

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.appversal.appstorys.utils.SdkJson
import com.appversal.appstorys.utils.getDeviceInfo
import com.appversal.appstorys.utils.toJsonElementMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.jsonObject

internal class ApiRepository(
    context: Context,
    private val apiService: ApiService,
    private val webSocketApiService: ApiService,
    private val getScreen: () -> String,
) {
    private val sharedPreferences = context.getSharedPreferences("appversal_campaigns", Context.MODE_PRIVATE)
    private var cachedCampaignsJson: List<Campaign>? = null
    private var isCampaignsJsonFetchedThisSession = false

    companion object {
        private const val PREF_CAMPAIGNS_JSON = "campaigns_json"
        private const val PREF_ETAG = "campaigns_etag"
        private const val PREF_DEVICE_INFO_SENT = "device_info_sent"
    }

    suspend fun getAccessToken(app_id: String, account_id: String, user_id: String, context: Context): String? {
        return withContext(Dispatchers.IO) {

            val deviceInfoAlreadySent = sharedPreferences.getBoolean(PREF_DEVICE_INFO_SENT, false)

            val attributes = if (!deviceInfoAlreadySent) {
                Log.d("ApiRepository", "Sending device info for the first time")
                getDeviceInfo(context = context).toJsonElementMap()
            } else {
                Log.d("ApiRepository", "Device info already sent, skipping")
                null
            }

            when (val result = safeApiCall {
                webSocketApiService.validateAccount(
                    accountId = account_id,
                    ValidateAccountRequest(app_id = app_id, account_id = account_id, user_id = user_id, attributes = attributes)
                ).access_token
            }) {
                is ApiResult.Success -> {
                    // Mark device info as sent after successful API call
                    if (!deviceInfoAlreadySent) {
                        sharedPreferences.edit {
                            putBoolean(PREF_DEVICE_INFO_SENT, true)
                        }
                        Log.d("ApiRepository", "Device info sent successfully, flag saved")
                    }
                    result.data
                }
                is ApiResult.Error -> {
                    Log.e("ApiRepository", "Error getting access token: ${result.message}")
                    null
                }
            }
        }
    }

    suspend fun sendWidgetPositions(
        accessToken: String,
        screenName: String,
        positionList: List<String>
    ) {
        return withContext(Dispatchers.IO) {
            when (val result = safeApiCall {
                apiService.identifyPositions(
                    token = "Bearer $accessToken",
                    IdentifyPositionsRequest(screen_name = screenName, position_list = positionList)
                )
            }) {
                is ApiResult.Success -> {
                    Log.i("ApiRepository", "Widgets Positions sent successfully.: ${result.data}")
                    null
                }

                is ApiResult.Error -> {
                    Log.e("ApiRepository", "Error sending widget positions: ${result.message}")
                    null
                }
            }
        }
    }

    private suspend fun fetchCampaignsJson(accountId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Check if already fetched this session
                if (isCampaignsJsonFetchedThisSession) {
                    Log.d("ApiRepository", "Campaigns already fetched this session, using cached data")
                    return@withContext true
                }

                // Below link is for prod
                            val campaignsJsonUrl = "https://s3.ap-south-1.amazonaws.com/cdn-campaigns.appstorys.com/clients/$accountId/campaigns.json"

                // Below link is for dev
//                val campaignsJsonUrl = "https://dev-cdn-campaign-appstorys.s3.ap-south-1.amazonaws.com/clients/$accountId/campaigns.json"

                val savedETag = sharedPreferences.getString(PREF_ETAG, null)

                val client = okhttp3.OkHttpClient()
                val requestBuilder = okhttp3.Request.Builder()
                    .url(campaignsJsonUrl)

                // Add If-None-Match header if we have a saved ETag
                if (savedETag != null) {
                    requestBuilder.addHeader("If-None-Match", savedETag)
                    Log.d("ApiRepository", "Using cached ETag: $savedETag")
                }

                val request = requestBuilder.build()
                val response = client.newCall(request).execute()

                when (response.code) {
                    200 -> {
                        // New data available
                        val jsonString = response.body?.string()
                        val newETag = response.header("ETag")

                        if (jsonString != null) {
                            // Parse and cache the campaigns
                            cachedCampaignsJson = SdkJson.decodeFromString<List<Campaign>>(jsonString)

                            // Save to SharedPreferences
                            sharedPreferences.edit {
                                putString(PREF_CAMPAIGNS_JSON, jsonString)
                                if (newETag != null) {
                                    putString(PREF_ETAG, newETag)
                                }
                            }

                            isCampaignsJsonFetchedThisSession = true
                            Log.d("ApiRepository", "Campaigns.json fetched and cached (200 OK). Total campaigns: ${cachedCampaignsJson?.size}, ETag: $newETag")
                            return@withContext true
                        }
                    }
                    304 -> {
                        // Not Modified - use cached data from SharedPreferences
                        Log.d("ApiRepository", "Campaigns.json not modified (304), using local storage")

                        val cachedJsonString = sharedPreferences.getString(PREF_CAMPAIGNS_JSON, null)
                        if (cachedJsonString != null) {
                            cachedCampaignsJson = SdkJson.decodeFromString<List<Campaign>>(cachedJsonString)
                            isCampaignsJsonFetchedThisSession = true
                            Log.d("ApiRepository", "Loaded ${cachedCampaignsJson?.size} campaigns from local storage")
                            return@withContext true
                        } else {
                            Log.e("ApiRepository", "Got 304 but no cached data in SharedPreferences")
                            return@withContext false
                        }
                    }
                    else -> {
                        Log.e("ApiRepository", "Error fetching campaigns.json: ${response.code}")

                        // Try to use cached data as fallback
                        val cachedJsonString = sharedPreferences.getString(PREF_CAMPAIGNS_JSON, null)
                        if (cachedJsonString != null) {
                            cachedCampaignsJson = SdkJson.decodeFromString<List<Campaign>>(cachedJsonString)
                            isCampaignsJsonFetchedThisSession = true
                            Log.d("ApiRepository", "Using cached data as fallback. Total campaigns: ${cachedCampaignsJson?.size}")
                            return@withContext true
                        }
                        return@withContext false
                    }
                }

                false
            } catch (e: Exception) {
                Log.e("ApiRepository", "Exception fetching campaigns.json: ${e.message}", e)

                // Try to use cached data as fallback
                val cachedJsonString = sharedPreferences.getString(PREF_CAMPAIGNS_JSON, null)
                if (cachedJsonString != null) {
                    try {
                        cachedCampaignsJson = SdkJson.decodeFromString<List<Campaign>>(cachedJsonString)
                        isCampaignsJsonFetchedThisSession = true
                        Log.d("ApiRepository", "Using cached data after exception. Total campaigns: ${cachedCampaignsJson?.size}")
                        return@withContext true
                    } catch (parseException: Exception) {
                        Log.e("ApiRepository", "Error parsing cached data: ${parseException.message}", parseException)
                    }
                }
                false
            }
        }
    }

    private fun extractVariantFromCampaign(campaign: Campaign, variantId: String): Campaign {
        try {
            val details = campaign.details

            // Check if the campaign has VariantCampaignDetails
            if (details !is VariantCampaignDetails) {
                Log.d("ApiRepository", "Campaign ${campaign.id} does not have variants, returning as-is")
                return campaign
            }

            // Get the specific variant data from the variants object
            val variantData = details.variants[variantId]?.jsonObject
            if (variantData == null) {
                Log.e("ApiRepository", "Variant $variantId not found in campaign ${campaign.id}")
                return campaign
            }

            Log.d("ApiRepository", "Extracting variant $variantId from campaign ${campaign.id}")

            // Deserialize the variant data based on campaign type
            val variantDetails: CampaignDetails? = when (campaign.campaignType) {
                "BAN" -> SdkJson.decodeFromJsonElement(BannerDetails.serializer(), variantData)
                "FLT" -> SdkJson.decodeFromJsonElement(FloaterDetails.serializer(), variantData)
                "CSAT" -> SdkJson.decodeFromJsonElement(CSATDetails.serializer(), variantData)
                "WID" -> SdkJson.decodeFromJsonElement(WidgetDetails.serializer(), variantData)
                "REL" -> SdkJson.decodeFromJsonElement(ReelsDetails.serializer(), variantData)
                "TTP" -> SdkJson.decodeFromJsonElement(TooltipsDetails.serializer(), variantData)
                "PIP" -> SdkJson.decodeFromJsonElement(PipDetails.serializer(), variantData)
                "BTS" -> SdkJson.decodeFromJsonElement(BottomSheetDetails.serializer(), variantData)
                "SUR" -> SdkJson.decodeFromJsonElement(SurveyDetails.serializer(), variantData)
                "MOD" -> SdkJson.decodeFromJsonElement(ModalDetails.serializer(), variantData)
                "STR" -> SdkJson.decodeFromJsonElement(StoriesDetails.serializer(), variantData)
                "SCRT" -> SdkJson.decodeFromJsonElement(ScratchCardDetails.serializer(), variantData)
                "MIL" -> SdkJson.decodeFromJsonElement(MilestoneDetails.serializer(), variantData)
                else -> {
                    Log.w("ApiRepository", "Campaign type ${campaign.campaignType} does not support variants yet")
                    null
                }
            }

            // Return a new campaign with the variant details
            return if (variantDetails != null) {
                campaign.copy(details = variantDetails)
            } else {
                campaign
            }

        } catch (e: Exception) {
            Log.e("ApiRepository", "Error extracting variant from campaign ${campaign.id}: ${e.message}", e)
            return campaign
        }
    }

    data class ScreenCampaignResult(
        val campaigns: List<Campaign>?,
        val variants: List<CampaignVariant>,
        val personalizationData: Map<String, String>,
        val testUser: Boolean?
    )

    suspend fun getScreenCampaignsData(
        accessToken: String,
        accountId: String,
        screenName: String,
        userId: String
    ): ScreenCampaignResult {
        return withContext(Dispatchers.IO) {
            try {
                // Step 1: Call track-user-res to get eligible campaigns
                val eligibleCampaignsResult = safeApiCall {
                    webSocketApiService.getEligibleCampaigns(
                        accountId = accountId,
                        token = "Bearer $accessToken",
                        request = TrackUserWebSocketRequest(
                            screenName = screenName,
                            user_id = userId
                        )
                    )
                }

                when (eligibleCampaignsResult) {
                    is ApiResult.Success -> {
                        val eligibleCampaigns = eligibleCampaignsResult.data.eligibleCampaignList
                        val variants = eligibleCampaignsResult.data.variants ?: emptyList()
                        val personalizationData = eligibleCampaignsResult.data.personalization_data ?: emptyMap()
                        val testUser = eligibleCampaignsResult.data.test_user

                        Log.d("ApiRepository", "Eligible campaigns: $eligibleCampaigns")
                        Log.d("ApiRepository", "Variants: $variants")
                        Log.d("ApiRepository", "Personalization Data: $personalizationData")

                        // Step 2: If eligibleCampaigns is empty, return null
                        if (eligibleCampaigns.isNullOrEmpty()) {
                            Log.d("ApiRepository", "No eligible campaigns for screen: $screenName")
                            return@withContext ScreenCampaignResult(
                                campaigns = null,
                                variants = emptyList(),
                                personalizationData = emptyMap(),
                                testUser = eligibleCampaignsResult.data.test_user
                            )
                        }

                        // Step 3: Fetch campaigns.json (with caching)
                        val fetchSuccess = fetchCampaignsJson(accountId)

                        if (!fetchSuccess) {
                            Log.e("ApiRepository", "Failed to fetch campaigns.json")
                            return@withContext ScreenCampaignResult(
                                campaigns = null,
                                variants = emptyList(),
                                personalizationData = emptyMap(),
                                testUser = false
                            )
                        }

                        val cachedCampaignIds = cachedCampaignsJson?.mapNotNull { it.id }?.toSet() ?: emptySet()
                        val missingCampaignIds = eligibleCampaigns.filter { it !in cachedCampaignIds }

                        if (missingCampaignIds.isNotEmpty()) {
                            Log.d("ApiRepository", "Missing ${missingCampaignIds.size} campaigns from cache: $missingCampaignIds")

                            // Fetch missing campaigns from load-campaign-data endpoint
                            val missingCampaignsResult = safeApiCall {
                                webSocketApiService.loadMissingCampaigns(
                                    token = "Bearer $accessToken",
                                    campaignIds = missingCampaignIds
                                )
                            }

                            when (missingCampaignsResult) {
                                is ApiResult.Success -> {
                                    val fetchedCampaigns = missingCampaignsResult.data
                                    Log.d("ApiRepository", "Fetched ${fetchedCampaigns.size} missing campaigns from load-campaign-data")

                                    // Merge fetched campaigns with cached campaigns
                                    cachedCampaignsJson = (cachedCampaignsJson ?: emptyList()) + fetchedCampaigns
                                    Log.d("ApiRepository", "Total campaigns after merge: ${cachedCampaignsJson?.size}")

                                    // Update SharedPreferences with merged data
                                    try {
                                        val updatedJsonString = SdkJson.encodeToString(ListSerializer(Campaign.serializer()), cachedCampaignsJson!!)
                                        sharedPreferences.edit {
                                            putString(PREF_CAMPAIGNS_JSON, updatedJsonString)
                                        }
                                        Log.d("ApiRepository", "Updated local storage with merged campaigns")
                                    } catch (e: Exception) {
                                        Log.e("ApiRepository", "Error saving merged campaigns: ${e.message}", e)
                                    }
                                }
                                is ApiResult.Error -> {
                                    Log.e("ApiRepository", "Error loading missing campaigns: ${missingCampaignsResult.message}")
                                    // Continue with cached campaigns only
                                }
                            }
                        } else {
                            Log.d("ApiRepository", "All eligible campaigns found in cache")
                        }

                        // Step 4: Filter campaigns by screenName and eligibleCampaigns
                        val filteredCampaigns = cachedCampaignsJson?.filter { campaign ->
                            val isEligible = campaign.id in eligibleCampaigns
                            val isScreenMatch = campaign.screen?.equals(screenName, ignoreCase = true) == true
                            isEligible && isScreenMatch
                        }?.map { campaign ->
                            // Apply variant extraction if this campaign has a variant in the response
                            val variant = variants.find { it.id == campaign.id }
                            if (variant != null) {
                                extractVariantFromCampaign(campaign, variant.v_id)
                            } else {
                                campaign
                            }
                        }

                        Log.d("ApiRepository", "Filtered campaigns for screen '$screenName': ${filteredCampaigns?.size ?: 0}")

                        ScreenCampaignResult(
                            campaigns = filteredCampaigns,
                            variants = variants,
                            personalizationData = personalizationData,
                            testUser = testUser
                        )
                    }

                    is ApiResult.Error -> {
                        Log.e("ApiRepository", "Error getting eligible campaigns: ${eligibleCampaignsResult.message}")
                        return@withContext ScreenCampaignResult(
                            campaigns = null,
                            variants = emptyList(),
                            personalizationData = emptyMap(),
                            testUser = false
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("ApiRepository", "Error in getScreenCampaignsData: ${e.message}", e)
                return@withContext ScreenCampaignResult(
                    campaigns = null,
                    variants = emptyList(),
                    personalizationData = emptyMap(),
                    testUser = false
                )
            }
        }
    }

    suspend fun captureCSATResponse(accessToken: String, actions: CsatFeedbackPostRequest) {
        withContext(Dispatchers.IO) {
            when (val result = safeApiCall {
                apiService.sendCSATResponse(
                    token = "Bearer $accessToken",
                    request = actions
                )
            }) {
                is ApiResult.Error -> println("Error capturing CSAT response: ${result.message}")
                else -> Unit
            }
        }
    }

    suspend fun sendReelLikeStatus(accessToken: String, actions: ReelStatusRequest) {
        withContext(Dispatchers.IO) {
            when (val result = safeApiCall {
                apiService.sendReelLikeStatus(
                    token = "Bearer $accessToken",
                    request = actions
                )
            }) {
                is ApiResult.Error -> println("Error tracking actions: ${result.message}")
                else -> Unit
            }
        }
    }

    suspend fun tooltipIdentify(
        accessToken: String,
        screenName: String,
        user_id: String,
        childrenJson: String,
        screenshotFile: File
    ) {
        withContext(Dispatchers.IO) {
            try {

                val mediaType = "text/plain".toMediaTypeOrNull()
                val jsonMediaType = "application/json".toMediaTypeOrNull()

                val screenNamePart = screenName.toRequestBody(mediaType)
                val userIdPart = user_id.toRequestBody(mediaType)
                val childrenPart = childrenJson.toRequestBody(jsonMediaType)

                val requestFile = screenshotFile.asRequestBody("image/png".toMediaTypeOrNull())
                val screenshotPart = MultipartBody.Part.createFormData(
                    "screenshot",
                    screenshotFile.name,
                    requestFile
                )

                val result = safeApiCall {
                    apiService.identifyTooltips(
                        token = "Bearer $accessToken",
                        user_id = userIdPart,
                        screenName = screenNamePart,
                        children = childrenPart,
                        screenshot = screenshotPart
                    )
                }

                when (result) {
                    is ApiResult.Success -> println("Tooltip identified: $result")
                    is ApiResult.Error -> println("Tooltip Server error: ${result.code} ${result.message}")
                }
            } catch (e: Exception) {
                println("Exception in tooltipIdentify: ${e.message}")
            }
        }
    }
}
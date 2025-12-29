package com.appversal.appstorys.api

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.appversal.appstorys.api.RetrofitClient.webSocketApiService
import com.appversal.appstorys.utils.SdkJson
import com.appversal.appstorys.utils.toJsonElementMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

internal class ApiRepository(
    context: Context,
    private val apiService: ApiService,
    private val webSocketApiService: ApiService,
    private val getScreen: () -> String,
) {
    private var cachedCampaignsJson: List<Campaign>? = null
    private var isCampaignsJsonFetched = false

    suspend fun getAccessToken(app_id: String, account_id: String, user_id: String): String? {
        return withContext(Dispatchers.IO) {
            when (val result = safeApiCall {
                webSocketApiService.validateAccount(
                    ValidateAccountRequest(app_id = app_id, account_id = account_id, user_id = user_id)
                ).access_token
            }) {
                is ApiResult.Success -> result.data
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

    suspend fun getScreenCampaignsData(
        accessToken: String,
        accountId: String,
        screenName: String,
        userId: String
    ): List<Campaign>? {
        return withContext(Dispatchers.IO) {
            try {
                // Step 1: Call track-user-res to get eligible campaigns
                val eligibleCampaignsResult = safeApiCall {
                    webSocketApiService.getEligibleCampaigns(
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

                        Log.d("ApiRepository", "Eligible campaigns: $eligibleCampaigns")

                        // Step 2: If eligibleCampaigns is empty, return null
                        if (eligibleCampaigns.isEmpty()) {
                            Log.d("ApiRepository", "No eligible campaigns for screen: $screenName")
                            return@withContext null
                        }

                        // Step 3: Fetch campaigns.json from S3 if not already cached
                        if (!isCampaignsJsonFetched) {
                            // Below link is for prod
//                            val campaignsJsonUrl = "https://s3.ap-south-1.amazonaws.com/cdn-campaigns.appstorys.com/clients/$accountId/campaigns.json"

                            // Below link is for dev
                            val campaignsJsonUrl = "https://dev-cdn-campaign-appstorys.s3.ap-south-1.amazonaws.com/clients/$accountId/campaigns.json"

                            val client = okhttp3.OkHttpClient()
                            val request = okhttp3.Request.Builder()
                                .url(campaignsJsonUrl)
                                .build()

                            val response = client.newCall(request).execute()

                            if (response.isSuccessful) {
                                val jsonString = response.body?.string()
                                if (jsonString != null) {
                                    cachedCampaignsJson = SdkJson.decodeFromString<List<Campaign>>(jsonString)
                                    isCampaignsJsonFetched = true
                                    Log.d("ApiRepository", "Campaigns.json fetched and cached successfully. Total campaigns: ${cachedCampaignsJson?.size}")
                                }
                            } else {
                                Log.e("ApiRepository", "Error fetching campaigns.json: ${response.code}")
                                return@withContext null
                            }
                        }

                        val cachedCampaignIds = cachedCampaignsJson?.mapNotNull { it.id }?.toSet() ?: emptySet()
                        val missingCampaignIds = eligibleCampaigns.filter { it !in cachedCampaignIds }

                        if (missingCampaignIds.isNotEmpty()) {
                            Log.d("ApiRepository", "Missing ${missingCampaignIds.size} campaigns from S3: $missingCampaignIds")

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
                        }

                        Log.d("ApiRepository", "Filtered campaigns for screen '$screenName': ${filteredCampaigns?.size ?: 0}")

                        filteredCampaigns
                    }

                    is ApiResult.Error -> {
                        Log.e("ApiRepository", "Error getting eligible campaigns: ${eligibleCampaignsResult.message}")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e("ApiRepository", "Error in getScreenCampaignsData: ${e.message}", e)
                null
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
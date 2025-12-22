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
    private val getScreen: () -> String,
) {
    private var webSocketClient: WebSocketClient? = null
    private var webSocketConfig: WebSocketConfig? = null
    private var campaignResponseChannel = Channel<CampaignResponse?>(Channel.UNLIMITED)

    private val sharedPreferences =
        context.getSharedPreferences("appstorys_sdk_prefs", Context.MODE_PRIVATE)
    private var lastProcessedMessageId: String?
        get() = sharedPreferences.getString("last_message_id", null)
        set(value) {
            sharedPreferences.edit { putString("last_message_id", value) }
        }

    init {
        webSocketClient = WebSocketClient()

        CoroutineScope(Dispatchers.IO).launch {
            webSocketClient?.message?.collect { message ->
                try {
                    val campaignResponse = SdkJson.decodeFromString<CampaignResponse>(message)

                    if (campaignResponse.messageId == lastProcessedMessageId) {
                        Log.d(
                            "ApiRepository",
                            "Duplicate WebSocket message skipped: ${campaignResponse.messageId}"
                        )
                        return@collect
                    }
                    lastProcessedMessageId = campaignResponse.messageId

                    val campaign = campaignResponse.campaigns?.firstOrNull()
                    val campaignId = campaign?.id
                    val campaignScreen = campaign?.screen

                    if (
                        campaign != null &&
                        getScreen().equals(campaignScreen, ignoreCase = true)
                    ) {
                        campaignResponseChannel.send(campaignResponse)
                        Log.d("ApiRepository", "New campaign processed: $campaignId")

//                        webSocketClient?.disconnect()
                    } else {
                        Log.d("ApiRepository", "Campaign skipped: $campaignId")
                    }
                } catch (e: Exception) {
                    Log.e("ApiRepository", "Error parsing WebSocket message: ${e.message}", e)
                    campaignResponseChannel.send(null)
                }
            }
        }
    }

    suspend fun getAccessToken(app_id: String, account_id: String): String? {
        return withContext(Dispatchers.IO) {
            when (val result = safeApiCall {
                webSocketApiService.validateAccount(
                    ValidateAccountRequest(app_id = app_id, account_id = account_id)
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

    suspend fun initializeWebSocketConnection(
        accessToken: String,
        screenName: String,
        userId: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                when (val result = safeApiCall {
                    webSocketApiService.getWebSocketConnectionDetails(
                        token = "Bearer $accessToken",
                        request = TrackUserWebSocketRequest(
                            screenName = screenName,
                            user_id = userId
                        )
                    )
                }) {
                    is ApiResult.Success -> {
                        result.data.ws.let { config ->
                            webSocketConfig = config
                            webSocketClient?.connect(config) ?: false
                        }
                    }

                    is ApiResult.Error -> {
                        Log.e("ApiRepository", "Error getting WebSocket config: ${result.message}")
                        false
                    }
                }
            } catch (e: Exception) {
                Log.e("ApiRepository", "Error initializing WebSocket connection: ${e.message}")
                false
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun triggerScreenData(
        accessToken: String,
        screenName: String,
        userId: String,
        timeoutMs: Long = 20000
    ): Pair<CampaignResponse?, WebSocketConnectionResponse?> {
        return withContext(Dispatchers.IO) {

            var webSocketResponse: WebSocketConnectionResponse?

            while (!campaignResponseChannel.isEmpty) {
                campaignResponseChannel.tryReceive()
            }

            if (!webSocketClient!!.isConnected()) {
                val reconnected =
                    initializeWebSocketConnection(accessToken, screenName, userId)
                if (!reconnected) {
                    Log.e("ApiRepository", "Failed to reconnect WebSocket")
                    return@withContext Pair(null, null)
                }

            }
            when (val result = safeApiCall {
                webSocketApiService.getWebSocketConnectionDetails(
                    token = "Bearer $accessToken",
                    request = TrackUserWebSocketRequest(
                        screenName = screenName,
                        user_id = userId
                    )
                )
            }) {
                is ApiResult.Success -> {
                    Log.i("ApiRepository", "Parsed WebSocket response: ${result.data}")
                    webSocketResponse = result.data
                }

                is ApiResult.Error -> {
                    Log.e(
                        "ApiRepository",
                        "Error sending track-user request: ${result.message}"
                    )
                    return@withContext Pair(null, null)
                }
            }

            val campaignResponse = withTimeoutOrNull(timeoutMs) {
                campaignResponseChannel.receive()
            }

            Pair(campaignResponse, webSocketResponse)
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

    fun disconnect() {
        webSocketClient?.disconnect()
    }
}
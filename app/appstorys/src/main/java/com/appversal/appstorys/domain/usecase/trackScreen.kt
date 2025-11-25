package com.appversal.appstorys.domain.usecase

import com.appversal.appstorys.api.ApiService
import com.appversal.appstorys.domain.State
import com.appversal.appstorys.domain.State.getAccessToken
import com.appversal.appstorys.domain.State.userId
import com.appversal.appstorys.utils.SdkJson
import com.appversal.appstorys.utils.toJsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber
import kotlin.coroutines.resume

private var client: WebSocket? = null

internal val screenTracked = MutableSharedFlow<String>(extraBufferCapacity = 100)

private val log: Timber.Tree
    get() = Timber.tag("TrackScreen")

private fun disconnect() {
    if (client == null) {
        return
    }
    try {
        client?.close(1000, "Disconnecting")
        client = null
        log.d("WebSocket disconnected")
    } catch (error: Exception) {
        log.e(error, "Error disconnecting WebSocket")
    }
}

internal suspend fun trackScreen(
    screenName: String,
    emitTrackEvent: Boolean
): JsonObject? = withContext(Dispatchers.IO) {
    suspendCancellableCoroutine { continuation ->
        try {
            val accessToken = getAccessToken()
            if (accessToken.isNullOrBlank()) {
                Timber.e("Access token not found")
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            if (emitTrackEvent) {
                launch {
                    screenTracked.emit(screenName)
                }
            }

            log.d("Tracking $screenName")

            val requestBody = SdkJson.encodeToString(
                mapOf(
                    "user_id" to userId.value,
                    "screenName" to screenName,
                    "attributes" to emptyMap<String, String>()
                ).toJsonObject()
            )

            val getConfigRequest = Request.Builder()
                .url("https://users.appstorys.com/track-user")
                .post(requestBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", accessToken)
                .build()

            val responseBody = ApiService.getClient().newCall(getConfigRequest).execute().use {
                if (!it.isSuccessful) {
                    log.e("Failed to track $screenName ${it.code} ${it.body?.string()}")
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }

                val body = it.body?.string()
                if (body == null) {
                    log.e("Empty response body when tracking $screenName")
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }

                body
            }

            val data = Json.decodeFromString<JsonObject>(responseBody)
            State.setIsCapturingEnabled(
                screenName,
                data["screen_capture_enabled"]?.jsonPrimitive?.booleanOrNull == true
            )

            val config = data["ws"]?.jsonObject
            val url = config?.get("url")?.jsonPrimitive?.contentOrNull
            val token = config?.get("token")?.jsonPrimitive?.contentOrNull
            val sessionId = config?.get("sessionID")?.jsonPrimitive?.contentOrNull

            if (url == null || token == null || sessionId == null) {
                log.e("WebSocket config missing for $screenName")
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            Timber.d("$screenName tracking initialized: $data")

            val webSocketRequest = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Session-ID", sessionId)
                .build()

            disconnect()
            client = ApiService
                .getClient()
                .newWebSocket(webSocketRequest, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        log.d("WebSocket connection opened")
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        log.d("WebSocket message received: $text")
                        try {
                            disconnect()

                            val data = Json.decodeFromString<JsonObject>(text)
                            State.setIsCapturingEnabled(
                                screenName,
                                data["metadata"]?.jsonObject["screen_capture_enabled"]?.jsonPrimitive?.booleanOrNull == true
                            )

                            continuation.resume(data)
                        } catch (error: Exception) {
                            log.e(error, "Error parsing message for $screenName")
                            continuation.resume(null)
                        }
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        log.d("WebSocket closing: $code $reason")
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        log.d("WebSocket closed: $code $reason")
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?
                    ) {
                        log.e(t, "WebSocket error")
                    }
                })
        } catch (error: Exception) {
            log.e(error, "Error when tracking $screenName")
            continuation.resume(null)
        }
    }
}

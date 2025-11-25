package com.appversal.appstorys.domain.usecase

import android.content.Context
import com.appversal.appstorys.domain.State.getAccessToken
import com.appversal.appstorys.domain.State.userId
import com.appversal.appstorys.domain.model.QueuedRequest
import com.appversal.appstorys.utils.SdkJson
import com.appversal.appstorys.utils.toJsonElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber

/**
 * Set user properties with offline queue support
 *
 * @param context Application context
 * @param attributes User attributes to set
 */
internal suspend fun setUserProperties(
    context: Context,
    attributes: Map<String, Any>
) = withContext(Dispatchers.IO) {
    val log = Timber.tag("SetUserProperties")
    try {
        // Get access token and user ID
        val accessToken = getAccessToken()
        val userId = userId.value

        if (accessToken == null || userId.isBlank()) {
            log.w("Missing accessToken or userId")
            return@withContext
        }

        // Build request body
        val body = buildJsonObject {
            put("user_id", userId)
            put(
                "attributes",
                buildJsonObject {
                    // Add custom attributes first
                    attributes.forEach { (key, value) ->
                        put(key, value.toJsonElement())
                    }


                    // Add device info
                    getDeviceInfo(context).forEach { (key, value) ->
                        put(key, value.toJsonElement())
                    }
                }
            )
            put("silentUpdate", true)
        }

        // Log the request body
        log.d(
            "📤 Sending setUserProperties body: %s",
            SdkJson.encodeToString(body)
        )

        // Create queued request
        val queuedRequest = QueuedRequest(
            url = "https://users.appstorys.com/track-user",
            method = "POST",
            headers = mapOf(
                "Content-Type" to "application/json",
                "Authorization" to accessToken
            ),
            body = SdkJson.encodeToString(body)
        )

        // Send or queue the request
        val response = OfflineQueueWorker(context, queuedRequest)

        when {
            response == null -> log.d("📥 setUserProperties request queued for offline processing")
            response.isSuccessful -> log.d("📥 setUserProperties request sent successfully")
            else -> log.w("📥 setUserProperties request queued or failed: ${response.code}")
        }
    } catch (error: Exception) {
        log.e(error, "❌ Error in setUserProperties")
    }
}


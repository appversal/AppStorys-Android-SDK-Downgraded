package com.appversal.appstorys.domain.usecase

import android.content.Context
import com.appversal.appstorys.domain.State.getAccessToken
import com.appversal.appstorys.domain.State.setTrackedEvents
import com.appversal.appstorys.domain.State.trackedEvents
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
 * Track events with offline queue support
 *
 * @param context Application context
 * @param event Event name (required)
 * @param campaignId Optional campaign ID
 * @param metadata Optional metadata attributes
 */
internal suspend fun trackEvent(
    context: Context,
    event: String,
    campaignId: String? = null,
    metadata: Map<String, Any>? = null
) = withContext(Dispatchers.IO) {
    val log = Timber.tag("TrackEvent")
    try {
        if (event.isBlank()) {
            log.e("Event name is required")
            return@withContext
        }

        val accessToken = getAccessToken()
        if (accessToken == null) {
            log.e("Access token not found")
            return@withContext
        }

        if (event != "viewed" && event != "clicked" && event != "csat captured" && event != "survey captured") {
            val trackedEvents = trackedEvents.value.toMutableList()
            if (trackedEvents.contains(event)) {
                // Remove and re-add to update the order
                trackedEvents.remove(event)
            }
            trackedEvents.add(event)
            setTrackedEvents(trackedEvents)
        }

        val response = OfflineQueueWorker(
            context,
            QueuedRequest(
                url = "https://tracking.appstorys.com/capture-event",
                method = "POST",
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "Authorization" to accessToken
                ),
                body = SdkJson.encodeToString(
                    buildJsonObject {
                        put("user_id", userId.value)
                        put("event", event)
                        put(
                            "metadata",
                            buildJsonObject {
                                // Add custom metadata first
                                metadata?.forEach { (key, value) ->
                                    put(key, value.toJsonElement())
                                }

                                // Add device info
                                getDeviceInfo(context).forEach { (key, value) ->
                                    put(key, value.toJsonElement())
                                }
                            }
                        )
                        campaignId?.let { put("campaign_id", it) }
                    }
                )
            )
        )

        if (response != null && !response.isSuccessful) {
            log.e("Something went wrong: ${response.code} ${response.message}")
        }
    } catch (error: Exception) {
        log.e(error, "Error in trackEvent")
    }
}


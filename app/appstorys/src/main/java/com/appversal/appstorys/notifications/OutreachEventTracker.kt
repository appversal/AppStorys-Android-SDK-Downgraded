package com.appversal.appstorys.notifications

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Self-contained outreach (push) event tracker. Uses its own access token
 * obtained from /update-user-device-token — that token is valid ~30 days
 * and cached on disk; we only refetch on first install or when the server
 * rejects it (401/403).
 *
 * Designed to work from cold-start contexts (FirebaseMessagingService,
 * BroadcastReceiver) where the AppStorys singleton may not be initialized.
 *
 * NEVER throws.
 */
internal object OutreachEventTracker {

    private const val TAG = "AppStorysOutreach"
    private const val EVENT_URL = "https://tracking.appstorys.co/capture-outreach-event"
    private const val TOKEN_URL = "https://users.appstorys.co/update-user-device-token"

    private const val PREFS = "appstorys_outreach"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_DEVICE_TOKEN = "device_push_token"
    private const val KEY_OUTREACH_TOKEN = "outreach_access_token"
    private const val KEY_QUEUE = "pending_queue"
    private const val MAX_QUEUE_SIZE = 100

    private const val KEY_SDK_ACCESS_TOKEN = "sdk_access_token"

    private val executor = Executors.newSingleThreadExecutor()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Called by AppStorys.initialize / setUserId once a user_id is known. */
    fun saveUserId(context: Context, userId: String) {
        try {
            val p = prefs(context)
            val previous = p.getString(KEY_USER_ID, null)
            p.edit { putString(KEY_USER_ID, userId) }
            // user_id changed → outreach token was bound to the old user, drop it.
            if (previous != null && previous != userId) {
                p.edit { remove(KEY_OUTREACH_TOKEN) }
                Log.i(TAG, "user_id changed, outreach token invalidated")
            }
        } catch (e: Exception) {
            Log.e(TAG, "saveUserId failed", e)
        }
    }

    /** Called by AppStorys.initialize once the validate-account access token is available. */
    fun saveSdkAccessToken(context: Context, accessToken: String) {
        try {
            prefs(context).edit { putString(KEY_SDK_ACCESS_TOKEN, accessToken) }
        } catch (e: Exception) {
            Log.e(TAG, "saveSdkAccessToken failed", e)
        }
    }

    /**
     * Called from AppStorys.setFirebaseToken. Persists the device token and
     * fetches an outreach access token IF we don't already have one cached.
     * Per spec: token is valid ~30 days; we only refresh on rejection.
     */
    fun ensureAccessToken(context: Context, userId: String, fcmToken: String) {
        executor.execute {
            try {
                val p = prefs(context)
                p.edit {
                    putString(KEY_USER_ID, userId)
                    putString(KEY_DEVICE_TOKEN, fcmToken)
                }
                val cached = p.getString(KEY_OUTREACH_TOKEN, null)
                if (cached.isNullOrBlank()) {
                    Log.d(TAG, "No cached outreach token — calling update-user-device-token")
                    fetchAndStoreAccessToken(p, userId, fcmToken)
                } else {
                    Log.d(TAG, "Outreach access token cached, reusing (no network call)")
                }
                drainPendingQueueLocked(p)
            } catch (e: Exception) {
                Log.e(TAG, "ensureAccessToken failed", e)
            }
        }
    }

    /** Async fire-and-forget. Used by AppStorysMessagingService. */
    fun fireEvent(context: Context, notificationId: String, event: String) {
        executor.execute { fireEventBlocking(context, notificationId, event) }
    }

    /** Synchronous variant — for BroadcastReceiver.goAsync() worker thread. */
    fun fireEventBlocking(context: Context, notificationId: String, event: String) {
        try {
            if (notificationId.isBlank()) {
                Log.w(TAG, "fireEvent: blank notificationId")
                return
            }
            if (event != "viewed" && event != "clicked") {
                Log.w(TAG, "fireEvent: invalid event '$event'")
                return
            }

            val p = prefs(context)
            val userId = p.getString(KEY_USER_ID, null)
            if (userId.isNullOrBlank()) {
                queueEvent(p, notificationId, event)
                Log.w(TAG, "No user_id yet — queued $event for $notificationId")
                return
            }

            var token = p.getString(KEY_OUTREACH_TOKEN, null)
            if (token.isNullOrBlank()) {
                // No outreach token cached — try to mint one if we have an FCM token.
                val fcm = p.getString(KEY_DEVICE_TOKEN, null)
                if (fcm.isNullOrBlank()) {
                    queueEvent(p, notificationId, event)
                    Log.w(TAG, "No fcm token on disk — queued $event")
                    return
                }
                token = fetchAndStoreAccessToken(p, userId, fcm)
                if (token == null) {
                    queueEvent(p, notificationId, event)
                    return
                }
            }

            when (sendEvent(userId, token, notificationId, event)) {
                SendResult.OK -> { /* done */ }
                SendResult.UNAUTHORIZED -> {
                    // Token rejected — refetch once and retry.
                    Log.w(TAG, "Outreach token rejected (401/403), refetching")
                    p.edit { remove(KEY_OUTREACH_TOKEN) }
                    val fcm = p.getString(KEY_DEVICE_TOKEN, null)
                    if (fcm.isNullOrBlank()) {
                        queueEvent(p, notificationId, event)
                        return
                    }
                    val fresh = fetchAndStoreAccessToken(p, userId, fcm)
                    if (fresh == null) {
                        queueEvent(p, notificationId, event)
                        return
                    }
                    if (sendEvent(userId, fresh, notificationId, event) != SendResult.OK) {
                        queueEvent(p, notificationId, event)
                    }
                }
                SendResult.OTHER_ERROR -> queueEvent(p, notificationId, event)
            }
        } catch (e: Exception) {
            Log.e(TAG, "fireEventBlocking failed", e)
            try { queueEvent(prefs(context), notificationId, event) } catch (_: Exception) { }
        }
    }

    fun drainPendingQueue(context: Context) {
        executor.execute {
            try { drainPendingQueueLocked(prefs(context)) }
            catch (e: Exception) { Log.e(TAG, "drainPendingQueue failed", e) }
        }
    }

    // ---------- internals ----------

    private enum class SendResult { OK, UNAUTHORIZED, OTHER_ERROR }

    private fun sendEvent(
        userId: String,
        outreachToken: String,
        notificationId: String,
        event: String
    ): SendResult {
        return try {
            val body = JSONObject().apply {
                put("user_id", userId)
                put("campaign_id", notificationId)
                put("event", event)
            }
            val request = Request.Builder()
                .url(EVENT_URL)
                .post(body.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .addHeader("Authorization", "Bearer $outreachToken")
                .build()
            httpClient.newCall(request).execute().use { response ->
                Log.i(TAG, "Outreach $event → HTTP ${response.code} (notif=$notificationId)")
                when {
                    response.isSuccessful -> SendResult.OK
                    response.code == 401 || response.code == 403 -> SendResult.UNAUTHORIZED
                    else -> SendResult.OTHER_ERROR
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendEvent failed: ${e.message}", e)
            SendResult.OTHER_ERROR
        }
    }

    private fun fetchAndStoreAccessToken(
        prefs: SharedPreferences,
        userId: String,
        fcmToken: String
    ): String? {
        return try {
            val sdkAccessToken = prefs.getString(KEY_SDK_ACCESS_TOKEN, null)
            if (sdkAccessToken.isNullOrBlank()) {
                // Can happen on a fresh install if a notification arrives before
                // initialize() has ever completed. Next app launch will persist
                // the SDK token and the queued event will drain.
                Log.w(TAG, "No SDK access token cached — cannot call update-user-device-token yet")
                return null
            }

            val body = JSONObject().apply {
                put("user_id", userId)
                put("device_push_token", fcmToken)
            }
            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(body.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .addHeader("Authorization", "Bearer $sdkAccessToken")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "update-user-device-token failed: HTTP ${response.code}")
                    // If even the SDK access token is rejected (401/403), drop it from disk —
                    // next initialize() will refresh it and on-disk creds will be valid again.
                    if (response.code == 401 || response.code == 403) {
                        prefs.edit { remove(KEY_SDK_ACCESS_TOKEN) }
                    }
                    return null
                }
                val text = response.body?.string().orEmpty()
                val token = JSONObject(text).optString("long_lived_token", "")
                if (token.isBlank()) {
                    Log.e(TAG, "update-user-device-token: no long_lived_token in response")
                    return null
                }
                prefs.edit { putString(KEY_OUTREACH_TOKEN, token) }
                Log.i(TAG, "update-user-device-token: cached new outreach access token")
                token
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchAndStoreAccessToken failed: ${e.message}", e)
            null
        }
    }

    private fun queueEvent(p: SharedPreferences, notificationId: String, event: String) {
        try {
            val existing = p.getString(KEY_QUEUE, null)
            val queue = if (existing != null) JSONArray(existing) else JSONArray()
            if (queue.length() >= MAX_QUEUE_SIZE) {
                Log.w(TAG, "Queue full ($MAX_QUEUE_SIZE), dropping event")
                return
            }
            queue.put(JSONObject().apply {
                put("notification_id", notificationId)
                put("event", event)
                put("ts", System.currentTimeMillis())
            })
            p.edit { putString(KEY_QUEUE, queue.toString()) }
        } catch (e: Exception) {
            Log.e(TAG, "queueEvent failed", e)
        }
    }

    private fun drainPendingQueueLocked(p: SharedPreferences) {
        val raw = p.getString(KEY_QUEUE, null) ?: return
        val userId = p.getString(KEY_USER_ID, null) ?: return
        var token = p.getString(KEY_OUTREACH_TOKEN, null) ?: return
        val queue = JSONArray(raw)
        if (queue.length() == 0) return

        val remaining = JSONArray()
        var sent = 0
        for (i in 0 until queue.length()) {
            val item = queue.optJSONObject(i) ?: continue
            val nId = item.optString("notification_id")
            val ev = item.optString("event")
            if (nId.isBlank() || ev.isBlank()) continue

            when (sendEvent(userId, token, nId, ev)) {
                SendResult.OK -> sent++
                SendResult.UNAUTHORIZED -> {
                    // refetch once mid-drain
                    val fcm = p.getString(KEY_DEVICE_TOKEN, null)
                    if (!fcm.isNullOrBlank()) {
                        p.edit { remove(KEY_OUTREACH_TOKEN) }
                        val fresh = fetchAndStoreAccessToken(p, userId, fcm)
                        if (fresh != null) {
                            token = fresh
                            if (sendEvent(userId, token, nId, ev) == SendResult.OK) sent++
                            else remaining.put(item)
                        } else remaining.put(item)
                    } else remaining.put(item)
                }
                SendResult.OTHER_ERROR -> remaining.put(item)
            }
        }
        p.edit { putString(KEY_QUEUE, remaining.toString()) }
        Log.i(TAG, "Drained outreach queue: $sent sent, ${remaining.length()} retained")
    }
}
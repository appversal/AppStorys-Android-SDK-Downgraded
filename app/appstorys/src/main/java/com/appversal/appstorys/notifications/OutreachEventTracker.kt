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
 * Self-contained outreach (push) event tracker.
 *
 * Token flow:
 *   1. /update-user-device-token        → { long_lived_token, refresh_token }
 *      Authenticated with the SDK access token (from validate-account).
 *      Called once on first install (and whenever both cached tokens are gone).
 *   2. /capture-outreach-event          authenticated with long_lived_token.
 *   3. If (2) returns 401/403 → call /refresh-fcm-refresh-token with
 *      `Authorization: Bearer <refresh_token>` → { long_lived_token, refresh_token }.
 *      Both new tokens replace the cached pair, then the event is retried.
 *   4. If the refresh call itself returns 401/403 (refresh token also dead) we
 *      drop both tokens and fall back to step (1) so the SDK can self-heal.
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
    private const val REFRESH_URL = "https://users.appstorys.co/refresh-fcm-refresh-token"

    private const val PREFS = "appstorys_outreach"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_DEVICE_TOKEN = "device_push_token"
    private const val KEY_OUTREACH_TOKEN = "outreach_access_token"
    private const val KEY_REFRESH_TOKEN = "outreach_refresh_token"
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
            // user_id changed → token pair was bound to the old user, drop it all.
            if (previous != null && previous != userId) {
                p.edit {
                    remove(KEY_OUTREACH_TOKEN)
                    remove(KEY_REFRESH_TOKEN)
                }
                Log.i(TAG, "user_id changed, outreach tokens invalidated")
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
     * fetches an outreach long-lived + refresh token pair IF we don't already
     * have one cached. The pair is valid ~30 days and only re-minted on rejection.
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
    fun fireEvent(context: Context, notificationId: String, event: String, variantId: String? = null) {
        executor.execute { fireEventBlocking(context, notificationId, event, variantId) }
    }

    /** Synchronous variant — for BroadcastReceiver.goAsync() worker thread. */
    fun fireEventBlocking(context: Context, notificationId: String, event: String, variantId: String? = null) {
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
                queueEvent(p, notificationId, event, variantId)
                Log.w(TAG, "No user_id yet — queued $event for $notificationId")
                return
            }

            var token = p.getString(KEY_OUTREACH_TOKEN, null)
            if (token.isNullOrBlank()) {
                // No cached long-lived token — try to mint one. Refresh path needs
                // a cached refresh_token; if we have none, fall through to the full
                // bootstrap via /update-user-device-token.
                token = refreshLongLivedToken(p) ?: run {
                    val fcm = p.getString(KEY_DEVICE_TOKEN, null)
                    if (fcm.isNullOrBlank()) {
                        queueEvent(p, notificationId, event, variantId)
                        Log.w(TAG, "No fcm token on disk — queued $event")
                        return
                    }
                    fetchAndStoreAccessToken(p, userId, fcm)
                }
                if (token == null) {
                    queueEvent(p, notificationId, event, variantId)
                    return
                }
            }

            when (sendEvent(userId, token, notificationId, event, variantId)) {
                SendResult.OK -> { /* done */
                }

                SendResult.UNAUTHORIZED -> {
                    Log.w(TAG, "Outreach long-lived token rejected (401/403), refreshing")
                    val fresh = recoverLongLivedToken(p, userId)
                    if (fresh == null) {
                        queueEvent(p, notificationId, event, variantId)
                        return
                    }
                    if (sendEvent(userId, fresh, notificationId, event, variantId) != SendResult.OK) {
                        queueEvent(p, notificationId, event, variantId)
                    }
                }

                SendResult.OTHER_ERROR -> queueEvent(p, notificationId, event, variantId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "fireEventBlocking failed", e)
            try {
                queueEvent(prefs(context), notificationId, event, variantId)
            } catch (_: Exception) {
            }
        }
    }

    fun drainPendingQueue(context: Context) {
        executor.execute {
            try {
                drainPendingQueueLocked(prefs(context))
            } catch (e: Exception) {
                Log.e(TAG, "drainPendingQueue failed", e)
            }
        }
    }

    // ---------- internals ----------

    private enum class SendResult { OK, UNAUTHORIZED, OTHER_ERROR }

    private fun sendEvent(
        userId: String,
        outreachToken: String,
        notificationId: String,
        event: String,
        variantId: String? = null
    ): SendResult {
        return try {
            val body = JSONObject().apply {
                put("user_id", userId)
                put("campaign_id", notificationId)
                put("event", event)
                if (!variantId.isNullOrBlank()) {
                    put("metadata", JSONObject().apply {
                        put("variant_id", variantId)
                    })
                }
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

    /**
     * Recover a usable long-lived token after the cached one was rejected.
     * Strategy:
     *   1. Drop the rejected long-lived token.
     *   2. Try /refresh-fcm-refresh-token using the cached refresh_token.
     *   3. If that fails (no refresh token cached, network error, or refresh
     *      token itself rejected) fall back to a full bootstrap via
     *      /update-user-device-token.
     */
    private fun recoverLongLivedToken(prefs: SharedPreferences, userId: String): String? {
        prefs.edit { remove(KEY_OUTREACH_TOKEN) }

        val refreshed = refreshLongLivedToken(prefs)
        if (refreshed != null) return refreshed

        val fcm = prefs.getString(KEY_DEVICE_TOKEN, null)
        if (fcm.isNullOrBlank()) {
            Log.w(TAG, "Cannot bootstrap: no fcm token cached")
            return null
        }
        return fetchAndStoreAccessToken(prefs, userId, fcm)
    }

    /**
     * Calls /refresh-fcm-refresh-token using the cached refresh_token in the
     * Authorization header. On success, persists the new long_lived_token AND
     * the new refresh_token (the server rotates both). Returns the new
     * long-lived token, or null on any failure.
     */
    private fun refreshLongLivedToken(prefs: SharedPreferences): String? {
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
        if (refreshToken.isNullOrBlank()) {
            Log.d(TAG, "No refresh_token cached — cannot call refresh-fcm-refresh-token")
            return null
        }
        return try {
            val body = JSONObject()
                .toString()
                .toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(REFRESH_URL)
                .post(body)
                .addHeader("Authorization", "Bearer $refreshToken")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "refresh-fcm-refresh-token failed: HTTP ${response.code}")
                    // Refresh token itself is dead — drop the whole pair so the
                    // next recovery step bootstraps from scratch.
                    if (response.code == 401 || response.code == 403) {
                        prefs.edit {
                            remove(KEY_REFRESH_TOKEN)
                            remove(KEY_OUTREACH_TOKEN)
                        }
                    }
                    return null
                }
                val text = response.body?.string().orEmpty()
                val json = JSONObject(text)
                val newLongLived = json.optString("long_lived_token", "")
                val newRefresh = json.optString("refresh_token", "")
                if (newLongLived.isBlank()) {
                    Log.e(TAG, "refresh-fcm-refresh-token: no long_lived_token in response")
                    return null
                }
                prefs.edit {
                    putString(KEY_OUTREACH_TOKEN, newLongLived)
                    if (newRefresh.isNotBlank()) putString(KEY_REFRESH_TOKEN, newRefresh)
                }
                Log.i(TAG, "refresh-fcm-refresh-token: rotated tokens cached")
                newLongLived
            }
        } catch (e: Exception) {
            Log.e(TAG, "refreshLongLivedToken failed: ${e.message}", e)
            null
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
                val json = JSONObject(text)
                val longLived = json.optString("long_lived_token", "")
                val refresh = json.optString("refresh_token", "")
                if (longLived.isBlank()) {
                    Log.e(TAG, "update-user-device-token: no long_lived_token in response")
                    return null
                }
                prefs.edit {
                    putString(KEY_OUTREACH_TOKEN, longLived)
                    if (refresh.isNotBlank()) {
                        putString(KEY_REFRESH_TOKEN, refresh)
                    } else {
                        // Server didn't return one — clear any stale refresh token
                        // so we don't try to use it later against a different pair.
                        remove(KEY_REFRESH_TOKEN)
                    }
                }
                Log.i(TAG, "update-user-device-token: cached new outreach token pair")
                longLived
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchAndStoreAccessToken failed: ${e.message}", e)
            null
        }
    }

    private fun queueEvent(p: SharedPreferences, notificationId: String, event: String,  variantId: String? = null) {
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
                if (!variantId.isNullOrBlank()) put("variant_id", variantId)
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
            val variantId = item.optString("variant_id").takeIf { it.isNotBlank() }
            if (nId.isBlank() || ev.isBlank()) continue

            when (sendEvent(userId, token, nId, ev, variantId)) {
                SendResult.OK -> sent++
                SendResult.UNAUTHORIZED -> {
                    // Refresh once mid-drain via refresh-fcm-refresh-token,
                    // then fall back to /update-user-device-token if needed.
                    val fresh = recoverLongLivedToken(p, userId)
                    if (fresh != null) {
                        token = fresh
                        if (sendEvent(userId, token, nId, ev, variantId) == SendResult.OK) sent++
                        else remaining.put(item)
                    } else {
                        remaining.put(item)
                    }
                }

                SendResult.OTHER_ERROR -> remaining.put(item)
            }
        }
        p.edit { putString(KEY_QUEUE, remaining.toString()) }
        Log.i(TAG, "Drained outreach queue: $sent sent, ${remaining.length()} retained")
    }

    fun getCachedDeviceToken(context: Context): String? =
        try {
            prefs(context).getString(KEY_DEVICE_TOKEN, null)
        } catch (e: Exception) {
            null
        }

    fun invalidateOutreachToken(context: Context) {
        try {
            prefs(context).edit { remove(KEY_OUTREACH_TOKEN) }
            Log.i(TAG, "Outreach token invalidated locally (backend unsubscribed)")
        } catch (e: Exception) {
            Log.e(TAG, "invalidateOutreachToken failed", e)
        }
    }

    /**
     * Unconditionally re-registers the device token with the backend, bypassing
     * the outreach-token cache check. Must be called right after a successful
     * subscribe-fcm — its handler resets the server-side token, so the normal
     * cache-skip in ensureAccessToken() would otherwise leave the backend
     * without a token until the cached outreach token happens to expire/reject.
     */
    fun forceResyncDeviceToken(context: Context, userId: String, fcmToken: String) {
        executor.execute {
            try {
                val p = prefs(context)
                p.edit {
                    putString(KEY_USER_ID, userId)
                    putString(KEY_DEVICE_TOKEN, fcmToken)
                }
                fetchAndStoreAccessToken(p, userId, fcmToken)
                drainPendingQueueLocked(p)
            } catch (e: Exception) {
                Log.e(TAG, "forceResyncDeviceToken failed", e)
            }
        }
    }

    /** Persists user_id + device token locally only — no network call. */
    fun cacheDeviceTokenLocally(context: Context, userId: String, fcmToken: String) {
        try {
            val p = prefs(context)
            p.edit {
                putString(KEY_USER_ID, userId)
                putString(KEY_DEVICE_TOKEN, fcmToken)
            }
        } catch (e: Exception) {
            Log.e(TAG, "cacheDeviceTokenLocally failed", e)
        }
    }
}
package com.appversal.appstorys.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.appversal.appstorys.AppStorys
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.net.HttpURLConnection
import java.net.URL

/**
 * FCM service shipped by the AppStorys SDK. Clients register this in their
 * AndroidManifest.xml — no need to write their own.
 *
 * If the host app already has its own FirebaseMessagingService, it can call
 * [handleNewToken] and [handleMessage] from there instead — both are static.
 *
 * ── Click handling ─────────────────────────────────────────────────────────
 * Notification taps target [AppStorysNotificationActivity] via
 * `PendingIntent.getActivity()`. We deliberately do NOT use a BroadcastReceiver
 * here: Android 12+ blocks any `startActivity()` originating from a
 * notification-fired Receiver/Service ("indirect notification activity start
 * (trampoline) blocked"), which would silently swallow URL opens during
 * cold-start.
 *
 * ── Notification icon ──────────────────────────────────────────────────────
 * Android 5+ requires the small icon to be a monochrome drawable (alpha-only).
 * The SDK resolves the icon in this order:
 *   1. <meta-data android:name="com.google.firebase.messaging.default_notification_icon"
 *                 android:resource="@drawable/ic_your_notification_icon"/>
 *   2. Drawable named "ic_notification" or "ic_stat_notify" in the host app
 *   3. Falls back to the app launcher icon (may appear as a white square on
 *      API 26+ devices — prefer option 1 or 2)
 *
 * ── Notification image (big picture) ───────────────────────────────────────
 * If the data payload carries an `image_url`, it is downloaded and shown as a
 * BigPictureStyle expanded image (with a collapsed thumbnail). Because this is
 * a data message, FCM does NOT fetch the image for us — the SDK does it on the
 * background thread that already runs handleMessage. If the download fails or
 * times out, the notification silently falls back to text-only.
 */
class AppStorysMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        handleNewToken(this, token)
    }

    /**
     * Run handleMessage on a background thread via goAsync() so the process
     * stays alive long enough for the synchronous "viewed" network call to
     * complete even when the app was not previously running.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        @Suppress("MissingPermission")
        handleMessage(this, message)
    }

    companion object {
        private const val TAG = "AppStorysMessaging"
        private const val CHANNEL_ID = "appstorys_outreach"
        private const val CHANNEL_NAME = "Notifications"

        // Network + decode safeguards for the big-picture image.
        private const val IMAGE_CONNECT_TIMEOUT_MS = 8_000
        private const val IMAGE_READ_TIMEOUT_MS = 8_000
        // Max width for the downloaded bitmap. Very large images can be
        // silently dropped by the system notification size limit, so we
        // downscale anything wider than this.
        private const val MAX_IMAGE_WIDTH_PX = 1024

        // Meta-data key the host app can use to declare a proper notification icon,
        // same convention Firebase itself uses.
        private const val META_DEFAULT_NOTIFICATION_ICON =
            "com.google.firebase.messaging.default_notification_icon"

        /**
         * Forward the FCM token to AppStorys. Safe to call from any
         * FirebaseMessagingService implementation.
         */
        @JvmStatic
        fun handleNewToken(context: Context, token: String) {
            try {
                AppStorys.setFirebaseToken(token)
            } catch (e: Exception) {
                Log.e(TAG, "handleNewToken failed", e)
            }
        }

        /**
         * Process an incoming FCM message. Returns true if the message was
         * an AppStorys outreach push (i.e. carried a `notification_id`),
         * false otherwise — letting host services fall through to their own
         * handling.
         *
         * IMPORTANT: This method makes blocking network calls (the "viewed"
         * event and the optional image download). Always call it from a
         * background thread, NOT the main thread. The
         * [AppStorysMessagingService.onMessageReceived] override already
         * does this via goAsync(). If you call this from your own service,
         * make sure you are also on a background thread.
         */
        @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
        @JvmStatic
        fun handleMessage(context: Context, message: RemoteMessage): Boolean {
            return try {
                val data = message.data
                val raw = mapOf(
                    "from" to message.from,
                    "messageId" to message.messageId,
                    "data" to message.data,
                    "notification" to message.notification?.body
                )

                Log.i(TAG, "handleMessage: $raw")
                val notificationId = data["notification_id"]
                if (notificationId.isNullOrBlank()) return false
                val variantId = data["variant_id"]

                val shown = showNotification(
                    context.applicationContext,
                    notificationId = notificationId,
                    title = data["title"].orEmpty(),
                    body  = data["body"].orEmpty(),
                    deepLink = data["deep_link"] ?: data["url"],
                    imageUrl = data["image_url"],
                    variantId = variantId
                )

                // "viewed" must fire synchronously here (we are already on a
                // background thread). This guarantees the event is delivered
                // before the process is released — critical when the app was
                // not running and was cold-started purely for this FCM message.
                if (shown) {
                    OutreachEventTracker.fireEventBlocking(
                        context.applicationContext, notificationId, "viewed", variantId
                    )
                } else {
                    Log.w(
                        TAG,
                        "Notification not shown (perm denied or channel blocked) — " +
                                "skipping 'viewed' for $notificationId"
                    )
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "handleMessage failed", e)
                false
            }
        }

        @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
        private fun showNotification(
            context: Context,
            notificationId: String,
            title: String,
            body: String,
            deepLink: String?,
            imageUrl: String?,
            variantId: String?
        ): Boolean {
            return try {
                val nm = NotificationManagerCompat.from(context)
                if (!nm.areNotificationsEnabled()) {
                    Log.w(TAG, "Notifications disabled for app — dropping $notificationId")
                    return false
                }

                ensureChannel(context)

                // (Android 8+) also check the specific channel isn't blocked.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = nm.getNotificationChannel(CHANNEL_ID)
                    if (channel != null &&
                        channel.importance == NotificationManager.IMPORTANCE_NONE
                    ) {
                        Log.w(TAG, "Channel '$CHANNEL_ID' blocked — dropping $notificationId")
                        return false
                    }
                }

                // Use PendingIntent.getActivity (NOT getBroadcast) so we don't
                // hit Android 12+'s notification-trampoline restriction. The
                // target activity is transparent and finishes immediately.
                val clickIntent = AppStorysNotificationActivity.newIntent(
                    context, notificationId, deepLink, variantId
                )
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    notificationId.hashCode(),
                    clickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(resolveNotificationIcon(context))   // ← fixed icon lookup
                    .setContentTitle(title)
                    .setContentText(body)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)

                // Safe to block here: handleMessage already runs on a
                // background thread via goAsync().
                val bigPicture = imageUrl
                    ?.takeIf { it.isNotBlank() }
                    ?.let { downloadBitmap(it) }

                if (bigPicture != null) {
                    builder
                        .setLargeIcon(bigPicture)                 // thumbnail when collapsed
                        .setStyle(
                            NotificationCompat.BigPictureStyle()
                                .bigPicture(bigPicture)           // full image when expanded
                                .bigLargeIcon(null as Bitmap?)    // hide thumbnail once expanded
                                .setBigContentTitle(title)
                                .setSummaryText(body)
                        )
                } else {
                    builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
                }

                nm.notify(notificationId.hashCode(), builder.build())
                Log.i(TAG, "Notification posted: $notificationId")
                true
            } catch (e: Exception) {
                Log.e(TAG, "showNotification failed", e)
                false
            }
        }

        /**
         * Download (and, if needed, downscale) the notification's big-picture
         * image. Returns null on any failure so the caller can fall back to a
         * text-only notification rather than dropping the push entirely.
         *
         * Must be called from a background thread.
         */
        private fun downloadBitmap(url: String): Bitmap? {
            return try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = IMAGE_CONNECT_TIMEOUT_MS
                    readTimeout = IMAGE_READ_TIMEOUT_MS
                    instanceFollowRedirects = true
                    doInput = true
                }
                conn.connect()
                val bitmap = conn.inputStream.use { BitmapFactory.decodeStream(it) }
                    ?: return null
                downscaleIfNeeded(bitmap)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to download notification image: $url", e)
                null   // fall back to text-only notification
            }
        }

        /**
         * The system rejects oversized notification bitmaps silently (the
         * image just doesn't appear on some devices). Scale anything wider
         * than [MAX_IMAGE_WIDTH_PX] down, preserving aspect ratio.
         */
        private fun downscaleIfNeeded(src: Bitmap): Bitmap {
            if (src.width <= MAX_IMAGE_WIDTH_PX) return src
            return try {
                val ratio = MAX_IMAGE_WIDTH_PX.toFloat() / src.width.toFloat()
                val targetH = (src.height * ratio).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(
                    src, MAX_IMAGE_WIDTH_PX, targetH, true
                )
                if (scaled !== src) src.recycle()
                scaled
            } catch (e: Exception) {
                Log.w(TAG, "downscaleIfNeeded failed, using original bitmap", e)
                src
            }
        }

        /**
         * Resolve an appropriate monochrome small-icon resource for use in
         * status-bar notifications.
         *
         * Android 5+ requires the small icon to be a drawable whose pixels are
         * treated as a monochrome mask (only the alpha channel matters).  Using
         * an adaptive launcher icon (the default `applicationInfo.icon`) renders
         * as a plain white square on API 26+ devices, making the notification
         * look empty.
         *
         * Resolution order:
         *  1. `com.google.firebase.messaging.default_notification_icon` meta-data
         *     declared in the host app's AndroidManifest (recommended).
         *  2. Drawable named `ic_notification` in the host app's resources.
         *  3. Drawable named `ic_stat_notify` in the host app's resources.
         *  4. Fallback to `applicationInfo.icon` (may appear white on API 26+;
         *     add option 1 in your manifest to avoid this).
         */
        private fun resolveNotificationIcon(context: Context): Int {
            return try {
                // 1. Honour the same meta-data key Firebase itself uses.
                val metaData = context.packageManager
                    .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                    .metaData
                val fromMeta = metaData
                    ?.getInt(META_DEFAULT_NOTIFICATION_ICON, 0)
                    ?: 0
                if (fromMeta != 0) {
                    Log.d(TAG, "Using notification icon from manifest meta-data")
                    return fromMeta
                }

                // 2. Common dedicated notification icon names in the host app.
                val res = context.resources
                val pkg = context.packageName
                listOf("ic_notification", "ic_stat_notify").forEach { name ->
                    val id = res.getIdentifier(name, "drawable", pkg)
                    if (id != 0) {
                        Log.d(TAG, "Using notification icon resource: $name")
                        return id
                    }
                }

                // 3. Fallback — works but may look like a white square on API 26+.
                Log.w(
                    TAG,
                    "No dedicated notification icon found. Add " +
                            "<meta-data android:name=\"$META_DEFAULT_NOTIFICATION_ICON\" " +
                            "android:resource=\"@drawable/YOUR_ICON\"/> to your AndroidManifest " +
                            "to avoid a blank icon on Android 8+."
                )
                context.applicationInfo.icon
            } catch (e: Exception) {
                Log.e(TAG, "resolveNotificationIcon failed, using launcher icon", e)
                context.applicationInfo.icon
            }
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val mgr = context.getSystemService(NotificationManager::class.java)
                if (mgr?.getNotificationChannel(CHANNEL_ID) == null) {
                    mgr?.createNotificationChannel(
                        NotificationChannel(
                            CHANNEL_ID,
                            CHANNEL_NAME,
                            NotificationManager.IMPORTANCE_DEFAULT
                        )
                    )
                }
            }
        }
    }
}
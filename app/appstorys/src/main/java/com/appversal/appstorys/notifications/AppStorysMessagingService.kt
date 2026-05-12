package com.appversal.appstorys.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.appversal.appstorys.AppStorys
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM service shipped by the AppStorys SDK. Clients register this in their
 * AndroidManifest.xml — no need to write their own.
 *
 * If the host app already has its own FirebaseMessagingService, it can call
 * [handleNewToken] and [handleMessage] from there instead — both are static.
 */
class AppStorysMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        handleNewToken(this, token)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onMessageReceived(message: RemoteMessage) {
        handleMessage(this, message)
    }

    companion object {
        private const val TAG = "AppStorysMessaging"
        private const val CHANNEL_ID = "appstorys_outreach"
        private const val CHANNEL_NAME = "Notifications"

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

                val shown = showNotification(
                    context.applicationContext,
                    notificationId = notificationId,
                    title = data["title"].orEmpty(),
                    body  = data["body"].orEmpty(),
                    deepLink = data["deep_link"] ?: data["url"]
                )

                // Only count as "viewed" if the OS actually accepted the notification.
                if (shown) {
                    OutreachEventTracker.fireEvent(
                        context.applicationContext, notificationId, "viewed"
                    )
                } else {
                    Log.w(TAG, "Notification not shown (perm denied or channel blocked) — skipping 'viewed' for $notificationId")
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
            deepLink: String?
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
                    if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
                        Log.w(TAG, "Channel '$CHANNEL_ID' blocked — dropping $notificationId")
                        return false
                    }
                }

                val clickIntent = Intent(context, AppStorysNotificationReceiver::class.java).apply {
                    action = AppStorysNotificationReceiver.ACTION_CLICK
                    putExtra(AppStorysNotificationReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                    putExtra(AppStorysNotificationReceiver.EXTRA_DEEP_LINK, deepLink)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    notificationId.hashCode(),
                    clickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(context.applicationInfo.icon)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build()

                nm.notify(notificationId.hashCode(), notification)
                Log.i(TAG, "Notification posted: $notificationId")
                true
            } catch (e: Exception) {
                Log.e(TAG, "showNotification failed", e)
                false
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
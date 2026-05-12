package com.appversal.appstorys.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

class AppStorysNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action != ACTION_CLICK) return

            val notificationId = intent.getStringExtra(EXTRA_NOTIFICATION_ID)
            val deepLink = intent.getStringExtra(EXTRA_DEEP_LINK)

            // Launch the host app FIRST — this is a user-tap context, so the
            // start-activity-from-background restriction doesn't apply.
            launchHostApp(context.applicationContext, deepLink)

            if (notificationId.isNullOrBlank()) return

            // Send "clicked" on a worker thread; goAsync() keeps the receiver
            // process alive long enough for the HTTP call to finish (~10s max).
            val pendingResult = goAsync()
            Thread {
                try {
                    OutreachEventTracker.fireEventBlocking(
                        context.applicationContext, notificationId, "clicked"
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "fireEventBlocking error", e)
                } finally {
                    try { pendingResult.finish() } catch (_: Exception) { /* ignore */ }
                }
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "onReceive failed", e)
        }
    }

    private fun launchHostApp(context: Context, deepLink: String?) {
        try {
            val intent = if (!deepLink.isNullOrBlank()) {
                Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage(context.packageName)
                }
            } else {
                context.packageManager
                    .getLaunchIntentForPackage(context.packageName)
                    ?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
            }
            intent?.let { context.startActivity(it) }
        } catch (e: Exception) {
            Log.e(TAG, "launchHostApp failed", e)
        }
    }

    companion object {
        private const val TAG = "AppStorysReceiver"
        const val ACTION_CLICK = "com.appversal.appstorys.NOTIFICATION_CLICK"
        const val EXTRA_NOTIFICATION_ID = "appstorys_notification_id"
        const val EXTRA_DEEP_LINK = "appstorys_deep_link"
    }
}
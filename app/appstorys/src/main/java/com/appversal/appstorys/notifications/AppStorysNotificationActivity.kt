package com.appversal.appstorys.notifications

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log

/**
 * Transparent trampoline activity used as the click target for AppStorys
 * outreach notifications.
 *
 * ## Why an Activity and not a BroadcastReceiver
 *
 * Android 12 (API 31) blocks **notification trampolines** — when a notification
 * `PendingIntent` fires a `BroadcastReceiver` or `Service` that then calls
 * `startActivity()`. The system logs:
 *
 *     "Indirect notification activity start (trampoline) … blocked"
 *
 * and silently drops the start. Symptom: notifications fire the
 * tracking event but never open the URL when the app was killed.
 *
 * Activities are NOT subject to that restriction — starting an activity
 * from an activity launched by a notification PendingIntent is allowed.
 *
 * ## Lifecycle
 *
 * 1. `onCreate` reads the extras and calls `startActivity(target)` immediately.
 * 2. The "clicked" event is sent on a worker thread; the activity stays alive
 *    (transparent, off-screen) until the network call finishes. This keeps the
 *    process alive long enough to deliver the event even on cold start, same
 *    semantics the old `BroadcastReceiver.goAsync()` flow provided.
 * 3. `finish()` is called from the worker thread when the event completes
 *    (success or failure); the activity is then removed from the task and the
 *    system can reap the process.
 *
 * ## Required manifest declaration
 *
 * Add to the library (or host) `AndroidManifest.xml`:
 *
 *     <activity
 *         android:name="com.appversal.appstorys.notifications.AppStorysNotificationActivity"
 *         android:exported="false"
 *         android:theme="@android:style/Theme.Translucent.NoTitleBar"
 *         android:noHistory="true"
 *         android:excludeFromRecents="true"
 *         android:taskAffinity=""
 *         android:launchMode="singleTop" />
 */
class AppStorysNotificationActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No window animation — we want the browser/host activity to come up
        // as if the trampoline weren't there.
        overridePendingTransition(0, 0)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTop reuses the instance — still act on the new tap.
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val notificationId = intent?.getStringExtra(EXTRA_NOTIFICATION_ID)
        val deepLink = intent?.getStringExtra(EXTRA_DEEP_LINK)

        // 1. Launch the target FIRST. startActivity is synchronous-enqueue and
        //    the browser/host activity comes to the foreground immediately;
        //    we keep ourselves transparent so the user never sees a flash.
        try {
            launchTarget(deepLink)
        } catch (e: Exception) {
            Log.e(TAG, "launchTarget failed", e)
        }

        // 2. Fire the "clicked" event on a worker thread; keep the activity
        //    alive until it completes so the process doesn't get reaped
        //    mid-flight during cold-start.
        if (notificationId.isNullOrBlank()) {
            finish()
            overridePendingTransition(0, 0)
            return
        }

        Thread {
            try {
                OutreachEventTracker.fireEventBlocking(
                    applicationContext, notificationId, "clicked"
                )
            } catch (e: Exception) {
                Log.e(TAG, "fireEventBlocking failed", e)
            } finally {
                runOnUiThread {
                    if (!isFinishing) finish()
                    overridePendingTransition(0, 0)
                }
            }
        }.start()
    }

    /**
     *  - No link                                → launcher activity
     *  - http / https URL                       → ALWAYS the system browser
     *  - Custom scheme (myapp://…, mailto:, tel:, etc.) → in-app first,
     *    then external, then launcher fallback.
     *  - Non-URI payload (no scheme)            → launcher activity with the
     *    payload forwarded as an intent extra.
     */
    private fun launchTarget(deepLink: String?) {
        if (deepLink.isNullOrBlank()) {
            launchLauncher(null)
            return
        }

        val uri = runCatching { Uri.parse(deepLink) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()

        if (uri == null || scheme.isNullOrBlank()) {
            launchLauncher(deepLink)
            return
        }

        if (scheme == "http" || scheme == "https") {
            if (startExternal(uri)) return
            Log.w(TAG, "Failed to open URL '$deepLink' externally — opening app launcher")
            launchLauncher(deepLink)
            return
        }

        // Custom schemes: prefer in-app handling.
        val inApp = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage(packageName)
        }
        if (inApp.resolveActivity(packageManager) != null) {
            if (runCatching { startActivity(inApp) }.isSuccess) return
        }

        if (startExternal(uri)) return

        Log.w(TAG, "No handler for deep link '$deepLink' — opening app launcher")
        launchLauncher(deepLink)
    }

    private fun startExternal(uri: Uri): Boolean {
        val external = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT
            )
        }
        return runCatching { startActivity(external) }.isSuccess
    }

    private fun launchLauncher(payload: String?) {
        try {
            val launch = packageManager
                .getLaunchIntentForPackage(packageName)
                ?.apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                    if (!payload.isNullOrBlank()) putExtra(EXTRA_DEEP_LINK, payload)
                }
            launch?.let { startActivity(it) }
        } catch (e: Exception) {
            Log.e(TAG, "launchLauncher failed", e)
        }
    }

    companion object {
        private const val TAG = "AppStorysClickActivity"
        const val EXTRA_NOTIFICATION_ID = "appstorys_notification_id"
        const val EXTRA_DEEP_LINK = "appstorys_deep_link"

        /** Build the Intent that the notification PendingIntent will fire. */
        fun newIntent(
            context: Context,
            notificationId: String,
            deepLink: String?
        ): Intent {
            return Intent(context, AppStorysNotificationActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_DEEP_LINK, deepLink)
            }
        }
    }
}
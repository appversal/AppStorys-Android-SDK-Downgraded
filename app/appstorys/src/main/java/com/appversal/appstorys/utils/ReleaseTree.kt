package com.appversal.appstorys.utils

import android.annotation.SuppressLint
import android.util.Log
import timber.log.Timber

/**
 * A Timber Tree designed for release builds:
 * 1. Logs W, E, and F messages to the Android Logcat (for client debugging).
 * 2. Sends W, E, and F messages to a remote crash/analytics service.
 * 3. Ignores DEBUG and INFO logs completely.
 */
internal class ReleaseTree : Timber.Tree() {

    // Only logs messages with priority WARNING (5) or higher
    override fun isLoggable(tag: String?, priority: Int): Boolean {
        return priority >= Log.WARN
    }

    @SuppressLint("LogNotTimber")
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {

        // Use a concise default tag if none is provided
        val logTag = tag ?: "AppStorys"

        // 1. Conditionally log to Android's built-in Logcat
        when (priority) {
            Log.WARN -> Log.w(logTag, message, t)
            Log.ERROR -> Log.e(logTag, message, t)
            Log.ASSERT -> Log.wtf(logTag, message, t)
            // Other priorities (D, I, V) are ignored due to isLoggable check
        }

        // 2. Perform remote logging (e.g., Crashlytics) for high priority events
//        if (priority >= android.util.Log.ERROR) {
        // Example: Send errors/exceptions to your remote service
        // RemoteCrashService.logError(logTag, message, t)
//        }
    }
}
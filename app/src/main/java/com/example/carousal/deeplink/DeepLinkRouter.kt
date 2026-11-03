package com.example.carousal.deeplink

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.navigation.NavController

/**
 * One request to open a link, from wherever it came: "intent" (adb, browser, other apps) or
 * "sdk" (a campaign CTA). [params] is set only for a key-value redirect, whose key is [raw].
 */
data class LinkRequest(val raw: String, val source: String, val params: Map<String, Any?>? = null)

/** The only place that turns a link into navigation, so every source behaves the same. */
object DeepLinkRouter {
    private const val TAG = "DeepLinkTest"

    fun open(nav: NavController, context: Context, request: LinkRequest, isLoggedIn: Boolean) {
        val destination = request.params?.let { DeepLinkParser.parseKeyValue(request.raw, it) }
            ?: DeepLinkParser.parse(request.raw).takeIf { request.params == null }
        // Quoted so stray whitespace or quotes the SDK passed along are visible in logcat.
        val shown = "'${request.raw}'" + (request.params?.let { " $it" } ?: "")
        if (destination == null) {
            Log.w(TAG, "[${request.source}] unhandled: $shown")
            Toast.makeText(context, "Unhandled link: $shown", Toast.LENGTH_SHORT).show()
            return
        }

        // A gated screen goes through login first; the target rides along in the route,
        // so it survives rotation and process death with the back stack.
        val route = if (destination.requiresLogin && !isLoggedIn) {
            "login?next=${Uri.encode(destination.route)}"
        } else {
            destination.route
        }
        Log.i(TAG, "[${request.source}] $shown -> $route")

        nav.navigate(route) {
            popUpTo(Routes.HOME)      // Home always sits under a linked screen, so Back lands there
            launchSingleTop = true    // the same link twice doesn't stack two copies
        }
    }
}

package com.appversal.appstorys.domain.usecase

import android.content.Context
import android.content.Intent
import android.webkit.URLUtil.isValidUrl
import androidx.core.net.toUri
import org.json.JSONObject
import timber.log.Timber

internal object ClickEvent {
    private var navigate: ((String) -> Unit)? = null

    fun initialize(navigator: (String) -> Unit) {
        this.navigate = navigator
    }

    operator fun invoke(
        context: Context,
        link: Any?,
        campaignId: String? = null,
        widgetImageId: String? = null
    ): Boolean {
        val handled = when {
            link != null && link is String && link.isNotBlank() -> {
                if (!isValidUrl(link)) {
                    navigate?.invoke(link)
                } else {
                    openUrl(context, link)
                }
                true
            }

            link is Map<*, *> -> {
                handleDeepLink(JSONObject(link))
                true
            }

            link is JSONObject -> {
                handleDeepLink(link)
                true
            }

            else -> false
        }

        if (handled && campaignId != null) {
            launchTask {
                trackEvent(
                    context,
                    "clicked",
                    campaignId,
                    widgetImageId?.let { mapOf("widget_image_id" to it) }
                )
            }
        }

        return handled
    }

    private fun openUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun handleDeepLink(json: JSONObject) {
        try {
            val value = json.optString("value", "")
            if (!value.isNullOrBlank()) {
                navigate?.invoke(value)
            }
        } catch (e: Exception) {
            Timber.tag("ClickEvent").e(e)
        }
    }
}
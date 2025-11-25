package com.appversal.appstorys.domain.usecase

import android.app.Application
import android.content.Intent
import android.webkit.URLUtil.isValidUrl
import androidx.core.net.toUri
import com.appversal.appstorys.AppStorys
import org.json.JSONObject
import timber.log.Timber

internal object ClickEvent {
    private var application: Application? = null
    private var navigate: ((String) -> Unit)? = null

    fun initialize(application: Application, navigator: (String) -> Unit) {
        this.application = application
        this.navigate = navigator
    }

    operator fun invoke(link: Any?, campaignId: String? = null, widgetImageId: String? = null) {
        val handled = when {
            link != null && link is String && link.isNotBlank() -> {
                if (!isValidUrl(link)) {
                    navigate?.invoke(link)
                } else {
                    openUrl(link)
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
            AppStorys.trackEvent(
                campaignId,
                "clicked",
                widgetImageId?.let { mapOf("widget_image_id" to it) }
            )
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            application?.startActivity(intent)
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
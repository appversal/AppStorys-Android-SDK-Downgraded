package com.example.carousal.deeplink

import java.net.URI
import java.net.URLDecoder

/** The three pages of the Pay screen, addressable as appstorys://demo/pay/<slug>. */
enum class PayTab(val slug: String, val page: Int) {
    CASHBOOK("cashbook", 0), BILLS("bills", 1), ITEMS("items", 2);

    companion object {
        fun fromSlug(slug: String?): PayTab? = values().firstOrNull { it.slug.equals(slug, ignoreCase = true) }
    }
}

/** NavHost route patterns, one per screen. */
object Routes {
    const val HOME = "home"
    const val PAY = "pay?tab={tab}"
    const val PRODUCT = "product/{productId}"
    const val OFFERS = "offers?code={code}"
    const val PROFILE = "profile"
    const val LOGIN = "login?next={next}"
}

/** Every screen a link can open. [route] is the filled-in NavHost route that shows it. */
sealed class Destination(val route: String, val requiresLogin: Boolean = false) {
    object Home : Destination(Routes.HOME)
    data class Pay(val tab: PayTab) : Destination("pay?tab=${tab.slug}")
    data class Product(val id: String) : Destination("product/$id")
    data class Offers(val code: String?) : Destination(if (code == null) "offers" else "offers?code=$code")
    object Profile : Destination(Routes.PROFILE, requiresLogin = true)
}

/**
 * Turns any link string into a [Destination], or null when the app must not open it.
 * Pure Kotlin (java.net.URI, not android.net.Uri) so it runs in plain JVM unit tests.
 *
 * Strict on purpose, like a production host: no trimming or quote-stripping, so a link the
 * SDK delivers malformed shows up as "unhandled" instead of being quietly repaired here.
 */
object DeepLinkParser {
    const val SCHEME = "appstorys"
    const val HOST = "demo"
    const val WEB_HOST = "demo.appstorys.com"

    // This app's own screen names (the ones it passes to getScreenCampaigns), which the
    // dashboard offers as "page" redirects.
    private val screenNames = mapOf(
        "homescreen" to "/home",
        "home screen kotlin" to "/home",
        "payscreen" to "/pay",
        "cashbook tab" to "/pay/cashbook",
        "bills tab" to "/pay/bills",
        "items tab" to "/pay/items",
    )

    // Everything below comes from outside the app: validate it, never trust it.
    private val productId = Regex("[A-Za-z0-9_-]{1,32}")
    private val offerCode = Regex("[A-Z0-9]{3,20}")

    fun parse(link: String): Destination? {
        if (link.isEmpty()) return null

        screenNames[link.lowercase()]?.let { return fromPath(it, emptyMap()) }

        val uri = runCatching { URI(link) }.getOrNull() ?: return null
        val trusted = when (uri.scheme?.lowercase()) {
            SCHEME -> uri.host.equals(HOST, ignoreCase = true)
            "https" -> uri.host.equals(WEB_HOST, ignoreCase = true)
            else -> false
        }
        if (!trusted) return null

        return fromPath(uri.path.orEmpty(), queryOf(uri.rawQuery))
    }
    private fun fromPath(path: String, query: Map<String, String>): Destination? {
        val segments = path.split('/').filter { it.isNotEmpty() }
        return when (segments.firstOrNull()?.lowercase()) {
            null, "home" -> Destination.Home
            "pay" -> Destination.Pay(PayTab.fromSlug(segments.getOrNull(1)) ?: PayTab.CASHBOOK)
            "product" -> segments.getOrNull(1)?.takeIf { productId.matches(it) }?.let { Destination.Product(it) }
            "offers" -> Destination.Offers(query["code"]?.uppercase()?.takeIf { offerCode.matches(it) })
            "profile" -> Destination.Profile
            else -> null
        }
    }

    private fun queryOf(rawQuery: String?): Map<String, String> =
        rawQuery.orEmpty().split('&').mapNotNull { pair ->
            val parts = pair.split('=', limit = 2)
            if (parts.size != 2) return@mapNotNull null
            URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
        }.toMap()
}

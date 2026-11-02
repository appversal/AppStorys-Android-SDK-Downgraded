package com.appversal.appstorys

import com.appversal.appstorys.api.ModalRedirection
import org.junit.Assert.assertEquals
import org.junit.Test

class RedirectLinksTest {
    private fun link(type: String?, url: String? = "", value: String? = "", key: String? = null, page: String? = null) =
        AppStorys.buildRedirectLink(ModalRedirection(type, url, value, key, page))

    @Test
    fun modalRedirectionBuildsTheLinkLikeFlutter() {
        assertEquals("offers:{\"code\":\"DIWALI50\"}", link("key-value", value = "{\"code\":\"DIWALI50\"}", key = "offers"))
        assertEquals("screen:{\"screen\":\"appstorys://demo/product/4\"}", link("key-value", value = "appstorys://demo/product/4", key = "screen"))
        assertEquals(null, link("key-value", value = "x", key = ""))
        assertEquals("https://x.com", link("url", url = "https://x.com"))
        assertEquals("PayScreen", link("deeplink", url = "", value = "PayScreen"))
        assertEquals(AppStorys.CLOSE_LINK, link("close"))
        assertEquals(null, link(null))
        assertEquals(null, AppStorys.buildRedirectLink(null))
    }
}

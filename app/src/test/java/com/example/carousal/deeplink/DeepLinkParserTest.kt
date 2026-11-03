package com.example.carousal.deeplink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkParserTest {
    private fun route(link: String) = DeepLinkParser.parse(link)?.route

    @Test
    fun customSchemeAndAppLinkOpenTheSameScreen() {
        assertEquals("product/42", route("appstorys://demo/product/42"))
        assertEquals("product/42", route("https://demo.appstorys.com/product/42"))
    }

    @Test
    fun pathAndQueryParameters() {
        assertEquals("pay?tab=bills", route("appstorys://demo/pay/bills"))
        assertEquals("pay?tab=cashbook", route("appstorys://demo/pay/unknown-tab"))
        assertEquals("offers?code=DIWALI50", route("appstorys://demo/offers?code=diwali50&utm_source=sms"))
        assertEquals("offers", route("appstorys://demo/offers"))
        assertEquals("home", route("appstorys://demo"))
    }

    @Test
    fun screenNamesOpenTheirScreens() {
        assertEquals("pay?tab=cashbook", route("PayScreen"))
        assertEquals("pay?tab=items", route("Items Tab"))
    }

    @Test
    fun malformedLinksAreNotRepaired() {
        // A host must not paper over what the SDK hands it.
        assertNull(route(" PayScreen"))
        assertNull(route("\"HomeScreen\""))
        assertNull(route("appstorys://demo/product/42 "))
    }

    @Test
    fun untrustedOrInvalidLinksAreRejected() {
        assertNull(route("https://evil.com/product/42"))
        assertNull(route("http://demo.appstorys.com/product/42"))
        assertNull(route("otherapp://demo/product/42"))
        assertNull(route("appstorys://demo/product/../../etc"))
        assertNull(route("appstorys://demo/product/<script>"))
        assertNull(route("appstorys://demo/nowhere"))
        assertNull(route(""))
    }

    @Test
    fun keyValueKeyNamesTheScreenParamsAreItsArguments() {
        fun kv(key: String, params: Map<String, Any?>) = DeepLinkParser.parseKeyValue(key, params)?.route
        assertEquals("product/4", kv("product", mapOf("id" to "4")))
        assertEquals("product/4", kv("product", mapOf("id" to 4L)))
        assertEquals("offers?code=DIWALI50", kv("offers", mapOf("code" to "diwali50")))
        assertEquals("pay?tab=bills", kv("pay", mapOf("id" to "bills")))
        assertEquals(null, kv("screen", mapOf("id" to "4")))
        assertEquals(null, kv("product", mapOf("id" to "../etc")))
    }

    @Test
    fun profileIsGated() {
        assertEquals(true, DeepLinkParser.parse("appstorys://demo/profile")?.requiresLogin)
    }
}

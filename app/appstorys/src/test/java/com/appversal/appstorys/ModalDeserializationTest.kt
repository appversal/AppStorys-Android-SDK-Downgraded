package com.appversal.appstorys

import com.appversal.appstorys.api.ModalDetails
import com.appversal.appstorys.ui.modals.MediaType
import com.appversal.appstorys.ui.modals.determineMediaType
import com.appversal.appstorys.ui.modals.isCarousel
import com.appversal.appstorys.ui.modals.isMediaOnly
import com.appversal.appstorys.ui.modals.resolveMediaUrl
import com.appversal.appstorys.utils.SdkJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain-JVM checks for the MOD (modal) payload → model → renderer-selection
 * chain. No Android runtime, no Paparazzi: this is the part of "can the SDK
 * render every modal shape and every media type" that is decidable without a
 * device, so it belongs in a test rather than in a one-off investigation.
 *
 * Several assertions below deliberately pin CURRENT, WRONG behaviour. Each one
 * says so and says what the right behaviour would be. They exist so the defect
 * cannot be forgotten and so that FIXING the SDK makes this test fail loudly —
 * that failure is the signal to update the test, not a regression.
 *
 * Payloads are trimmed copies of real dashboard responses:
 *   carousel   — campaign d4c818b4-2b4e-4f8a-a74d-4f444e0cd416 ("Modal_fullpage")
 *   media-only — campaign e54012a6-429c-4df7-a84d-4f44781bcfa2 ("Modal_5")
 */
class ModalDeserializationTest {

    // ── Real "media-only-modal" payload (Modal_5) ────────────────────────
    // Note `size` and `backdrop.opacity` arrive as JSON NUMBERS while the
    // model declares them String?; `dimension.width` has no model field at all.
    private val mediaOnlyJson = """
    {
      "id": "3a85eacf-d437-45f5-aaca-d692028e13cd",
      "modal_type": "media-only-modal",
      "modals": [{
        "size": 300,
        "screen": 64,
        "styling": {
          "appearance": {
            "backdrop": { "color": "#f0f0f0", "opacity": 40 },
            "dimension": { "width": 300 },
            "cornerRadius": { "topLeft": 12, "topRight": 12, "bottomLeft": 12, "bottomRight": 12 },
            "enableBackdrop": true
          },
          "crossButton": {
            "size": 30,
            "color": { "fill": "#f5f0f0", "cross": "#fb0909", "stroke": "#fb0404" },
            "image": "",
            "margin": { "top": 4, "left": 4, "right": 4, "bottom": 4 },
            "enabled": true,
            "selectedStyle": "cross4"
          }
        },
        "redirection": { "key": "", "url": "https://www.instagram.com/?hl=en", "type": "url", "value": "", "pageName": "" },
        "chooseMediaType": { "url": "https://example.com/media/clip_uurro.mp4", "type": "video" }
      }],
      "cta_config": []
    }
    """.trimIndent()

    private fun mediaOnly(): ModalDetails = SdkJson.decodeFromString(mediaOnlyJson)

    // ── PARSING ─────────────────────────────────────────────────────────

    @Test
    fun `media-only payload parses despite number-typed size and opacity`() {
        val d = mediaOnly()
        val modal = d.modals!!.first()

        // Numbers coerced into the String? fields by SdkJson's isLenient.
        // The values survive, but the model types are still wrong — Layer 2
        // reports exactly these as "TYPE CHANGED: number -> string".
        assertEquals("300", modal.size)
        assertEquals("40", modal.styling?.appearance?.backdrop?.opacity)

        assertEquals("#f0f0f0", modal.styling?.appearance?.backdrop?.color)
        assertEquals(true, modal.styling?.appearance?.enableBackdrop)
        assertEquals(12, modal.styling?.appearance?.cornerRadius?.topLeft)
        assertEquals(30, modal.styling?.crossButton?.size)
        assertEquals(true, modal.styling?.crossButton?.enabled)
        assertEquals("https://www.instagram.com/?hl=en", modal.redirection?.url)
    }

    @Test
    fun `dimension width has no model field so the dashboard value is dropped`() {
        // ModalDimension declares only height + borderWidth. The dashboard's
        // width:300 is therefore unreadable; MediaOnlyModal happens to survive
        // because it takes its width from the flat `size` instead.
        // CORRECT BEHAVIOUR: ModalDimension should declare `width`.
        val appearance = mediaOnly().modals!!.first().styling?.appearance
        assertNotNull(appearance?.dimension)
        assertNull(appearance?.dimension?.height)
        assertNull(appearance?.dimension?.borderWidth)
    }

    @Test
    fun `modal_type is unreadable because ModalDetails declares no such field`() {
        // Both live campaigns send details.modal_type ("media-only-modal",
        // "modal-fullpage-carousel"). ModalDetails has id/modals/name only, so
        // the value is discarded at parse time and PopupModal has to guess the
        // shape from the payload instead. @SerialName("modal_type") exists on
        // the CHILD `Modal` class, which is not where the backend puts it.
        // CORRECT BEHAVIOUR: ModalDetails should declare modal_type.
        val fields = ModalDetails::class.java.declaredFields.map { it.name }
        assertFalse(
            "ModalDetails gained a modal_type field — make PopupModal use it and delete this assertion",
            fields.any { it.equals("modalType", true) || it.equals("modal_type", true) }
        )
    }

    // ── RENDERER SELECTION (PopupModal's when-block) ────────────────────

    @Test
    fun `media-only payload routes to the media-only renderer`() {
        val modal = mediaOnly().modals!!.first()
        assertFalse(modal.isCarousel())
        assertTrue(modal.isMediaOnly())
        assertEquals("https://example.com/media/clip_uurro.mp4", modal.resolveMediaUrl())
    }

    @Test
    fun `a content block with media but no text is NOT media-only`() {
        // isMediaOnly() requires content == null, not "content has no text".
        // A dashboard that emits an empty content object alongside the media
        // therefore lands in ModalWithCTA and renders a white card around the
        // image instead of the bare media. Worth knowing before blaming the UI.
        val json = """
        {"id":"x","modals":[{"content":{"chooseMediaType":{"type":"image","url":"https://e.com/a.png"},
          "titleText":null,"subtitleText":null,"primaryCtaText":null,"primaryCtaRedirection":null,
          "secondaryCtaText":null,"secondaryCtaRedirection":null}}]}
        """.trimIndent()
        val modal = SdkJson.decodeFromString<ModalDetails>(json).modals!!.first()
        assertFalse(modal.isCarousel())
        assertFalse("content != null, so PopupModal falls through to ModalWithCTA", modal.isMediaOnly())
    }

    @Test
    fun `a content set routes to the carousel renderer`() {
        val json = """
        {"id":"x","modal_type":"modal-fullpage-carousel","modals":[{"content":{"set":[
          {"titleText":"A","primaryCta":"Go","chooseMediaType":{"type":"image","url":"https://e.com/a.png"}},
          {"titleText":"B","primaryCta":"Go","chooseMediaType":{"type":"video","url":"https://e.com/b.mp4"}}
        ]}}]}
        """.trimIndent()
        val modal = SdkJson.decodeFromString<ModalDetails>(json).modals!!.first()
        assertTrue(modal.isCarousel())
        assertEquals(2, modal.content?.set?.size)
    }

    // ── MEDIA TYPE RESOLUTION ───────────────────────────────────────────

    @Test
    fun `all four media kinds resolve from the url extension`() {
        assertEquals(MediaType.IMAGE, determineMediaType("https://e.com/a.png"))
        assertEquals(MediaType.IMAGE, determineMediaType("https://e.com/a.jpg"))
        assertEquals(MediaType.IMAGE, determineMediaType("https://e.com/a.webp"))
        assertEquals(MediaType.GIF, determineMediaType("https://e.com/a.gif"))
        assertEquals(MediaType.LOTTIE, determineMediaType("https://e.com/a.json"))
        assertEquals(MediaType.VIDEO, determineMediaType("https://e.com/a.mp4"))
        assertEquals(MediaType.VIDEO, determineMediaType("https://e.com/a.m3u8"))
    }

    @Test
    fun `the dashboard type hint is never consulted by the renderers`() {
        // determineMediaType CAN honour a hint, and ModalMedia.getTypeHint()
        // exists to supply one — but both call sites in ModalComponents.kt pass
        // the URL only, and getTypeHint() has zero callers. These assertions
        // show what the hint WOULD do versus what actually happens.
        assertEquals(MediaType.GIF, determineMediaType("https://e.com/asset", "gif"))
        assertEquals(MediaType.IMAGE, determineMediaType("https://e.com/asset"))

        // Consequence: an animated GIF served without a .gif extension (a CDN
        // that keys on Content-Type, or any URL carrying a query string) is
        // decoded by the plain Coil ImageLoader with no GifDecoder registered,
        // so it renders as a STILL first frame while the dashboard preview
        // animates. CORRECT BEHAVIOUR: pass chooseMediaType.type through.
        assertEquals(MediaType.IMAGE, determineMediaType("https://e.com/a.gif?v=2"))
    }

    // ── CTA TEXT ALIASES ────────────────────────────────────────────────

    @Test
    fun `ModalWithCTA ignores the primaryCta alias that the carousel accepts`() {
        // The live dashboard emits `primaryCta` / `secondayCta` (sic) — that is
        // exactly what campaign d4c818b4's slides contain.
        // FullPageCarouselModal resolves primaryCtaText -> primaryCta -> …,
        // but ModalWithCTA/ModalCtaRow read ONLY primaryCtaText and
        // secondaryCtaText, and its `hasContent` gate checks only those two.
        // So a single-content modal authored in that dashboard renders with NO
        // buttons at all — reproduced visually in ModalScreenshotTest.
        // CORRECT BEHAVIOUR: ModalWithCTA should use the same fallback chain.
        val json = """
        {"id":"x","modals":[{"content":{
          "chooseMediaType":{"type":"image","url":"https://e.com/a.png"},
          "titleText":"T","primaryCta":"Click Here","secondayCta":"Maybe Later"}}]}
        """.trimIndent()
        val content = SdkJson.decodeFromString<ModalDetails>(json).modals!!.first().content!!

        // The aliases DO parse — the data reaches the SDK…
        assertEquals("Click Here", content.primaryCta)
        assertEquals("Maybe Later", content.secondayCta)
        // …but the only fields ModalWithCTA looks at are empty.
        assertNull(content.primaryCtaText)
        assertNull(content.secondaryCtaText)
    }

    @Test
    fun `canonical CTA keys populate the fields ModalWithCTA reads`() {
        val json = """
        {"id":"x","modals":[{"content":{
          "chooseMediaType":{"type":"image","url":"https://e.com/a.png"},
          "titleText":"T","primaryCtaText":"Click Here","secondaryCtaText":"Maybe Later"}}]}
        """.trimIndent()
        val content = SdkJson.decodeFromString<ModalDetails>(json).modals!!.first().content!!
        assertEquals("Click Here", content.primaryCtaText)
        assertEquals("Maybe Later", content.secondaryCtaText)
    }
}

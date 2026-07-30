package com.appversal.appstorys.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.appversal.appstorys.api.BottomSheetDetails
import com.appversal.appstorys.utils.SdkJson
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Paparazzi snapshot — Bottom Sheet (BTS) campaign.
 *
 * A bottom sheet slides up from the bottom of the screen and can
 * contain a mix of image, body (title + description), and CTA elements.
 * We render the sheet anchored to the bottom of the full-screen device
 * frame over the real app background — matching the runtime layout.
 *
 * One snapshot total: 01_bottomsheet
 * (Image elements are loaded from disk to bypass Gradle classpath caching.)
 *
 * SETUP (pipeline-config.json):
 *   "paparazziTestClasses": { "BTS": "com.appversal.appstorys.ui.BottomSheetScreenshotTest" }
 *   IMAGE_EXTRACTORS.BTS downloads element images to bts_images/<elementId>.png
 */
private const val BTS_JSON_RESOURCE = "campaign-data/bts_details.json"

// The SDK filters eligible campaigns by the screen the host app last reported,
// so the snapshot must composite over THAT screen's background. Layer 3 writes
// the slug beside the details JSON (lib/cdn.js). This was hardcoded to
// home_screen_kotlin, which drew a Lab-screen campaign over the Home screen.
private const val BTS_SCREEN_RESOURCE = "campaign-data/bts_screen.txt"
private const val APP_BG_FALLBACK   = "backgrounds/home_screen_kotlin.png"
private const val BTS_IMG_DIR       = "bts_images"

class BottomSheetScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        // Strict pixel match. Paparazzi defaults to maxPercentDifference = 0.1,
        // measured over the WHOLE frame colour energy — so a changed CTA label or a
        // recoloured icon (~0.06% of the frame) silently PASSED verifyPaparazziDebug
        // and Layer 3 went on showing the stale golden. Every campaign-data change
        // this suite exists to catch is that small, so the tolerance must be 0.
        // Full post-mortem in PipVideoScreenshotTest.
        maxPercentDifference = 0.0
    )

    @Test
    fun bottomsheet_renders() {

        // JSON via classpath
        val btsJson = javaClass.classLoader!!
            .getResourceAsStream(BTS_JSON_RESOURCE)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }

        val details: BottomSheetDetails? = btsJson?.let {
            runCatching { SdkJson.decodeFromString<BottomSheetDetails>(it) }.getOrNull()
        }

        val screenSlug = runCatching {
            javaClass.classLoader!!
                .getResourceAsStream(BTS_SCREEN_RESOURCE)
                ?.use { it.readBytes().toString(Charsets.UTF_8).trim() }
                ?.takeIf { it.isNotEmpty() }
        }.getOrNull()

        val bgBitmap = runCatching {
            val cl = javaClass.classLoader!!
            listOfNotNull(screenSlug?.let { "backgrounds/$it.png" }, APP_BG_FALLBACK)
                .firstNotNullOfOrNull { path ->
                    cl.getResourceAsStream(path)?.use { BitmapFactory.decodeStream(it) }
                }
        }.getOrNull()

        // ── Styling ──────────────────────────────────────────────────────────
        val sheetBg = safeParseColor(
            details?.backgroundColor ?: details?.styling?.backgroundColor,
            Color(0xFFFFFFFF)
        )
        val backdropColor = safeParseColor(details?.backdropColor, Color.Black)
        // Device: (backdropOpacity.asInt(50) / 100f). This was hardcoded 0.5f,
        // which happens to match THIS campaign (50) and silently diverges from
        // any other. The model types it JsonElement? because the backend may
        // send it as a number OR a string, so parse both.
        val backdropAlpha = (details?.backdropOpacity?.asIntOrNull() ?: 50)
            .coerceIn(0, 100) / 100f
        val cornerTl = (details?.cornerRadius?.topLeft  ?: 16).dp
        val cornerTr = (details?.cornerRadius?.topRight ?: 16).dp

        // Elements sorted by order
        val elements = details?.elements
            ?.sortedBy { it.order }
            .orEmpty()

        paparazzi.snapshot(name = "01_bottomsheet") {
            Box(modifier = Modifier.fillMaxSize()) {

                // ── Layer 0: App background ──────────────────────────────────
                if (bgBitmap != null) {
                    Image(
                        bitmap = bgBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D1117)))
                }

                // ── Layer 1: Backdrop ────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backdropColor.copy(alpha = backdropAlpha))
                )

                // ── Layer 2: the sheet ───────────────────────────────────────
                // This was a Card(containerColor = sheetBg, elevation = 8dp)
                // with a drag handle. All three are wrong:
                //   * ModalBottomSheet is created with dragHandle = null, so the
                //     product draws NO handle (same invention found in CSAT).
                //   * containerColor = Color.Transparent and
                //     shape = RoundedCornerShape(0.dp) — the sheet paints
                //     nothing. Corners are clipped on the inner Column, and each
                //     ELEMENT paints its own background (bodyBackgroundColor,
                //     ctaBoxColor, imageBackgroundColor). A sheet-wide white
                //     card plus an 8dp shadow existed on no real screen.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    Column(
                        modifier = Modifier
                            .clip(
                                RoundedCornerShape(
                                    topStart = cornerTl,
                                    topEnd   = cornerTr
                                )
                            )
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        elements.forEach { element ->
                            // NO .lowercase() — the SDK matches element.type
                            // case-SENSITIVELY (BottomSheetComponent.kt:
                            // `when (element.type)`) and has no else branch, so
                            // an element typed "Image" renders NOTHING on the
                            // device. Lowercasing here made this test strictly
                            // more permissive than the product: it would draw
                            // the element, match its own golden, and pass while
                            // the real bottom sheet came up empty. A replica
                            // must be exactly as strict as what it replicates.
                            when (element.type) {

                                "image" -> {
                                    // Load from disk — bypasses Gradle classpath caching
                                    val imgBitmap = loadImageFromDisk(
                                        BTS_IMG_DIR, element.id ?: "main"
                                    )
                                    val imgBg = safeParseColor(
                                        element.imageBackgroundColor, Color.Transparent
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(imgBg)
                                            .padding(
                                                horizontal = (element.paddingLeft ?: 0).dp,
                                                vertical   = (element.paddingTop  ?: 0).dp
                                            )
                                    ) {
                                        if (imgBitmap != null) {
                                            // Device doesn't force 16:9 — media keeps
                                            // its natural aspect ratio at full width
                                            Image(
                                                bitmap = imgBitmap.asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(
                                                        imgBitmap.width.toFloat() /
                                                            imgBitmap.height.toFloat()
                                                    ),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            // Placeholder when image not yet downloaded
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(180.dp)
                                                    .background(Color(0xFFBDBDBD))
                                            )
                                        }
                                    }
                                }

                                "body" -> {
                                    val bodyBg = safeParseColor(
                                        element.bodyBackgroundColor, Color.Transparent
                                    )
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(bodyBg)
                                            .padding(
                                                start  = (element.paddingLeft   ?: 16).dp,
                                                end    = (element.paddingRight  ?: 16).dp,
                                                top    = (element.paddingTop    ?: 12).dp,
                                                bottom = (element.paddingBottom ?: 4).dp
                                            )
                                    ) {
                                        val titleText = element.titleText
                                        if (!titleText.isNullOrBlank()) {
                                            Text(
                                                text = titleText,
                                                fontSize = (element.titleFontSize ?: 16).sp,   // device 16
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF000000),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        val descriptionText = element.descriptionText
                                        if (!descriptionText.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = descriptionText,
                                                fontSize = (element.descriptionFontSize ?: 14).sp,
                                                color = Color(0xFF555555),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }

                                "cta" -> {
                                    // NESTED-FIRST, flat fallback — exactly what
                                    // CtaElement in BottomSheetComponent.kt does:
                                    //   element.cta?.container?.X ?: element.X
                                    // This test used to read the FLAT fields only.
                                    // The backend has since moved to the nested
                                    // `cta` object (Layer 0 reports the migration as
                                    // "22 NEW fields: elements[].cta.container.*"),
                                    // so every flat field came back null and the
                                    // test silently fell through to its OWN defaults:
                                    // a full-width orange button with white text,
                                    // while the device renders a 120dp near-white
                                    // button with a blue border and near-black text.
                                    // The golden was wrong in colour, width, border
                                    // and corner radius simultaneously.
                                    val c = element.cta
                                    val ctaBg = safeParseColor(
                                        c?.container?.backgroundColor?.takeIf { it.isNotBlank() }
                                            ?: c?.container?.ctaBoxColor
                                            ?: element.ctaBoxColor
                                            ?: element.ctaBackgroundColor,
                                        Color(0xFFF97316)
                                    )
                                    val ctaTextColor = safeParseColor(
                                        c?.text?.color ?: element.ctaTextColour,
                                        Color(0xFFFFFFFF)
                                    )
                                    val ctaCorner = (
                                        c?.cornerRadius?.topLeft
                                            ?: element.ctaBorderRadius?.topLeft
                                            ?: 8
                                        ).dp
                                    // Device default is 100dp when unset (asInt(100)),
                                    // NOT full width — the old `?: true` inverted it.
                                    val fullWidth = c?.container?.ctaFullWidth
                                        ?: element.ctaFullWidth ?: false
                                    val ctaW = (c?.container?.ctaWidth?.asIntOrNull()
                                        ?: element.ctaWidth?.asIntOrNull() ?: 100).dp
                                    val ctaH = (c?.container?.height
                                        ?: element.ctaHeight?.asIntOrNull() ?: 50).dp
                                    val borderW = (c?.container?.borderWidth?.asIntOrNull() ?: 0)
                                    val borderCol = safeParseColor(
                                        c?.container?.borderColor, Color.Transparent
                                    )
                                    val ctaFontSize = (c?.text?.fontSize
                                        ?: element.ctaFontSize?.toIntOrNull() ?: 16).sp
                                    val ctaWeight =
                                        if (c?.text?.fontDecoration?.contains("bold") == true)
                                            FontWeight.Bold else FontWeight.SemiBold

                                    Box(
                                        modifier = Modifier
                                            .padding(
                                                start  = (c?.margin?.left   ?: element.marginLeft   ?: 16).dp,
                                                end    = (c?.margin?.right  ?: element.marginRight  ?: 16).dp,
                                                top    = (c?.margin?.top    ?: element.marginTop    ?: 8).dp,
                                                bottom = (c?.margin?.bottom ?: element.marginBottom ?: 8).dp
                                            )
                                            .then(
                                                if (fullWidth) Modifier.fillMaxWidth()
                                                else Modifier.width(ctaW)
                                            )
                                            .height(ctaH)
                                            .background(ctaBg, RoundedCornerShape(ctaCorner))
                                            .then(
                                                if (borderW > 0)
                                                    Modifier.border(
                                                        borderW.dp, borderCol,
                                                        RoundedCornerShape(ctaCorner)
                                                    )
                                                else Modifier
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = element.ctaText ?: "Continue",
                                            color = ctaTextColor,
                                            fontSize = ctaFontSize,
                                            fontWeight = ctaWeight,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                else -> {
                                    // Unknown element type — render labelled placeholder
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                            .background(Color(0xFFEEEEEE), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "element: ${element.type}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF888888)
                                        )
                                    }
                                }
                            }
                        }

                        // Fallback if no elements defined
                        if (elements.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No elements configured",
                                    color = Color(0xFF888888),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // ── Cross button ────────────────────────────────────────
                    // The test drew NONE — zero references — while the device
                    // shows one on every run (dump: content-desc="Close") and
                    // this campaign sets crossButton.enabled=true, size=30 with
                    // a red cross. Same omission class as the CSAT close button.
                    //
                    // BottomSheetComponent renders the shared CrossButton at
                    // TopEnd of the sheet Box, enabled unless explicitly false,
                    // reading crossButton ?: styling.crossButton. CrossButton
                    // itself is a circle: fill, a size*0.05 stroke border when
                    // strokeColor isn't transparent, glyph inset size*0.11.
                    // Paparazzi cannot load the SDK's R.drawable.cross, so the
                    // glyph is drawn as two lines at the same inset.
                    val xBtn = details?.crossButton ?: details?.styling?.crossButton
                    if (xBtn?.enabled != false) {
                        val xColors = xBtn?.color ?: xBtn?.colors
                        val xSize = (xBtn?.size ?: 16).dp
                        val xFill = safeParseColor(xColors?.fill, Color.Transparent)
                        val xStroke = safeParseColor(xColors?.stroke, Color.Transparent)
                        val xGlyph = safeParseColor(xColors?.cross, Color.White)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(
                                    top = (xBtn?.margin?.top ?: 0).dp,
                                    end = (xBtn?.margin?.right ?: 0).dp
                                )
                                .size(xSize)
                                .clip(CircleShape)
                                .background(xFill)
                                .then(
                                    if (xStroke != Color.Transparent)
                                        Modifier.border(xSize * 0.05f, xStroke, CircleShape)
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            // EXPERIMENT: use the REAL drawable the SDK uses.
                            androidx.compose.material3.Icon(
                                painter = androidx.compose.ui.res.painterResource(
                                    com.appversal.appstorys.R.drawable.cross
                                ),
                                contentDescription = "Close",
                                tint = xGlyph,
                                modifier = Modifier.padding(xSize * 0.11f)
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Reads a bts_images/<key>.png from disk using the same 3-candidate
     * strategy as FloaterScreenshotTest — avoids Gradle classpath caching.
     */

    /**
     * backdropOpacity / ctaWidth / ctaHeight arrive as JsonElement because the
     * backend may send a number OR a string. Mirrors the SDK's asInt() helper:
     * (this as? JsonPrimitive)?.intOrNull — which parses both forms.
     */
    private fun kotlinx.serialization.json.JsonElement?.asIntOrNull(): Int? =
        (this as? kotlinx.serialization.json.JsonPrimitive)?.content?.trim()?.toIntOrNull()

    private fun loadImageFromDisk(imgDir: String, key: String): android.graphics.Bitmap? {
        return runCatching {
            val moduleDir = File(System.getProperty("user.dir") ?: "")
            val relativePath = "$imgDir/$key.png"
            val candidates = listOf(
                File(moduleDir, "src/test/resources/$relativePath"),
                File(moduleDir, "../../app/appstorys/src/test/resources/$relativePath"),
                File(moduleDir, "app/appstorys/src/test/resources/$relativePath")
            )
            candidates.firstOrNull { it.exists() }
                ?.inputStream()
                ?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    private fun safeParseColor(hex: String?, fallback: Color): Color {
        if (hex.isNullOrBlank()) return fallback
        return runCatching {
            Color(android.graphics.Color.parseColor(hex))
        }.getOrDefault(fallback)
    }
}
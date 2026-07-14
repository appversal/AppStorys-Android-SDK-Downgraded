package com.appversal.appstorys.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
private const val APP_BG_RESOURCE   = "backgrounds/home_screen_kotlin.png"
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

        val bgBitmap = runCatching {
            javaClass.classLoader!!
                .getResourceAsStream(APP_BG_RESOURCE)
                ?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()

        // ── Styling ──────────────────────────────────────────────────────────
        val sheetBg = safeParseColor(
            details?.backgroundColor ?: details?.styling?.backgroundColor,
            Color(0xFFFFFFFF)
        )
        val backdropColor = safeParseColor(details?.backdropColor, Color.Black)
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
                        .background(backdropColor.copy(alpha = 0.5f))
                )

                // ── Layer 2: Bottom sheet card ───────────────────────────────
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    shape = RoundedCornerShape(
                        topStart = cornerTl,
                        topEnd   = cornerTr,
                        bottomStart = 0.dp,
                        bottomEnd   = 0.dp
                    ),
                    colors = CardDefaults.cardColors(containerColor = sheetBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Drag handle
                        Box(
                            modifier = Modifier
                                .padding(top = 12.dp, bottom = 8.dp)
                                .width(40.dp)
                                .height(4.dp)
                                .background(
                                    Color(0xFFCCCCCC),
                                    RoundedCornerShape(2.dp)
                                )
                        )

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
                                    val ctaBg = safeParseColor(
                                        element.ctaBoxColor ?: element.ctaBackgroundColor,
                                        Color(0xFFF97316)
                                    )
                                    val ctaTextColor = safeParseColor(
                                        element.ctaTextColour, Color(0xFFFFFFFF)
                                    )
                                    val ctaCorner = (
                                            element.ctaBorderRadius?.topLeft ?: 8
                                            ).dp
                                    val fullWidth = element.ctaFullWidth ?: true

                                    Box(
                                        modifier = Modifier
                                            .padding(
                                                horizontal = (element.marginLeft  ?: 16).dp,
                                                vertical   = (element.marginTop   ?: 8).dp
                                            )
                                            .then(
                                                if (fullWidth) Modifier.fillMaxWidth()
                                                else Modifier
                                            )
                                            .height(50.dp)   // device Button default height 50
                                            .background(ctaBg, RoundedCornerShape(ctaCorner)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = element.ctaText ?: "Continue",
                                            color = ctaTextColor,
                                            fontSize = (element.ctaFontSize?.toIntOrNull() ?: 16).sp,
                                            fontWeight = FontWeight.SemiBold,
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
                }
            }
        }
    }

    /**
     * Reads a bts_images/<key>.png from disk using the same 3-candidate
     * strategy as FloaterScreenshotTest — avoids Gradle classpath caching.
     */
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
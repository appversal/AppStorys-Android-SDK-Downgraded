package com.appversal.appstorys.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.appversal.appstorys.api.WidgetDetails
import com.appversal.appstorys.utils.SdkJson
import org.junit.Rule
import org.junit.Test

/**
 * Paparazzi snapshot — Widget (WID) campaign.
 *
 * Handles both widget types that exist in the SDK:
 *
 *   FULL WIDGET (type = "full"):
 *     Multiple images displayed in a sliding carousel.
 *     One snapshot per image → named 01_full_slide, 02_full_slide, ...
 *     Dashboard shows them in a carousel — swipe left/right to see each slide.
 *
 *   HALF WIDGET (type = "half"):
 *     Images displayed in pairs side-by-side (left + right).
 *     The SDK groups them: images 1+2 = slide 1, images 3+4 = slide 2, etc.
 *     One snapshot per pair → named 01_half_pair, 02_half_pair, ...
 *     Dashboard shows each pair in a carousel.
 *
 * WHY NO COIL/AsyncImage:
 *   Coil requires Android runtime and network access which are not available
 *   in the Paparazzi JVM environment. The pipeline downloads all widget images
 *   to src/test/resources/wid_images/{index}.png before the test runs, and
 *   this test loads them using BitmapFactory directly from the classpath.
 *
 * POSITION:
 *   The widget is rendered below the app header (padding(top = 180.dp)) to
 *   match where widget_one actually appears in the real app layout on Pixel 5.
 *   Width and height are calculated from the campaign JSON aspect ratio,
 *   matching AppStorys.kt's own sizing logic exactly.
 */

private const val WID_JSON_RESOURCE = "campaign-data/wid_details.json"
private const val WID_IMAGES_DIR    = "wid_images"
private const val APP_BG_RESOURCE   = "backgrounds/home_screen_kotlin.png"

// Pixel 5 logical screen width at 1.0 density scale
private val SCREEN_WIDTH = 393.dp

class WidgetScreenshotTest {

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
    fun widget_slides() {

        // ── Load campaign JSON written by the pipeline ────────────────────────
        val widJson = javaClass.classLoader!!
            .getResourceAsStream(WID_JSON_RESOURCE)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }

        val details: WidgetDetails? = widJson?.let {
            runCatching { SdkJson.decodeFromString<WidgetDetails>(it) }.getOrNull()
        }

        // ── App background ────────────────────────────────────────────────────
        val bgBitmap = runCatching {
            javaClass.classLoader!!
                .getResourceAsStream(APP_BG_RESOURCE)
                ?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()

        // ── Widget images — pipeline saves as wid_images/0.png, 1.png … ──────
        val sortedImages = details?.widgetImages?.sortedBy { it.order ?: 0 } ?: emptyList()
        val bitmaps: List<Bitmap?> = sortedImages.mapIndexed { i, _ ->
            runCatching {
                javaClass.classLoader!!
                    .getResourceAsStream("$WID_IMAGES_DIR/$i.png")
                    ?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }

        // ── Styling ───────────────────────────────────────────────────────────
        val styling      = details?.styling
        val leftMargin   = (styling?.leftMargin   ?: 0).dp
        val rightMargin  = (styling?.rightMargin  ?: 0).dp
        val topMargin    = (styling?.topMargin    ?: 0).dp
        val bottomMargin = (styling?.bottomMargin ?: 0).dp
        val tlRadius     = (styling?.topLeftRadius     ?: 0).dp
        val trRadius     = (styling?.topRightRadius    ?: 0).dp
        val blRadius     = (styling?.bottomLeftRadius  ?: 0).dp
        val brRadius     = (styling?.bottomRightRadius ?: 0).dp
        val shape = RoundedCornerShape(
            topStart    = tlRadius,
            topEnd      = trRadius,
            bottomStart = blRadius,
            bottomEnd   = brRadius
        )

        // ── Height — mirrors AppStorys.kt aspect-ratio calculation ────────────
        val actualWidth = SCREEN_WIDTH - leftMargin - rightMargin
        val widW = details?.width
        val widH = details?.height
        val fullHeight: Dp? = if (widW != null && widH != null) {
            val ratio = widH.toFloat() / widW.toFloat()
            (actualWidth.value * ratio).dp
        } else {
            details?.height?.dp
        }

        // ── FULL widget: one snapshot per slide image ─────────────────────────
        if (details?.type == "full") {
            sortedImages.forEachIndexed { i, _ ->
                val name = "%02d_full_slide".format(i + 1)
                paparazzi.snapshot(name = name) {
                    WidgetBackground(bgBitmap) {
                        FullWidgetSlide(
                            bitmap       = bitmaps.getOrNull(i),
                            height       = fullHeight,
                            leftMargin   = leftMargin,
                            rightMargin  = rightMargin,
                            topMargin    = topMargin,
                            bottomMargin = bottomMargin,
                            shape        = shape
                        )
                    }
                }
            }

            // ── HALF widget: images shown as pairs side-by-side ───────────────────
            //
            //   images [0,1,2,3,4,5] → pairs [(0,1), (2,3), (4,5)]
            //   Each pair = one carousel slide. Odd trailing image shows alone on left.
            //
            //   Height for each half-slot mirrors AppStorys.kt:
            //     halfHeight = (actualWidth - 12dp_gap) / 2 * aspectRatio
            //   The 12dp matches the horizontalArrangement.spacedBy(12.dp) in the SDK.
        } else {
            val halfHeight: Dp? = if (widW != null && widH != null) {
                val ratio = widH.toFloat() / widW.toFloat()
                val slotWidth = (actualWidth.value - 12f) / 2f
                (slotWidth * ratio).dp
            } else {
                fullHeight?.let { ((it.value - 12f) / 2f).dp }
            }

            val pairs = sortedImages.chunked(2)
            pairs.forEachIndexed { i, pair ->
                val name     = "%02d_half_pair".format(i + 1)
                val leftBmp  = bitmaps.getOrNull(i * 2)
                val rightBmp = bitmaps.getOrNull(i * 2 + 1)
                paparazzi.snapshot(name = name) {
                    WidgetBackground(bgBitmap) {
                        HalfWidgetPair(
                            leftBitmap   = leftBmp,
                            rightBitmap  = rightBmp,
                            height       = halfHeight,
                            leftMargin   = leftMargin,
                            rightMargin  = rightMargin,
                            topMargin    = topMargin,
                            bottomMargin = bottomMargin,
                            shape        = shape,
                            hasBoth      = pair.size == 2
                        )
                    }
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun WidgetBackground(
    bgBitmap: Bitmap?,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (bgBitmap != null) {
            Image(
                bitmap             = bgBitmap.asImageBitmap(),
                contentDescription = null,
                modifier           = Modifier.fillMaxSize(),
                contentScale       = ContentScale.Crop
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5)))
        }
        // Push widget below the blue app header (~180dp on Pixel 5).
        // This matches where widget_one sits in the real app layout.
        Box(
            modifier         = Modifier
                .fillMaxSize()
                .padding(top = 180.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            content()
        }
    }
}

@Composable
private fun FullWidgetSlide(
    bitmap: Bitmap?,
    height: Dp?,
    leftMargin: Dp,
    rightMargin: Dp,
    topMargin: Dp,
    bottomMargin: Dp,
    shape: RoundedCornerShape
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start  = leftMargin,
                end    = rightMargin,
                top    = topMargin,
                bottom = bottomMargin
            )
            .then(if (height != null) Modifier.height(height) else Modifier),
        shape  = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        if (bitmap != null) {
            Image(
                bitmap             = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier           = Modifier.fillMaxSize(),
                contentScale       = ContentScale.FillWidth
            )
        }
    }
}

@Composable
private fun HalfWidgetPair(
    leftBitmap: Bitmap?,
    rightBitmap: Bitmap?,
    height: Dp?,
    leftMargin: Dp,
    rightMargin: Dp,
    topMargin: Dp,
    bottomMargin: Dp,
    shape: RoundedCornerShape,
    hasBoth: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start  = leftMargin,
                end    = rightMargin,
                top    = topMargin,
                bottom = bottomMargin
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Left slot
        Card(
            modifier = Modifier
                .weight(1f)
                .then(if (height != null) Modifier.height(height) else Modifier),
            shape  = shape,
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            if (leftBitmap != null) {
                Image(
                    bitmap             = leftBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier           = Modifier.fillMaxSize(),
                    contentScale       = ContentScale.FillWidth
                )
            }
        }
        // Right slot — only when pair has two images
        if (hasBoth) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .then(if (height != null) Modifier.height(height) else Modifier),
                shape  = shape,
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                if (rightBitmap != null) {
                    Image(
                        bitmap             = rightBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier           = Modifier.fillMaxSize(),
                        contentScale       = ContentScale.FillWidth
                    )
                }
            }
        }
    }
}
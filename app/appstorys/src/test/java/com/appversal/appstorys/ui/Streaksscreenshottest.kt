package com.appversal.appstorys.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.appversal.appstorys.api.MilestoneDetails
import com.appversal.appstorys.utils.SdkJson
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Paparazzi snapshot — Streaks / Milestone (MIL) campaign.
 *
 * Mirrors the REAL renderers in Milestone.kt 1:1. On a device a milestone is
 * a pure IMAGE render — there is no step-progress strip, no badges, no
 * "Day N" text, no Continue button (the earlier version of this test
 * invented all of those; none exist in Milestone.kt):
 *
 *   - MilestoneBanner: the milestone image bottom-pinned, FillWidth, clipped
 *     to styling.banner border radii (default 0), margins default 0, plus a
 *     20dp semi-transparent (#4D000000) close circle with a white 16dp X at
 *     the image's top-end (6dp padding). No card, no elevation, no white bg.
 *   - MilestoneModal: full-screen white surface clipped to the same radii,
 *     the image at TOP with ContentScale.Fit, and a 28dp close circle
 *     (12dp padding, 22dp icon) at the top-end.
 *
 * Which item: the first milestoneItem by order (what a fresh user sees).
 *
 * Snapshots:
 *   01_streak_banner — MilestoneBanner replica
 *   02_streak_modal  — MilestoneModal replica
 *
 * Item images loaded from disk: mil_images/<itemId>.png
 *
 * SETUP (pipeline-config.json):
 *   "paparazziTestClasses": { "MIL": "com.appversal.appstorys.ui.StreaksScreenshotTest" }
 * IMAGE_EXTRACTORS.MIL → downloads each milestoneItem.image to mil_images/<id>.png
 */
private const val MIL_JSON_RESOURCE = "campaign-data/mil_details.json"
private const val APP_BG_RESOURCE   = "backgrounds/home_screen_kotlin.png"
private const val MIL_IMG_DIR       = "mil_images"

class StreaksScreenshotTest {

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
    fun streak_views() {

        val milJson = javaClass.classLoader!!
            .getResourceAsStream(MIL_JSON_RESOURCE)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }

        val details: MilestoneDetails? = milJson?.let {
            runCatching { SdkJson.decodeFromString<MilestoneDetails>(it) }.getOrNull()
        }

        val bgBitmap = runCatching {
            javaClass.classLoader!!
                .getResourceAsStream(APP_BG_RESOURCE)
                ?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()

        val items = details?.milestoneItems
            ?.sortedBy { it.order }
            .orEmpty()
        val currentItem = items.firstOrNull()
        val itemBitmap  = currentItem?.id?.let { loadDisk(MIL_IMG_DIR, it) }

        // Styling — defaults copied from Milestone.kt (everything defaults 0)
        val bannerStyle = details?.styling?.banner
        val shape = RoundedCornerShape(
            topStart    = (bannerStyle?.borderRadiusTopLeft?.toIntOrNull()     ?: 0).dp,
            topEnd      = (bannerStyle?.borderRadiusTopRight?.toIntOrNull()    ?: 0).dp,
            bottomStart = (bannerStyle?.borderRadiusBottomLeft?.toIntOrNull()  ?: 0).dp,
            bottomEnd   = (bannerStyle?.borderRadiusBottomRight?.toIntOrNull() ?: 0).dp
        )
        val marginTop    = (bannerStyle?.marginTop?.toIntOrNull()    ?: 0).dp
        val marginBottom = (bannerStyle?.marginBottom?.toIntOrNull() ?: 0).dp
        val marginLeft   = (bannerStyle?.marginLeft?.toIntOrNull()   ?: 0).dp
        val marginRight  = (bannerStyle?.marginRight?.toIntOrNull()  ?: 0).dp

        // ── Snapshot 1: banner — image pinned to bottom (MilestoneBanner) ─────
        paparazzi.snapshot(name = "01_streak_banner") {
            Box(modifier = Modifier.fillMaxSize()) {
                AppBackground(bgBitmap)

                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                top = marginTop, bottom = marginBottom,
                                start = marginLeft, end = marginRight
                            )
                    ) {
                        MilestoneImage(
                            bitmap = itemBitmap,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape),
                            contentScale = ContentScale.FillWidth
                        )

                        // Close: 20dp circle, 6dp padding, 16dp white X
                        CloseCircle(circle = 20.dp, pad = 6.dp, icon = 16.dp)
                    }
                }
            }
        }

        // ── Snapshot 2: modal — full-screen white sheet (MilestoneModal) ──────
        paparazzi.snapshot(name = "02_streak_modal") {
            Box(modifier = Modifier.fillMaxSize()) {
                AppBackground(bgBitmap)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = marginTop, bottom = marginBottom,
                            start = marginLeft, end = marginRight
                        )
                        .clip(shape)
                        .background(Color.White)
                ) {
                    MilestoneImage(
                        bitmap = itemBitmap,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                        contentScale = ContentScale.Fit
                    )

                    // Close: 28dp circle, 12dp padding, 22dp white X
                    CloseCircle(circle = 28.dp, pad = 12.dp, icon = 22.dp)
                }
            }
        }
    }

    // ── Composable helpers ────────────────────────────────────────────────────

    @androidx.compose.runtime.Composable
    private fun AppBackground(bgBitmap: android.graphics.Bitmap?) {
        if (bgBitmap != null) {
            Image(
                bitmap = bgBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5)))
        }
    }

    @androidx.compose.runtime.Composable
    private fun MilestoneImage(
        bitmap: android.graphics.Bitmap?,
        modifier: Modifier,
        contentScale: ContentScale
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Milestone",
                modifier = modifier,
                contentScale = contentScale
            )
        } else {
            // Grey stand-in with the image's typical banner proportions so the
            // snapshot still shows placement when the image isn't downloaded yet
            Box(
                modifier = modifier.aspectRatio(3f).background(Color(0xFFE0E0E0)),
                contentAlignment = Alignment.Center
            ) {
                Text("milestone image", color = Color(0xFF888888), fontSize = 12.sp)
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun androidx.compose.foundation.layout.BoxScope.CloseCircle(
        circle: androidx.compose.ui.unit.Dp,
        pad: androidx.compose.ui.unit.Dp,
        icon: androidx.compose.ui.unit.Dp
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(pad)
                .size(circle)
                .clip(CircleShape)
                .background(Color(0x4D000000)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(icon)
            )
        }
    }

    private fun loadDisk(imgDir: String, key: String): android.graphics.Bitmap? {
        return runCatching {
            val moduleDir = File(System.getProperty("user.dir") ?: "")
            val rel = "$imgDir/$key.png"
            listOf(
                File(moduleDir, "src/test/resources/$rel"),
                File(moduleDir, "../../app/appstorys/src/test/resources/$rel"),
                File(moduleDir, "app/appstorys/src/test/resources/$rel")
            ).firstOrNull { it.exists() }?.inputStream()?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }
}

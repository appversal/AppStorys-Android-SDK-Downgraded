package com.appversal.appstorys.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.appversal.appstorys.api.TooltipsDetails
import com.appversal.appstorys.utils.SdkJson
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Paparazzi snapshot — Tooltip (TTP) campaign.
 *
 * Tooltips are sequential coach marks, each targeting a UI element.
 * Since Paparazzi has no live view tree to anchor to, each tooltip is
 * rendered as a standalone card centred on the app background showing its
 * image (if tooltip_category is "ttpimage"), title, subtitle, CTA text,
 * and backdrop (if enabled) — enough to catch regressions in colour,
 * typography, image, and corner radius.
 *
 * One snapshot per tooltip step → 01_tooltip_<target>, 02_tooltip_<target>, …
 *
 * Image loaded from disk (ttp_images/<tooltipId>.png) to bypass Gradle
 * classpath caching, same pattern used by FLT/BTS/MOD tests.
 *
 * SETUP (pipeline-config.json):
 *   "paparazziTestClasses": { "TTP": "com.appversal.appstorys.ui.TooltipScreenshotTest" }
 * IMAGE_EXTRACTORS.TTP → downloads each tooltip.url to ttp_images/<id>.png
 */
private const val TTP_JSON_RESOURCE = "campaign-data/ttp_details.json"
private const val APP_BG_RESOURCE   = "backgrounds/home_screen_kotlin.png"
private const val TTP_IMG_DIR       = "ttp_images"

class TooltipScreenshotTest {

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
    fun tooltip_steps() {

        // JSON via classpath — Gradle always copies JSON correctly
        val ttpJson = javaClass.classLoader!!
            .getResourceAsStream(TTP_JSON_RESOURCE)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }

        val details: TooltipsDetails? = ttpJson?.let {
            runCatching { SdkJson.decodeFromString<TooltipsDetails>(it) }.getOrNull()
        }

        val bgBitmap = runCatching {
            javaClass.classLoader!!
                .getResourceAsStream(APP_BG_RESOURCE)
                ?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()

        val tooltips = details?.tooltips?.sortedBy { it.order }.orEmpty()

        if (tooltips.isEmpty()) {
            paparazzi.snapshot(name = "01_tooltip_empty") {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A2E)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No tooltip data", color = Color.White, fontSize = 14.sp)
                }
            }
            return
        }

        tooltips.forEachIndexed { i, tooltip ->
            val idx = "%02d".format(i + 1)
            val targetSlug = tooltip.target
                ?.replace(".", "_")
                ?.replace(" ", "_")
                ?: "step"
            val snapshotName = "${idx}_tooltip_$targetSlug"

            // ── Tooltip image — loaded from disk, keyed by tooltip id ────────
            val tooltipBitmap = loadDisk(TTP_IMG_DIR, tooltip.id ?: "tooltip_$i")
            val hasImage = tooltip.type == "image" || tooltip.url != null

            // ── Styling ─────────────────────────────────────────────────────
            val appearance = tooltip.styling?.appearance

            val tooltipBg = safeParseColor(
                appearance?.colors?.tooltip, Color(0xFFFFFFFF)
            )
            val backdropColor = safeParseColor(
                appearance?.colors?.backdrop, Color.Black
            )
            val backdropOpacity = (appearance?.backdropOpacity ?: 50) / 100f
            // Device defaults differ by content type (TooltipContent.kt):
            // image tooltips default 0dp corners, text tooltips default 8dp
            val cornerDefault = if (hasImage) 0 else 8
            val cornerTl = (appearance?.cornerRadius?.topLeft     ?: cornerDefault).dp
            val cornerTr = (appearance?.cornerRadius?.topRight    ?: cornerDefault).dp
            val cornerBl = (appearance?.cornerRadius?.bottomLeft  ?: cornerDefault).dp
            val cornerBr = (appearance?.cornerRadius?.bottomRight ?: cornerDefault).dp
            val tooltipWidth = (appearance?.width ?: 280).dp

            val titleColor  = safeParseColor(tooltip.styling?.title?.color,    Color(0xFF000000))
            val titleSize   = (tooltip.styling?.title?.fontSize    ?: 16).sp
            val subColor    = safeParseColor(tooltip.styling?.subTitle?.color,  Color(0xFF555555))
            val subSize     = (tooltip.styling?.subTitle?.fontSize ?: 13).sp
            val ctaBg       = safeParseColor(tooltip.styling?.cta?.container?.backgroundColor, Color(0xFFF97316))
            val ctaTextColor= safeParseColor(tooltip.styling?.cta?.text?.color, Color(0xFFFFFFFF))
            val ctaFontSize = (tooltip.styling?.cta?.text?.fontSize ?: 14).sp
            val ctaRadius = (tooltip.styling?.cta?.cornerRadius?.topLeft
                ?: tooltip.styling?.cta?.borderRadius?.topLeft ?: 8).dp

            paparazzi.snapshot(name = snapshotName) {
                Box(modifier = Modifier.fillMaxSize()) {

                    // ── Layer 0: App background ──────────────────────────────
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

                    // ── Layer 1: Backdrop ────────────────────────────────────
                    if (tooltip.enableBackdrop == true) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(backdropColor.copy(alpha = backdropOpacity))
                        )
                    }

                    // ── Layer 2: Tooltip card (centred) ──────────────────────
                    Card(
                        modifier = Modifier
                            .width(tooltipWidth)
                            .wrapContentHeight()
                            .align(Alignment.Center),
                        shape = RoundedCornerShape(
                            topStart = cornerTl, topEnd = cornerTr,
                            bottomStart = cornerBl, bottomEnd = cornerBr
                        ),
                        colors = CardDefaults.cardColors(containerColor = tooltipBg),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Tooltip image — fills card width, clipped to top corners
                            if (hasImage) {
                                if (tooltipBitmap != null) {
                                    Image(
                                        bitmap = tooltipBitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(140.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    // Image expected but not downloaded/available —
                                    // grey placeholder so the gap is visually obvious
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(140.dp)
                                            .background(Color(0xFFE0E0E0)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Image", color = Color(0xFF999999), fontSize = 12.sp)
                                    }
                                }
                            }

                            // Device text-content padding: top/bottom 12, left/right 16
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(
                                    top = 12.dp, bottom = 12.dp, start = 16.dp, end = 16.dp
                                )
                            ) {
                                val ttpTitle = tooltip.titleText
                                if (!ttpTitle.isNullOrBlank()) {
                                    Text(
                                        text = ttpTitle,
                                        color = titleColor,
                                        fontSize = titleSize,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                }
                                val ttpSubtitle = tooltip.subtitleText
                                if (!ttpSubtitle.isNullOrBlank()) {
                                    Text(
                                        text = ttpSubtitle,
                                        color = subColor,
                                        fontSize = subSize,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )
                                }
                                val ttpCta = tooltip.ctaText
                                if (!ttpCta.isNullOrBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(ctaBg, RoundedCornerShape(ctaRadius))
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = ttpCta,
                                            color = ctaTextColor,
                                            fontSize = ctaFontSize,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                // Step counter
                                if (tooltips.size > 1) {
                                    Text(
                                        text = "${i + 1} / ${tooltips.size}  ·  ${tooltip.target ?: ""}",
                                        color = Color(0xFF888888),
                                        fontSize = 10.sp,
                                        modifier = Modifier
                                            .align(Alignment.End)
                                            .padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadDisk(dir: String, key: String): Bitmap? = runCatching {
        val mod = File(System.getProperty("user.dir") ?: "")
        val rel = "$dir/$key.png"
        listOf(
            File(mod, "src/test/resources/$rel"),
            File(mod, "../../app/appstorys/src/test/resources/$rel"),
            File(mod, "app/appstorys/src/test/resources/$rel")
        ).firstOrNull { it.exists() }?.inputStream()?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    private fun safeParseColor(hex: String?, fallback: Color): Color {
        if (hex.isNullOrBlank()) return fallback
        return runCatching {
            Color(android.graphics.Color.parseColor(hex))
        }.getOrDefault(fallback)
    }
}
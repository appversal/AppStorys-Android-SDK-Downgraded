package com.appversal.appstorys.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.appversal.appstorys.api.SpinTheWheelDetails
import com.appversal.appstorys.utils.SdkJson
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * Paparazzi snapshot — Spin the Wheel (STW) campaign.
 *
 * Two snapshots:
 *   01_spinwheel_main   — full wheel UI: title, description, drawn wheel with
 *                         coloured slices + prize labels, spin button, spin count
 *   02_spinwheel_reward — reward popup shown after a winning spin, using the
 *                         first non-noPrize slice as the example winner
 *
 * The wheel is drawn using Compose Canvas drawArc — each slice gets:
 *   - fill colour from slice.styling.wheelStyling.color.background (or a
 *     default palette if not set)
 *   - prize label text rendered in the slice via Android Canvas drawText
 *
 * No images are downloaded for STW — slice colours and text are enough
 * to catch regressions in wheel composition. Slice images (sliceMedia)
 * are optional and omitted here because they'd require one download per
 * slice which could be 10+ files per campaign.
 *
 * SETUP (pipeline-config.json):
 *   "paparazziTestClasses": { "STW": "com.appversal.appstorys.ui.SpinTheWheelScreenshotTest" }
 * IMAGE_EXTRACTORS.STW → empty (no images to download)
 */
private const val STW_JSON_RESOURCE = "campaign-data/stw_details.json"
private const val APP_BG_RESOURCE   = "backgrounds/home_screen_kotlin.png"

// Default slice colour palette — used when a slice has no colour configured
private val DEFAULT_SLICE_COLORS = listOf(
    Color(0xFFE53935), Color(0xFF8E24AA), Color(0xFF1E88E5), Color(0xFF00897B),
    Color(0xFF43A047), Color(0xFFFDD835), Color(0xFFFB8C00), Color(0xFF6D4C41),
    Color(0xFF546E7A), Color(0xFF00ACC1)
)

class SpinTheWheelScreenshotTest {

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
    fun spinwheel_screens() {

        val stwJson = javaClass.classLoader!!
            .getResourceAsStream(STW_JSON_RESOURCE)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }

        val details: SpinTheWheelDetails? = stwJson?.let {
            runCatching { SdkJson.decodeFromString<SpinTheWheelDetails>(it) }.getOrNull()
        }

        val bgBitmap = runCatching {
            javaClass.classLoader!!
                .getResourceAsStream(APP_BG_RESOURCE)
                ?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()

        val slices = details?.slices.orEmpty()
        val totalSlices = slices.size.coerceAtLeast(6)

        // Visual styling
        val mainStyling  = details?.styling?.spinTheWheel
        val visualText   = mainStyling?.visualTextCommunication
        val wheelCfg     = mainStyling?.wheelConfiguration
        val rewardStyling = details?.styling?.rewardConfiguration

        val backdropColor  = safeColor(visualText?.backdropColor, Color.Black)
        val backdropOpacity = ((visualText?.backdropOpacity ?: 50) / 100f).coerceIn(0f, 1f)
        val wheelBg        = safeColor(wheelCfg?.backgroundColor, Color(0xFF1A237E))
        val wheelBorder    = safeColor(wheelCfg?.borderColor, Color.White)
        val wheelSizeDp    = (wheelCfg?.size ?: 300).dp

        val titleColor  = safeColor(visualText?.title?.textStyle?.color,    Color.White)
        val titleSize   = (visualText?.title?.textStyle?.fontSize    ?: 28).sp   // device default 28
        val subColor    = safeColor(visualText?.subtitle?.textStyle?.color, Color(0xFFCCCCCC))
        val subSize     = (visualText?.subtitle?.textStyle?.fontSize ?: 14).sp

        val spinBtnBg    = safeColor(
            visualText?.spinButton?.container?.backgroundColor, Color(0xFFF97316)
        )
        val spinBtnText  = safeColor(
            visualText?.spinButton?.text?.color, Color.White
        )
        val spinBtnCorner = (visualText?.spinButton?.container?.cornerRadius?.topLeft ?: 8).dp

        val rewardBg = safeColor(rewardStyling?.cardBackgroundColor, Color.White)
        val rewardTitleColor = safeColor(rewardStyling?.title?.textStyle?.color, Color(0xFF333333))
        val rewardSubColor   = safeColor(rewardStyling?.subtitle?.textStyle?.color, Color(0xFF777777))

        // Pick the first winning slice for the reward snapshot
        val winningSlice = slices.firstOrNull { it.noPrize != true } ?: slices.firstOrNull()

        // ── Snapshot 1: Main wheel screen ─────────────────────────────────────
        paparazzi.snapshot(name = "01_spinwheel_main") {
            Box(modifier = Modifier.fillMaxSize()) {

                // App background
                if (bgBitmap != null) {
                    Image(bitmap = bgBitmap.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D1117)))
                }

                // Backdrop
                Box(modifier = Modifier.fillMaxSize()
                    .background(backdropColor.copy(alpha = backdropOpacity)))

                // Main content column
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(40.dp))

                    // Title
                    if (!details?.popupTitle.isNullOrBlank()) {
                        Text(
                            text       = details!!.popupTitle!!,
                            color      = titleColor,
                            fontSize   = titleSize,
                            fontWeight = FontWeight.Bold,
                            textAlign  = TextAlign.Center,
                            modifier   = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        )
                    }

                    // Description
                    if (!details?.popupDescription.isNullOrBlank()) {
                        Text(
                            text      = details!!.popupDescription!!,
                            color     = subColor,
                            fontSize  = subSize,
                            textAlign = TextAlign.Center,
                            modifier  = Modifier.fillMaxWidth().padding(bottom = 20.dp)
                        )
                    }

                    // ── THE WHEEL ─────────────────────────────────────────────
                    Box(
                        modifier = Modifier.size(wheelSizeDp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Wheel drawn with Canvas
                        Canvas(modifier = Modifier.size(wheelSizeDp)) {
                            drawWheel(slices, totalSlices, wheelBg, wheelBorder)
                        }

                        // Centre pin
                        Box(
                            modifier = Modifier.size(28.dp)
                                .background(Color.White, CircleShape)
                        )
                    }

                    // Pointer triangle above the wheel
                    Spacer(Modifier.height(4.dp))
                    Text("▼", color = Color.White, fontSize = 24.sp)

                    Spacer(Modifier.height(20.dp))

                    // Available spins counter
                    val spinCount = details?.availableSpins ?: 1
                    val spinText  = details?.content?.availableSpinsText
                        ?: "$spinCount spin${if (spinCount != 1) "s" else ""} remaining"
                    Text(
                        text = spinText,
                        color = safeColor(visualText?.availableSpinText?.textStyle?.color,
                            Color(0xFFFFD700)),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Spin button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(spinBtnBg, RoundedCornerShape(spinBtnCorner))
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text       = details?.spinButtonText ?: "SPIN",
                            color      = spinBtnText,
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // ── Snapshot 2: Reward popup ──────────────────────────────────────────
        paparazzi.snapshot(name = "02_spinwheel_reward") {
            Box(modifier = Modifier.fillMaxSize()) {
                if (bgBitmap != null) {
                    Image(bitmap = bgBitmap.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D1117)))
                }

                // Dark backdrop
                Box(modifier = Modifier.fillMaxSize()
                    .background(safeColor(rewardStyling?.backdropColor, Color.Black)
                        .copy(alpha = 0.7f)))

                // Reward card centred
                Box(modifier = Modifier.align(Alignment.Center)) {
                    Card(
                        modifier  = Modifier.fillMaxWidth(0.85f).wrapContentHeight(),
                        shape     = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                        colors    = CardDefaults.cardColors(containerColor = rewardBg)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Trophy icon
                            Text("🎊", fontSize = 48.sp)
                            Spacer(Modifier.height(12.dp))

                            // Reward title
                            val rewardTitle = rewardStyling?.let {
                                details?.content?.rewardConfiguration?.rewardPopupTitle
                            } ?: "Congratulations!"
                            Text(
                                text       = rewardTitle,
                                color      = rewardTitleColor,
                                fontSize   = (rewardStyling?.title?.textStyle?.fontSize ?: 20).sp,
                                fontWeight = FontWeight.Bold,
                                textAlign  = TextAlign.Center,
                                modifier   = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                            )

                            // Prize label
                            val prizeText = winningSlice?.prizeLabel
                                ?: winningSlice?.rewards?.firstOrNull()?.prizeName
                                ?: "You won a prize!"
                            Text(
                                text      = prizeText,
                                color     = rewardSubColor,
                                fontSize  = (rewardStyling?.subtitle?.textStyle?.fontSize ?: 15).sp,
                                textAlign = TextAlign.Center,
                                modifier  = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            )

                            // Coupon code (if any)
                            val coupon = winningSlice?.coupon
                                ?: winningSlice?.rewards?.firstOrNull()?.couponCode
                            if (!coupon.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text       = coupon,
                                        fontSize   = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color      = Color(0xFF333333),
                                        letterSpacing = 3.sp
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            // CTA button
                            val ctaLabel = winningSlice?.buttonCtaText
                                ?: winningSlice?.rewards?.firstOrNull()?.buttonCta
                                ?: details?.button_text
                                ?: "Claim Reward"
                            val sliceCtaStyling = winningSlice?.rewards?.firstOrNull()
                                ?.styling?.cta
                            val ctaBg = safeColor(
                                sliceCtaStyling?.container?.backgroundColor, Color(0xFFF97316)
                            )
                            val ctaTextColor = safeColor(
                                sliceCtaStyling?.text?.color, Color.White
                            )
                            val ctaCorner = (sliceCtaStyling?.cornerRadius?.topLeft ?: 8).dp

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ctaBg, RoundedCornerShape(ctaCorner))
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(ctaLabel, color = ctaTextColor,
                                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Draws a pie-chart wheel with [totalSlices] segments.
     * Slice colours come from the WheelSlice.styling.wheelStyling.color.background
     * field — falls back to the DEFAULT_SLICE_COLORS palette when not configured.
     */
    private fun DrawScope.drawWheel(
        slices: List<com.appversal.appstorys.api.WheelSlice>,
        totalSlices: Int,
        wheelBg: Color,
        wheelBorder: Color
    ) {
        val sweepAngle = 360f / totalSlices
        val radius = size.minDimension / 2f
        val strokeWidth = 2f

        // Draw each slice
        repeat(totalSlices) { i ->
            val slice = slices.getOrNull(i)
            val sliceBgHex = slice?.styling?.wheelStyling?.color?.background
            val sliceColor = safeColorStatic(sliceBgHex, DEFAULT_SLICE_COLORS[i % DEFAULT_SLICE_COLORS.size])

            drawArc(
                color      = sliceColor,
                startAngle = i * sweepAngle - 90f,
                sweepAngle = sweepAngle,
                useCenter  = true,
                topLeft    = Offset.Zero,
                size       = size
            )

            // Slice border
            drawArc(
                color      = wheelBorder,
                startAngle = i * sweepAngle - 90f,
                sweepAngle = sweepAngle,
                useCenter  = true,
                topLeft    = Offset.Zero,
                size       = size,
                style      = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth)
            )

            // Prize label text in slice
            val label = slice?.prizeLabel?.take(10) ?: ""
            if (label.isNotBlank()) {
                val midAngle = (i * sweepAngle - 90f + sweepAngle / 2f) * (Math.PI / 180f)
                val textR = radius * 0.65f
                val tx = center.x + textR * cos(midAngle).toFloat()
                val ty = center.y + textR * sin(midAngle).toFloat()

                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color    = android.graphics.Color.WHITE
                        textSize = radius * 0.09f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isFakeBoldText = true
                    }
                    save()
                    rotate(
                        (i * sweepAngle + sweepAngle / 2f).toFloat(),
                        tx, ty
                    )
                    drawText(label, tx, ty + paint.textSize / 3f, paint)
                    restore()
                }
            }
        }

        // Outer border ring
        drawCircle(
            color  = wheelBorder,
            radius = radius - strokeWidth / 2f,
            style  = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth * 2)
        )
    }

    private fun safeColor(hex: String?, fallback: Color): Color {
        if (hex.isNullOrBlank()) return fallback
        return runCatching { Color(android.graphics.Color.parseColor(hex)) }
            .getOrDefault(fallback)
    }

    private fun safeColorStatic(hex: String?, fallback: Color): Color {
        if (hex.isNullOrBlank()) return fallback
        return runCatching { Color(android.graphics.Color.parseColor(hex)) }
            .getOrDefault(fallback)
    }
}
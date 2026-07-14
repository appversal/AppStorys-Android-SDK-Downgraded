package com.appversal.appstorys.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.appversal.appstorys.api.ModalDetails
import com.appversal.appstorys.utils.SdkJson
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Paparazzi snapshot — Modal (MOD) campaign.
 *
 * Modals are full-screen-overlay dialogs. They can be:
 *   - media-only  (image/gif/lottie covering the whole modal)
 *   - content     (title + subtitle + primary + secondary CTA, optional image)
 *   - carousel    (multiple content slides via content.set)
 *
 * One snapshot per modal in the list → 01_modal, 02_modal, …
 * For carousel modals, one additional snapshot per slide inside the modal.
 * Media images are loaded from disk (mod_images/<id>.png) to bypass Gradle
 * classpath caching. Lottie/GIF shows as a grey placeholder (no runtime).
 *
 * SETUP (pipeline-config.json):
 *   "paparazziTestClasses": { "MOD": "com.appversal.appstorys.ui.ModalScreenshotTest" }
 *   IMAGE_EXTRACTORS.MOD must download media to mod_images/<modalId>.png
 */
private const val MOD_JSON_RESOURCE = "campaign-data/mod_details.json"
private const val APP_BG_RESOURCE   = "backgrounds/home_screen_kotlin.png"
private const val MOD_IMG_DIR       = "mod_images"

class ModalScreenshotTest {

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
    fun modal_renders() {

        // JSON via classpath
        val modJson = javaClass.classLoader!!
            .getResourceAsStream(MOD_JSON_RESOURCE)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }

        val details: ModalDetails? = modJson?.let {
            runCatching { SdkJson.decodeFromString<ModalDetails>(it) }.getOrNull()
        }

        val bgBitmap = runCatching {
            javaClass.classLoader!!
                .getResourceAsStream(APP_BG_RESOURCE)
                ?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()

        val modals = details?.modals.orEmpty()

        if (modals.isEmpty()) {
            paparazzi.snapshot(name = "01_modal_empty") {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A2E)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No modal data", color = Color.White, fontSize = 14.sp)
                }
            }
            return
        }

        modals.forEachIndexed { mi, modal ->
            val midx = "%02d".format(mi + 1)

            // Styling
            val appearance = modal.styling?.appearance
            val modalBg = safeParseColor(
                appearance?.backgroundColor, Color(0xFFFFFFFF)
            )
            val backdropColor = safeParseColor(
                appearance?.backdrop?.color ?: appearance?.backdropColor,
                Color.Black
            )
            val cornerTl = (appearance?.cornerRadius?.topLeft     ?: 16).dp
            val cornerTr = (appearance?.cornerRadius?.topRight    ?: 16).dp
            val cornerBl = (appearance?.cornerRadius?.bottomLeft  ?: 16).dp
            val cornerBr = (appearance?.cornerRadius?.bottomRight ?: 16).dp
            val enableBackdrop = appearance?.enableBackdrop
                ?: modal.enableBackdrop
                ?: true
            val enableCross = modal.styling?.crossButton?.enableCrossButton
                ?: modal.enableCrossButton
                ?: true

            // Determine if this is a carousel modal (content.set has items)
            val slides = modal.content?.set
            val isCarousel = !slides.isNullOrEmpty()

            if (isCarousel && slides != null) {
                // One snapshot per slide
                slides.forEachIndexed { si, slide ->
                    val sidx = "%02d".format(si + 1)
                    val slideName = "${midx}_modal_slide_$sidx"
                    val mediaBitmap = loadImageFromDisk(MOD_IMG_DIR, "${modal.id}_slide_$si")
                        ?: loadImageFromDisk(MOD_IMG_DIR, modal.id ?: "main")

                    paparazzi.snapshot(name = slideName) {
                        renderModalFrame(
                            bgBitmap = bgBitmap,
                            enableBackdrop = enableBackdrop,
                            backdropColor = backdropColor,
                            modalBg = modalBg,
                            cornerTl = cornerTl.value, cornerTr = cornerTr.value,
                            cornerBl = cornerBl.value, cornerBr = cornerBr.value,
                            mediaBitmap = mediaBitmap,
                            mediaType = slide.chooseMediaType?.type ?: modal.content?.chooseMediaType?.type,
                            titleText = slide.titleText ?: modal.content?.titleText,
                            subtitleText = slide.subtitleText ?: modal.content?.subtitleText,
                            primaryCtaText = slide.primaryCtaText ?: modal.content?.primaryCtaText,
                            secondaryCtaText = slide.secondaryCtaText ?: modal.content?.secondaryCtaText,
                            styling = slide.styling ?: modal.styling,
                            enableCross = enableCross,
                            slideLabel = "${si + 1} / ${slides.size}"
                        )
                    }
                }
            } else {
                // Single modal snapshot
                val modalName = "${midx}_modal"
                val mediaBitmap = loadImageFromDisk(MOD_IMG_DIR, modal.id ?: "main")
                val mediaType = modal.chooseMediaType?.type
                    ?: modal.content?.chooseMediaType?.type

                paparazzi.snapshot(name = modalName) {
                    renderModalFrame(
                        bgBitmap = bgBitmap,
                        enableBackdrop = enableBackdrop,
                        backdropColor = backdropColor,
                        modalBg = modalBg,
                        cornerTl = cornerTl.value, cornerTr = cornerTr.value,
                        cornerBl = cornerBl.value, cornerBr = cornerBr.value,
                        mediaBitmap = mediaBitmap,
                        mediaType = mediaType,
                        titleText = modal.content?.titleText,
                        subtitleText = modal.content?.subtitleText,
                        primaryCtaText = modal.content?.primaryCtaText
                            ?: modal.content?.primaryCta,
                        secondaryCtaText = modal.content?.secondaryCtaText
                            ?: modal.content?.secondaryCtaAlt
                            ?: modal.content?.secondayCta,
                        styling = modal.styling,
                        enableCross = enableCross,
                        slideLabel = null
                    )
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun renderModalFrame(
        bgBitmap: android.graphics.Bitmap?,
        enableBackdrop: Boolean,
        backdropColor: Color,
        modalBg: Color,
        cornerTl: Float, cornerTr: Float,
        cornerBl: Float, cornerBr: Float,
        mediaBitmap: android.graphics.Bitmap?,
        mediaType: String?,
        titleText: String?,
        subtitleText: String?,
        primaryCtaText: String?,
        secondaryCtaText: String?,
        styling: com.appversal.appstorys.api.ModalStyling?,
        enableCross: Boolean,
        slideLabel: String?
    ) {
        val appearance = styling?.appearance
        // Device defaults (PopupModal.kt): primary CTA black, secondary DarkGray
        val primaryCtaBg    = safeParseColor(styling?.primaryCta?.backgroundColor,   Color.Black)
        val primaryCtaText2 = safeParseColor(styling?.primaryCta?.textColor,         Color(0xFFFFFFFF))
        val primaryCorner   = (styling?.primaryCta?.cornerRadius?.topLeft ?: 8).dp
        val secondaryCtaBg  = safeParseColor(styling?.secondaryCta?.backgroundColor, Color.DarkGray)
        val secondaryCtaTxt = safeParseColor(styling?.secondaryCta?.textColor,        Color(0xFFFFFFFF))
        val titleColor      = safeParseColor(styling?.title?.color,    Color(0xFF000000))
        val titleSize       = (styling?.title?.size ?: styling?.title?.fontSize ?: 16).sp   // device 16
        val subColor        = safeParseColor(styling?.subTitle?.color, Color(0xFF555555))
        val subSize         = (styling?.subTitle?.size ?: styling?.subTitle?.fontSize ?: 14).sp

        Box(modifier = Modifier.fillMaxSize()) {

            // ── Layer 0: App background ──────────────────────────────────────
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

            // ── Layer 1: Backdrop ────────────────────────────────────────────
            if (enableBackdrop) {
                // Device default backdrop opacity is 30% (FullPageCarouselModal.kt)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backdropColor.copy(alpha = 0.3f))
                )
            }

            // ── Layer 2: Modal card (centred) ────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .wrapContentHeight()
                    .align(Alignment.Center),
                shape = RoundedCornerShape(
                    topStart    = cornerTl.dp,
                    topEnd      = cornerTr.dp,
                    bottomStart = cornerBl.dp,
                    bottomEnd   = cornerBr.dp
                ),
                colors = CardDefaults.cardColors(containerColor = modalBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ── Media area ───────────────────────────────────────────
                    when {
                        mediaBitmap != null -> {
                            Image(
                                bitmap = mediaBitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                        mediaType == "lottie" -> {
                            // Lottie cannot run in JVM — grey placeholder
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .background(Color(0xFFBDBDBD)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Lottie (runtime only)",
                                    color = Color(0xFF888888),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        mediaType == "gif" -> {
                            // GIF cannot animate in JVM — grey placeholder
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .background(Color(0xFFBDBDBD)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "GIF (runtime only)",
                                    color = Color(0xFF888888),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        else -> { /* no media */ }
                    }

                    // ── Text content ─────────────────────────────────────────
                    if (!titleText.isNullOrBlank() || !subtitleText.isNullOrBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (!titleText.isNullOrBlank()) {
                                Text(
                                    text = titleText,
                                    color = titleColor,
                                    fontSize = titleSize,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (!subtitleText.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = subtitleText,
                                    color = subColor,
                                    fontSize = subSize,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // ── CTAs ─────────────────────────────────────────────────
                    val hasPrimary   = !primaryCtaText.isNullOrBlank()
                    val hasSecondary = !secondaryCtaText.isNullOrBlank()

                    if (hasPrimary || hasSecondary) {
                        Column(
                            // CORRECT — use all individual sides:
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (hasPrimary) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(primaryCtaBg, RoundedCornerShape(primaryCorner))
                                        .padding(vertical = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = primaryCtaText!!,
                                        color = primaryCtaText2,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                            if (hasSecondary) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(secondaryCtaBg, RoundedCornerShape(8.dp))
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = secondaryCtaText!!,
                                        color = secondaryCtaTxt,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }

                    // ── Carousel slide indicator ─────────────────────────────
                    if (slideLabel != null) {
                        Row(
                            modifier = Modifier.padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = slideLabel,
                                color = Color(0xFF888888),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // ── Cross button (top-right of modal) ────────────────────────────
            if (enableCross) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 8.dp, end = 8.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Box(
                        modifier = Modifier
                            .padding(
                                top   = 8.dp,
                                start = (0.88f * 360).dp - 40.dp
                            )
                            .size(28.dp)
                            .background(Color(0x99000000), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }

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
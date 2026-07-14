package com.appversal.appstorys.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.appversal.appstorys.api.SurveyDetails
import com.appversal.appstorys.utils.SdkJson
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Paparazzi snapshot — Survey (SUR) campaign.
 *
 * Mirrors the REAL renderer (SurveyBottomSheet.kt) 1:1. The real sheet is a
 * Dialog, which Paparazzi's headless JVM can't host, so the sheet content is
 * replicated here — but every default, color fallback and layout rule below
 * is copied from SurveyBottomSheet/SurveyContent, not invented:
 *
 *   - Per slide the device renders ONLY: title, subtitle, options, CTA, dots.
 *     It never renders slide.question (SurveyContent has no question block)
 *     and never renders slide.image. This test matches that — the earlier
 *     version drew question + image + a "1 / N" label, which do not exist on
 *     a real device (question text duplicated the subtitle).
 *   - No options are pre-selected on first render.
 *   - "Others" (additionalComment.enabled / hasOthers) appears as an OPTION
 *     ROW; its free-text box only appears after selection, so no box here.
 *   - Option rows: numbered prefix by default (optionListStyle "number"),
 *     bg optionColor→LightGray, border #E5E7EB 1dp, corner 12, text centered.
 *   - CTA: "NEXT" on non-last slides, "SUBMIT" on the last (unless
 *     submitButtonText set), height 56, corner 12, bg cta.container →
 *     ctaBackgroundColor → #000000, full width.
 *   - Cross button: 18dp circle, top-right, fill crossButton.color.fill →
 *     ctaBackgroundColor → transparent, white cross. Shown unless
 *     crossButton.enabled == false. No drag handle exists on the device.
 *   - Sheet: corner default 24dp top, bg white, content padding 15dp.
 *     Backdrop: backdropColor→black at backdropOpacity→100%.
 *   - Multi-slide: dot indicators below the CTA (selected dot elongated).
 *   - Thank-you page renders ONLY when thankYouButtonConfig.enabled == true.
 *     Image 80x80 (12dp margins), title 20sp, subtitle 14sp, CTA "Okay",
 *     bg #1F35DB, height 50, corner 12, NOT full width.
 *
 * Snapshots: 01_survey_slide, 02_survey_slide, … + XX_survey_thankyou (only
 * if enabled). Thank-you image from disk: sur_images/thankyou.png.
 *
 * SETUP (pipeline-config.json):
 *   "paparazziTestClasses": { "SUR": "com.appversal.appstorys.ui.SurveyScreenshotTest" }
 * IMAGE_EXTRACTORS.SUR → downloads thankYouImage
 */

private const val SUR_JSON_RESOURCE = "campaign-data/sur_details.json"
private const val APP_BG_RESOURCE   = "backgrounds/home_screen_kotlin.png"
private const val SUR_IMG_DIR       = "sur_images"

class SurveyScreenshotTest {

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
    fun survey_slides() {

        val surJson = javaClass.classLoader!!
            .getResourceAsStream(SUR_JSON_RESOURCE)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }

        val details: SurveyDetails? = surJson?.let {
            runCatching { SdkJson.decodeFromString<SurveyDetails>(it) }.getOrNull()
        }

        val bgBitmap = runCatching {
            javaClass.classLoader!!
                .getResourceAsStream(APP_BG_RESOURCE)
                ?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()

        // ── Styling — every fallback copied from SurveyBottomSheet.kt ─────────
        val styling    = details?.styling
        val appearance = styling?.appearance

        val sheetBg = safeColor(
            appearance?.backgroundColor ?: styling?.backgroundColor, Color.White
        )
        val backdropColor = safeColor(appearance?.backdropColor, Color.Black)
        // Real default is 100 (fully opaque backdrop), not 50
        val backdropOpacity = ((appearance?.backdropOpacity
            ?: appearance?.backgroundOpacity ?: 100).coerceIn(0, 100)) / 100f

        // Real default corner radius is 24dp
        val sheetShape = RoundedCornerShape(
            topStart = (appearance?.cornerRadius?.topLeft  ?: 24).dp,
            topEnd   = (appearance?.cornerRadius?.topRight ?: 24).dp
        )

        // Option styling — defaults from SurveyOptionItem
        val optCfg    = styling?.options
        val nonSelBg  = safeColor(
            optCfg?.nonSelectedOptions?.colors?.background ?: styling?.optionColor,
            Color.LightGray
        )
        val nonSelBdr = safeColor(optCfg?.nonSelectedOptions?.colors?.border, Color(0xFFE5E7EB))
        val nonSelTxt = safeColor(
            optCfg?.nonSelectedOptions?.colors?.text ?: styling?.optionTextColor,
            Color.Black
        )
        val optShape = RoundedCornerShape(
            topStart    = (optCfg?.cornerRadius?.topLeft     ?: 12).dp,
            topEnd      = (optCfg?.cornerRadius?.topRight    ?: 12).dp,
            bottomStart = (optCfg?.cornerRadius?.bottomLeft  ?: 12).dp,
            bottomEnd   = (optCfg?.cornerRadius?.bottomRight ?: 12).dp
        )
        val optSpacing    = (optCfg?.optionsSpacing?.toIntOrNull() ?: 12).dp
        val bulletSpacing = (optCfg?.bulletSpacing?.toIntOrNull() ?: 12).dp
        val optionListStyle = optCfg?.optionListStyle ?: "number"
        val optTextSize   = (optCfg?.nonSelectedOptions?.textStyle?.fontSize ?: 14).sp

        // CTA — defaults from the navButtonConfig block
        val ctaCfg  = styling?.cta
        val ctaBg   = safeColor(
            ctaCfg?.container?.backgroundColor ?: styling?.ctaBackgroundColor, Color(0xFF000000)
        )
        val ctaTextColor = safeColor(
            ctaCfg?.text?.color ?: styling?.ctaTextIconColor, Color.White
        )
        val ctaTextSize = (ctaCfg?.text?.fontSize ?: 16).sp
        val ctaHeight   = (ctaCfg?.container?.height ?: 56).dp
        val ctaShape = RoundedCornerShape(
            topStart    = (ctaCfg?.cornerRadius?.topLeft     ?: 12).dp,
            topEnd      = (ctaCfg?.cornerRadius?.topRight    ?: 12).dp,
            bottomStart = (ctaCfg?.cornerRadius?.bottomLeft  ?: 12).dp,
            bottomEnd   = (ctaCfg?.cornerRadius?.bottomRight ?: 12).dp
        )

        // Title / subtitle — real code falls back to surveyQuestionColor for BOTH
        val titleColor = safeColor(
            styling?.title?.textStyle?.color ?: styling?.surveyQuestionColor, Color.Black
        )
        val titleSize = (styling?.title?.textStyle?.fontSize ?: 18).sp
        val subtitleColor = safeColor(
            styling?.subtitle?.textStyle?.color ?: styling?.surveyQuestionColor, Color.Black
        )
        val subtitleSize = (styling?.subtitle?.textStyle?.fontSize ?: 14).sp

        // Cross button — enabled unless explicitly false
        val crossButton   = styling?.crossButton
        val crossEnabled  = crossButton?.enabled != false
        val crossFill     = safeColor(
            crossButton?.color?.fill ?: styling?.ctaBackgroundColor, Color.Transparent
        )
        val crossColor    = safeColor(crossButton?.color?.cross, Color.White)
        val crossSize     = (crossButton?.size ?: 18).dp

        // Dots — selected = CTA bg, unselected = optionColor → LightGray
        val dotSelected   = ctaBg
        val dotUnselected = safeColor(styling?.optionColor, Color.LightGray)

        // ── Slide list — same fallback as the real component ──────────────────
        // NOTE: for the legacy single-question format the device puts the
        // question into slide.question — which SurveyContent NEVER renders.
        // On a real device such a survey shows only its options. Mirrored here.
        val slides = details?.slides
            ?.sortedBy { it.order }
            ?.takeIf { it.isNotEmpty() }
            ?: run {
                if (!details?.surveyQuestion.isNullOrBlank()) {
                    listOf(
                        com.appversal.appstorys.api.SurveySlide(
                            id              = "legacy",
                            order           = 1,
                            parent          = null,
                            title           = null,
                            subtitle        = null,
                            question        = details?.surveyQuestion,
                            options         = details?.surveyOptions,
                            image           = null,
                            submitButtonText = null,
                            logic           = null,
                            additionalComment = null
                        )
                    )
                } else emptyList()
            }

        val totalSlides = slides.size

        if (slides.isEmpty()) {
            paparazzi.snapshot(name = "01_survey_empty") {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF0D1117)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No survey data", color = Color.White, fontSize = 14.sp)
                }
            }
        } else {

            slides.forEachIndexed { i, slide ->
                val idx  = "%02d".format(i + 1)
                val name = "${idx}_survey_slide"

                // Option values sorted by key (option1, option2, …) + "Others"
                // row when additional comments are enabled — same as device.
                val baseOptions = slide.options?.entries?.sortedBy { it.key }?.map { it.value }
                    ?: slide.surveyOptions?.entries?.sortedBy { it.key }?.map { it.value }
                    ?: emptyList()
                val options =
                    if (slide.additionalComment?.enabled == true || slide.hasOthers == true)
                        baseOptions + "Others"
                    else baseOptions

                val isLastSlide = i == totalSlides - 1
                val btnLabel = slide.submitButtonText?.takeIf { it.isNotEmpty() }
                    ?: if (isLastSlide) "SUBMIT" else "NEXT"

                paparazzi.snapshot(name = name) {
                    SurveyBackground(bgBitmap, backdropColor, backdropOpacity) {
                        SurveySheet(
                            bg = sheetBg,
                            shape = sheetShape,
                            crossEnabled = crossEnabled,
                            crossFill = crossFill,
                            crossColor = crossColor,
                            crossSize = crossSize
                        ) {

                            // Slide title (device: 18sp, centered)
                            val slideTitle = slide.title
                            if (!slideTitle.isNullOrBlank()) {
                                Text(
                                    text      = slideTitle,
                                    color     = titleColor,
                                    fontSize  = titleSize,
                                    textAlign = TextAlign.Center,
                                    modifier  = Modifier.fillMaxWidth()
                                )
                            }

                            // Slide subtitle (device: 14sp, centered).
                            // slide.question is intentionally NOT rendered —
                            // the device never shows it (see SurveyContent).
                            val slideSubtitle = slide.subtitle
                            if (!slideSubtitle.isNullOrBlank()) {
                                Text(
                                    text      = slideSubtitle,
                                    color     = subtitleColor,
                                    fontSize  = subtitleSize,
                                    textAlign = TextAlign.Center,
                                    modifier  = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            // Answer options — none selected on first render
                            options.forEachIndexed { oi, option ->
                                val prefix = when (optionListStyle.lowercase()) {
                                    "number" -> "${oi + 1}."
                                    "roman"  -> "${toRoman(oi + 1).uppercase()}."
                                    "alpha", "alphabetic", "alphabet" -> "${('A' + oi)}."
                                    else -> ""
                                }
                                val isBulleted = optionListStyle.equals("bulleted", true)

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight()
                                        .clip(optShape)
                                        .background(nonSelBg)
                                        .border(1.dp, nonSelBdr, optShape),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (prefix.isNotEmpty()) {
                                            Text(
                                                text       = prefix,
                                                color      = nonSelBdr,
                                                fontSize   = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        } else if (isBulleted) {
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .border(1.dp, nonSelBdr, CircleShape)
                                            )
                                        }
                                        Spacer(Modifier.width(bulletSpacing))
                                        Text(
                                            text      = option,
                                            color     = nonSelTxt,
                                            fontSize  = optTextSize,
                                            textAlign = TextAlign.Center,
                                            modifier  = Modifier.weight(1f)
                                        )
                                    }
                                }
                                if (oi < options.lastIndex) {
                                    Spacer(Modifier.height(optSpacing))
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            // NEXT / SUBMIT button (device: 56dp, full width)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(ctaHeight)
                                    .background(ctaBg, ctaShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text       = btnLabel,
                                    color      = ctaTextColor,
                                    fontSize   = ctaTextSize,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Dot indicators (multi-slide only, current = i)
                            if (totalSlides > 1) {
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    repeat(totalSlides) { d ->
                                        val selected = d == i
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 3.dp)
                                                .height(8.dp)
                                                .width(if (selected) 20.dp else 8.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (selected) dotSelected else dotUnselected)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Thank you page — device shows it ONLY when enabled ───────────────
        if (details != null && details.thankYouButtonConfig?.enabled == true) {
            val thankyouIdx    = "%02d".format(slides.size + 1)
            val thankyouBitmap = loadImageFromDisk(SUR_IMG_DIR, "thankyou")
            val thankyouPage   = styling?.thankyouPage
            val tyCta          = thankyouPage?.cta

            paparazzi.snapshot(name = "${thankyouIdx}_survey_thankyou") {
                SurveyBackground(bgBitmap, backdropColor, backdropOpacity) {
                    SurveySheet(
                        bg = sheetBg,
                        shape = sheetShape,
                        crossEnabled = crossEnabled,
                        crossFill = crossFill,
                        crossColor = crossColor,
                        crossSize = crossSize
                    ) {

                        // Image — device default 80x80 with 12dp margins,
                        // rendered only when thankYouImage is configured
                        if (!details.thankYouImage.isNullOrEmpty()) {
                            val imgStyle = thankyouPage?.imageStyle
                            val m = imgStyle?.margin
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .size(
                                        width  = (imgStyle?.width  ?: 80).dp,
                                        height = (imgStyle?.height ?: 80).dp
                                    )
                                    .padding(
                                        top    = (m?.top    ?: 12).dp,
                                        bottom = (m?.bottom ?: 12).dp,
                                        start  = (m?.left   ?: 12).dp,
                                        end    = (m?.right  ?: 12).dp
                                    )
                            ) {
                                if (thankyouBitmap != null) {
                                    Image(
                                        bitmap = thankyouBitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize()
                                            .background(Color(0xFFEEEEEE), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🎉", fontSize = 32.sp)
                                    }
                                }
                            }
                        }

                        // Title — device default 20sp, centered
                        if (!details.thankYouTitle.isNullOrEmpty()) {
                            Text(
                                text      = details.thankYouTitle!!,
                                color     = safeColor(
                                    thankyouPage?.title?.textStyle?.color, Color.Black
                                ),
                                fontSize  = (thankyouPage?.title?.textStyle?.fontSize ?: 20).sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier  = Modifier.fillMaxWidth()
                            )
                        }

                        // Subtitle — device default 14sp, centered
                        if (!details.thankYouText.isNullOrEmpty()) {
                            Text(
                                text      = details.thankYouText!!,
                                color     = safeColor(
                                    thankyouPage?.subtitle?.textStyle?.color, Color.Black
                                ),
                                fontSize  = (thankyouPage?.subtitle?.textStyle?.fontSize ?: 14).sp,
                                textAlign = TextAlign.Center,
                                modifier  = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        // CTA — device: "Okay", bg #1F35DB, 50dp, NOT full width
                        val tyBtnBg = safeColor(
                            tyCta?.container?.backgroundColor, Color(0xFF1F35DB)
                        )
                        val tyBtnTextColor = safeColor(tyCta?.text?.color, Color.White)
                        val tyBtnShape = RoundedCornerShape(
                            topStart    = (tyCta?.cornerRadius?.topLeft     ?: 12).dp,
                            topEnd      = (tyCta?.cornerRadius?.topRight    ?: 12).dp,
                            bottomStart = (tyCta?.cornerRadius?.bottomLeft  ?: 12).dp,
                            bottomEnd   = (tyCta?.cornerRadius?.bottomRight ?: 12).dp
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .height((tyCta?.container?.height ?: 50).dp)
                                .background(tyBtnBg, tyBtnShape)
                                .padding(horizontal = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text     = details.thankYouButtonText?.takeIf { it.isNotBlank() }
                                    ?: "Okay",
                                color    = tyBtnTextColor,
                                fontSize = (tyCta?.text?.fontSize ?: 14).sp
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Composable helpers ────────────────────────────────────────────────────

    @androidx.compose.runtime.Composable
    private fun SurveyBackground(
        bgBitmap: android.graphics.Bitmap?,
        backdropColor: Color,
        backdropOpacity: Float,
        content: @androidx.compose.runtime.Composable () -> Unit
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(backdropColor.copy(alpha = backdropOpacity))
            )
            content()
        }
    }

    /**
     * Sheet chrome copied from the real SurveyBottomSheet: bottom-aligned Box
     * clipped to the top corner radius, 15dp content padding, NO drag handle,
     * cross button floating top-right (18dp circle, white cross).
     */
    @androidx.compose.runtime.Composable
    private fun SurveySheet(
        bg: Color,
        shape: RoundedCornerShape,
        crossEnabled: Boolean,
        crossFill: Color,
        crossColor: Color,
        crossSize: androidx.compose.ui.unit.Dp,
        content: @androidx.compose.runtime.Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clip(shape)
                    .background(bg)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(15.dp),
                    content = content
                )
                if (crossEnabled) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(crossSize)
                                .clip(CircleShape)
                                .background(crossFill),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(crossSize * 0.55f)) {
                                val stroke = size.minDimension * 0.14f
                                drawLine(
                                    color = crossColor,
                                    start = Offset(0f, 0f),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = stroke,
                                    cap = StrokeCap.Round
                                )
                                drawLine(
                                    color = crossColor,
                                    start = Offset(size.width, 0f),
                                    end = Offset(0f, size.height),
                                    strokeWidth = stroke,
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadImageFromDisk(imgDir: String, key: String): android.graphics.Bitmap? {
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

    private fun safeColor(hex: String?, fallback: Color): Color {
        if (hex.isNullOrBlank()) return fallback
        return runCatching { Color(android.graphics.Color.parseColor(hex)) }
            .getOrDefault(fallback)
    }

    private fun toRoman(num: Int): String {
        val values = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
        val symbols = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
        val sb = StringBuilder()
        var n = num
        for (i in values.indices) {
            while (n >= values[i]) {
                sb.append(symbols[i])
                n -= values[i]
            }
        }
        return sb.toString().lowercase()
    }
}

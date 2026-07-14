package com.appversal.appstorys.ui

import android.graphics.BitmapFactory
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
import com.appversal.appstorys.api.CSATDetails
import com.appversal.appstorys.utils.SdkJson
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Paparazzi snapshot — CSAT campaign.
 *
 * CSAT has three pages shown sequentially:
 *   01_csat_rating   — initial rating page (star / emoji / number scale)
 *   02_csat_feedback — feedback options + optional comment box
 *   03_csat_thankyou — thank you confirmation page
 *
 * Rating type is driven by details.styling.rating.ratingType from the live
 * CDN JSON — the test renders whichever type the campaign is configured for.
 * All colours, corner radii, and text come directly from the model fields.
 *
 * SETUP (pipeline-config.json):
 *   "paparazziTestClasses": { "CSAT": "com.appversal.appstorys.ui.CsatScreenshotTest" }
 * IMAGE_EXTRACTORS.CSAT → downloads thankyouImage to csat_images/thankyou.png
 */

private const val CSAT_JSON_RESOURCE = "campaign-data/csat_details.json"
private const val APP_BG_RESOURCE    = "backgrounds/home_screen_kotlin.png"
private const val CSAT_IMG_DIR       = "csat_images"

class CsatScreenshotTest {

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
    fun csat_pages() {

        // JSON via classpath
        val csatJson = javaClass.classLoader!!
            .getResourceAsStream(CSAT_JSON_RESOURCE)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }

        val details: CSATDetails? = csatJson?.let {
            runCatching { SdkJson.decodeFromString<CSATDetails>(it) }.getOrNull()
        }

        val bgBitmap = runCatching {
            javaClass.classLoader!!
                .getResourceAsStream(APP_BG_RESOURCE)
                ?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()

        // Thank you image via disk
        val thankyouBitmap = loadImageFromDisk(CSAT_IMG_DIR, "thankyou")

        // ── Common styling ────────────────────────────────────────────────────
        val appearance  = details?.styling?.appearance
        val sheetBg     = safeColor(appearance?.backgroundColor, Color(0xFFFFFFFF))
        val cornerRadius = (appearance?.borderRadius ?: 16).dp
        val shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius,
            bottomStart = 0.dp, bottomEnd = 0.dp)

        val rating       = details?.styling?.rating
        val ratingType   = rating?.ratingType ?: "star"
        val initFeedback = details?.styling?.initialFeedback

        // ── PAGE 1: Rating ────────────────────────────────────────────────────
        paparazzi.snapshot(name = "01_csat_rating") {
            CsatBackground(bgBitmap) {
                CsatSheet(sheetBg, shape) {

                    // Title from initialFeedback
                    val titleColor = safeColor(initFeedback?.title?.color, Color(0xFF000000))
                    val titleSize  = (initFeedback?.title?.textStyle?.fontSize
                        ?: initFeedback?.title?.textStyle?.size ?: 18).sp

                    if (!details?.title.isNullOrBlank()) {
                        Text(
                            text       = details!!.title!!,
                            color      = titleColor,
                            fontSize   = titleSize,
                            fontWeight = FontWeight.Bold,
                            textAlign  = TextAlign.Center,
                            modifier   = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                        )
                    }
                    if (!details?.descriptionText.isNullOrBlank()) {
                        val subColor = safeColor(initFeedback?.subtitle?.color, Color(0xFF777777))
                        Text(
                            text      = details!!.descriptionText!!,
                            color     = subColor,
                            fontSize  = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier  = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        )
                    }

                    // Rating widget.
                    // The SDK's final branch is `else -> StarRating(...)`
                    // (CsatDialog.kt), i.e. ANY unrecognised rating type still
                    // renders STARS on the device. This test used to draw a grey
                    // "Rating: <type>" placeholder instead, so an unknown type
                    // produced a golden showing something the product never
                    // shows. Normalise the same way the SDK does, so the
                    // placeholder below is unreachable.
                    val effectiveRatingType = when (ratingType.lowercase()) {
                        "star", "emoji", "number" -> ratingType.lowercase()
                        else -> "star"
                    }
                    when (effectiveRatingType) {

                        "star" -> {
                            val unselBg = safeColor(
                                rating?.star?.unselected?.stylingStar?.background
                                    ?: rating?.unselected?.background, Color(0xFFCCCCCC)
                            )
                            val highBg = safeColor(
                                rating?.star?.high?.stylingStar?.background
                                    ?: rating?.high?.background, Color(0xFFFFD700)
                            )
                            Row(
                                modifier            = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                repeat(5) { i ->
                                    val starColor = if (i < 3) highBg else unselBg
                                    Box(
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .size(36.dp)
                                            .background(starColor, RoundedCornerShape(4.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("★", color = Color.White, fontSize = 20.sp)
                                    }
                                }
                            }
                        }

                        "emoji" -> {
                            // Device defaults (CsatDialog.kt EmojiRating): set starts
                            // with 😢, 48dp circles, 24sp, 8dp gaps, nothing selected
                            // on first render (unselected fill #f0f0f0 + #cccccc 1dp)
                            val emojiValues = rating?.emoji?.values
                                ?: listOf("😢", "😕", "😐", "🙂", "😄")
                            val unselBg = safeColor(
                                rating?.emoji?.unselected?.stylingContainer?.fill, Color(0xFFF0F0F0)
                            )
                            val unselBorder = safeColor(
                                rating?.emoji?.unselected?.stylingContainer?.border, Color(0xFFCCCCCC)
                            )
                            Row(
                                modifier            = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                emojiValues.forEachIndexed { i, emoji ->
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(unselBg, CircleShape)
                                            .border(1.dp, unselBorder, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(emoji, fontSize = 24.sp)
                                    }
                                    if (i < emojiValues.lastIndex) Spacer(Modifier.width(8.dp))
                                }
                            }
                        }

                        "number" -> {
                            // Device (CsatDialog.kt NumberRating): exactly FIVE 48dp
                            // circles numbered 1-5 — not a 0-10 scale. First render
                            // has nothing selected: fill #ededed, text #FE6B35, 16sp.
                            val unselBg  = safeColor(
                                rating?.number?.unselected?.stylingContainer?.fill, Color(0xFFEDEDED)
                            )
                            val unselText = safeColor(
                                rating?.number?.unselected?.stylingNumber?.text
                                    ?: rating?.number?.stylingNumber?.text, Color(0xFFFE6B35)
                            )
                            val numSize = (rating?.number?.stylingNumber?.textSize ?: 16).sp

                            if (!details?.lowStarText.isNullOrBlank() || !details?.highStarText.isNullOrBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(details?.lowStarText ?: "", fontSize = 10.sp, color = Color(0xFF777777))
                                    Text(details?.highStarText ?: "", fontSize = 10.sp, color = Color(0xFF777777))
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                (1..5).forEach { n ->
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(unselBg, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(n.toString(), color = unselText, fontSize = numSize)
                                    }
                                    if (n < 5) Spacer(Modifier.width(8.dp))
                                }
                            }
                        }

                        else -> {
                            // UNREACHABLE — effectiveRatingType above collapses
                            // anything unknown to "star", mirroring the SDK.
                            // Kept only so the `when` stays exhaustive-looking;
                            // if this ever renders, the normalisation broke.
                            Box(
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                                    .background(Color(0xFFEEEEEE), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Rating: $ratingType", color = Color(0xFF888888), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // ── PAGE 2: Feedback options ──────────────────────────────────────────
        paparazzi.snapshot(name = "02_csat_feedback") {
            CsatBackground(bgBitmap) {
                CsatSheet(sheetBg, shape) {

                    Text(
                        text       = rating?.highRatingTitle ?: rating?.lowRatingTitle ?: "How can we improve?",
                        color      = Color(0xFF000000),
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign  = TextAlign.Center,
                        modifier   = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    )
                    if (!rating?.highRatingSubtitle.isNullOrBlank()) {
                        Text(
                            text     = rating!!.highRatingSubtitle!!,
                            color    = Color(0xFF777777),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        )
                    }

                    // Feedback options from feedbackOption model
                    val feedbackPage = details?.styling?.feedbackPage
                    val options      = details?.feedbackOption?.toList() ?: emptyList()
                    val optStyle     = feedbackPage?.options
                    val nonSelBg     = safeColor(optStyle?.nonSelectedOptions?.colors?.background, Color(0xFFF5F5F5))
                    val nonSelBorder = safeColor(optStyle?.nonSelectedOptions?.colors?.border,     Color(0xFFDDDDDD))
                    val nonSelText   = safeColor(optStyle?.nonSelectedOptions?.colors?.text,       Color(0xFF333333))
                    val selBg        = safeColor(optStyle?.selectedOptions?.colors?.background,    Color(0xFFE3F2FD))
                    val selBorder    = safeColor(optStyle?.selectedOptions?.colors?.border,        Color(0xFF1976D2))
                    val selText      = safeColor(optStyle?.selectedOptions?.colors?.text,          Color(0xFF1976D2))
                    val optCorner    = (optStyle?.cornerRadius?.topLeft ?: 24).dp   // device default 24

                    options.forEachIndexed { i, option ->
                        val isSelected = i == 0
                        val bg     = if (isSelected) selBg     else nonSelBg
                        val border = if (isSelected) selBorder else nonSelBorder
                        val text   = if (isSelected) selText   else nonSelText
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(bg, RoundedCornerShape(optCorner))
                                .border(1.dp, border, RoundedCornerShape(optCorner))
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(option, color = text, fontSize = 13.sp)
                        }
                    }

                    // Additional comments box
                    val comments = feedbackPage?.additionalComments
                    if (comments?.enabled == true) {
                        Spacer(Modifier.height(8.dp))
                        val commentBg = safeColor(comments.colors?.background, Color(0xFFF9F9F9))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(92.dp)   // device: 92dp, corner 18
                                .background(commentBg, RoundedCornerShape(18.dp))
                                .border(1.dp, Color(0xFFDDDDDD), RoundedCornerShape(18.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = comments.placeholder ?: "Add a comment...",
                                color = Color(0xFFAAAAAA),
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Submit button
                    val submitBtn = feedbackPage?.submitButton
                    val btnBg     = safeColor(
                        submitBtn?.cta?.container?.backgroundColor
                            ?: submitBtn?.colors?.background, Color(0xFF007AFF)   // device default blue
                    )
                    val btnText   = safeColor(
                        submitBtn?.cta?.text?.color
                            ?: submitBtn?.colors?.text, Color(0xFFFFFFFF)
                    )
                    val btnRadius = (submitBtn?.cta?.cornerRadius?.topLeft
                        ?: submitBtn?.containerRadius?.topLeft ?: 12).dp   // device default 12
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(btnBg, RoundedCornerShape(btnRadius))
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text       = submitBtn?.text ?: "Submit",
                            color      = btnText,
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // ── PAGE 3: Thank you ─────────────────────────────────────────────────
        paparazzi.snapshot(name = "03_csat_thankyou") {
            CsatBackground(bgBitmap) {
                CsatSheet(sheetBg, shape) {

                    val thankyouPage = details?.styling?.thankyouPage
                    val imgStyle     = thankyouPage?.imageStyle

                    // Thank you image
                    val imgW = (imgStyle?.width  ?: 66).dp   // device default 66x66
                    val imgH = (imgStyle?.height ?: 66).dp
                    Box(
                        modifier = Modifier
                            .padding(
                                top    = (imgStyle?.margin?.top    ?: 8).dp,
                                bottom = (imgStyle?.margin?.bottom ?: 8).dp
                            )
                            .size(width = imgW, height = imgH)
                            .align(Alignment.CenterHorizontally)
                    ) {
                        if (thankyouBitmap != null) {
                            Image(
                                bitmap           = thankyouBitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier         = Modifier.fillMaxSize(),
                                contentScale     = ContentScale.Fit
                            )
                        } else {
                            Box(
                                modifier         = Modifier.fillMaxSize()
                                    .background(Color(0xFFEEEEEE), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🎉", fontSize = 40.sp)
                            }
                        }
                    }

                    // Title
                    val titleColor = safeColor(
                        thankyouPage?.title?.color
                            ?: thankyouPage?.title?.textStyle?.color, Color(0xFF000000)
                    )
                    if (!details?.thankyouText.isNullOrBlank()) {
                        Text(
                            text       = details!!.thankyouText!!,
                            color      = titleColor,
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign  = TextAlign.Center,
                            modifier   = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        )
                    }

                    // Subtitle / description
                    val subColor = safeColor(
                        thankyouPage?.subtitle?.color
                            ?: thankyouPage?.subtitle?.textStyle?.color, Color(0xFF777777)
                    )
                    if (!details?.thankyouDescription.isNullOrBlank()) {
                        Text(
                            text      = details!!.thankyouDescription!!,
                            color     = subColor,
                            fontSize  = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier  = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        )
                    }

                    // Done button
                    val doneBtn   = thankyouPage?.doneButton
                    val doneBg    = safeColor(
                        doneBtn?.cta?.container?.backgroundColor
                            ?: doneBtn?.colors?.background, Color(0xFF007AFF)   // device default blue
                    )
                    val doneText  = safeColor(
                        doneBtn?.cta?.text?.color
                            ?: doneBtn?.colors?.text, Color(0xFFFFFFFF)
                    )
                    val doneRadius = (doneBtn?.cta?.cornerRadius?.topLeft
                        ?: doneBtn?.containerRadius?.topLeft ?: 12).dp   // device default 12

                    if (doneBtn?.enabled != false) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(doneBg, RoundedCornerShape(doneRadius))
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text       = doneBtn?.text ?: "Done",
                                color      = doneText,
                                fontSize   = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun CsatBackground(
        bgBitmap: android.graphics.Bitmap?,
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
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
            content()
        }
    }

    @androidx.compose.runtime.Composable
    private fun CsatSheet(
        bg: Color,
        shape: RoundedCornerShape,
        content: @androidx.compose.runtime.Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Card(
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = bg),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                // Drag handle
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.width(36.dp).height(4.dp)
                            .background(Color(0xFFCCCCCC), RoundedCornerShape(2.dp))
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = content
                )
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
        return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)
    }
}
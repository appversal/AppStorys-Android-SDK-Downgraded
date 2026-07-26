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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
        // RatingComponent wraps every rating widget in a Row whose
        // horizontalArrangement comes from styling.rating.alignment. All three
        // branches below used to hardcode Center, so a left/right-aligned
        // campaign would render centred here and match nothing on device.
        val ratingArrangement = when ((rating?.alignment ?: "center").lowercase()) {
            "left", "start" -> Arrangement.Start
            "right", "end"  -> Arrangement.End
            else            -> Arrangement.Center
        }
        val initFeedback = details?.styling?.initialFeedback
        // CsatDialog reads whichever of the two field names the payload uses:
        //   styling.crossButton ?: styling.csatCrossButton
        val csatCross = details?.styling?.crossButton ?: details?.styling?.csatCrossButton

        // ── PAGE 1: Rating ────────────────────────────────────────────────────
        paparazzi.snapshot(name = "01_csat_rating") {
            CsatBackground(bgBitmap) {
                CsatSheet(sheetBg, shape, csatCross, appearance?.margin?.right) {

                    // Title from initialFeedback
                    val titleColor = safeColor(initFeedback?.title?.color, Color(0xFF000000))
                    val titleSize  = (initFeedback?.title?.textStyle?.fontSize
                        ?: initFeedback?.title?.textStyle?.size ?: 18).sp

                    // Alignment/weight/size come from the campaign, NOT hardcoded.
                    // CsatDialog routes both strings through CommonText, and this
                    // campaign sends textAlign "left" for title AND subtitle — the
                    // test used to centre both. Verified against a device
                    // screenshot: the sheet's title sits hard left at x=25.
                    val titleTs = initFeedback?.title?.textStyle
                    val subTs   = initFeedback?.subtitle?.textStyle
                    if (!details?.title.isNullOrBlank()) {
                        Text(
                            text       = details!!.title!!,
                            color      = titleColor,
                            fontSize   = titleSize,
                            // CsatDialog defaults the TITLE to bold when
                            // fontDecoration is empty:
                            //   fontDecoration?.takeIf { it.isNotEmpty() } ?: listOf("bold")
                            // so bold here is correct only by that default — an
                            // ["italic"] campaign must NOT come out bold.
                            fontWeight = sdkFontWeight(
                                titleTs?.fontDecoration?.takeIf { it.isNotEmpty() }
                                    ?: listOf("bold")
                            ),
                            textAlign  = sdkTextAlign(titleTs?.textAlign ?: titleTs?.alignment),
                            modifier   = Modifier.fillMaxWidth().padding(
                                start  = (initFeedback?.title?.margin?.left  ?: 16).dp,
                                end    = (initFeedback?.title?.margin?.right ?: 16).dp,
                                bottom = 4.dp
                            )
                        )
                    }
                    if (!details?.descriptionText.isNullOrBlank()) {
                        val subColor = safeColor(initFeedback?.subtitle?.color, Color(0xFF777777))
                        Text(
                            text      = details!!.descriptionText!!,
                            color     = subColor,
                            // SDK: subtitleTextStyle?.fontSize ?: size ?: (styling.fontSize ?: 16).
                            // This was hardcoded 13.sp while the campaign sends 16.
                            fontSize  = (subTs?.fontSize ?: subTs?.size
                                ?: (details?.styling?.fontSize ?: 16)).sp,
                            // The SUBTITLE has no bold default — unlike the title.
                            fontWeight = sdkFontWeight(subTs?.fontDecoration),
                            textAlign = sdkTextAlign(subTs?.textAlign ?: subTs?.alignment),
                            modifier  = Modifier.fillMaxWidth().padding(
                                start  = (initFeedback?.subtitle?.margin?.left  ?: 16).dp,
                                end    = (initFeedback?.subtitle?.margin?.right ?: 16).dp,
                                bottom = 16.dp
                            )
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
                    // Shared with PAGE 2 — the device keeps the rating widget on
                    // screen when the feedback options appear, so both snapshots
                    // must draw the identical widget (differing only in
                    // selectedRating). See CsatRatingRow below.
                    CsatRatingRow(
                        rating = rating,
                        ratingType = ratingType,
                        arrangement = ratingArrangement,
                        selectedRating = 0
                    )
                }
            }
        }

        // ── PAGE 2: Feedback options ──────────────────────────────────────────
        paparazzi.snapshot(name = "02_csat_feedback") {
            CsatBackground(bgBitmap) {
                CsatSheet(sheetBg, shape, csatCross, appearance?.margin?.right) {

                    // THE FEEDBACK STATE IS NOT A SEPARATE PAGE.
                    // MainContent is one Column: title -> subtitle -> rating ->
                    // AnimatedVisibility(showFeedback) { FeedbackContent }. So the
                    // title, subtitle AND rating widget all stay on screen when the
                    // options appear. Verified on device after tapping Star 2: the
                    // dump still lists the title (y1039), subtitle (y1106) and
                    // Star 1..5 (y1185), with the options below at y1388+.
                    //
                    // This snapshot used to render NONE of those three — it drew an
                    // invented heading from rating.highRatingTitle / lowRatingTitle
                    // with a "How can we improve?" fallback. Those four fields are
                    // used ONLY in ThankYouContent (CsatDialog.kt:823-864); the
                    // feedback state never shows them.
                    val titleColor2 = safeColor(initFeedback?.title?.color, Color(0xFF000000))
                    val titleSize2  = (initFeedback?.title?.textStyle?.fontSize
                        ?: initFeedback?.title?.textStyle?.size ?: 18).sp
                    val titleTs2 = initFeedback?.title?.textStyle
                    val subTs2   = initFeedback?.subtitle?.textStyle
                    if (!details?.title.isNullOrBlank()) {
                        Text(
                            text       = details!!.title!!,
                            color      = titleColor2,
                            fontSize   = titleSize2,
                            fontWeight = sdkFontWeight(
                                titleTs2?.fontDecoration?.takeIf { it.isNotEmpty() }
                                    ?: listOf("bold")
                            ),
                            textAlign  = sdkTextAlign(titleTs2?.textAlign ?: titleTs2?.alignment),
                            modifier   = Modifier.fillMaxWidth().padding(
                                start  = (initFeedback?.title?.margin?.left  ?: 16).dp,
                                end    = (initFeedback?.title?.margin?.right ?: 16).dp,
                                bottom = 4.dp
                            )
                        )
                    }
                    if (!details?.descriptionText.isNullOrBlank()) {
                        Text(
                            text      = details!!.descriptionText!!,
                            color     = safeColor(initFeedback?.subtitle?.color, Color(0xFF777777)),
                            fontSize  = (subTs2?.fontSize ?: subTs2?.size
                                ?: (details?.styling?.fontSize ?: 16)).sp,
                            fontWeight = sdkFontWeight(subTs2?.fontDecoration),
                            textAlign = sdkTextAlign(subTs2?.textAlign ?: subTs2?.alignment),
                            modifier  = Modifier.fillMaxWidth().padding(
                                start  = (initFeedback?.subtitle?.margin?.left  ?: 16).dp,
                                end    = (initFeedback?.subtitle?.margin?.right ?: 16).dp,
                                bottom = 12.dp
                            )
                        )
                    }

                    // The rating stays visible, showing its SELECTED state. 2 of 5
                    // is the low-rating branch — the only one that opens the
                    // feedback options at all (>= 4 skips straight to thank-you).
                    CsatRatingRow(
                        rating = rating,
                        ratingType = ratingType,
                        arrangement = ratingArrangement,
                        selectedRating = 2
                    )

                    // FeedbackContent sits in a Column with 16dp top padding.
                    Spacer(Modifier.height(16.dp))

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
                    // SUBMIT BUTTON WIDTH — this was hardcoded fillMaxWidth().
                    // The SDK does:  if (submitButtonFullWidth) fillMaxWidth() else Modifier
                    // with submitButtonFullWidth = fullWidth ?: cta.container.ctaFullWidth ?: true.
                    // This campaign sends ctaFullWidth=false, so the button is
                    // INTRINSIC width — measured ~97dp on device, wrapping "Submit".
                    //
                    // NOTE (SDK BUG, unfixed): the else-branch applies NO width at
                    // all — there is no `submitButtonWidth` anywhere in CsatDialog,
                    // so cta.container.ctaWidth (192 here) is SILENTLY IGNORED.
                    // The done button on the thank-you page DOES honour its width
                    // (line 888: `else Modifier.width(doneButtonWidth?.dp ?: 120.dp)`).
                    // Mirroring the buggy behaviour deliberately: the golden must
                    // match what ships, not what the dashboard intended.
                    val submitFullWidth = submitBtn?.fullWidth
                        ?: submitBtn?.cta?.container?.ctaFullWidth ?: true
                    val submitHeight = submitBtn?.containerStyle?.height
                        ?: submitBtn?.cta?.container?.height
                    Box(
                        modifier = Modifier
                            .then(if (submitFullWidth) Modifier.fillMaxWidth() else Modifier)
                            .then(
                                if (submitHeight != null) Modifier.height(submitHeight.dp)
                                else Modifier
                            )
                            .background(btnBg, RoundedCornerShape(btnRadius))
                            .padding(horizontal = 24.dp)
                            .then(if (submitHeight == null) Modifier.padding(vertical = 14.dp) else Modifier),
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
                CsatSheet(sheetBg, shape, csatCross, appearance?.margin?.right) {

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

                    // DONE BUTTON WIDTH — was hardcoded fillMaxWidth(). Unlike the
                    // submit button, ThankYouContent DOES honour the configured
                    // width (CsatDialog.kt:888):
                    //   if (doneButtonFullWidth) fillMaxWidth()
                    //   else Modifier.width(doneButtonWidth?.dp ?: 120.dp)
                    // with doneButtonFullWidth defaulting to TRUE and
                    // doneButtonWidth = containerStyle.width ?: cta.container.ctaWidth.
                    // This campaign sends ctaFullWidth=false + ctaWidth=176.
                    val doneFullWidth = doneBtn?.fullWidth
                        ?: doneBtn?.cta?.container?.ctaFullWidth ?: true
                    val doneWidth = doneBtn?.containerStyle?.width
                        ?: doneBtn?.cta?.container?.ctaWidth
                    val doneHeight = doneBtn?.containerStyle?.height
                        ?: doneBtn?.cta?.container?.height
                    if (doneBtn?.enabled != false) {
                        Box(
                            modifier = Modifier
                                .then(
                                    if (doneFullWidth) Modifier.fillMaxWidth()
                                    else Modifier.width((doneWidth ?: 120).dp)
                                )
                                .then(
                                    if (doneHeight != null) Modifier.height(doneHeight.dp)
                                    else Modifier
                                )
                                .background(doneBg, RoundedCornerShape(doneRadius))
                                .then(if (doneHeight == null) Modifier.padding(vertical = 14.dp) else Modifier),
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
        cross: com.appversal.appstorys.api.BannerStyleConfig? = null,
        containerMarginRight: Int? = null,
        content: @androidx.compose.runtime.Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
            Card(
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = bg),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                // NO drag handle. CsatDialog has no dragHandle/handle anywhere —
                // grepped the whole file, zero hits — and a device screenshot of
                // this campaign shows the title directly under the sheet's top
                // edge. The test used to draw a 36x4 grey pill that has never
                // existed on a real screen.
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = content
                )
            }

            // CLOSE BUTTON — the test drew none at all, on any of the three
            // pages, while the device shows one on every page (dump lists
            // contentDescription "Close" at x1029..1065, y1875).
            // CsatDialog.kt:274-295 renders the shared CrossButton at TopEnd,
            // enabled by default (`crossButton?.enabled ?: true`), with
            //   marginTop = margin.top ?: 12
            //   marginEnd = (margin.right ?: 12) + (appearance.margin.right ?: 16)
            //   size      = crossButton.size ?: 16
            // and CrossButton itself is a circle: fill, a stroke border of
            // size*0.05 when strokeColor isn't transparent, and the cross glyph
            // inset by size*0.11. Paparazzi can't load the SDK's drawable
            // resource, so the glyph is drawn with two lines at the same inset.
            val crossEnabled = cross?.enabled ?: true
            if (crossEnabled) {
                val cSize = (cross?.size ?: 16).dp
                val cFill = safeColor(cross?.color?.fill, Color.Transparent)
                val cStroke = safeColor(cross?.color?.stroke, Color.Transparent)
                val cGlyph = safeColor(cross?.color?.cross, Color.White)
                val cTop = (cross?.margin?.top ?: 12).dp
                val cEnd = ((cross?.margin?.right ?: 12) + (containerMarginRight ?: 16)).dp
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = cTop, end = cEnd)
                        .size(cSize)
                        .clip(CircleShape)
                        .background(cFill)
                        .then(
                            if (cStroke != Color.Transparent)
                                Modifier.border(cSize * 0.05f, cStroke, CircleShape)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier.size(cSize).padding(cSize * 0.11f)
                    ) {
                        val sw = size.minDimension * 0.12f
                        drawLine(
                            color = cGlyph,
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                            strokeWidth = sw,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                        drawLine(
                            color = cGlyph,
                            start = androidx.compose.ui.geometry.Offset(size.width, 0f),
                            end = androidx.compose.ui.geometry.Offset(0f, size.height),
                            strokeWidth = sw,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
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

    /**
     * The rating widget, shared by PAGE 1 and PAGE 2.
     *
     * The device does NOT navigate to a separate feedback page: MainContent is
     * one Column — title, subtitle, RatingComponent, then
     * `AnimatedVisibility(showFeedback) { FeedbackContent }`. So when the options
     * appear, the title, subtitle AND the rating widget all stay on screen, with
     * the rating showing its SELECTED state. Verified on device: after tapping
     * Star 2 the dump still lists the title at y1039, subtitle y1106 and
     * Star 1..5 at y1185, with the options below at y1388+.
     *
     * `selectedRating` = 0 on page 1, and the chosen value on page 2.
     * Selection semantics differ per type, copied from CsatDialog:
     *   star / number -> isSelected = index < selectedRating   (a fill-up bar)
     *   emoji         -> isSelected = index == selectedRating - 1 (exactly one)
     * and star/number additionally branch on isHighRatingMode = rating >= 4.
     */
    @androidx.compose.runtime.Composable
    private fun CsatRatingRow(
        rating: com.appversal.appstorys.api.CsatRating?,
        ratingType: String,
        arrangement: Arrangement.Horizontal,
        selectedRating: Int
    ) {
        // Unknown types render STARS on the device (`else -> StarRating`).
        val effective = when (ratingType.lowercase()) {
            "star", "emoji", "number" -> ratingType.lowercase()
            else -> "star"
        }
        val isHigh = selectedRating >= 4

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = arrangement
        ) {
            when (effective) {
                "emoji" -> {
                    val emojis = rating?.emoji?.values
                        ?: listOf("😢", "😕", "😐", "🙂", "😄")
                    emojis.forEachIndexed { i, emoji ->
                        val sel = i == selectedRating - 1
                        val fill = safeColor(
                            if (sel) rating?.emoji?.selected?.stylingContainer?.fill
                            else rating?.emoji?.unselected?.stylingContainer?.fill,
                            if (sel) Color(0xFFFFF3ED) else Color(0xFFF0F0F0)
                        )
                        val bdr = safeColor(
                            if (sel) rating?.emoji?.selected?.stylingContainer?.border
                            else rating?.emoji?.unselected?.stylingContainer?.border,
                            if (sel) Color(0xFFFE6B35) else Color(0xFFCCCCCC)
                        )
                        val bw = (if (sel) rating?.emoji?.selected?.stylingContainer?.borderWidth ?: 2
                        else rating?.emoji?.unselected?.stylingContainer?.borderWidth ?: 1).dp
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(fill)
                                .border(bw, bdr, CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Text(emoji, fontSize = 24.sp) }
                        if (i < emojis.lastIndex) Spacer(Modifier.width(8.dp))
                    }
                }

                "number" -> {
                    val num = rating?.number
                    val textSize = (num?.stylingNumber?.textSize ?: 16).sp
                    repeat(5) { i ->
                        val sel = i < selectedRating
                        val fill = safeColor(
                            when {
                                !sel   -> num?.unselected?.stylingContainer?.fill
                                isHigh -> num?.high?.stylingContainer?.fill
                                else   -> num?.low?.stylingContainer?.fill
                            },
                            when {
                                !sel   -> Color(0xFFEDEDED)
                                isHigh -> Color(0xFF42E6F5)
                                else   -> Color(0xFF87FF66)
                            }
                        )
                        val bdr = safeColor(
                            when {
                                !sel   -> num?.unselected?.stylingContainer?.border
                                isHigh -> num?.high?.stylingContainer?.border
                                else   -> num?.low?.stylingContainer?.border
                            },
                            when {
                                !sel   -> Color(0xFFFE6B35)
                                isHigh -> Color(0xFFF75555)
                                else   -> Color(0xFFFF4242)
                            }
                        )
                        val bw = when {
                            !sel   -> num?.unselected?.stylingContainer?.borderWidth ?: 0
                            isHigh -> num?.high?.stylingContainer?.borderWidth ?: 0
                            else   -> num?.low?.stylingContainer?.borderWidth ?: 1
                        }
                        val txt = safeColor(
                            if (!sel) num?.unselected?.stylingNumber?.text ?: num?.stylingNumber?.text
                            else num?.stylingNumber?.text,
                            if (!sel) Color(0xFFFE6B35) else Color.Black
                        )
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(fill)
                                .then(
                                    if (bw > 0) Modifier.border(bw.dp, bdr, CircleShape)
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            // NumberRating hardcodes Bold on the digit.
                            Text(
                                "${i + 1}",
                                color = txt,
                                fontSize = textSize,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (i < 4) Spacer(Modifier.width(8.dp))
                    }
                }

                else -> {   // "star"
                    repeat(5) { i ->
                        val sel = i < selectedRating
                        val starColor = safeColor(
                            when {
                                !sel   -> rating?.unselected?.background
                                    ?: rating?.star?.unselected?.stylingStar?.background
                                isHigh -> rating?.high?.background
                                    ?: rating?.star?.high?.stylingStar?.background
                                else   -> rating?.low?.background
                                    ?: rating?.star?.low?.stylingStar?.background
                            },
                            when {
                                !sel   -> Color(0xFFCCCCCC)
                                isHigh -> Color(0xFFFFD700)
                                else   -> Color(0xFFFF6B6B)
                            }
                        )
                        val bdrColor = safeColor(
                            when {
                                !sel   -> rating?.unselected?.border
                                    ?: rating?.star?.unselected?.stylingStar?.border
                                isHigh -> rating?.high?.border
                                    ?: rating?.star?.high?.stylingStar?.border
                                else   -> rating?.low?.border
                                    ?: rating?.star?.low?.stylingStar?.border
                            },
                            Color.Transparent
                        )
                        val bw = when {
                            !sel   -> rating?.unselected?.borderWidth
                                ?: rating?.star?.unselected?.stylingStar?.borderWidth ?: 0
                            isHigh -> rating?.high?.borderWidth
                                ?: rating?.star?.high?.stylingStar?.borderWidth ?: 0
                            else   -> rating?.low?.borderWidth
                                ?: rating?.star?.low?.stylingStar?.borderWidth ?: 0
                        }
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Outline pass: a scaled-up star behind the fill.
                            if (bw > 0) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = bdrColor,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .graphicsLayer {
                                            val s = 1f + (bw * 0.08f)
                                            scaleX = s
                                            scaleY = s
                                        }
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Star ${i + 1}",
                                tint = starColor,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        // SDK emits an 8dp Spacer after EVERY star.
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
        }
    }

    /**
     * Mirrors CommonText.parseTextAlign in the SDK. Default is CENTER, but
     * "left"/"start" map to Start — this campaign sends "left" for the rating
     * page's title and subtitle.
     */
    private fun sdkTextAlign(alignment: String?): TextAlign = when (alignment?.lowercase()) {
        "left", "start" -> TextAlign.Start
        "right", "end"  -> TextAlign.End
        "justify"       -> TextAlign.Justify
        else            -> TextAlign.Center
    }

    /** Mirrors CommonText's fontDecoration -> FontWeight mapping. */
    private fun sdkFontWeight(decoration: List<String>?): FontWeight {
        val d = decoration.orEmpty()
        return when {
            d.contains("bold")     -> FontWeight.Bold
            d.contains("semibold") -> FontWeight.SemiBold
            d.contains("medium")   -> FontWeight.Medium
            else                   -> FontWeight.Normal
        }
    }

    private fun safeColor(hex: String?, fallback: Color): Color {
        if (hex.isNullOrBlank()) return fallback
        return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)
    }
}
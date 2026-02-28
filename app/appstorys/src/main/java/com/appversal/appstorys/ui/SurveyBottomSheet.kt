package com.appversal.appstorys.ui

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.toColorInt
import coil.compose.AsyncImage
import com.appversal.appstorys.api.SlideResponse
import com.appversal.appstorys.api.SurveyDetails
import com.appversal.appstorys.api.SurveySlide
import com.appversal.appstorys.api.SurveyStyling
import com.appversal.appstorys.ui.common_components.CTAButton
import com.appversal.appstorys.ui.common_components.CrossButton
import com.appversal.appstorys.ui.common_components.createCTAButtonConfig
import com.appversal.appstorys.ui.common_components.createCrossButtonConfig
import com.appversal.appstorys.utils.toColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class SurveyFeedback(
    // single-question backward compat
    val responseOptions: List<String>? = null,
    val comment: String = "",
    // multi-slide
    val slideResponses: List<SlideResponse>? = null
)

private fun String?.toColorOr(default: Color): Color {
    return try {
        if (this != null) Color(this.toColorInt()) else default
    } catch (_: Exception) {
        default
    }
}

@Composable
fun SurveyBottomSheet(
    onDismissRequest: () -> Unit,
    surveyDetails: SurveyDetails,
    campaignId: String?,
    onTrackEvent: (campaignId: String, event: String, metadata: Map<String, Any>?) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    val styling = surveyDetails.styling
    val appearance = styling?.appearance

    // ── Survey container background — NO opacity applied ─────────────────
    val backgroundColor = (appearance?.backgroundColor ?: styling?.backgroundColor)
        .toColorOr(Color.White)

    // ── appearance.cornerRadius (topLeft / topRight only for bottom sheet) ─
    val cornerRadiusTopStart = (appearance?.cornerRadius?.topLeft ?: 24).dp
    val cornerRadiusTopEnd = (appearance?.cornerRadius?.topRight ?: 24).dp


    // ── appearance.displayDelay (seconds) ─────────────────────────────────
    val displayDelaySec = appearance?.displayDelay ?: 0
    var isVisible by remember { mutableStateOf(displayDelaySec <= 0) }
    LaunchedEffect(Unit) {
        if (displayDelaySec > 0) {
            delay(displayDelaySec * 1000L)
            isVisible = true
        }
    }

    val hasThankYouPage = surveyDetails.thankYouButtonConfig?.enabled == true
    var showThankYou by remember { mutableStateOf(false) }

    val slides = surveyDetails.slides
        ?.sortedBy { it.order ?: 0 }
        ?: listOf(
            SurveySlide(
                id = surveyDetails.id,
                order = 0,
                parent = null,
                title = null,
                subtitle = null,
                question = surveyDetails.surveyQuestion,
                options = surveyDetails.surveyOptions,
                image = null,
                submitButtonText = null,
                logic = null,
                additionalComment = null,
                surveyQuestion = surveyDetails.surveyQuestion,
                surveyOptions = surveyDetails.surveyOptions,
                hasOthers = surveyDetails.hasOthers
            )
        )

    val pagerState = rememberPagerState(pageCount = { slides.size })

    // Per-slide state — keyed by slides.size so it rebuilds if slide count changes
    val selectedOptionsPerSlide = remember(slides.size) {
        List(slides.size) { mutableStateOf(setOf<String>()) }
    }
    val othersTextPerSlide = remember(slides.size) {
        MutableList(slides.size) { "" }.toMutableStateList()
    }

    // Track visited slide indices for back navigation (logic can jump slides)
    val slideHistory = remember { mutableStateListOf<Int>(0) }

    // Helper: build a SlideResponse for a single slide index
    fun slideResponseFor(index: Int): SlideResponse {
        val s = slides[index]
        val selected = selectedOptionsPerSlide[index].value
        val hasOthers = selected.contains("Others")
        return SlideResponse(
            slideId = s.id,
            // Keep "Others" in responseOptions so the server knows it was selected;
            // the typed free-text goes separately in comment.
            responseOptions = selected.toList(),
            comment = if (hasOthers) othersTextPerSlide[index] else ""
        )
    }

    // Helper: collect all responses answered so far (up to and including upToIndex)
    fun collectResponses(upToIndex: Int = slides.size - 1): SurveyFeedback {
        val responses = (0..upToIndex).map { slideResponseFor(it) }
        return SurveyFeedback(slideResponses = responses)
    }

    // Helper: resolve the logic redirect for the currently selected option on a slide.
    // logic.selectOption = the option VALUE text (as shown in the dashboard "If" dropdown)
    // logic.redirectTo   = "thank_you" | "thank-you" | slide id (UUID) | slide title | "Slide N" label
    fun resolveLogicRedirect(slideIndex: Int): String? {
        val slide = slides[slideIndex]
        val logic = slide.logic ?: return null
        val selectedValue = selectedOptionsPerSlide[slideIndex].value.firstOrNull() ?: return null

        return logic.firstOrNull { rule ->
            rule.selectOption?.contains(selectedValue) == true
        }?.redirectTo
    }

    // Helper: resolve a redirectTo string to a slide index.
    fun resolveRedirectIndex(redirectTo: String): Int? {
        if (redirectTo == "thank_you" || redirectTo == "thank-you") return null
        // 1. Try matching by slide id (UUID)
        val byId = slides.indexOfFirst { it.id == redirectTo }.takeIf { it != -1 }
        if (byId != null) return byId
        // 2. Try matching by slide title (exact)
        val byTitle = slides.indexOfFirst {
            it.title.equals(redirectTo, ignoreCase = true)
        }.takeIf { it != -1 }
        if (byTitle != null) return byTitle
        // 3. Try matching "Slide N" pattern (e.g. "Slide 1" → order index 0)
        // Slides are already sorted by order, so positional index matches "Slide N"
        val slideLabel = Regex("^[Ss]lide\\s*(\\d+)$").find(redirectTo)
        if (slideLabel != null) {
            val n = slideLabel.groupValues[1].toIntOrNull() ?: return null
            val targetIndex = n - 1  // "Slide 1" = index 0
            return if (targetIndex in slides.indices) targetIndex else null
        }
        return null
    }

    // Configs built once here — used in header
    val crossButton = surveyDetails.styling?.crossButton
    val isCrossEnabled = crossButton?.enabled != false  // default true if null
    val crossColors = crossButton?.color
    val crossMargin = crossButton?.margin

    val crossConfig = createCrossButtonConfig(
        fillColorString = crossColors?.fill ?: surveyDetails.styling?.ctaBackgroundColor,
        crossColorString = crossColors?.cross ?: surveyDetails.styling?.ctaTextIconColor,
        strokeColorString = crossColors?.stroke,
        marginTop = crossMargin?.top,
        marginEnd = crossMargin?.right,
        size = crossButton?.size,
        imageUrl = crossButton?.image
    )

    // Use Dialog instead of ModalBottomSheet for full control
    if (!isVisible) return

    Dialog(
        onDismissRequest = { /* Do nothing - only cross button can dismiss */ },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        val backdropColor = appearance?.backdropColor.toColor(Color.Black)
        // backdropOpacity is the new field; fall back to legacy backgroundOpacity if not present
        val backdropAlpha = remember(appearance?.backdropOpacity, appearance?.backgroundOpacity) {
            val raw = appearance?.backdropOpacity
                ?: appearance?.backgroundOpacity
                ?: 100
            raw.coerceIn(0, 100) / 100f
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backdropColor.copy(alpha = backdropAlpha)),
            contentAlignment = Alignment.BottomCenter
        ) {

            // ✅ SHEET LAYER — container background has NO opacity applied
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .systemBarsPadding()
                    .clip(
                        RoundedCornerShape(
                            topStart = cornerRadiusTopStart,
                            topEnd = cornerRadiusTopEnd
                        )
                    )
                    .background(backgroundColor)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* consume clicks so they don't dismiss via backdrop */ }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(15.dp)
                ) {
                    // ── Header: close button ────────────────────────────
                    if (isCrossEnabled) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            CrossButton(
                                modifier = Modifier.align(Alignment.CenterEnd),
                                config = crossConfig,
                                onClose = {
                                    if (showThankYou) {
                                        onDismissRequest()
                                    } else {
                                        campaignId?.let {
                                            onTrackEvent(
                                                it,
                                                "SurveyDismissed",
                                                mapOf("survey_id" to (surveyDetails.id ?: ""))
                                            )
                                        }
                                        val answeredUpTo = pagerState.currentPage
                                        onDismissRequest()
                                    }
                                }
                            )
                        }
                    }

                    if (showThankYou) {
                        // ── Thank You page — data already submitted, CTA only redirects ──
                        SurveyThankYouContent(
                            surveyDetails = surveyDetails,
                            onDismiss = onDismissRequest,
                            onThankYouCtaClicked = {
                                campaignId?.let {
                                    onTrackEvent(
                                        it,
                                        "ThankYouCTAClicked",
                                        mapOf("survey_id" to (surveyDetails.id ?: ""))
                                    )
                                }
                            }
                        )
                    } else {
                        // ── Survey slides ────────────────────────────────────────────────
                        val currentSelected = selectedOptionsPerSlide[pagerState.currentPage].value
                        val isCurrentSlideValid = currentSelected.isNotEmpty()

                        LaunchedEffect(pagerState.currentPage) {
                            if (!showThankYou) {
                                val slide = slides[pagerState.currentPage]
                                campaignId?.let {
                                    onTrackEvent(
                                        it, "viewed",
                                        mapOf(
                                            "survey_id" to (surveyDetails.id ?: ""),
                                            "slide_id" to (slide.id ?: "")
                                        )
                                    )
                                }
                            }
                        }

                        HorizontalPager(
                            state = pagerState,
                            userScrollEnabled = isCurrentSlideValid,
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                        ) { pageIndex ->
                            val slide = slides[pageIndex]
                            val currentSelectedInPage = selectedOptionsPerSlide[pageIndex].value
                            val showInputBox = currentSelectedInPage.contains("Others")

                            SurveyContent(
                                slide = slide,
                                styling = surveyDetails.styling,
                                selectedOptions = currentSelectedInPage,
                                showInputBox = showInputBox,
                                othersText = othersTextPerSlide[pageIndex],
                                onOptionSelected = { optionName ->
                                    val current = selectedOptionsPerSlide[pageIndex].value
                                    // Toggle: if already selected remove it, else add it
                                    selectedOptionsPerSlide[pageIndex].value =
                                        if (current.contains(optionName)) current - optionName
                                        else current + optionName
                                    othersTextPerSlide[pageIndex] = ""
                                },
                                onOthersTextChanged = { text ->
                                    othersTextPerSlide[pageIndex] = text
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // ── Navigation row: NEXT / SUBMIT ──────────────────────────────
                        val isLastSlide = pagerState.currentPage == slides.size - 1

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val currentPage = pagerState.currentPage
                            val currentSlide = slides[currentPage]
                            val currentSelectedInRow = selectedOptionsPerSlide[currentPage].value
                            val isCurrentSlideValidInRow = currentSelectedInRow.isNotEmpty()

                            val logicRedirect =
                                if (isCurrentSlideValidInRow) resolveLogicRedirect(currentPage) else
                                    null
                            // Normalize: treat both "thank_you" and "thank-you" as a thank-you redirect
                            val isThankYouRedirect =
                                logicRedirect == "thank_you" || logicRedirect == "thank-you"
                            // Resolve redirect target: id / title / "Slide N" → index; null if thank_you or no match
                            val redirectTargetIndex =
                                logicRedirect?.let { resolveRedirectIndex(it) }

                            val buttonText = when {
                                isThankYouRedirect ->
                                    currentSlide.submitButtonText?.takeIf { it.isNotEmpty() }
                                        ?: "SUBMIT"

                                redirectTargetIndex != null ->
                                    currentSlide.submitButtonText?.takeIf { it.isNotEmpty() }
                                        ?: "NEXT"

                                isLastSlide ->
                                    currentSlide.submitButtonText?.takeIf { it.isNotEmpty() }
                                        ?: "SUBMIT"

                                else ->
                                    currentSlide.submitButtonText?.takeIf { it.isNotEmpty() }
                                        ?: "NEXT"
                            }

                            val navButtonConfig = run {
                                val ctaStyling = surveyDetails.styling?.cta
                                val ctaContainer = ctaStyling?.container
                                val ctaCornerRadius = ctaStyling?.cornerRadius
                                val ctaMargin = ctaStyling?.margin
                                createCTAButtonConfig(
                                    // text
                                    textColor = if (isCurrentSlideValidInRow)
                                        (ctaStyling?.text?.color
                                            ?: surveyDetails.styling?.ctaTextIconColor ?: "#FFFFFF")
                                    else "#666666",
                                    textSize = ctaStyling?.text?.fontSize ?: 16,
                                    fontFamily = ctaStyling?.text?.fontFamily,
                                    fontDecoration = ctaStyling?.text?.fontDecoration,
                                    // margins
                                    marginTop = ctaMargin?.top,
                                    marginBottom = ctaMargin?.bottom,
                                    marginStart = ctaMargin?.left,
                                    marginEnd = ctaMargin?.right,
                                    // container
                                    height = ctaContainer?.height ?: 56,
                                    width = ctaContainer?.ctaWidth,
                                    alignment = ctaContainer?.alignment ?: "center",
                                    backgroundColorString = if (isCurrentSlideValidInRow)
                                        (ctaContainer?.backgroundColor
                                            ?: surveyDetails.styling?.ctaBackgroundColor
                                            ?: "#000000")
                                    else "#CCCCCC",
                                    borderColorString = ctaContainer?.borderColor,
                                    borderWidth = ctaContainer?.borderWidth ?: 0,
                                    fullWidth = ctaContainer?.ctaFullWidth ?: true,
                                    // corner radius
                                    borderRadiusTopLeft = ctaCornerRadius?.topLeft ?: 12,
                                    borderRadiusTopRight = ctaCornerRadius?.topRight ?: 12,
                                    borderRadiusBottomLeft = ctaCornerRadius?.bottomLeft ?: 12,
                                    borderRadiusBottomRight = ctaCornerRadius?.bottomRight ?: 12,
                                )
                            }

                            CTAButton(
                                text = buttonText,
                                config = navButtonConfig,
                                onClick = {
                                    if (!isCurrentSlideValidInRow) return@CTAButton

                                    // ── Track "clicked" for selected options ─────────────────────────
                                    val selectedOptionKeys = currentSelectedInRow
                                        .mapNotNull { selectedName ->
                                            currentSlide.options?.entries
                                                ?.firstOrNull { it.value == selectedName }?.key
                                        }
                                    campaignId?.let {
                                        onTrackEvent(
                                            it, "clicked",
                                            mapOf(
                                                "survey_id" to (surveyDetails.id ?: ""),
                                                "slide_id" to (currentSlide.id ?: ""),
                                                "option" to selectedOptionKeys
                                            )
                                        )
                                    }

                                    // ── Track "clicked" for additional comment if non-empty ──────────
                                    val comment = othersTextPerSlide[currentPage]
                                    if (comment.isNotEmpty()) {
                                        campaignId?.let {
                                            onTrackEvent(
                                                it, "clicked",
                                                mapOf(
                                                    "survey_id" to (surveyDetails.id ?: ""),
                                                    "slide_id" to (currentSlide.id ?: ""),
                                                    "additional_comment" to comment
                                                )
                                            )
                                        }
                                    }

                                    coroutineScope.launch {
                                        when {
                                            isThankYouRedirect -> {
                                                campaignId?.let {
                                                    onTrackEvent(
                                                        it,
                                                        "SurveySubmitted",
                                                        mapOf(
                                                            "survey_id" to (surveyDetails.id ?: "")
                                                        )
                                                    )
                                                }
                                                if (hasThankYouPage) showThankYou = true
                                                else onDismissRequest()
                                            }

                                            redirectTargetIndex != null -> {
                                                slideHistory.add(redirectTargetIndex)
                                                pagerState.animateScrollToPage(redirectTargetIndex)
                                            }

                                            isLastSlide -> {
                                                campaignId?.let {
                                                    onTrackEvent(
                                                        it,
                                                        "SurveySubmitted",
                                                        mapOf(
                                                            "survey_id" to (surveyDetails.id ?: "")
                                                        )
                                                    )
                                                }
                                                if (hasThankYouPage) showThankYou = true
                                                else onDismissRequest()
                                            }

                                            else -> {
                                                val nextPage = currentPage + 1
                                                slideHistory.add(nextPage)
                                                pagerState.animateScrollToPage(nextPage)
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        // ── Dot indicators ─────────────────────────────────────────────
                        if (slides.size > 1) {
                            Spacer(modifier = Modifier.height(12.dp))
                            DotsIndicator(
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                totalDots = slides.size,
                                selectedIndex = pagerState.currentPage,
                                selectedColor = surveyDetails.styling?.ctaBackgroundColor.toColorOr(
                                    Color.Blue
                                ),
                                unSelectedColor = surveyDetails.styling?.optionColor.toColorOr(Color.LightGray),
                                dotSize = 8.dp,
                                selectedLength = 20.dp
                            )
                        }

                    } // end if/else showThankYou
                }
            } // end sheet Box
        } // end outer Box
    } // end Dialog
} // end SurveyBottomSheet

// ── Thank You page ────────────────────────────────────────────────────────────

@Composable
private fun SurveyThankYouContent(
    surveyDetails: SurveyDetails,
    onDismiss: () -> Unit,
    onThankYouCtaClicked: () -> Unit,
) {
    val context = LocalContext.current
    val thankyouPage = surveyDetails.styling?.thankyouPage

    // ── Title styling (styling.thankyouPage.title.textStyle) ──────────────
    val titleTextStyle = thankyouPage?.title?.textStyle
    val titleColor = titleTextStyle?.color.toColorOr(Color.Black)
    val titleFontSize = (titleTextStyle?.fontSize ?: 20).sp
    val titleFontWeight =
        if (titleTextStyle?.fontDecoration?.contains("bold") == true) FontWeight.Bold else FontWeight.Normal
    val titleFontStyle =
        if (titleTextStyle?.fontDecoration?.contains("italic") == true) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
    val titleTextDecoration =
        if (titleTextStyle?.fontDecoration?.contains("underline") == true) androidx.compose.ui.text.style.TextDecoration.Underline else androidx.compose.ui.text.style.TextDecoration.None
    val titleTextAlign = when (titleTextStyle?.textAlign?.lowercase()) {
        "left" -> TextAlign.Start
        "right" -> TextAlign.End
        else -> TextAlign.Center
    }
    val titleFontFamily = when (titleTextStyle?.fontFamily?.lowercase()) {
        "serif" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        else -> FontFamily.SansSerif
    }
    val titleMargin = titleTextStyle?.margin

    // ── Subtitle styling (styling.thankyouPage.subtitle.textStyle) ────────
    val subtitleTextStyle = thankyouPage?.subtitle?.textStyle
    val subtitleColor = subtitleTextStyle?.color.toColorOr(Color(0xFF6B7280))
    val subtitleFontSize = (subtitleTextStyle?.fontSize ?: 14).sp
    val subtitleFontWeight =
        if (subtitleTextStyle?.fontDecoration?.contains("bold") == true) FontWeight.Bold else FontWeight.Normal
    val subtitleFontStyle =
        if (subtitleTextStyle?.fontDecoration?.contains("italic") == true) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
    val subtitleTextDecoration =
        if (subtitleTextStyle?.fontDecoration?.contains("underline") == true) androidx.compose.ui.text.style.TextDecoration.Underline else androidx.compose.ui.text.style.TextDecoration.None
    val subtitleTextAlign = when (subtitleTextStyle?.textAlign?.lowercase()) {
        "left" -> TextAlign.Start
        "right" -> TextAlign.End
        else -> TextAlign.Center
    }
    val subtitleFontFamily = when (subtitleTextStyle?.fontFamily?.lowercase()) {
        "serif" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        else -> FontFamily.SansSerif
    }
    val subtitleMargin = subtitleTextStyle?.margin

    // ── CTA config (styling.thankyouPage.cta) ─────────────────────────────
    val ctaStyling = thankyouPage?.cta
    val ctaContainer = ctaStyling?.container
    val ctaMargin = ctaStyling?.margin
    val ctaCornerRadius = ctaStyling?.cornerRadius

    // ── thankYouButtonConfig (top-level: action / enabled / redirectUrl) ──
    val buttonConfig = surveyDetails.thankYouButtonConfig
    // "CTA Text" field → thankYouButtonText
    val buttonText = surveyDetails.thankYouButtonText?.takeIf { it.isNotBlank() } ?: "Okay"
    // "Redirect to" field → thankYouButtonConfig.redirectUrl
    val redirectUrl = buttonConfig?.redirectUrl

    // Build CTAButtonConfig using the common factory
    val ctaButtonConfig = createCTAButtonConfig(
        // text styling
        textColor = ctaStyling?.text?.color ?: "#FFFFFF",
        textSize = ctaStyling?.text?.fontSize ?: 14,
        fontFamily = ctaStyling?.text?.fontFamily,
        fontDecoration = ctaStyling?.text?.fontDecoration,
        // margins
        marginTop = ctaMargin?.top,
        marginBottom = ctaMargin?.bottom,
        marginStart = ctaMargin?.left,
        marginEnd = ctaMargin?.right,
        // container
        height = ctaContainer?.height ?: 50,
        width = ctaContainer?.ctaWidth,
        alignment = ctaContainer?.alignment ?: "center",
        backgroundColorString = ctaContainer?.backgroundColor ?: "#1F35DB",
        borderColorString = ctaContainer?.borderColor,
        borderWidth = ctaContainer?.borderWidth ?: 0,
        fullWidth = ctaContainer?.ctaFullWidth ?: false,
        // corner radius
        borderRadiusTopLeft = ctaCornerRadius?.topLeft ?: 12,
        borderRadiusTopRight = ctaCornerRadius?.topRight ?: 12,
        borderRadiusBottomLeft = ctaCornerRadius?.bottomLeft ?: 12,
        borderRadiusBottomRight = ctaCornerRadius?.bottomRight ?: 12,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // ── "Upload Image" → thankYouImage ──────────────────
        val imageUrl = surveyDetails.thankYouImage
        if (!imageUrl.isNullOrEmpty()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Thank you image",
                modifier = Modifier
                    .size(80.dp)
                    .padding(bottom = 12.dp)
            )
        }

        // ── "Title Text" → thankYouTitle ────────────────────
        val title = surveyDetails.thankYouTitle
        if (!title.isNullOrEmpty()) {
            Text(
                text = title,
                fontSize = titleFontSize,
                fontWeight = titleFontWeight,
                fontStyle = titleFontStyle,
                textDecoration = titleTextDecoration,
                fontFamily = titleFontFamily,
                color = titleColor,
                textAlign = titleTextAlign,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = (titleMargin?.left ?: 4).dp,
                        end = (titleMargin?.right ?: 4).dp,
                        top = (titleMargin?.top ?: 4).dp,
                        bottom = (titleMargin?.bottom ?: 4).dp
                    )
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ── "Subtitle Text" → thankYouText ──────────────────
        val bodyText = surveyDetails.thankYouText
        if (!bodyText.isNullOrEmpty()) {
            Text(
                text = bodyText,
                fontSize = subtitleFontSize,
                fontWeight = subtitleFontWeight,
                fontStyle = subtitleFontStyle,
                textDecoration = subtitleTextDecoration,
                fontFamily = subtitleFontFamily,
                color = subtitleColor,
                textAlign = subtitleTextAlign,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = (subtitleMargin?.left ?: 4).dp,
                        end = (subtitleMargin?.right ?: 4).dp,
                        top = (subtitleMargin?.top ?: 4).dp,
                        bottom = (subtitleMargin?.bottom ?: 4).dp
                    )
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── "CTA Text" + "Redirect to" → thankYouButtonText / thankYouButtonConfig
        // CTA button shown only when the thank-you page toggle is enabled
        // (thankYouButtonConfig.enabled == true, controlled by the toggle in the screenshot)
        if (buttonConfig?.enabled == true) {
            // "CTA Text" → thankYouButtonText | "Redirect to" → thankYouButtonConfig.redirectUrl
            CTAButton(
                text = buttonText,
                config = ctaButtonConfig,
                onClick = {
                    onThankYouCtaClicked()
                    // Data was already submitted when the user tapped Next/Submit on each slide.
                    // CTA here only handles optional redirect + dismissal.
                    if (!redirectUrl.isNullOrEmpty() && buttonConfig.action == "redirect") {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, redirectUrl.toUri()))
                        } catch (_: Exception) {
                        }
                    }
                    onDismiss()
                }
            )
        }
    }
}

// ── Per-slide question + options ─────────────────────────────────────────────

@Composable
private fun SurveyContent(
    slide: SurveySlide,
    styling: SurveyStyling?,
    selectedOptions: Set<String>,
    showInputBox: Boolean,
    othersText: String,
    onOptionSelected: (String) -> Unit,
    onOthersTextChanged: (String) -> Unit,
) {
    val surveyOptions = remember(slide) {
        val optionsMap = slide.options ?: slide.surveyOptions

        val base = optionsMap?.entries
            ?.sortedBy { it.key }  // Sort option1, option2, option3...
            ?.mapIndexed { index, entry ->
                // Extract number from "option1", "option2" or use the key directly
                val displayId = entry.key.removePrefix("option").ifEmpty {
                    ('A' + index).toString()
                }
                SurveyOption(displayId, entry.value)
            }?.toMutableList() ?: mutableListOf()

        // Check additionalComment.enabled for new format, hasOthers for old
        val shouldAddOthers = slide.additionalComment?.enabled == true || slide.hasOthers == true

        if (shouldAddOthers) {
            val nextId = ('A' + base.size).toString()
            base.add(SurveyOption(nextId, "Others"))
        }
        base
    }


    Column(modifier = Modifier.fillMaxWidth()) {

        // Question text
        Column {
            // Display title if exists
            slide.title?.let { title ->
                val titleStyle = styling?.title?.textStyle
                val titleColor = titleStyle?.color.toColorOr(
                    styling?.surveyQuestionColor.toColorOr(Color.Black)
                )
                val titleFontSize = (titleStyle?.fontSize ?: 18).sp
                val titleFontWeight = when {
                    titleStyle?.fontDecoration?.contains("bold") == true -> FontWeight.Bold
                    else -> FontWeight.Normal
                }
                val titleFontStyle = when {
                    titleStyle?.fontDecoration?.contains("italic") == true -> androidx.compose.ui.text.font.FontStyle.Italic
                    else -> androidx.compose.ui.text.font.FontStyle.Normal
                }
                val titleTextDecoration = when {
                    titleStyle?.fontDecoration?.contains("underline") == true ->
                        androidx.compose.ui.text.style.TextDecoration.Underline

                    else -> androidx.compose.ui.text.style.TextDecoration.None
                }
                val titleTextAlign = when (titleStyle?.textAlign?.lowercase()) {
                    "left" -> TextAlign.Start
                    "right" -> TextAlign.End
                    else -> TextAlign.Center
                }
                val titleFontFamily = when (titleStyle?.fontFamily?.lowercase()) {
                    "serif" -> FontFamily.Serif
                    "monospace" -> FontFamily.Monospace
                    "cursive" -> FontFamily.Cursive
                    else -> FontFamily.SansSerif
                }
                val titleMargin = titleStyle?.margin
                Text(
                    text = title,
                    fontSize = titleFontSize,
                    fontWeight = titleFontWeight,
                    fontStyle = titleFontStyle,
                    textDecoration = titleTextDecoration,
                    fontFamily = titleFontFamily,
                    color = titleColor,
                    textAlign = titleTextAlign,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = (titleMargin?.left ?: 0).dp,
                            end = (titleMargin?.right ?: 0).dp,
                            top = (titleMargin?.top ?: 0).dp,
                            bottom = (titleMargin?.bottom ?: 0).dp
                        )
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Display subtitle if exists
            slide.subtitle?.let { subtitle ->
                val subtitleStyle = styling?.subtitle?.textStyle
                val subtitleColor = subtitleStyle?.color.toColorOr(
                    styling?.surveyQuestionColor.toColorOr(Color.Gray)
                )
                val subtitleFontSize = (subtitleStyle?.fontSize ?: 14).sp
                val subtitleFontWeight = when {
                    subtitleStyle?.fontDecoration?.contains("bold") == true -> FontWeight.Bold
                    else -> FontWeight.Normal
                }
                val subtitleFontStyle = when {
                    subtitleStyle?.fontDecoration?.contains("italic") == true -> androidx.compose.ui.text.font.FontStyle.Italic
                    else -> androidx.compose.ui.text.font.FontStyle.Normal
                }
                val subtitleTextDecoration = when {
                    subtitleStyle?.fontDecoration?.contains("underline") == true ->
                        androidx.compose.ui.text.style.TextDecoration.Underline

                    else -> androidx.compose.ui.text.style.TextDecoration.None
                }
                val subtitleTextAlign = when (subtitleStyle?.textAlign?.lowercase()) {
                    "left" -> TextAlign.Start
                    "right" -> TextAlign.End
                    else -> TextAlign.Center
                }
                val subtitleFontFamily = when (subtitleStyle?.fontFamily?.lowercase()) {
                    "serif" -> FontFamily.Serif
                    "monospace" -> FontFamily.Monospace
                    "cursive" -> FontFamily.Cursive
                    else -> FontFamily.SansSerif
                }
                val subtitleMargin = subtitleStyle?.margin
                Text(
                    text = subtitle,
                    fontSize = subtitleFontSize,
                    fontWeight = subtitleFontWeight,
                    fontStyle = subtitleFontStyle,
                    textDecoration = subtitleTextDecoration,
                    fontFamily = subtitleFontFamily,
                    color = subtitleColor,
                    textAlign = subtitleTextAlign,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = (subtitleMargin?.left ?: 0).dp,
                            end = (subtitleMargin?.right ?: 0).dp,
                            top = (subtitleMargin?.top ?: 0).dp,
                            bottom = (subtitleMargin?.bottom ?: 0).dp
                        )
                )
            }
        }

        // Options list
        val optionsConfig = styling?.options
        val optionsSpacing = optionsConfig?.optionsSpacing?.toIntOrNull() ?: 12
        val bulletSpacing = optionsConfig?.bulletSpacing?.toIntOrNull() ?: 12
        val optionListStyle = optionsConfig?.optionListStyle ?: "number"
        val visibleOptions = surveyOptions.filter { it.id.isNotEmpty() && it.name.isNotEmpty() }
        Column {
            visibleOptions.forEachIndexed { index, option ->
                val isTextBullet = when (optionListStyle.lowercase()) {
                    "none", "plain", "no_bullet", "nobullet" -> false
                    else -> true
                }
                val displayId = when (optionListStyle.lowercase()) {
                    "none", "plain", "no_bullet", "nobullet" -> ""
                    "roman" -> "${toRoman(index + 1).uppercase()}."
                    "alpha", "alphabetic", "alphabet" -> "${('A' + index)}."
                    else -> "${index + 1}."
                }
                SurveyOptionItem(
                    option = option.copy(id = displayId),
                    isSelected = selectedOptions.contains(option.name),
                    styling = styling,
                    bulletSpacing = bulletSpacing,
                    showBullet = isTextBullet,
                    onOptionClick = { onOptionSelected(option.name) }
                )
                if (index < visibleOptions.lastIndex) {
                    Spacer(modifier = Modifier.height(optionsSpacing.dp))
                }
            }
        }

        // Others text input
        if (showInputBox) {
            val addlStyle = styling?.options?.additionalComments
            val addlColors = addlStyle?.colors
            val addlTextStyle = addlStyle?.textStyle
            val addlBgColor = addlColors?.background.toColorOr(
                styling?.othersBackgroundColor.toColorOr(Color.LightGray)
            )
            val addlBorderColor = addlColors?.border.toColorOr(
                styling?.othersBackgroundColor.toColorOr(Color.LightGray)
            )
            val addlTextColor = addlColors?.text.toColorOr(
                styling?.othersTextColor.toColorOr(Color.Black)
            )
            val addlFontSize = (addlTextStyle?.fontSize ?: 14).sp
            val addlFontWeight =
                if (addlTextStyle?.fontDecoration?.contains("bold") == true) FontWeight.Bold else FontWeight.Normal
            val addlFontStyle =
                if (addlTextStyle?.fontDecoration?.contains("italic") == true) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
            val addlTextDecoration =
                if (addlTextStyle?.fontDecoration?.contains("underline") == true) androidx.compose.ui.text.style.TextDecoration.Underline else androidx.compose.ui.text.style.TextDecoration.None
            val addlTextAlign = when (addlTextStyle?.textAlign?.lowercase()) {
                "left" -> TextAlign.Start
                "right" -> TextAlign.End
                else -> TextAlign.Center
            }
            val addlBorderWidth = (addlTextStyle?.borderwidth
                ?.let {
                    if (it.toString() == "null") null else it.toString().removeSuffix(".0")
                        .toIntOrNull()
                }
                ?: 1).dp

            OutlinedTextField(
                value = othersText,
                onValueChange = onOthersTextChanged,
                placeholder = {
                    Text(
                        slide.additionalComment?.placeholder
                            ?: "Please enter Others text…..upto 200 chars",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = addlTextColor,
                            fontWeight = FontWeight.Light
                        )
                    )
                },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = addlFontSize,
                    fontWeight = addlFontWeight,
                    fontStyle = addlFontStyle,
                    textDecoration = addlTextDecoration,
                    textAlign = addlTextAlign,
                    color = addlTextColor
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height((optionsConfig?.optionsHeight ?: 56).dp)
                    .border(addlBorderWidth, addlBorderColor, RoundedCornerShape(8.dp)),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = addlBorderColor,
                    unfocusedBorderColor = addlBorderColor,
                    focusedContainerColor = addlBgColor,
                    unfocusedContainerColor = addlBgColor,
                    focusedTextColor = addlTextColor,
                    unfocusedTextColor = addlTextColor,
                    cursorColor = addlTextColor
                ),
                shape = RoundedCornerShape(8.dp), maxLines = 1,
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Option row card ──────────────────────────────────────────────────────────

@Composable
private fun SurveyOptionItem(
    option: SurveyOption,
    isSelected: Boolean,
    styling: SurveyStyling?,
    bulletSpacing: Int = 12,
    showBullet: Boolean = true,
    onOptionClick: () -> Unit
) {
    val optionsConfig = styling?.options
    val optionHeight = optionsConfig?.optionsHeight
    val activeStyle =
        if (isSelected) optionsConfig?.selectedOptions else optionsConfig?.nonSelectedOptions
    val activeColors = activeStyle?.colors
    val activeTextStyle = activeStyle?.textStyle

    // Corner radius from optionsConfig (future-proof — backend not sending yet)
    val cr = optionsConfig?.cornerRadius
    val optionShape = RoundedCornerShape(
        topStart = (cr?.topLeft ?: 12).dp,
        topEnd = (cr?.topRight ?: 12).dp,
        bottomStart = (cr?.bottomLeft ?: 12).dp,
        bottomEnd = (cr?.bottomRight ?: 12).dp
    )

    // Colors
    val bgColor = activeColors?.background.toColorOr(
        if (isSelected) styling?.selectedOptionColor.toColorOr(Color(0xFFF3F4F6))
        else styling?.optionColor.toColorOr(Color.LightGray)
    )
    val borderColor = activeColors?.border.toColorOr(Color(0xFFE5E7EB))
    val textColor = activeColors?.text.toColorOr(
        if (isSelected) styling?.selectedOptionTextColor.toColorOr(Color.White)
        else styling?.optionTextColor.toColorOr(Color.Black)
    )

    // Border width from textStyle.borderwidth
    val borderWidth = (activeTextStyle?.borderwidth
        ?.let {
            if (it.toString() == "null") null else it.toString().removeSuffix(".0").toIntOrNull()
        }
        ?: 1).dp

    // Text style
    val fontSize = (activeTextStyle?.fontSize ?: 14).sp
    val fontWeight =
        if (activeTextStyle?.fontDecoration?.contains("bold") == true) FontWeight.Bold else FontWeight.Normal
    val fontStyle =
        if (activeTextStyle?.fontDecoration?.contains("italic") == true) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
    val textDecoration =
        if (activeTextStyle?.fontDecoration?.contains("underline") == true) androidx.compose.ui.text.style.TextDecoration.Underline else androidx.compose.ui.text.style.TextDecoration.None
    val textAlign = when (activeTextStyle?.textAlign?.lowercase()) {
        "left" -> TextAlign.Start
        "right" -> TextAlign.End
        else -> TextAlign.Center
    }
    val fontFamily = when (activeTextStyle?.fontFamily?.lowercase()) {
        "serif" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        else -> FontFamily.SansSerif
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (optionHeight != null) Modifier.height(optionHeight.dp) else Modifier.wrapContentHeight())
            .clip(optionShape)
            .background(bgColor)
            .border(borderWidth, borderColor, optionShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onOptionClick() },
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bullet / ID badge
            if (showBullet) {
                // Numbered / Alpha / Roman — show plain text (no circle wrapper)
                Text(
                    text = option.id,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = textColor,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            } else {
                // Plain / None — show a small filled circle dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(textColor)
                )
            }
            Spacer(modifier = Modifier.width(bulletSpacing.dp))

            Text(
                text = option.name,
                fontSize = fontSize,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                textDecoration = textDecoration,
                fontFamily = fontFamily,
                color = textColor,
                textAlign = textAlign,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

data class SurveyOption(
    val id: String,
    val name: String
)

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


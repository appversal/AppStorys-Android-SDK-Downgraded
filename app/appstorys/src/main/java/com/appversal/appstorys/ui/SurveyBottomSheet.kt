package com.appversal.appstorys.ui

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.toColorInt
import coil.compose.AsyncImage
import com.appversal.appstorys.api.CommonMargins
import com.appversal.appstorys.api.SlideResponse
import com.appversal.appstorys.api.SurveyDetails
import com.appversal.appstorys.api.SurveySlide
import com.appversal.appstorys.api.SurveyStyling
import com.appversal.appstorys.api.TextStyling
import com.appversal.appstorys.ui.common_components.CTAButton
import com.appversal.appstorys.ui.common_components.CommonText
import com.appversal.appstorys.ui.common_components.CrossButton
import com.appversal.appstorys.ui.common_components.createCTAButtonConfig
import com.appversal.appstorys.ui.common_components.createCrossButtonConfig
import com.appversal.appstorys.utils.toColor
import kotlinx.coroutines.delay

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

    var currentPage by remember { mutableIntStateOf(0) }

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
        val byId = slides.indexOfFirst { it.id == redirectTo }
            .takeIf { it != -1 }
        if (byId != null) return byId

        // 2. Try matching by slide title (exact)
        val byTitle = slides.indexOfFirst {
            it.title.equals(redirectTo, ignoreCase = true)
        }.takeIf { it != -1 }
        if (byTitle != null) return byTitle

        // 3. Try matching "Question N"
        val questionMatch = Regex("^[Qq]uestion\\s*(\\d+)$").find(redirectTo)
        if (questionMatch != null) {
            val questionNumber = questionMatch.groupValues[1].toIntOrNull()
            if (questionNumber != null) {
                val targetIndex = questionNumber - 1   // 🔥 because order starts at 0
                return if (targetIndex in slides.indices) targetIndex else null
            }
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
                    .imePadding()
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
                Box {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(15.dp)
                    ) {

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
                                            mapOf(
                                                "survey_id" to (surveyDetails.id ?: ""),
                                                "slide_id" to (slides.lastOrNull()?.id ?: "")
                                            )
                                        )
                                    }
                                }
                            )
                        } else {
                            // ── Survey slides ────────────────────────────────────────────────
                            val currentSelected =
                                selectedOptionsPerSlide[currentPage].value
                            val isCurrentSlideValid = currentSelected.isNotEmpty()

                            LaunchedEffect(currentPage) {
                                if (!showThankYou) {
                                    val slide = slides[currentPage]
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

                            AnimatedContent(
                                targetState = currentPage,
                                transitionSpec = {
                                    if (targetState > initialState) {
                                        // Forward: slide in from right
                                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left) togetherWith
                                                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left)
                                    } else {
                                        // Backward: slide in from left
                                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right) togetherWith
                                                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize(),
                                label = "surveySlide"
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
                                        selectedOptionsPerSlide[pageIndex].value =
                                            if (current.contains(optionName)) current - optionName
                                            else current + optionName
                                        if (optionName == "Others" && current.contains("Others")) {
                                            othersTextPerSlide[pageIndex] = ""
                                        }
                                    },
                                    onOthersTextChanged = { text ->
                                        othersTextPerSlide[pageIndex] = text
                                    }
                                )
                            }

                            // ── Navigation row: NEXT / SUBMIT ──────────────────────────────
                            val isLastSlide = currentPage == slides.size - 1

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val currentSlide = slides[currentPage]
                                val currentSelectedInRow =
                                    selectedOptionsPerSlide[currentPage].value
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
                                        textColor =
                                            ctaStyling?.text?.color
                                                ?: surveyDetails.styling?.ctaTextIconColor
                                                ?: "#FFFFFF",
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
                                        backgroundColorString =
                                            ctaContainer?.backgroundColor
                                                ?: surveyDetails.styling?.ctaBackgroundColor
                                                ?: "#000000",
                                        borderColorString = ctaContainer?.borderColor,
                                        borderWidth = ctaContainer?.borderWidth ?: 0,
                                        fullWidth = ctaContainer?.ctaFullWidth ?: true,
                                        // corner radius
                                        borderRadiusTopLeft = ctaCornerRadius?.topLeft ?: 12,
                                        borderRadiusTopRight = ctaCornerRadius?.topRight ?: 12,
                                        borderRadiusBottomLeft = ctaCornerRadius?.bottomLeft ?: 12,
                                        borderRadiusBottomRight = ctaCornerRadius?.bottomRight
                                            ?: 12,
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

                                        if (selectedOptionKeys.isNotEmpty()) {
                                            campaignId?.let {
                                                onTrackEvent(
                                                    it, "clicked",
                                                    mapOf(
                                                        "survey_id" to (surveyDetails.id ?: ""),
                                                        "slide_id" to (currentSlide.id ?: ""),
                                                        "selected_options" to selectedOptionKeys
                                                    )
                                                )
                                            }
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
                                        when {
                                            isThankYouRedirect -> {
                                                campaignId?.let {
                                                    onTrackEvent(
                                                        it,
                                                        "SurveySubmitted",
                                                        mapOf(
                                                            "survey_id" to (surveyDetails.id
                                                                ?: ""),
                                                            "slide_id" to (slides.lastOrNull()?.id
                                                                ?: "")
                                                        )
                                                    )
                                                }
                                                if (hasThankYouPage) showThankYou = true
                                                else onDismissRequest()
                                            }

                                            redirectTargetIndex != null -> {
                                                slideHistory.add(redirectTargetIndex)
                                                currentPage = redirectTargetIndex
                                            }

                                            isLastSlide -> {
                                                campaignId?.let {
                                                    onTrackEvent(
                                                        it,
                                                        "SurveySubmitted",
                                                        mapOf(
                                                            "survey_id" to (surveyDetails.id
                                                                ?: ""),
                                                            "slide_id" to (slides.lastOrNull()?.id
                                                                ?: "")
                                                        )
                                                    )
                                                }
                                                if (hasThankYouPage) showThankYou = true
                                                else onDismissRequest()
                                            }

                                            else -> {
                                                val nextPage = currentPage + 1
                                                slideHistory.add(nextPage)
                                                currentPage = nextPage
                                            }
                                        }
                                    }
                                )
                            }

                            // ── Dot indicators ─────────────────────────────────────────────
                            if (slides.size > 1) {
                                DotsIndicator(
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                    totalDots = slides.size,
                                    selectedIndex = currentPage,
                                    selectedColor = surveyDetails.styling?.cta?.container?.backgroundColor.toColor(
                                        surveyDetails.styling?.ctaBackgroundColor.toColor(Color.Black)
                                    ),
                                    unSelectedColor = surveyDetails.styling?.optionColor.toColorOr(
                                        Color.LightGray
                                    ),
                                    dotSize = 8.dp,
                                    selectedLength = 20.dp
                                )
                            }

                        } // end if/else showThankYou
                    }

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
                                                mapOf(
                                                    "survey_id" to (surveyDetails.id ?: ""),
                                                    "slide_id" to (slides.lastOrNull()?.id ?: "")
                                                )
                                            )
                                        }
                                        onDismissRequest()
                                    }
                                }
                            )
                        }
                    }
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
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // ── "Upload Image" → thankYouImage ──────────────────
        val imageUrl = surveyDetails.thankYouImage
        if (!imageUrl.isNullOrEmpty()) {
            val imgStyle = thankyouPage?.imageStyle
            val imgWidth = imgStyle?.width ?: 80
            val imgHeight = imgStyle?.height ?: 80
            val imgMargin = imgStyle?.margin
            val imgModifier = Modifier
                .size(width = imgWidth.dp, height = imgHeight.dp)
                .padding(
                    top = (imgMargin?.top ?: 12).dp,
                    bottom = (imgMargin?.bottom ?: 12).dp,
                    start = (imgMargin?.left ?: 12).dp,
                    end = (imgMargin?.right ?: 12).dp
                )
            // Detect media type purely from URL extension (strip query params first)
            val urlClean = imageUrl.substringBefore("?").lowercase()
            when {
                urlClean.endsWith(".json") || urlClean.endsWith(".lottie") -> {
                    com.airbnb.lottie.compose.LottieAnimation(
                        composition = com.airbnb.lottie.compose.rememberLottieComposition(
                            com.airbnb.lottie.compose.LottieCompositionSpec.Url(imageUrl)
                        ).value,
                        iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever,
                        modifier = imgModifier
                    )
                }
                urlClean.endsWith(".gif") -> {
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(LocalContext.current)
                            .data(imageUrl)
                            .decoderFactory(coil.decode.GifDecoder.Factory())
                            .build(),
                        contentDescription = "Thank you image",
                        modifier = imgModifier
                    )
                }
                else -> {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Thank you image",
                        modifier = imgModifier
                    )
                }
            }
        }

        // ── "Title Text" → thankYouTitle ────────────────────
        val title = surveyDetails.thankYouTitle
        if (!title.isNullOrEmpty()) {
            val titleTextStyle = thankyouPage?.title?.textStyle
            CommonText(
                modifier = Modifier.fillMaxWidth(),
                text = title,
                styling = TextStyling(
                    color = titleTextStyle?.color,
                    fontFamily = titleTextStyle?.fontFamily,
                    fontSize = titleTextStyle?.fontSize ?: 20,
                    textAlign = titleTextStyle?.textAlign ?: "center",
                    fontDecoration = titleTextStyle?.fontDecoration,
                    margin = titleTextStyle?.margin?.let {
                        CommonMargins(
                            top = it.top,
                            bottom = it.bottom,
                            left = it.left,
                            right = it.right
                        )
                    }
                )
            )
        }

        // ── "Subtitle Text" → thankYouText ──────────────────
        val bodyText = surveyDetails.thankYouText
        if (!bodyText.isNullOrEmpty()) {
            val subtitleTextStyle = thankyouPage?.subtitle?.textStyle
            CommonText(
                modifier = Modifier.fillMaxWidth(),
                text = bodyText,
                styling = TextStyling(
                    color = subtitleTextStyle?.color,
                    fontFamily = subtitleTextStyle?.fontFamily,
                    fontSize = subtitleTextStyle?.fontSize ?: 14,
                    textAlign = subtitleTextStyle?.textAlign ?: "center",
                    fontDecoration = subtitleTextStyle?.fontDecoration,
                    margin = subtitleTextStyle?.margin?.let {
                        CommonMargins(
                            top = it.top,
                            bottom = it.bottom,
                            left = it.left,
                            right = it.right
                        )
                    }
                )
            )
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


    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {

        // Question text
        Column {
            // Display title if exists
            slide.title?.let { title ->
                val titleStyle = styling?.title?.textStyle
                CommonText(
                    modifier = Modifier.fillMaxWidth(),
                    text = title,
                    styling = TextStyling(
                        color = titleStyle?.color ?: styling?.surveyQuestionColor,
                        fontFamily = titleStyle?.fontFamily,
                        fontSize = titleStyle?.fontSize ?: 18,
                        textAlign = titleStyle?.textAlign ?: "center",
                        fontDecoration = titleStyle?.fontDecoration,
                        margin = titleStyle?.margin?.let {
                            CommonMargins(
                                top = it.top,
                                bottom = it.bottom,
                                left = it.left,
                                right = it.right
                            )
                        }
                    )
                )
            }

            // Display subtitle if exists
            slide.subtitle?.let { subtitle ->
                val subtitleStyle = styling?.subtitle?.textStyle
                CommonText(
                    modifier = Modifier.fillMaxWidth(),
                    text = subtitle,
                    styling = TextStyling(
                        color = subtitleStyle?.color ?: styling?.surveyQuestionColor,
                        fontFamily = subtitleStyle?.fontFamily,
                        fontSize = subtitleStyle?.fontSize ?: 14,
                        textAlign = subtitleStyle?.textAlign ?: "center",
                        fontDecoration = subtitleStyle?.fontDecoration,
                        margin = subtitleStyle?.margin?.let {
                            CommonMargins(
                                top = it.top,
                                bottom = it.bottom,
                                left = it.left,
                                right = it.right
                            )
                        }
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
                val showTextPrefix = when (optionListStyle.lowercase()) {
                    "number", "roman", "alpha", "alphabetic", "alphabet" -> true
                    else -> false
                }

                val showCircleBullet =
                    optionListStyle.equals("bulleted", ignoreCase = true)

                val displayId = when (optionListStyle.lowercase()) {
                    "number" -> "${index + 1}."
                    "roman" -> "${toRoman(index + 1).uppercase()}."
                    "alpha", "alphabetic", "alphabet" -> "${('A' + index)}."
                    else -> ""
                }
                SurveyOptionItem(
                    option = option.copy(id = displayId),
                    isSelected = selectedOptions.contains(option.name),
                    styling = styling,
                    bulletSpacing = bulletSpacing,
                    showBullet = showTextPrefix,
                    showCircleBullet = showCircleBullet,
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .height(92.dp)
                    .background(
                        color = addlBgColor,
                        shape = RoundedCornerShape(18.dp)
                    )
                    .border(
                        width = addlBorderWidth,
                        color = addlBorderColor,
                        shape = RoundedCornerShape(18.dp)
                    )
            ) {
                TextField(
                    value = othersText,
                    onValueChange = {
                        if (it.length <= 200) {
                            onOthersTextChanged(it)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    placeholder = {
                        Text(
                            text = slide.additionalComment?.placeholder
                                ?: "Please enter here",
                            color = addlTextColor.copy(alpha = 0.6f),
                            fontSize = addlFontSize
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
                    maxLines = Int.MAX_VALUE,
                    singleLine = false,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = addlTextColor,
                        unfocusedTextColor = addlTextColor,
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = addlTextColor
                    )
                )
            }
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
    showCircleBullet: Boolean = false,
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
            // Prefix Rendering
            if (showBullet) {
                // Number / Alpha / Roman
                val bulletStyle = TextStyling(
                    color = activeColors?.border,
                    fontFamily = activeTextStyle?.fontFamily,
                    fontSize = activeTextStyle?.fontSize ?: 12,
                    textAlign = "start",
                    fontDecoration = listOf("semibold")
                )
                CommonText(
                    text = option.id,
                    styling = bulletStyle
                )
            } else if (showCircleBullet) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .border(
                            width = 1.dp,
                            color = borderColor,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(borderColor)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(bulletSpacing.dp))

            CommonText(
                text = option.name,
                modifier = Modifier.weight(1f),
                styling = TextStyling(
                    color = activeColors?.text,
                    fontFamily = activeTextStyle?.fontFamily,
                    fontSize = activeTextStyle?.fontSize ?: 14,
                    textAlign = activeTextStyle?.textAlign ?: "center",
                    fontDecoration = activeTextStyle?.fontDecoration
                )
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


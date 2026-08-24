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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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

/** Sheet grows with its content and starts scrolling once it would pass this
 *  fraction of the screen height. Mirrors _maxSheetFraction in survey.dart. */
private const val MAX_SHEET_FRACTION = 0.85f

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

    // ── appearance.cornerRadius — default 0 on every corner (Flutter parity) ─
    val cornerRadiusTopStart = (appearance?.cornerRadius?.topLeft ?: 0).dp
    val cornerRadiusTopEnd = (appearance?.cornerRadius?.topRight ?: 0).dp
    val cornerRadiusBottomStart = (appearance?.cornerRadius?.bottomLeft ?: 0).dp
    val cornerRadiusBottomEnd = (appearance?.cornerRadius?.bottomRight ?: 0).dp

    // ── appearance.padding — falls back to 16 on every side when absent ──
    val sheetPadTop = (appearance?.padding?.top ?: 16).dp
    val sheetPadBottom = (appearance?.padding?.bottom ?: 16).dp
    val sheetPadStart = (appearance?.padding?.left ?: 16).dp
    val sheetPadEnd = (appearance?.padding?.right ?: 16).dp

    // ── appearance.displayDelay (seconds) ─────────────────────────────────
    val displayDelaySec = appearance?.displayDelay ?: 0
    var isVisible by remember { mutableStateOf(displayDelaySec <= 0) }
    LaunchedEffect(Unit) {
        if (displayDelaySec > 0) {
            delay(displayDelaySec * 1000L)
            isVisible = true
        }
    }

    // styling.content.isThankyouPage decides whether the page is shown at all
    // (default true). thankYouButtonConfig.enabled only decides whether its CTA
    // is shown — that check lives in SurveyThankYouContent.
    val hasThankYouPage = styling?.content?.isThankyouPage != false
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

        // Sheet never grows past MAX_SHEET_FRACTION of the screen — past that it scrolls.
        val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * MAX_SHEET_FRACTION).dp

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
                    .heightIn(max = maxSheetHeight)
                    // SafeArea(top: false) → bottom inset only, plus keyboard inset
                    .navigationBarsPadding()
                    .imePadding()
                    .clip(
                        RoundedCornerShape(
                            topStart = cornerRadiusTopStart,
                            topEnd = cornerRadiusTopEnd,
                            bottomStart = cornerRadiusBottomStart,
                            bottomEnd = cornerRadiusBottomEnd
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
                            // appearance.padding wraps the sheet content; each element
                            // still adds its own margin on top of it.
                            .padding(
                                top = sheetPadTop,
                                bottom = sheetPadBottom,
                                start = sheetPadStart,
                                end = sheetPadEnd
                            )
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

                            // ── Navigation CTA: Next / Submit ──────────────────────────────
                            // No Row wrapper: CTAButton positions itself from
                            // container.alignment / ctaFullWidth, exactly like Flutter's
                            // Padding(margin) → Align(alignment) → Container.
                            val isLastSlide = currentPage == slides.size - 1

                            run {
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

                                // Flutter: slide.submitButtonText, else "Submit" on the last
                                // slide and "Next" everywhere else.
                                val buttonText = when {
                                    isThankYouRedirect ->
                                        currentSlide.submitButtonText?.takeIf { it.isNotEmpty() }
                                            ?: "Submit"

                                    redirectTargetIndex != null ->
                                        currentSlide.submitButtonText?.takeIf { it.isNotEmpty() }
                                            ?: "Next"

                                    isLastSlide ->
                                        currentSlide.submitButtonText?.takeIf { it.isNotEmpty() }
                                            ?: "Submit"

                                    else ->
                                        currentSlide.submitButtonText?.takeIf { it.isNotEmpty() }
                                            ?: "Next"
                                }

                                val navButtonConfig = run {
                                    val ctaStyling = surveyDetails.styling?.cta
                                    val ctaContainer = ctaStyling?.container
                                    val ctaCornerRadius = ctaStyling?.cornerRadius
                                    val ctaMargin = ctaStyling?.margin
                                    // Defaults below mirror common/cta_button.dart exactly:
                                    // 180x32, #F97316 bg, #FFFFFF 2px border, radius 8,
                                    // margin 12, white text @12, centre aligned.
                                    createCTAButtonConfig(
                                        // text
                                        textColor =
                                            ctaStyling?.text?.color
                                                ?: surveyDetails.styling?.ctaTextIconColor
                                                ?: "#FFFFFF",
                                        textSize = ctaStyling?.text?.fontSize ?: 12,
                                        fontFamily = ctaStyling?.text?.fontFamily,
                                        fontDecoration = ctaStyling?.text?.fontDecoration,
                                        // margins
                                        marginTop = ctaMargin?.top ?: 12,
                                        marginBottom = ctaMargin?.bottom ?: 12,
                                        marginStart = ctaMargin?.left ?: 12,
                                        marginEnd = ctaMargin?.right ?: 12,
                                        // container
                                        height = ctaContainer?.height ?: 32,
                                        width = ctaContainer?.ctaWidth ?: 180,
                                        alignment = ctaContainer?.alignment ?: "center",
                                        backgroundColorString =
                                            ctaContainer?.backgroundColor
                                                ?: surveyDetails.styling?.ctaBackgroundColor
                                                ?: "#F97316",
                                        borderColorString = ctaContainer?.borderColor ?: "#FFFFFF",
                                        borderWidth = ctaContainer?.borderWidth ?: 2,
                                        fullWidth = ctaContainer?.ctaFullWidth ?: false,
                                        // corner radius
                                        borderRadiusTopLeft = ctaCornerRadius?.topLeft ?: 8,
                                        borderRadiusTopRight = ctaCornerRadius?.topRight ?: 8,
                                        borderRadiusBottomLeft = ctaCornerRadius?.bottomLeft ?: 8,
                                        borderRadiusBottomRight = ctaCornerRadius?.bottomRight
                                            ?: 8,
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

                            // ── Dot indicators — always rendered, like Flutter ─────────────
                            DotsIndicator(
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                totalDots = slides.size,
                                selectedIndex = currentPage,
                                selectedColor = surveyDetails.styling?.cta?.container?.backgroundColor
                                    .toColorOr(Color(0xFF1F35DB)),
                                unSelectedColor = surveyDetails.styling?.options?.nonSelectedOptions
                                    ?.colors?.border.toColorOr(Color(0xFFE5E7EB)),
                                dotSize = 8.dp,
                                selectedLength = 20.dp
                            )

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
    val buttonText = surveyDetails.thankYouButtonText?.takeIf { it.isNotBlank() } ?: "Done"
    // "Redirect to" field → thankYouButtonConfig.redirectUrl
    val redirectUrl = buttonConfig?.redirectUrl

    // Build CTAButtonConfig using the common factory.
    // Defaults mirror common/cta_button.dart (180x32, #F97316, white 2px border,
    // radius 8, margin 12, white text @12, centre aligned).
    val ctaButtonConfig = createCTAButtonConfig(
        // text styling
        textColor = ctaStyling?.text?.color ?: "#FFFFFF",
        textSize = ctaStyling?.text?.fontSize ?: 12,
        fontFamily = ctaStyling?.text?.fontFamily,
        fontDecoration = ctaStyling?.text?.fontDecoration,
        // margins
        marginTop = ctaMargin?.top ?: 12,
        marginBottom = ctaMargin?.bottom ?: 12,
        marginStart = ctaMargin?.left ?: 12,
        marginEnd = ctaMargin?.right ?: 12,
        // container
        height = ctaContainer?.height ?: 32,
        width = ctaContainer?.ctaWidth ?: 180,
        alignment = ctaContainer?.alignment ?: "center",
        backgroundColorString = ctaContainer?.backgroundColor ?: "#F97316",
        borderColorString = ctaContainer?.borderColor ?: "#FFFFFF",
        borderWidth = ctaContainer?.borderWidth ?: 2,
        fullWidth = ctaContainer?.ctaFullWidth ?: false,
        // corner radius
        borderRadiusTopLeft = ctaCornerRadius?.topLeft ?: 8,
        borderRadiusTopRight = ctaCornerRadius?.topRight ?: 8,
        borderRadiusBottomLeft = ctaCornerRadius?.bottomLeft ?: 8,
        borderRadiusBottomRight = ctaCornerRadius?.bottomRight ?: 8,
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
            // padding BEFORE size → the margin sits outside the 80x80 box,
            // matching Flutter's Padding(imgMargin) → SizedBox(w, h).
            val imgModifier = Modifier
                .padding(
                    top = (imgMargin?.top ?: 0).dp,
                    bottom = (imgMargin?.bottom ?: 16).dp,
                    start = (imgMargin?.left ?: 0).dp,
                    end = (imgMargin?.right ?: 0).dp
                )
                .size(width = imgWidth.dp, height = imgHeight.dp)
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
                        contentScale = ContentScale.Fit,
                        modifier = imgModifier
                    )
                }
                else -> {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Thank you image",
                        contentScale = ContentScale.Fit,
                        modifier = imgModifier
                    )
                }
            }
        }

        // ── "Title Text" → thankYouTitle ────────────────────
        val title = surveyDetails.thankYouTitle?.takeIf { it.isNotEmpty() } ?: "Thank You"
        if (title.isNotEmpty()) {
            val titleTextStyle = thankyouPage?.title?.textStyle
            CommonText(
                modifier = Modifier.fillMaxWidth(),
                text = title,
                styling = TextStyling(
                    color = titleTextStyle?.color ?: "#111827",
                    fontFamily = titleTextStyle?.fontFamily,
                    fontSize = titleTextStyle?.fontSize ?: 14,
                    textAlign = titleTextStyle?.textAlign ?: "left",
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
                    color = subtitleTextStyle?.color ?: "#111827",
                    fontFamily = subtitleTextStyle?.fontFamily,
                    fontSize = subtitleTextStyle?.fontSize ?: 14,
                    textAlign = subtitleTextStyle?.textAlign ?: "left",
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
        // CTA button shown unless explicitly disabled (Flutter: enabled != false)
        if (buttonConfig?.enabled != false) {
            // "CTA Text" → thankYouButtonText | "Redirect to" → thankYouButtonConfig.redirectUrl
            CTAButton(
                text = buttonText,
                config = ctaButtonConfig,
                onClick = {
                    onThankYouCtaClicked()
                    // Data was already submitted when the user tapped Next/Submit on each slide.
                    // CTA here only handles optional redirect + dismissal.
                    if (!redirectUrl.isNullOrEmpty() && buttonConfig?.action == "redirect") {
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

    val optionsConfig = styling?.options
    val optionsSpacing = optionsConfig?.optionsSpacing?.toIntOrNull() ?: 12

    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {

        // Question text
        Column {
            // Title — falls back to the question when only one of the two is sent
            val titleText = slide.title?.takeIf { it.isNotBlank() } ?: slide.question
            if (!titleText.isNullOrBlank()) {
                val titleStyle = styling?.title?.textStyle
                CommonText(
                    modifier = Modifier.fillMaxWidth(),
                    text = titleText,
                    styling = TextStyling(
                        color = titleStyle?.color ?: styling?.surveyQuestionColor ?: "#111827",
                        fontFamily = titleStyle?.fontFamily,
                        fontSize = titleStyle?.fontSize ?: 14,
                        textAlign = titleStyle?.textAlign ?: "left",
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
            slide.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                val subtitleStyle = styling?.subtitle?.textStyle
                CommonText(
                    modifier = Modifier.fillMaxWidth(),
                    text = subtitle,
                    styling = TextStyling(
                        color = subtitleStyle?.color ?: styling?.surveyQuestionColor ?: "#111827",
                        fontFamily = subtitleStyle?.fontFamily,
                        fontSize = subtitleStyle?.fontSize ?: 14,
                        textAlign = subtitleStyle?.textAlign ?: "left",
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
        val bulletSpacing = optionsConfig?.bulletSpacing?.toIntOrNull() ?: 12
        val optionListStyle = optionsConfig?.optionListStyle ?: "number"
        val visibleOptions = surveyOptions.filter { it.name.isNotEmpty() }
        Column {
            visibleOptions.forEachIndexed { index, option ->
                val showCircleBullet =
                    optionListStyle.equals("bulleted", ignoreCase = true)

                // Flutter's _getOptionPrefix: number → "1.", alpha → "A.",
                // roman → lowercase "i.", anything else → number.
                val displayId = when (optionListStyle.lowercase()) {
                    "alpha", "alphabetic", "alphabet" -> "${('A' + index)}."
                    "roman" -> "${toRoman(index + 1)}."
                    else -> "${index + 1}."
                }

                SurveyOptionItem(
                    option = option.copy(id = displayId),
                    isSelected = selectedOptions.contains(option.name),
                    styling = styling,
                    bulletSpacing = bulletSpacing,
                    showBullet = !showCircleBullet,
                    showCircleBullet = showCircleBullet,
                    onOptionClick = { onOptionSelected(option.name) }
                )
                // Flutter puts optionsSpacing UNDER every option, including the last one.
                Spacer(modifier = Modifier.height(optionsSpacing.dp))
            }
        }

        // Others text input
        if (showInputBox) {
            val addlStyle = styling?.options?.additionalComments
            val addlColors = addlStyle?.colors
            val addlTextStyle = addlStyle?.textStyle
            val addlBgColor = addlColors?.background.toColorOr(Color.White)
            val addlBorderColor = addlColors?.border.toColorOr(Color(0xFFE5E7EB))
            val addlTextColor = addlColors?.text.toColorOr(Color(0xFF6B7280))
            val addlFontSize = (addlTextStyle?.fontSize ?: 12).sp
            val addlFontWeight =
                if (addlTextStyle?.fontDecoration?.contains("bold") == true) FontWeight.Bold else FontWeight.Normal
            val addlFontStyle =
                if (addlTextStyle?.fontDecoration?.contains("italic") == true) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
            val addlTextDecoration =
                if (addlTextStyle?.fontDecoration?.contains("underline") == true) androidx.compose.ui.text.style.TextDecoration.Underline else androidx.compose.ui.text.style.TextDecoration.None
            val addlTextAlign = when (addlTextStyle?.textAlign?.lowercase()) {
                "center" -> TextAlign.Center
                "right" -> TextAlign.End
                "justify" -> TextAlign.Justify
                else -> TextAlign.Start
            }
            val addlBorderWidth = (addlTextStyle?.borderwidth
                ?.let {
                    if (it.toString() == "null") null else it.toString().removeSuffix(".0")
                        .toIntOrNull()
                }
                ?: 1).dp

            // additionalComments has no cornerRadius field, so it follows the
            // options.cornerRadius the rest of the list uses.
            val cr = optionsConfig?.cornerRadius
            val commentShape = RoundedCornerShape(
                topStart = (cr?.topLeft ?: 12).dp,
                topEnd = (cr?.topRight ?: 12).dp,
                bottomStart = (cr?.bottomLeft ?: 12).dp,
                bottomEnd = (cr?.bottomRight ?: 12).dp
            )

            val placeholderText = slide.additionalComment?.placeholder?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "Please enter details (max 200 characters)"

            val commentTextStyle = androidx.compose.ui.text.TextStyle(
                fontSize = addlFontSize,
                fontWeight = addlFontWeight,
                fontStyle = addlFontStyle,
                textDecoration = addlTextDecoration,
                textAlign = addlTextAlign,
                color = addlTextColor
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color = addlBgColor, shape = commentShape)
                    .border(width = addlBorderWidth, color = addlBorderColor, shape = commentShape)
            ) {
                BasicTextField(
                    value = othersText,
                    onValueChange = {
                        if (it.length <= 200) {
                            onOthersTextChanged(it)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        // Flutter's InputDecoration contentPadding: EdgeInsets.all(12)
                        .padding(12.dp),
                    textStyle = commentTextStyle,
                    cursorBrush = SolidColor(addlTextColor),
                    // Flutter: minLines: 3, maxLines: 3
                    minLines = 3,
                    maxLines = 3,
                    decorationBox = { innerTextField ->
                        Box {
                            if (othersText.isEmpty()) {
                                Text(
                                    text = placeholderText,
                                    color = addlTextColor.copy(alpha = 0.6f),
                                    fontSize = addlFontSize,
                                    textAlign = addlTextAlign,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            // Bottom half of the Others block spacing (Flutter wraps the whole
            // Others + comment group in Padding(bottom: optionsSpacing)).
            Spacer(modifier = Modifier.height(optionsSpacing.dp))
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

    // Corner radius from optionsConfig
    val cr = optionsConfig?.cornerRadius
    val optionShape = RoundedCornerShape(
        topStart = (cr?.topLeft ?: 12).dp,
        topEnd = (cr?.topRight ?: 12).dp,
        bottomStart = (cr?.bottomLeft ?: 12).dp,
        bottomEnd = (cr?.bottomRight ?: 12).dp
    )

    // Colors — defaults mirror OptionsConfig in survey.dart
    val bgColor = activeColors?.background.toColorOr(
        if (isSelected) styling?.selectedOptionColor.toColorOr(Color(0xFF1E56C8))
        else styling?.optionColor.toColorOr(Color.White)
    )
    val borderColor = activeColors?.border.toColorOr(
        if (isSelected) Color(0xFF111827) else Color(0xFFE5E7EB)
    )
    val textColor = activeColors?.text.toColorOr(
        if (isSelected) styling?.selectedOptionTextColor.toColorOr(Color.White)
        else styling?.optionTextColor.toColorOr(Color(0xFF111827))
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
            // minHeight instead of a fixed height: long option text grows the row
            .heightIn(min = (optionHeight ?: 0).dp)
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
                // No vertical padding — options.optionsHeight sets the row height
                // and the content is centred inside it.
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Prefix Rendering
            if (showBullet) {
                // Number / Alpha / Roman — colour follows the option TEXT colour
                val bulletStyle = TextStyling(
                    color = activeColors?.text,
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
                        .background(if (isSelected) borderColor else Color.Transparent)
                        .border(
                            width = borderWidth,
                            color = borderColor,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(bgColor)
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
                    fontSize = activeTextStyle?.fontSize ?: 12,
                    textAlign = activeTextStyle?.textAlign ?: "left",
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
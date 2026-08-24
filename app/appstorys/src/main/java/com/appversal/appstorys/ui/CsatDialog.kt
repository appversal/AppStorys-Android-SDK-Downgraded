package com.appversal.appstorys.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.zIndex
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.appversal.appstorys.R
import com.appversal.appstorys.AppStorys.trackEvents
import com.appversal.appstorys.api.CSATDetails
import com.appversal.appstorys.api.CsatTextStyle
import com.appversal.appstorys.ui.common_components.CrossButton
import com.appversal.appstorys.ui.common_components.createCrossButtonConfig
import com.appversal.appstorys.utils.toColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.appversal.appstorys.api.TextStyling
import com.appversal.appstorys.ui.common_components.CommonText
import com.appversal.appstorys.utils.noRippleClickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor

data class CsatFeedback(
    val rating: Int,
    val feedbackOption: String? = null,
    val additionalComments: String = ""
)

@Composable
internal fun CsatDialog(
    onDismiss: () -> Unit,
    onSubmitFeedback: (CsatFeedback) -> Unit,
    csatDetails: CSATDetails
) {

    val localContent: Map<String, String> = remember {
        mapOf(
            "title" to (csatDetails.title ?: "Title"),
            "description" to (csatDetails.descriptionText ?: "Description")
        )
    }

    val styling = remember {
        val s = csatDetails.styling
        mapOf(
            // Background and container colors
            "csatBackgroundColor" to (s?.appearance?.backgroundColor?.toColor(Color.White)
                ?: (Color.White)),

            // Title colors - check both colors field and textStyle.color
            "csatTitleColor" to ((s?.initialFeedback?.title?.textStyle?.color
                ?: s?.initialFeedback?.title?.color)?.toColor(Color.Black)
                ?: (Color.Black)),

            // Description colors - check both colors field and textStyle.color
            "csatDescriptionTextColor" to ((s?.initialFeedback?.subtitle?.textStyle?.color
                ?: s?.initialFeedback?.subtitle?.color)?.toColor(Color(0xFF666666))
                ?: (Color(0xFF666666))),

            // CTA colors - check both flat colors and nested cta structure
            "csatCtaBackgroundColor" to ((s?.feedbackPage?.submitButton?.cta?.container?.backgroundColor
                ?: s?.feedbackPage?.submitButton?.colors?.background)?.toColor(
                Color(
                    0xFFFE6B35
                )
            ) ?: (Color(0xFFFE6B35))),
            "csatCtaTextColor" to ((s?.feedbackPage?.submitButton?.cta?.text?.color
                ?: s?.feedbackPage?.submitButton?.colors?.text)?.toColor(Color.White)
                ?: (Color.White)),
            "csatCtaBorderColor" to ((s?.feedbackPage?.submitButton?.cta?.container?.borderColor
                ?: s?.feedbackPage?.submitButton?.colors?.border)?.toColor(Color(0xFF050505))
                ?: (Color(0xFF050505))),

            // Option colors - non-selected
            "csatOptionBoxColour" to (s?.feedbackPage?.options?.nonSelectedOptions?.colors?.background?.toColor(
                Color(0xFFF5F5F5)
            ) ?: (Color(0xFFF5F5F5))),
            "csatOptionTextColor" to (s?.feedbackPage?.options?.nonSelectedOptions?.colors?.text?.toColor(
                Color.Black
            ) ?: (Color.Black)),
            "csatOptionStrokeColor" to (s?.feedbackPage?.options?.nonSelectedOptions?.colors?.border?.toColor(
                Color(0xFFDDDDDD)
            ) ?: (Color(0xFFDDDDDD))),

            // Option colors - selected
            "csatSelectedOptionBackgroundColor" to (s?.feedbackPage?.options?.selectedOptions?.colors?.background?.toColor(
                Color(0xFFFE6B35)
            ) ?: (Color(0xFFFE6B35))),
            "csatSelectedOptionTextColor" to (s?.feedbackPage?.options?.selectedOptions?.colors?.text?.toColor(
                Color.White
            ) ?: (Color.White)),
            "csatSelectedOptionStrokeColor" to (s?.feedbackPage?.options?.selectedOptions?.colors?.border?.toColor(
                Color(0xFFFE6B35)
            ) ?: (Color(0xFFFE6B35))),

            // Star colors - nested star structure wins, matching Flutter
            "csatLowStarColor" to ((s?.rating?.star?.low?.stylingStar?.background
                ?: s?.rating?.low?.background)?.toColor(Color(0xFFFF6B35))
                ?: (Color(0xFFFF6B35))),
            "csatLowStarBorderColor" to ((s?.rating?.star?.low?.stylingStar?.border
                ?: s?.rating?.low?.border)?.toColor(Color(0xFFFF4500))
                ?: (Color(0xFFFF4500))),
            "csatHighStarColor" to ((s?.rating?.star?.high?.stylingStar?.background
                ?: s?.rating?.high?.background)?.toColor(Color(0xFFFFD700))
                ?: (Color(0xFFFFD700))),
            "csatHighStarBorderColor" to ((s?.rating?.star?.high?.stylingStar?.border
                ?: s?.rating?.high?.border)?.toColor(Color(0xFFDAA520))
                ?: (Color(0xFFDAA520))),
            "csatUnselectedStarColor" to ((s?.rating?.star?.unselected?.stylingStar?.background
                ?: s?.rating?.unselected?.background)?.toColor(Color(0xFFCCCCCC))
                ?: (Color(0xFFCCCCCC))),
            "csatUnselectedStarBorderColor" to ((s?.rating?.star?.unselected?.stylingStar?.border
                ?: s?.rating?.unselected?.border)?.toColor(Color(0xFF999999))
                ?: (Color(0xFF999999))),

            // Additional comments colors
            "csatAdditionalTextColor" to (s?.feedbackPage?.additionalComments?.colors?.text?.toColor(
                Color.Black
            ) ?: (Color.Black)),
            "csatAdditionalBackgroundColor" to (s?.feedbackPage?.additionalComments?.colors?.background?.toColor(
                Color(0xFFEDEDED)
            ) ?: (Color(0xFFEDEDED))),
            "csatAdditionalBorderColor" to (s?.feedbackPage?.additionalComments?.colors?.border?.toColor(
                Color(0xFF050505)
            ) ?: (Color(0xFF050505))),

            // Thank you page colors - check both colors field and textStyle.color
            "thankyouTitleColor" to ((s?.thankyouPage?.title?.textStyle?.color
                ?: s?.thankyouPage?.title?.color)?.toColor(Color(0xFFFE6B35))
                ?: (Color(0xFFFE6B35))),
            "thankyouSubtitleColor" to ((s?.thankyouPage?.subtitle?.textStyle?.color
                ?: s?.thankyouPage?.subtitle?.color)?.toColor(Color(0xFFFE6B35))
                ?: (Color(0xFFFE6B35))),
            // Thank you done button - check both flat colors and nested cta structure
            "thankyouButtonBackgroundColor" to ((s?.thankyouPage?.doneButton?.cta?.container?.backgroundColor
                ?: s?.thankyouPage?.doneButton?.colors?.background)?.toColor(
                Color(
                    0xFFFE6B35
                )
            ) ?: (Color(0xFFFE6B35))),
            "thankyouButtonTextColor" to ((s?.thankyouPage?.doneButton?.cta?.text?.color
                ?: s?.thankyouPage?.doneButton?.colors?.text)?.toColor(Color.White)
                ?: (Color.White)),
            "thankyouButtonBorderColor" to ((s?.thankyouPage?.doneButton?.cta?.container?.borderColor
                ?: s?.thankyouPage?.doneButton?.colors?.border)?.toColor(Color(0xFFFE6B35))
                ?: (Color(0xFFFE6B35)))
        )
    }

    val feedbackOptions = remember {
        if (csatDetails.feedbackOption?.toList()?.isNotEmpty() == true) {
            csatDetails.feedbackOption.toList()
        } else {
            null
        }
    }
    var selectedStars by remember { mutableStateOf(0) }
    var showThanks by remember { mutableStateOf(false) }
    var showFeedback by remember { mutableStateOf(false) }
    var selectedOption by remember { mutableStateOf<String?>(null) }
    var additionalComments by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Extract appearance settings — defaults mirror the Flutter `_flattenStyling` map
    val borderRadius = csatDetails.styling?.appearance?.borderRadius ?: 4
    val containerPadding = csatDetails.styling?.appearance?.padding
    val containerMargin = csatDetails.styling?.appearance?.margin

    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = (containerMargin?.top ?: 0).dp,
                    bottom = (containerMargin?.bottom ?: 0).dp,
                    start = (containerMargin?.left ?: 0).dp,
                    end = (containerMargin?.right ?: 0).dp
                ),
            shape = RoundedCornerShape(borderRadius.dp),
            color = styling["csatBackgroundColor"] ?: Color.White,
//        shadowElevation = 8.dp
        ) {
            Box(
                modifier = Modifier
                    .animateContentSize()
                    .padding(
                        top = (containerPadding?.top ?: 10).dp,
                        bottom = (containerPadding?.bottom ?: 20).dp,
                        start = (containerPadding?.left ?: 10).dp,
                        end = (containerPadding?.right ?: 10).dp
                    )
            ) {
                AnimatedVisibility(
                    visible = !showThanks,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    MainContent(
                        localContent = localContent,
                        styling = styling,
                        selectedStars = selectedStars,
                        showFeedback = showFeedback,
                        feedbackOptions = feedbackOptions,
                        selectedOption = selectedOption,
                        additionalComments = additionalComments,
                        onStarSelected = { stars ->
                            selectedStars = stars
                            when {
                                stars >= 4 -> {
                                    scope.launch {
                                        delay(1000)
                                        onSubmitFeedback(CsatFeedback(rating = stars))
                                        showThanks = true
                                    }
                                }

                                else -> showFeedback = true
                            }
                        },
                        onOptionSelected = { selectedOption = it },
                        onCommentsChanged = { additionalComments = it },
                        onSubmit = {
                            onSubmitFeedback(
                                CsatFeedback(
                                    rating = selectedStars,
                                    feedbackOption = selectedOption,
                                    additionalComments = additionalComments
                                )
                            )
                            showThanks = true
                        },
                        csatDetails = csatDetails,
                        containerPadding = containerPadding
                    )
                }

                // Flutter renders the thank-you screen whether or not an image is set.
                AnimatedVisibility(
                    visible = showThanks,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    ThankYouContent(
                        localContent = localContent,
                        styling = styling,
                        onDone = onDismiss,
                        image = csatDetails.thankyouImage,
                        csatDetails = csatDetails,
                        selectedStars = selectedStars
                    )
                }
            }
        }
        // Cross button using common component - check both field names
        val crossButton = csatDetails.styling?.crossButton ?: csatDetails.styling?.csatCrossButton
        val isCrossEnabled = crossButton?.enabled ?: true

        if (isCrossEnabled) {
            val crossColors = crossButton?.color ?: crossButton?.colors

            CrossButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .zIndex(1f),
                config = createCrossButtonConfig(
                    fillColorString = crossColors?.fill,
                    crossColorString = crossColors?.cross,
                    strokeColorString = crossColors?.stroke,
                    // Flutter pins the cross to the card's top-right corner, so the
                    // container margin has to be added back on top of the button margin.
                    marginTop = (crossButton?.margin?.top ?: 0) + (containerMargin?.top ?: 0),
                    marginEnd = (crossButton?.margin?.right ?: 0) + (containerMargin?.right ?: 0),
                    size = crossButton?.size ?: 16,
                    imageUrl = crossButton?.image
                ),
                onClose = onDismiss
            )
        }
    }
}

/**
 * Mirrors the Flutter `CtaButton` widget:
 * Padding(margin, default 12 per side) -> fullWidth ? button : Align(alignment, button),
 * where the button is a fixed-size box with a centred label.
 */
@Composable
private fun CsatCtaButton(
    text: String,
    marginTop: Int,
    marginBottom: Int,
    marginStart: Int,
    marginEnd: Int,
    radiusTopStart: Int,
    radiusTopEnd: Int,
    radiusBottomStart: Int,
    radiusBottomEnd: Int,
    backgroundColor: Color,
    borderColor: Color,
    borderWidth: Int,
    height: Int,
    width: Int,
    fullWidth: Boolean,
    alignment: String,
    textColorHex: String?,
    fontSize: Int,
    fontFamily: String,
    fontDecoration: List<String>?,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(
        topStart = radiusTopStart.dp,
        topEnd = radiusTopEnd.dp,
        bottomStart = radiusBottomStart.dp,
        bottomEnd = radiusBottomEnd.dp
    )

    val boxAlignment = when (alignment.lowercase()) {
        "left", "start" -> Alignment.CenterStart
        "right", "end" -> Alignment.CenterEnd
        else -> Alignment.Center
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = marginTop.dp,
                bottom = marginBottom.dp,
                start = marginStart.dp,
                end = marginEnd.dp
            ),
        contentAlignment = boxAlignment
    ) {
        Surface(
            modifier = Modifier
                .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier.width(width.dp))
                .height(height.dp)
                .then(
                    if (borderWidth > 0) {
                        Modifier.border(width = borderWidth.dp, color = borderColor, shape = shape)
                    } else Modifier
                )
                .noRippleClickable(onClick = onClick),
            shape = shape,
            color = backgroundColor
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CommonText(
                    modifier = Modifier.fillMaxWidth(),
                    text = text,
                    styling = TextStyling(
                        color = textColorHex,
                        fontSize = fontSize,
                        fontFamily = fontFamily,
                        textAlign = "center",
                        fontDecoration = fontDecoration
                    )
                )
            }
        }
    }
}

@Composable
private fun MainContent(
    localContent: Map<String, String?>,
    styling: Map<String, Color>,
    selectedStars: Int,
    showFeedback: Boolean,
    feedbackOptions: List<String>?,
    selectedOption: String?,
    additionalComments: String,
    onStarSelected: (Int) -> Unit,
    onOptionSelected: (String) -> Unit,
    onCommentsChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    csatDetails: CSATDetails,
    containerPadding: com.appversal.appstorys.api.Margin?
) {


    Column(
        modifier = Modifier
    ) {
        // Extract title and subtitle textStyle
        val titleTextStyle = csatDetails.styling?.initialFeedback?.title?.textStyle
        val subtitleTextStyle = csatDetails.styling?.initialFeedback?.subtitle?.textStyle

        CommonText(
            modifier = Modifier
                .padding(
                    start = (csatDetails.styling?.initialFeedback?.title?.margin?.left ?: 0).dp,
                    end = (csatDetails.styling?.initialFeedback?.title?.margin?.right ?: 0).dp,
                    top = (csatDetails.styling?.initialFeedback?.title?.margin?.top ?: 0).dp,
                    bottom = (csatDetails.styling?.initialFeedback?.title?.margin?.bottom ?: 0).dp
                )
                .fillMaxWidth(),
            text = localContent["title"].toString(),
            styling = TextStyling(
                color = titleTextStyle?.color ?: csatDetails.styling?.initialFeedback?.title?.color,
                fontSize = (titleTextStyle?.fontSize ?: titleTextStyle?.size ?: 12),
                fontFamily = titleTextStyle?.fontFamily ?: "",
                textAlign = titleTextStyle?.textAlign ?: titleTextStyle?.alignment ?: "center",
                fontDecoration = titleTextStyle?.fontDecoration
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        CommonText(
            modifier = Modifier
                .padding(
                    start = (csatDetails.styling?.initialFeedback?.subtitle?.margin?.left ?: 0).dp,
                    end = (csatDetails.styling?.initialFeedback?.subtitle?.margin?.right ?: 0).dp,
                    top = (csatDetails.styling?.initialFeedback?.subtitle?.margin?.top ?: 0).dp,
                    bottom = (csatDetails.styling?.initialFeedback?.subtitle?.margin?.bottom
                        ?: 0).dp
                )
                .fillMaxWidth(),
            text = localContent["description"].toString(),
            styling = TextStyling(
                color = subtitleTextStyle?.color
                    ?: csatDetails.styling?.initialFeedback?.subtitle?.color,
                fontSize = (subtitleTextStyle?.fontSize ?: subtitleTextStyle?.size ?: 12),
                fontFamily = subtitleTextStyle?.fontFamily ?: "",
                textAlign = subtitleTextStyle?.textAlign ?: subtitleTextStyle?.alignment
                ?: "center",
                fontDecoration = subtitleTextStyle?.fontDecoration
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Rating component - supports stars, emojis, and numbers
        RatingComponent(
            csatDetails = csatDetails,
            styling = styling,
            selectedRating = selectedStars,
            onRatingSelected = onStarSelected
        )

        AnimatedVisibility(visible = showFeedback) {
            FeedbackContent(
                localContent = localContent,
                styling = styling,
                feedbackOptions = feedbackOptions,
                selectedOption = selectedOption,
                additionalComments = additionalComments,
                onOptionSelected = onOptionSelected,
                onCommentsChanged = onCommentsChanged,
                onSubmit = onSubmit,
                csatDetails = csatDetails
            )
        }
    }
}

@Composable
private fun FeedbackContent(
    localContent: Map<String, String?>,
    styling: Map<String, Color>,
    feedbackOptions: List<String>?,
    selectedOption: String?,
    additionalComments: String,
    onOptionSelected: (String) -> Unit,
    onCommentsChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    csatDetails: CSATDetails
) {
    Column(
        modifier = Modifier.padding(top = 8.dp)
    ) {
        val optionsConfig = csatDetails.styling?.feedbackPage?.options
        val optionMargin = optionsConfig?.margin
        val optionSpacing = optionsConfig?.optionsSpacing ?: 8

        // Shared by the options and the additional-comments box, as in Flutter.
        val optionShape = RoundedCornerShape(
            topStart = (optionsConfig?.cornerRadius?.topLeft ?: 12).dp,
            topEnd = (optionsConfig?.cornerRadius?.topRight ?: 12).dp,
            bottomStart = (optionsConfig?.cornerRadius?.bottomLeft ?: 12).dp,
            bottomEnd = (optionsConfig?.cornerRadius?.bottomRight ?: 12).dp
        )

        Column(
            Modifier.padding(
                top = (optionMargin?.top ?: 0).dp,
                bottom = (optionMargin?.bottom ?: 0).dp,
                start = (optionMargin?.left ?: 0).dp,
                end = (optionMargin?.right ?: 0).dp
            )
        ) {
            feedbackOptions?.forEach { option ->
                val isSelected = option == selectedOption

                val optionHeight = optionsConfig?.optionsHeight

                // Extract text style settings for options
                val optionTextStyle = if (isSelected) {
                    csatDetails.styling?.feedbackPage?.options?.selectedOptions?.textStyle
                } else {
                    csatDetails.styling?.feedbackPage?.options?.nonSelectedOptions?.textStyle
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (optionHeight != null) Modifier.height(optionHeight.dp) else Modifier
                        ),
                    color = if (isSelected) styling["csatSelectedOptionBackgroundColor"] ?: Color(
                        0xFFFE6B35
                    )
                    else styling["csatOptionBoxColour"] ?: Color(0xFFF5F5F5),
                    shape = optionShape,
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isSelected) optionsConfig?.selectedOptions?.borderWidth?.dp
                            ?: 1.dp else
                            optionsConfig?.nonSelectedOptions?.borderWidth?.dp ?: 1.dp,
                        color = if (isSelected) styling["csatSelectedOptionStrokeColor"] ?: Color(
                            0xFFFE6B35
                        )
                        else styling["csatOptionStrokeColor"] ?: Color(0xFFDDDDDD)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (optionHeight != null) Modifier.fillMaxHeight() else Modifier)
                            .noRippleClickable() { onOptionSelected(option) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        CommonText(
                            modifier = Modifier.fillMaxWidth(),
                            text = option,
                            styling = TextStyling(
                                color = if (isSelected) csatDetails.styling?.feedbackPage?.options?.selectedOptions?.colors?.text
                                else csatDetails.styling?.feedbackPage?.options?.nonSelectedOptions?.colors?.text,
                                fontSize = (optionTextStyle?.fontSize ?: optionTextStyle?.size
                                ?: 12),
                                fontFamily = optionTextStyle?.fontFamily ?: "",
                                textAlign = optionTextStyle?.textAlign
                                    ?: optionTextStyle?.alignment ?: "center",
                                fontDecoration = optionTextStyle?.fontDecoration
                            )
                        )
                    }
                }

                if (optionSpacing > 0) {
                    Spacer(modifier = Modifier.height(optionSpacing.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Only show additional comments if enabled (default to true for backward compatibility)
        val isAdditionalCommentsEnabled =
            csatDetails.styling?.feedbackPage?.additionalComments?.enabled ?: true

        if (isAdditionalCommentsEnabled) {
            // Extract text style settings for additional comments
            val commentsTextStyle = csatDetails.styling?.feedbackPage?.additionalComments?.textStyle
            val commentsFontSizeValue =
                (commentsTextStyle?.fontSize ?: commentsTextStyle?.size ?: 12)
            val commentsFontSize = commentsFontSizeValue.sp
            val commentsTextColor = styling["csatAdditionalTextColor"] ?: Color.Black
            val commentsAlignment =
                when ((commentsTextStyle?.textAlign ?: commentsTextStyle?.alignment)?.lowercase()) {
                    "left", "start" -> androidx.compose.ui.text.style.TextAlign.Start
                    "right", "end" -> androidx.compose.ui.text.style.TextAlign.End
                    else -> androidx.compose.ui.text.style.TextAlign.Center
                }

            // Flutter uses minLines 3 / maxLines 5 with 6dp vertical content padding.
            // The bounds go on the text itself, not the Box: a Box with heightIn plus a
            // fillMaxSize child would always render at the 5-line maximum.
            val lineHeight = commentsFontSizeValue * 1.4f
            val minTextHeight = (lineHeight * 3).dp
            val maxTextHeight = (lineHeight * 5).dp
            val commentsBorderColor =
                styling["csatAdditionalBorderColor"] ?: Color(0xFF050505)
            val commentsFontFamily = when (commentsTextStyle?.font?.lowercase()) {
                "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
                "monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
                "cursive" -> androidx.compose.ui.text.font.FontFamily.Cursive
                else -> androidx.compose.ui.text.font.FontFamily.SansSerif
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = styling["csatAdditionalBackgroundColor"] ?: Color(0xFFEDEDED),
                        shape = optionShape
                    )
                    .border(
                        width = csatDetails.styling?.feedbackPage?.additionalComments?.borderWidth?.dp
                            ?: 1.2.dp,
                        color = commentsBorderColor,
                        shape = optionShape
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                // BasicTextField, not TextField: the Material3 TextField enforces a
                // 56dp minimum height and adds 16dp of internal padding top and bottom,
                // neither of which Flutter's TextField has.
                BasicTextField(
                    value = additionalComments,
                    onValueChange = onCommentsChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minTextHeight, max = maxTextHeight),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = commentsTextColor,
                        fontSize = commentsFontSize,
                        lineHeight = lineHeight.sp,
                        textAlign = commentsAlignment,
                        fontFamily = commentsFontFamily
                    ),
                    cursorBrush = SolidColor(commentsBorderColor),
                    maxLines = 5,
                    singleLine = false,
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (additionalComments.isEmpty()) {
                                Text(
                                    text = csatDetails.styling?.feedbackPage?.additionalComments?.placeholder
                                        ?: "Additional comments",
                                    color = commentsTextColor.copy(alpha = 0.6f),
                                    fontSize = commentsFontSize,
                                    lineHeight = lineHeight.sp,
                                    textAlign = commentsAlignment,
                                    fontFamily = commentsFontFamily,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }


        val submitButton = csatDetails.styling?.feedbackPage?.submitButton
        val isSubmitEnabled = submitButton?.enabled != false

        if (isSubmitEnabled) {
            val submitButtonMargin = submitButton?.cta?.margin ?: submitButton?.margin
            val submitButtonText = submitButton?.text ?: "Submit"
            val submitButtonRadius =
                submitButton?.cta?.cornerRadius ?: submitButton?.containerRadius
            val submitButtonBorderWidth =
                submitButton?.cta?.container?.borderWidth
                    ?: submitButton?.containerStyle?.borderWidth
                    ?: 0
            val submitButtonHeight =
                submitButton?.cta?.container?.height ?: submitButton?.containerStyle?.height ?: 40
            val submitButtonWidth = submitButton?.cta?.container?.ctaWidth ?: 100
            val submitButtonAlignment =
                submitButton?.cta?.container?.alignment ?: submitButton?.containerStyle?.alignment
                ?: "center"
            val submitButtonFullWidth =
                submitButton?.cta?.container?.ctaFullWidth ?: submitButton?.fullWidth ?: false
            val submitButtonTextStyle =
                submitButton?.cta?.text?.let { ctaText ->
                    CsatTextStyle(
                        color = ctaText.color,
                        fontFamily = ctaText.fontFamily,
                        fontSize = ctaText.fontSize,
                        fontDecoration = ctaText.fontDecoration
                    )
                } ?: submitButton?.textStyle

            CsatCtaButton(
                text = submitButtonText,
                marginTop = submitButtonMargin?.top ?: 12,
                marginBottom = submitButtonMargin?.bottom ?: 12,
                marginStart = submitButtonMargin?.left ?: 12,
                marginEnd = submitButtonMargin?.right ?: 12,
                radiusTopStart = submitButtonRadius?.topLeft ?: 8,
                radiusTopEnd = submitButtonRadius?.topRight ?: 8,
                radiusBottomStart = submitButtonRadius?.bottomLeft ?: 8,
                radiusBottomEnd = submitButtonRadius?.bottomRight ?: 8,
                backgroundColor = styling["csatCtaBackgroundColor"] ?: Color(0xFFFE6B35),
                borderColor = styling["csatCtaBorderColor"] ?: Color(0xFF050505),
                borderWidth = submitButtonBorderWidth,
                height = submitButtonHeight,
                width = submitButtonWidth,
                fullWidth = submitButtonFullWidth,
                alignment = submitButtonAlignment,
                textColorHex = submitButtonTextStyle?.color
                    ?: csatDetails.styling?.feedbackPage?.submitButton?.colors?.text,
                fontSize = (submitButtonTextStyle?.fontSize ?: submitButtonTextStyle?.size ?: 12),
                fontFamily = submitButtonTextStyle?.fontFamily ?: "",
                fontDecoration = submitButtonTextStyle?.fontDecoration,
                onClick = onSubmit
            )
        } // end isSubmitEnabled
    }
}

@Composable
private fun ThankYouContent(
    localContent: Map<String, String?>,
    styling: Map<String, Color>,
    image: String?,
    csatDetails: CSATDetails,
    selectedStars: Int,
    onDone: () -> Unit
) {
    val context = LocalContext.current

    // Extract thank you page styling
    val imageStyle = csatDetails.styling?.thankyouPage?.imageStyle
    val imageMargin = imageStyle?.margin
    val imageWidth = imageStyle?.width ?: 80
    val imageHeight = imageStyle?.height ?: 80
    val doneButton = csatDetails.styling?.thankyouPage?.doneButton
    val doneButtonText = doneButton?.text?.takeIf { it.isNotBlank() }
        ?: (if (selectedStars < 4) csatDetails.lowStarText else csatDetails.highStarText) ?: "Done"
    val doneButtonRadius = doneButton?.cta?.cornerRadius ?: doneButton?.containerRadius
    val doneButtonBorderWidth =
        doneButton?.cta?.container?.borderWidth ?: doneButton?.containerStyle?.borderWidth ?: 0
    val doneButtonHeight =
        doneButton?.cta?.container?.height ?: doneButton?.containerStyle?.height ?: 40
    val doneButtonWidth =
        doneButton?.cta?.container?.ctaWidth ?: doneButton?.containerStyle?.width ?: 100
    val doneButtonAlignment =
        doneButton?.cta?.container?.alignment ?: doneButton?.containerStyle?.alignment ?: "center"
    val doneButtonFullWidth =
        doneButton?.cta?.container?.ctaFullWidth ?: doneButton?.fullWidth ?: false
    val doneButtonMargin = doneButton?.cta?.margin ?: doneButton?.margin
    val doneButtonTextStyle = doneButton?.cta?.text?.let { ctaText ->
        CsatTextStyle(
            color = ctaText.color,
            fontFamily = ctaText.fontFamily,
            fontSize = ctaText.fontSize,
            fontDecoration = ctaText.fontDecoration
        )
    } ?: doneButton?.textStyle

    // Flutter picks the rating-specific copy first, then falls back to thankyouText.
    val thankYouTitle = (
            if (selectedStars < 4)
                csatDetails.styling?.rating?.lowRatingTitle
                    ?: csatDetails.styling?.rating?.low?.lowRatingTitle
            else
                csatDetails.styling?.rating?.highRatingTitle
                    ?: csatDetails.styling?.rating?.high?.highRatingTitle
            )?.takeIf { it.isNotBlank() } ?: csatDetails.thankyouText.orEmpty()

    val thankYouDescription = (
            if (selectedStars < 4)
                csatDetails.styling?.rating?.lowRatingSubtitle
                    ?: csatDetails.styling?.rating?.low?.lowRatingSubtitle
            else
                csatDetails.styling?.rating?.highRatingSubtitle
                    ?: csatDetails.styling?.rating?.high?.highRatingSubtitle
            )?.takeIf { it.isNotBlank() } ?: csatDetails.thankyouDescription.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Determine image type and render accordingly
        if (!image.isNullOrEmpty()) {
            val isLottie = image.endsWith(".json", ignoreCase = true)

            // Margins sit outside the image box, matching Flutter's Container margin.
            val mediaModifier = Modifier
                .padding(
                    top = (imageMargin?.top ?: 12).dp,
                    bottom = (imageMargin?.bottom ?: 12).dp,
                    start = (imageMargin?.left ?: 12).dp,
                    end = (imageMargin?.right ?: 12).dp
                )
                .size(width = imageWidth.dp, height = imageHeight.dp)

            when {
                isLottie -> {
                    // Lottie animation
                    com.airbnb.lottie.compose.LottieAnimation(
                        composition = com.airbnb.lottie.compose.rememberLottieComposition(
                            com.airbnb.lottie.compose.LottieCompositionSpec.Url(image)
                        ).value,
                        iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever,
                        modifier = mediaModifier
                    )
                }

                else -> {
                    // Static image (JPEG, PNG, GIF)
                    AsyncImage(
                        model = image,
                        contentDescription = "Thank you",
                        modifier = mediaModifier,
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        // Extract title textStyle
        val titleConfig = csatDetails.styling?.thankyouPage?.title
        val titleTextStyle = titleConfig?.textStyle

        if (thankYouTitle.isNotEmpty()) {
            CommonText(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = (titleConfig?.margin?.left ?: 0).dp,
                        end = (titleConfig?.margin?.right ?: 0).dp,
                        top = (titleConfig?.margin?.top ?: 0).dp,
                        bottom = (titleConfig?.margin?.bottom ?: 0).dp
                    ),
                text = thankYouTitle,
                styling = TextStyling(
                    color = titleTextStyle?.color
                        ?: csatDetails.styling?.thankyouPage?.title?.color,
                    fontSize = (titleTextStyle?.fontSize ?: titleTextStyle?.size ?: 12),
                    fontFamily = titleTextStyle?.fontFamily ?: "",
                    textAlign = titleTextStyle?.textAlign ?: titleConfig?.alignment
                    ?: titleTextStyle?.alignment ?: "center",
                    // Flutter forces bold, then lets an explicit decoration list override it.
                    fontDecoration = titleTextStyle?.fontDecoration?.takeIf { it.isNotEmpty() }
                        ?: listOf("bold")
                )
            )

            Spacer(modifier = Modifier.height(4.dp))
        }

        // Extract subtitle textStyle
        val subtitleConfig = csatDetails.styling?.thankyouPage?.subtitle
        val subtitleTextStyle = subtitleConfig?.textStyle

        if (thankYouDescription.isNotEmpty()) {
            CommonText(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = (subtitleConfig?.margin?.left ?: 0).dp,
                        end = (subtitleConfig?.margin?.right ?: 0).dp,
                        top = (subtitleConfig?.margin?.top ?: 0).dp,
                        bottom = (subtitleConfig?.margin?.bottom ?: 0).dp
                    ),
                text = thankYouDescription,
                styling = TextStyling(
                    color = subtitleTextStyle?.color
                        ?: csatDetails.styling?.thankyouPage?.subtitle?.color,
                    fontSize = (subtitleTextStyle?.fontSize ?: subtitleTextStyle?.size ?: 12),
                    fontFamily = subtitleTextStyle?.fontFamily ?: "",
                    textAlign = subtitleTextStyle?.textAlign ?: subtitleConfig?.alignment
                    ?: subtitleTextStyle?.alignment ?: "center",
                    fontDecoration = subtitleTextStyle?.fontDecoration
                )
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        CsatCtaButton(
            text = doneButtonText,
            marginTop = doneButtonMargin?.top ?: 12,
            marginBottom = doneButtonMargin?.bottom ?: 12,
            marginStart = doneButtonMargin?.left ?: 12,
            marginEnd = doneButtonMargin?.right ?: 12,
            radiusTopStart = doneButtonRadius?.topLeft ?: 8,
            radiusTopEnd = doneButtonRadius?.topRight ?: 8,
            radiusBottomStart = doneButtonRadius?.bottomLeft ?: 8,
            radiusBottomEnd = doneButtonRadius?.bottomRight ?: 8,
            backgroundColor = styling["thankyouButtonBackgroundColor"] ?: Color(0xFFFE6B35),
            borderColor = styling["thankyouButtonBorderColor"] ?: Color(0xFFFE6B35),
            borderWidth = doneButtonBorderWidth,
            height = doneButtonHeight,
            width = doneButtonWidth,
            fullWidth = doneButtonFullWidth,
            alignment = doneButtonAlignment,
            textColorHex = doneButtonTextStyle?.color
                ?: csatDetails.styling?.thankyouPage?.doneButton?.colors?.text,
            fontSize = (doneButtonTextStyle?.fontSize ?: doneButtonTextStyle?.size ?: 12),
            fontFamily = doneButtonTextStyle?.fontFamily ?: "",
            fontDecoration = doneButtonTextStyle?.fontDecoration,
            onClick = {
                if (csatDetails.link.isNullOrEmpty() || selectedStars < 4) {
                    onDone()
                } else {
                    try {
                        trackEvents(csatDetails.campaign, "clicked")
                        val uri = Uri.parse(csatDetails.link)
                        val intent =
                            android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(
                            context,
                            "Could not open link",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }
}

/**
 * Mirrors Flutter `_ratingItemHorizontalPadding`: half of `rating.spacing` is applied
 * to each side of an item so the gap between two neighbours equals the configured
 * spacing. Falls back to the per-type hardcoded gap when the backend omits it.
 */
private fun ratingItemHorizontalPadding(csatDetails: CSATDetails, fallbackSpacing: Int): Dp =
    ((csatDetails.styling?.rating?.spacing ?: fallbackSpacing) / 2f).dp

/**
 * Mirrors Flutter `_ratingCornerRadius`: emoji / number containers stay circular
 * unless the backend sends `rating.cornerRadius`, in which case each corner is
 * honoured individually (missing corners default to 0).
 */
private fun ratingShape(csatDetails: CSATDetails): Shape {
    val cr = csatDetails.styling?.rating?.cornerRadius ?: return CircleShape
    return RoundedCornerShape(
        topStart = (cr.topLeft ?: 0).dp,
        topEnd = (cr.topRight ?: 0).dp,
        bottomStart = (cr.bottomLeft ?: 0).dp,
        bottomEnd = (cr.bottomRight ?: 0).dp
    )
}

@Composable
private fun RatingComponent(
    csatDetails: CSATDetails,
    styling: Map<String, Color>,
    selectedRating: Int,
    onRatingSelected: (Int) -> Unit
) {
    val ratingType = csatDetails.styling?.rating?.ratingType ?: "star"
    val alignment = csatDetails.styling?.rating?.alignment ?: "center"

    val horizontalArrangement = when (alignment.lowercase()) {
        "left", "start" -> Arrangement.Start
        "right", "end" -> Arrangement.End
        else -> Arrangement.Center
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (ratingType.lowercase()) {
            "star" -> StarRating(
                csatDetails = csatDetails,
                styling = styling,
                selectedRating = selectedRating,
                onRatingSelected = onRatingSelected
            )

            "emoji" -> EmojiRating(
                csatDetails = csatDetails,
                selectedRating = selectedRating,
                onRatingSelected = onRatingSelected
            )

            "number" -> NumberRating(
                csatDetails = csatDetails,
                selectedRating = selectedRating,
                onRatingSelected = onRatingSelected
            )

            else -> StarRating(
                csatDetails = csatDetails,
                styling = styling,
                selectedRating = selectedRating,
                onRatingSelected = onRatingSelected
            )
        }
    }
}

@Composable
private fun StarRating(
    csatDetails: CSATDetails,
    styling: Map<String, Color>,
    selectedRating: Int,
    onRatingSelected: (Int) -> Unit
) {
    repeat(5) { index ->
        val isSelected = index < selectedRating
        val isHighRatingMode = selectedRating >= 4

        val starColor = when {
            !isSelected -> styling["csatUnselectedStarColor"] ?: Color(0xFFCCCCCC)
            isHighRatingMode -> styling["csatHighStarColor"] ?: Color(0xFFFFD700)
            else -> styling["csatLowStarColor"] ?: Color(0xFFFF6B35)
        }

        val borderColor = when {
            !isSelected -> styling["csatUnselectedStarBorderColor"] ?: Color(0xFF999999)
            isHighRatingMode -> styling["csatHighStarBorderColor"] ?: Color(0xFFDAA520)
            else -> styling["csatLowStarBorderColor"] ?: Color(0xFFFF4500)
        }

        val borderWidth = when {
            !isSelected -> csatDetails.styling?.rating?.star?.unselected?.stylingStar?.borderWidth
                ?: csatDetails.styling?.rating?.unselected?.borderWidth ?: 0

            isHighRatingMode -> csatDetails.styling?.rating?.star?.high?.stylingStar?.borderWidth
                ?: csatDetails.styling?.rating?.high?.borderWidth ?: 0

            else -> csatDetails.styling?.rating?.star?.low?.stylingStar?.borderWidth
                ?: csatDetails.styling?.rating?.low?.borderWidth ?: 0
        }

        Box(
            modifier = Modifier
                .padding(
                    horizontal = ratingItemHorizontalPadding(csatDetails, 12),
                    vertical = 3.dp
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onRatingSelected(index + 1) },
            contentAlignment = Alignment.Center
        ) {
            if (borderWidth > 0) {
                Icon(
                    painter = painterResource(R.drawable.star),
                    contentDescription = null,
                    tint = borderColor,
                    modifier = Modifier.size((40 + (borderWidth * 3)).dp)
                )
            }

            Icon(
                painter = painterResource(R.drawable.star),
                contentDescription = "Star ${index + 1}",
                tint = starColor,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

@Composable
private fun EmojiRating(
    csatDetails: CSATDetails,
    selectedRating: Int,
    onRatingSelected: (Int) -> Unit
) {
    val emojiConfig = csatDetails.styling?.rating?.emoji
    val fallback = listOf("😞", "😕", "😐", "🙂", "😄")
    val emojis = emojiConfig?.values ?: fallback

    repeat(5) { index ->
        val emoji = emojis.getOrNull(index) ?: fallback[index]
        val isSelected = index == selectedRating - 1

        val containerFill = if (isSelected) {
            emojiConfig?.selected?.stylingContainer?.fill?.toColor(Color(0xFFfff3ed)) ?: Color(
                0xFFfff3ed
            )
        } else {
            emojiConfig?.unselected?.stylingContainer?.fill?.toColor(Color(0xFFf0f0f0)) ?: Color(
                0xFFf0f0f0
            )
        }

        val containerBorder = if (isSelected) {
            emojiConfig?.selected?.stylingContainer?.border?.toColor(Color(0xFFff4400)) ?: Color(
                0xFFff4400
            )
        } else {
            emojiConfig?.unselected?.stylingContainer?.border?.toColor(Color(0xFF908989)) ?: Color(
                0xFF908989
            )
        }

        val borderWidth = if (isSelected) {
            emojiConfig?.selected?.stylingContainer?.borderWidth ?: 2
        } else {
            emojiConfig?.unselected?.stylingContainer?.borderWidth ?: 1
        }

        // Flutter: 46dp container, spacing/2 horizontal padding a side (8dp gap by
        // default), 2dp vertical. Circular unless rating.cornerRadius is sent.
        val shape = ratingShape(csatDetails)

        Box(
            modifier = Modifier
                .padding(
                    horizontal = ratingItemHorizontalPadding(csatDetails, 8),
                    vertical = 2.dp
                )
                .size(46.dp)
                .clip(shape)
                .background(containerFill)
                .border(borderWidth.dp, containerBorder, shape)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onRatingSelected(index + 1) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emoji,
                fontSize = 24.sp
            )
        }
    }
}

@Composable
private fun NumberRating(
    csatDetails: CSATDetails,
    selectedRating: Int,
    onRatingSelected: (Int) -> Unit
) {
    val numberConfig = csatDetails.styling?.rating?.number
    val isHighRatingMode = selectedRating >= 4

    // Flutter hardcodes csatNumberSize = 24 → 40dp box, 22sp glyph.
    val numberSize = 24
    val boxSize = numberSize + 16

    // Flutter uses one text colour for every number, from `unselected.stylingNumber.text`.
    val textColor = numberConfig?.unselected?.stylingNumber?.text?.toColor(Color(0xFFFE6B35))
        ?: Color(0xFFFE6B35)

    repeat(5) { index ->
        val isSelected = index == selectedRating - 1

        val containerFill = when {
            !isSelected -> numberConfig?.unselected?.stylingContainer?.fill?.toColor(
                Color(
                    0xFFededed
                )
            )
                ?: Color(0xFFededed)

            isHighRatingMode -> numberConfig?.high?.stylingContainer?.fill?.toColor(Color(0xFF42e6f5))
                ?: Color(0xFF42e6f5)

            else -> numberConfig?.low?.stylingContainer?.fill?.toColor(Color(0xFF87ff66))
                ?: Color(0xFF87ff66)
        }

        val containerBorder = when {
            !isSelected -> numberConfig?.unselected?.stylingContainer?.border?.toColor(
                Color(
                    0xFFFE6B35
                )
            )
                ?: Color(0xFFFE6B35)

            isHighRatingMode -> numberConfig?.high?.stylingContainer?.border?.toColor(
                Color(
                    0xFFf75555
                )
            )
                ?: Color(0xFFf75555)

            else -> numberConfig?.low?.stylingContainer?.border?.toColor(Color(0xFFff4242))
                ?: Color(0xFFff4242)
        }

        val borderWidth = when {
            !isSelected -> numberConfig?.unselected?.stylingContainer?.borderWidth ?: 0
            isHighRatingMode -> numberConfig?.high?.stylingContainer?.borderWidth ?: 0
            else -> numberConfig?.low?.stylingContainer?.borderWidth ?: 1
        }

        // Flutter: spacing/2 horizontal padding a side (12dp gap by default), 2dp
        // vertical. Circular unless rating.cornerRadius is sent.
        val shape = ratingShape(csatDetails)

        Box(
            modifier = Modifier
                .padding(
                    horizontal = ratingItemHorizontalPadding(csatDetails, 12),
                    vertical = 2.dp
                )
                .size(boxSize.dp)
                .clip(shape)
                .background(containerFill)
                .then(
                    if (borderWidth > 0) {
                        Modifier.border(borderWidth.dp, containerBorder, shape)
                    } else {
                        Modifier
                    }
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onRatingSelected(index + 1) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${index + 1}",
                fontSize = (numberSize - 2).sp,
                color = textColor,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
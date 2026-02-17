package com.appversal.appstorys.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.zIndex
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.appversal.appstorys.AppStorys.trackEvents
import com.appversal.appstorys.api.CSATDetails
import com.appversal.appstorys.api.CsatTextStyle
import com.appversal.appstorys.ui.common_components.CrossButton
import com.appversal.appstorys.ui.common_components.createCrossButtonConfig
import com.appversal.appstorys.utils.toColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.graphicsLayer
import com.appversal.appstorys.api.TextStyling
import com.appversal.appstorys.ui.common_components.CommonText
import com.appversal.appstorys.utils.noRippleClickable

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
            "csatTitleColor" to ((s?.initialFeedback?.title?.color
                ?: s?.initialFeedback?.title?.textStyle?.color)?.toColor(Color.Black)
                ?: (Color.Black)),

            // Description colors - check both colors field and textStyle.color
            "csatDescriptionTextColor" to ((s?.initialFeedback?.subtitle?.color
                ?: s?.initialFeedback?.subtitle?.textStyle?.color)?.toColor(Color(0xFF504F58))
                ?: (Color(0xFF504F58))),

            // CTA colors - check both flat colors and nested cta structure
            "csatCtaBackgroundColor" to ((s?.feedbackPage?.submitButton?.colors?.background
                ?: s?.feedbackPage?.submitButton?.cta?.container?.backgroundColor)?.toColor(
                Color(
                    0xFF007AFF
                )
            ) ?: (Color(0xFF007AFF))),
            "csatCtaTextColor" to ((s?.feedbackPage?.submitButton?.colors?.text
                ?: s?.feedbackPage?.submitButton?.cta?.text?.color)?.toColor(Color.White)
                ?: (Color.White)),
            "csatCtaBorderColor" to ((s?.feedbackPage?.submitButton?.colors?.border
                ?: s?.feedbackPage?.submitButton?.cta?.container?.borderColor)?.toColor(Color.Transparent)
                ?: (Color.Transparent)),

            // Option colors - non-selected
            "csatOptionBoxColour" to (s?.feedbackPage?.options?.nonSelectedOptions?.colors?.background?.toColor(
                Color.White
            ) ?: (Color.White)),
            "csatOptionTextColor" to (s?.feedbackPage?.options?.nonSelectedOptions?.colors?.text?.toColor(
                Color.Black
            ) ?: (Color.Black)),
            "csatOptionStrokeColor" to (s?.feedbackPage?.options?.nonSelectedOptions?.colors?.border?.toColor(
                Color(0xFFCCCCCC)
            ) ?: (Color(0xFFCCCCCC))),

            // Option colors - selected
            "csatSelectedOptionBackgroundColor" to (s?.feedbackPage?.options?.selectedOptions?.colors?.background?.toColor(
                Color(0xFFE3F2FD)
            ) ?: (Color(0xFFE3F2FD))),
            "csatSelectedOptionTextColor" to (s?.feedbackPage?.options?.selectedOptions?.colors?.text?.toColor(
                Color(0xFF007AFF)
            ) ?: (Color(0xFF007AFF))),
            "csatSelectedOptionStrokeColor" to (s?.feedbackPage?.options?.selectedOptions?.colors?.border?.toColor(
                Color(0xFF007AFF)
            ) ?: (Color(0xFF007AFF))),

            // Star colors - check both flat and nested star structure
            "csatLowStarColor" to ((s?.rating?.low?.background
                ?: s?.rating?.star?.low?.stylingStar?.background)?.toColor(Color(0xFFFF6B6B))
                ?: (Color(0xFFFF6B6B))),
            "csatLowStarBorderColor" to ((s?.rating?.low?.border
                ?: s?.rating?.star?.low?.stylingStar?.border)?.toColor(Color.Transparent)
                ?: (Color.Transparent)),
            "csatHighStarColor" to ((s?.rating?.high?.background
                ?: s?.rating?.star?.high?.stylingStar?.background)?.toColor(Color(0xFFFFD700))
                ?: (Color(0xFFFFD700))),
            "csatHighStarBorderColor" to ((s?.rating?.high?.border
                ?: s?.rating?.star?.high?.stylingStar?.border)?.toColor(Color.Transparent)
                ?: (Color.Transparent)),
            "csatUnselectedStarColor" to ((s?.rating?.unselected?.background
                ?: s?.rating?.star?.unselected?.stylingStar?.background)?.toColor(Color(0xFFCCCCCC))
                ?: (Color(0xFFCCCCCC))),
            "csatUnselectedStarBorderColor" to ((s?.rating?.unselected?.border
                ?: s?.rating?.star?.unselected?.stylingStar?.border)?.toColor(Color.Transparent)
                ?: (Color.Transparent)),

            // Additional comments colors
            "csatAdditionalTextColor" to (s?.feedbackPage?.additionalComments?.colors?.text?.toColor(
                Color.Black
            ) ?: (Color.Black)),
            "csatAdditionalBackgroundColor" to (s?.feedbackPage?.additionalComments?.colors?.background?.toColor(
                Color.White
            ) ?: (Color.White)),
            "csatAdditionalBorderColor" to (s?.feedbackPage?.additionalComments?.colors?.border?.toColor(
                Color(0xFFCCCCCC)
            ) ?: (Color(0xFFCCCCCC))),

            // Thank you page colors - check both colors field and textStyle.color
            "thankyouTitleColor" to ((s?.thankyouPage?.title?.color
                ?: s?.thankyouPage?.title?.textStyle?.color)?.toColor(Color.Black)
                ?: (Color.Black)),
            "thankyouSubtitleColor" to ((s?.thankyouPage?.subtitle?.color
                ?: s?.thankyouPage?.subtitle?.textStyle?.color)?.toColor(Color(0xFF504F58))
                ?: (Color(0xFF504F58))),
            // Thank you done button - check both flat colors and nested cta structure
            "thankyouButtonBackgroundColor" to ((s?.thankyouPage?.doneButton?.colors?.background
                ?: s?.thankyouPage?.doneButton?.cta?.container?.backgroundColor)?.toColor(
                Color(
                    0xFF007AFF
                )
            ) ?: (Color(0xFF007AFF))),
            "thankyouButtonTextColor" to ((s?.thankyouPage?.doneButton?.colors?.text
                ?: s?.thankyouPage?.doneButton?.cta?.text?.color)?.toColor(Color.White)
                ?: (Color.White)),
            "thankyouButtonBorderColor" to ((s?.thankyouPage?.doneButton?.colors?.border
                ?: s?.thankyouPage?.doneButton?.cta?.container?.borderColor)?.toColor(Color.Transparent)
                ?: (Color.Transparent))
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

    // Extract appearance settings
    val borderRadius = csatDetails.styling?.appearance?.borderRadius ?: 24
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
                    top = (containerMargin?.top ?: 16).dp,
                    bottom = (containerMargin?.bottom ?: 16).dp,
                    start = (containerMargin?.left ?: 16).dp,
                    end = (containerMargin?.right ?: 16).dp
                ),
            shape = RoundedCornerShape(borderRadius.dp),
            color = styling["csatBackgroundColor"] ?: Color.White,
//        shadowElevation = 8.dp
        ) {
            Box(
                modifier = Modifier.animateContentSize()
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

                if (csatDetails.thankyouImage != null) {
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
                    marginTop = crossButton?.margin?.top ?: 12,
                    marginEnd = (crossButton?.margin?.right ?: 12) + (containerMargin?.right ?: 16),
                    size = crossButton?.size ?: 16,
                    imageUrl = crossButton?.image
                ),
                onClose = onDismiss
            )
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
            .padding(
                top = (containerPadding?.top ?: 24).dp,
                bottom = (containerPadding?.bottom ?: 24).dp,
                start = (containerPadding?.left ?: 24).dp,
                end = (containerPadding?.right ?: 24).dp
            )
    ) {
        // Extract title and subtitle textStyle
        val titleTextStyle = csatDetails.styling?.initialFeedback?.title?.textStyle
        val subtitleTextStyle = csatDetails.styling?.initialFeedback?.subtitle?.textStyle

        // Title alignment
        val titleAlignment = when (titleTextStyle?.alignment?.lowercase()) {
            "left" -> androidx.compose.ui.text.style.TextAlign.Start
            "right" -> androidx.compose.ui.text.style.TextAlign.End
            "center" -> androidx.compose.ui.text.style.TextAlign.Center
            else -> androidx.compose.ui.text.style.TextAlign.Start
        }

        // Title font family
        val titleFontFamily = when (titleTextStyle?.font?.lowercase()) {
            "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
            "monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
            "cursive" -> androidx.compose.ui.text.font.FontFamily.Cursive
            else -> androidx.compose.ui.text.font.FontFamily.SansSerif
        }

        // Subtitle alignment
        val subtitleAlignment = when (subtitleTextStyle?.alignment?.lowercase()) {
            "left" -> androidx.compose.ui.text.style.TextAlign.Start
            "right" -> androidx.compose.ui.text.style.TextAlign.End
            "center" -> androidx.compose.ui.text.style.TextAlign.Center
            else -> androidx.compose.ui.text.style.TextAlign.Start
        }

        // Subtitle font family
        val subtitleFontFamily = when (subtitleTextStyle?.font?.lowercase()) {
            "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
            "monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
            "cursive" -> androidx.compose.ui.text.font.FontFamily.Cursive
            else -> androidx.compose.ui.text.font.FontFamily.SansSerif
        }

        CommonText(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp)
                .fillMaxWidth(),
            text = localContent["title"].toString(),
            styling = TextStyling(
                color = csatDetails.styling?.initialFeedback?.title?.color,
                fontSize = (titleTextStyle?.size ?: ((csatDetails.styling?.fontSize ?: 16) + 6)),
                fontFamily = titleTextStyle?.fontFamily ?: "",
                textAlign = titleTextStyle?.alignment,
                fontDecoration = listOf("bold")
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        CommonText(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp)
                .fillMaxWidth(),
            text = localContent["description"].toString(),
            styling = TextStyling(
                color = csatDetails.styling?.initialFeedback?.subtitle?.color,
                fontSize = (subtitleTextStyle?.size ?: (csatDetails.styling?.fontSize ?: 16)),
                fontFamily = subtitleTextStyle?.fontFamily ?: "",
                textAlign = subtitleTextStyle?.alignment
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

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
        modifier = Modifier.padding(top = 16.dp)
    ) {
        localContent["feedbackPrompt"]?.let { feedbackPrompt ->
            CommonText(
                text = feedbackPrompt,
                styling = TextStyling(
                    color = csatDetails.styling?.initialFeedback?.title?.color,
                    fontSize = (csatDetails.styling?.fontSize ?: 16),
                    fontFamily = csatDetails.styling?.initialFeedback?.title?.textStyle?.fontFamily ?: "",
                )
            )
        }

        feedbackOptions?.forEach { option ->
            val isSelected = option == selectedOption

            // Extract text style settings for options
            val optionTextStyle = if (isSelected) {
                csatDetails.styling?.feedbackPage?.options?.selectedOptions?.textStyle
            } else {
                csatDetails.styling?.feedbackPage?.options?.nonSelectedOptions?.textStyle
            }

            val optionFontSize = (optionTextStyle?.size ?: csatDetails.styling?.fontSize ?: 16).sp
            val optionAlignment = when (optionTextStyle?.alignment?.lowercase()) {
                "center" -> androidx.compose.ui.text.style.TextAlign.Center
                "right", "end" -> androidx.compose.ui.text.style.TextAlign.End
                else -> androidx.compose.ui.text.style.TextAlign.Start
            }
            val optionFontFamily = when (optionTextStyle?.font?.lowercase()) {
                "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
                "monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
                "cursive" -> androidx.compose.ui.text.font.FontFamily.Cursive
                else -> androidx.compose.ui.text.font.FontFamily.SansSerif
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                color = if (isSelected) styling["csatSelectedOptionBackgroundColor"] ?: Color(
                    0xFFE3F2FD
                )
                else styling["csatOptionBoxColour"] ?: Color.White,
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) styling["csatSelectedOptionStrokeColor"] ?: Color(
                        0xFF007AFF
                    )
                    else styling["csatOptionStrokeColor"] ?: Color(0xFFCCCCCC)
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOptionSelected(option) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    CommonText(
                        modifier = Modifier.fillMaxWidth(),
                        text = option,
                        styling = TextStyling(
                            color = if (isSelected) csatDetails.styling?.feedbackPage?.options?.selectedOptions?.colors?.text
                            else csatDetails.styling?.feedbackPage?.options?.nonSelectedOptions?.colors?.text,
                            fontSize = (optionTextStyle?.size ?: csatDetails.styling?.fontSize
                            ?: 16),
                            fontFamily = optionTextStyle?.fontFamily ?: "",
                            textAlign = optionTextStyle?.alignment
                        )
                    )
                }
            }
        }

        if (feedbackOptions?.toList()?.isNotEmpty() == true) {
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Only show additional comments if enabled (default to true for backward compatibility)
        val isAdditionalCommentsEnabled =
            csatDetails.styling?.feedbackPage?.additionalComments?.enabled ?: true

        if (isAdditionalCommentsEnabled) {
            // Extract text style settings for additional comments
            val commentsTextStyle = csatDetails.styling?.feedbackPage?.additionalComments?.textStyle
            val commentsFontSize =
                (commentsTextStyle?.size ?: csatDetails.styling?.fontSize ?: 14).sp
            val commentsAlignment = when (commentsTextStyle?.alignment?.lowercase()) {
                "center" -> androidx.compose.ui.text.style.TextAlign.Center
                "right", "end" -> androidx.compose.ui.text.style.TextAlign.End
                else -> androidx.compose.ui.text.style.TextAlign.Start
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .background(
                        color = styling["csatAdditionalBackgroundColor"] ?: Color.White,
                        shape = RoundedCornerShape(18.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = styling["csatAdditionalBorderColor"] ?: Color(0xFFCCCCCC),
                        shape = RoundedCornerShape(18.dp)
                    )
            ) {
                TextField(
                    value = additionalComments,
                    onValueChange = onCommentsChanged,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = {
                        CommonText(
                            modifier = Modifier.align(Alignment.TopStart),
                            text = "Enter comments",
                            styling = TextStyling(
                                color = "#808080",
                                fontSize = commentsTextStyle?.size,
                                fontFamily = commentsTextStyle?.fontFamily ?: "",
                                textAlign = commentsTextStyle?.alignment
                            )
                        )
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = commentsFontSize,
                        textAlign = commentsAlignment,
                        fontFamily = when (commentsTextStyle?.font?.lowercase()) {
                            "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
                            "monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
                            "cursive" -> androidx.compose.ui.text.font.FontFamily.Cursive
                            else -> androidx.compose.ui.text.font.FontFamily.SansSerif
                        }
                    ),
                    maxLines = Int.MAX_VALUE,
                    singleLine = false,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = styling["csatAdditionalTextColor"] ?: Color.Black,
                        unfocusedTextColor = styling["csatAdditionalTextColor"] ?: Color.Black,
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }
        }


        val submitButton = csatDetails.styling?.feedbackPage?.submitButton
        val submitButtonMargin = submitButton?.margin ?: submitButton?.cta?.margin

        Spacer(modifier = Modifier.height((submitButtonMargin?.top ?: 18).dp))

        val submitButtonText = submitButton?.text ?: "Submit"
        val submitButtonRadius = submitButton?.containerRadius ?: submitButton?.cta?.cornerRadius
        val submitButtonBorderWidth =
            submitButton?.containerStyle?.borderWidth ?: submitButton?.cta?.container?.borderWidth
            ?: 0
        val submitButtonHeight =
            submitButton?.containerStyle?.height ?: submitButton?.cta?.container?.height
        val submitButtonAlignment =
            submitButton?.containerStyle?.alignment ?: submitButton?.cta?.container?.alignment
        val submitButtonFullWidth =
            submitButton?.fullWidth ?: submitButton?.cta?.container?.ctaFullWidth ?: true
        val submitButtonTextStyle =
            submitButton?.textStyle ?: submitButton?.cta?.text?.let { ctaText ->
                CsatTextStyle(
                    color = ctaText.color,
                    fontFamily = ctaText.fontFamily,
                    fontSize = ctaText.fontSize,
                    fontDecoration = ctaText.fontDecoration
                )
            }

        // Determine button alignment
        val buttonAlignment = when (submitButtonAlignment?.lowercase()) {
            "left" -> Alignment.Start
            "right" -> Alignment.End
            else -> Alignment.CenterHorizontally
        }

        Button(
            onClick = onSubmit,
            modifier = Modifier
                .align(buttonAlignment)
                .padding(
                    start = (submitButtonMargin?.left ?: 0).dp,
                    end = (submitButtonMargin?.right ?: 0).dp
                )
                .then(
                    if (submitButtonFullWidth) Modifier.fillMaxWidth() else Modifier
                )
                .then(
                    if (submitButtonHeight != null) Modifier.height(submitButtonHeight.dp) else Modifier
                )
                .then(
                    if (submitButtonBorderWidth > 0) {
                        Modifier.border(
                            width = submitButtonBorderWidth.dp,
                            color = styling["csatCtaBorderColor"] ?: Color.Transparent,
                            shape = RoundedCornerShape(
                                topStart = (submitButtonRadius?.topLeft ?: 12).dp,
                                topEnd = (submitButtonRadius?.topRight ?: 12).dp,
                                bottomStart = (submitButtonRadius?.bottomLeft ?: 12).dp,
                                bottomEnd = (submitButtonRadius?.bottomRight ?: 12).dp
                            )
                        )
                    } else Modifier
                ),
            shape = RoundedCornerShape(
                topStart = (submitButtonRadius?.topLeft ?: 12).dp,
                topEnd = (submitButtonRadius?.topRight ?: 12).dp,
                bottomStart = (submitButtonRadius?.bottomLeft ?: 12).dp,
                bottomEnd = (submitButtonRadius?.bottomRight ?: 12).dp
            ),
            colors = ButtonDefaults.buttonColors(
                containerColor = styling["csatCtaBackgroundColor"] ?: Color(0xFF007AFF)
            )
        ) {
            // Determine text alignment
            val textAlign = when ((submitButtonTextStyle?.alignment
                ?: submitButtonTextStyle?.textAlign)?.lowercase()) {
                "left" -> androidx.compose.ui.text.style.TextAlign.Start
                "right" -> androidx.compose.ui.text.style.TextAlign.End
                else -> androidx.compose.ui.text.style.TextAlign.Center
            }

            // Determine font family
            val fontFamily = when ((submitButtonTextStyle?.font
                ?: submitButtonTextStyle?.fontFamily)?.lowercase()) {
                "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
                "monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
                "cursive" -> androidx.compose.ui.text.font.FontFamily.Cursive
                else -> androidx.compose.ui.text.font.FontFamily.SansSerif
            }

            CommonText(
                modifier = if (submitButtonFullWidth) Modifier.fillMaxWidth() else Modifier,
                text = submitButtonText,
                styling = TextStyling(
                    color = csatDetails.styling?.feedbackPage?.submitButton?.colors?.text,
                    fontSize = (submitButtonTextStyle?.size ?: submitButtonTextStyle?.fontSize
                    ?: ((csatDetails.styling?.fontSize ?: 16) + 2)),
                    fontFamily = submitButtonTextStyle?.fontFamily ?: "",
                    textAlign = submitButtonTextStyle?.alignment ?: submitButtonTextStyle?.textAlign
                )
            )
        }

        Spacer(modifier = Modifier.height((submitButtonMargin?.bottom ?: 0).dp))
    }
}

@Composable
private fun ThankYouContent(
    localContent: Map<String, String?>,
    styling: Map<String, Color>,
    image: String,
    csatDetails: CSATDetails,
    selectedStars: Int,
    onDone: () -> Unit
) {
    val context = LocalContext.current

    // Extract thank you page styling
    val imageStyle = csatDetails.styling?.thankyouPage?.imageStyle
    val imageMargin = imageStyle?.margin
    val imageWidth = imageStyle?.width ?: 66
    val imageHeight = imageStyle?.height ?: 66
    val doneButton = csatDetails.styling?.thankyouPage?.doneButton
    val doneButtonText = doneButton?.text?.takeIf { it.isNotBlank() }
        ?: (if (selectedStars < 4) csatDetails.lowStarText else csatDetails.highStarText) ?: "Done"
    val doneButtonRadius = doneButton?.containerRadius ?: doneButton?.cta?.cornerRadius
    val doneButtonBorderWidth =
        doneButton?.containerStyle?.borderWidth ?: doneButton?.cta?.container?.borderWidth ?: 0
    val doneButtonHeight = doneButton?.containerStyle?.height ?: doneButton?.cta?.container?.height
    val doneButtonWidth = doneButton?.containerStyle?.width ?: doneButton?.cta?.container?.ctaWidth
    val doneButtonAlignment =
        doneButton?.containerStyle?.alignment ?: doneButton?.cta?.container?.alignment
    val doneButtonFullWidth =
        doneButton?.fullWidth ?: doneButton?.cta?.container?.ctaFullWidth ?: true
    val doneButtonMargin = doneButton?.margin ?: doneButton?.cta?.margin
    val doneButtonTextStyle = doneButton?.textStyle ?: doneButton?.cta?.text?.let { ctaText ->
        CsatTextStyle(
            color = ctaText.color,
            fontFamily = ctaText.fontFamily,
            fontSize = ctaText.fontSize,
            fontDecoration = ctaText.fontDecoration
        )
    }

    // Determine button alignment
    val buttonAlignment = when (doneButtonAlignment?.lowercase()) {
        "left" -> Alignment.Start
        "right" -> Alignment.End
        else -> Alignment.CenterHorizontally
    }

    Column(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Determine image type and render accordingly
        val imageUrl =
            image.ifEmpty { "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTwlQ-xYqAIcjylz3NUGJ_jcdRmdzk_vMae0w&s" }
        val isLottie = imageUrl.endsWith(".json", ignoreCase = true)

        when {
            isLottie -> {
                // Lottie animation
                com.airbnb.lottie.compose.LottieAnimation(
                    composition = com.airbnb.lottie.compose.rememberLottieComposition(
                        com.airbnb.lottie.compose.LottieCompositionSpec.Url(imageUrl)
                    ).value,
                    iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever,
                    modifier = Modifier
                        .size(width = imageWidth.dp, height = imageHeight.dp)
                        .padding(
                            top = (imageMargin?.top ?: 0).dp,
                            bottom = (imageMargin?.bottom ?: 0).dp,
                            start = (imageMargin?.left ?: 0).dp,
                            end = (imageMargin?.right ?: 0).dp
                        )
                )
            }

            else -> {
                // Static image (JPEG, PNG, GIF)
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Thank you",
                    modifier = Modifier
                        .size(width = imageWidth.dp, height = imageHeight.dp)
                        .padding(
                            top = (imageMargin?.top ?: 0).dp,
                            bottom = (imageMargin?.bottom ?: 0).dp,
                            start = (imageMargin?.left ?: 0).dp,
                            end = (imageMargin?.right ?: 0).dp
                        ),
                    contentScale = ContentScale.Fit
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Extract title textStyle
        val titleConfig = csatDetails.styling?.thankyouPage?.title
        val titleTextStyle = titleConfig?.textStyle
        val titleAlignment =
            when ((titleConfig?.alignment ?: titleTextStyle?.alignment)?.lowercase()) {
                "left" -> androidx.compose.ui.text.style.TextAlign.Start
                "right" -> androidx.compose.ui.text.style.TextAlign.End
                "center" -> androidx.compose.ui.text.style.TextAlign.Center
                else -> androidx.compose.ui.text.style.TextAlign.Center
            }
        val titleFontFamily = when (titleTextStyle?.font?.lowercase()) {
            "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
            "monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
            "cursive" -> androidx.compose.ui.text.font.FontFamily.Cursive
            else -> androidx.compose.ui.text.font.FontFamily.SansSerif
        }

        CommonText(
            modifier = Modifier.fillMaxWidth(),
            text = csatDetails.thankyouText
                ?.takeIf { it.isNotBlank() }
                ?: (
                        if (selectedStars < 4)
                            csatDetails.styling?.rating?.low?.lowRatingTitle
                                ?: csatDetails.styling?.rating?.lowRatingTitle
                        else
                            csatDetails.styling?.rating?.high?.highRatingTitle
                                ?: csatDetails.styling?.rating?.highRatingTitle
                        ) ?: "Thank You",
            styling = TextStyling(
                color = csatDetails.styling?.thankyouPage?.title?.color,
                fontSize = (titleTextStyle?.size ?: ((csatDetails.styling?.fontSize ?: 16) + 6)),
                fontFamily = titleTextStyle?.fontFamily ?: "",
                textAlign = titleConfig?.alignment ?: titleTextStyle?.alignment,
                fontDecoration = listOf("bold")
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Extract subtitle textStyle
        val subtitleConfig = csatDetails.styling?.thankyouPage?.subtitle
        val subtitleTextStyle = subtitleConfig?.textStyle
        val subtitleAlignment =
            when ((subtitleConfig?.alignment ?: subtitleTextStyle?.alignment)?.lowercase()) {
                "left" -> androidx.compose.ui.text.style.TextAlign.Start
                "right" -> androidx.compose.ui.text.style.TextAlign.End
                "center" -> androidx.compose.ui.text.style.TextAlign.Center
                else -> androidx.compose.ui.text.style.TextAlign.Center
            }
        val subtitleFontFamily = when (subtitleTextStyle?.font?.lowercase()) {
            "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
            "monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
            "cursive" -> androidx.compose.ui.text.font.FontFamily.Cursive
            else -> androidx.compose.ui.text.font.FontFamily.SansSerif
        }

        CommonText(
            modifier = Modifier.fillMaxWidth(),
            text = csatDetails.thankyouDescription
                ?.takeIf { it.isNotBlank() }
                ?: (
                        if (selectedStars < 4)
                            csatDetails.styling?.rating?.low?.lowRatingSubtitle
                                ?: csatDetails.styling?.rating?.lowRatingSubtitle
                        else
                            csatDetails.styling?.rating?.high?.highRatingSubtitle
                                ?: csatDetails.styling?.rating?.highRatingSubtitle
                        ) ?: "Thank you",
            styling = TextStyling(
                color = csatDetails.styling?.thankyouPage?.subtitle?.color,
                fontSize = (subtitleTextStyle?.size ?: (csatDetails.styling?.fontSize ?: 16)),
                fontFamily = subtitleTextStyle?.fontFamily ?: "",
                textAlign = subtitleConfig?.alignment ?: subtitleTextStyle?.alignment
            )
        )

        Spacer(modifier = Modifier.height((doneButtonMargin?.top ?: 16).dp))

        Surface(
            modifier = Modifier
                .align(buttonAlignment)
                .padding(
                    start = (doneButtonMargin?.left ?: 0).dp,
                    end = (doneButtonMargin?.right ?: 0).dp
                )
                .then(
                    if (doneButtonFullWidth) Modifier.fillMaxWidth() else Modifier.width(
                        doneButtonWidth?.dp ?: 120.dp
                    )
                )
                .then(
                    if (doneButtonHeight != null) Modifier.height(doneButtonHeight.dp) else Modifier.height(
                        50.dp
                    )
                )
                .border(
                    width = doneButtonBorderWidth.dp,
                    color = styling["thankyouButtonBorderColor"] ?: Color.Transparent,
                    shape = RoundedCornerShape(
                        topStart = (doneButtonRadius?.topLeft ?: 12).dp,
                        topEnd = (doneButtonRadius?.topRight ?: 12).dp,
                        bottomStart = (doneButtonRadius?.bottomLeft ?: 12).dp,
                        bottomEnd = (doneButtonRadius?.bottomRight ?: 12).dp
                    )
                )
                .noRippleClickable(
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
                ),
            shape = RoundedCornerShape(
                topStart = (doneButtonRadius?.topLeft ?: 12).dp,
                topEnd = (doneButtonRadius?.topRight ?: 12).dp,
                bottomStart = (doneButtonRadius?.bottomLeft ?: 12).dp,
                bottomEnd = (doneButtonRadius?.bottomRight ?: 12).dp
            ),
            color = styling["thankyouButtonBackgroundColor"] ?: Color(0xFF007AFF)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Determine text alignment
                val textAlign = when ((doneButtonTextStyle?.alignment
                    ?: doneButtonTextStyle?.textAlign)?.lowercase()) {
                    "left" -> androidx.compose.ui.text.style.TextAlign.Start
                    "right" -> androidx.compose.ui.text.style.TextAlign.End
                    else -> androidx.compose.ui.text.style.TextAlign.Center
                }

                // Determine font family
                val fontFamily = when ((doneButtonTextStyle?.font
                    ?: doneButtonTextStyle?.fontFamily)?.lowercase()) {
                    "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
                    "monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
                    "cursive" -> androidx.compose.ui.text.font.FontFamily.Cursive
                    else -> androidx.compose.ui.text.font.FontFamily.SansSerif
                }

                CommonText(
                    modifier = if (doneButtonFullWidth) Modifier.fillMaxWidth() else Modifier,
                    text = doneButtonText,
                    styling = TextStyling(
                        color = csatDetails.styling?.thankyouPage?.doneButton?.colors?.text,
                        fontSize = (doneButtonTextStyle?.size ?: doneButtonTextStyle?.fontSize
                        ?: ((csatDetails.styling?.fontSize
                            ?: 16) + 2)),
                        fontFamily = doneButtonTextStyle?.fontFamily ?: "",
                        textAlign = doneButtonTextStyle?.alignment ?: doneButtonTextStyle?.textAlign
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height((doneButtonMargin?.bottom ?: 0).dp))
    }
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
        horizontalArrangement = horizontalArrangement
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
            else -> styling["csatLowStarColor"] ?: Color(0xFFFF6B6B)
        }

        val borderColor = when {
            !isSelected -> styling["csatUnselectedStarBorderColor"] ?: Color.Transparent
            isHighRatingMode -> styling["csatHighStarBorderColor"] ?: Color.Transparent
            else -> styling["csatLowStarBorderColor"] ?: Color.Transparent
        }

        val borderWidth = when {
            !isSelected -> csatDetails.styling?.rating?.unselected?.borderWidth
                ?: csatDetails.styling?.rating?.star?.unselected?.stylingStar?.borderWidth ?: 0

            isHighRatingMode -> csatDetails.styling?.rating?.high?.borderWidth
                ?: csatDetails.styling?.rating?.star?.high?.stylingStar?.borderWidth ?: 0

            else -> csatDetails.styling?.rating?.low?.borderWidth
                ?: csatDetails.styling?.rating?.star?.low?.stylingStar?.borderWidth ?: 0
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onRatingSelected(index + 1) },
            contentAlignment = Alignment.Center
        ) {
            if (borderWidth > 0) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = borderColor,
                    modifier = Modifier
                        .size(36.dp)
                        .graphicsLayer {
                            val scale = 1f + (borderWidth * 0.08f)
                            scaleX = scale
                            scaleY = scale
                        }
                )
            }

            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Star ${index + 1}",
                tint = starColor,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
    }
}

@Composable
private fun EmojiRating(
    csatDetails: CSATDetails,
    selectedRating: Int,
    onRatingSelected: (Int) -> Unit
) {
    val emojiConfig = csatDetails.styling?.rating?.emoji
    val emojis = emojiConfig?.values ?: listOf("😢", "😕", "😐", "🙂", "😄")

    emojis.forEachIndexed { index, emoji ->
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
            emojiConfig?.selected?.stylingContainer?.border?.toColor(Color(0xFFFE6B35)) ?: Color(
                0xFFFE6B35
            )
        } else {
            emojiConfig?.unselected?.stylingContainer?.border?.toColor(Color(0xFFcccccc)) ?: Color(
                0xFFcccccc
            )
        }

        val borderWidth = if (isSelected) {
            emojiConfig?.selected?.stylingContainer?.borderWidth ?: 2
        } else {
            emojiConfig?.unselected?.stylingContainer?.borderWidth ?: 1
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(containerFill)
                .border(borderWidth.dp, containerBorder, CircleShape)
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
        if (index < emojis.size - 1) {
            Spacer(modifier = Modifier.width(8.dp))
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

    repeat(5) { index ->
        val isSelected = index < selectedRating

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

        val textColor = when {
            !isSelected -> numberConfig?.unselected?.stylingNumber?.text?.toColor(Color(0xFFFE6B35))
                ?: numberConfig?.stylingNumber?.text?.toColor(Color(0xFFFE6B35))
                ?: Color(0xFFFE6B35)

            else -> numberConfig?.stylingNumber?.text?.toColor(Color.Black) ?: Color.Black
        }

        val textSize = numberConfig?.stylingNumber?.textSize ?: 16

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(containerFill)
                .then(
                    if (borderWidth > 0) {
                        Modifier.border(borderWidth.dp, containerBorder, CircleShape)
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
                fontSize = if (textSize > 0) textSize.sp else 16.sp,
                color = textColor,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }
        if (index < 4) {
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}
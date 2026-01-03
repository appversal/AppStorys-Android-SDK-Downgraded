package com.appversal.appstorys.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import com.appversal.appstorys.utils.toColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.MutableInteractionSource

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
    val localContent: Map<String, String?> = remember {
        mapOf(
            "title" to csatDetails.title?.takeIf { it.isNotEmpty() },
            "description" to csatDetails.descriptionText?.takeIf { it.isNotEmpty() },
            "thankyouText" to csatDetails.thankyouText?.takeIf { it.isNotEmpty() },
            "thankyouDescription" to csatDetails.thankyouDescription?.takeIf { it.isNotEmpty() },
            "feedbackPrompt" to csatDetails.styling?.csatFeedbackTitleText?.takeIf { it.isNotEmpty() },
        )
    }

    val styling = remember {
        val s = csatDetails.styling
        mapOf(
            // Background and container colors - prefer new structure
            "csatBackgroundColor" to (s?.appearance?.backgroundColor?.toColor(
                s?.csatBackgroundColor.toColor(Color.White)
            ) ?: s?.csatBackgroundColor.toColor(Color.White)),

            // Title colors - prefer new structure
            "csatTitleColor" to (s?.initialFeedback?.title?.colors?.toColor(
                s?.csatTitleColor.toColor(Color.Black)
            ) ?: s?.csatTitleColor.toColor(Color.Black)),

            // Description colors - prefer new structure
            "csatDescriptionTextColor" to (s?.initialFeedback?.subtitle?.colors?.toColor(
                s?.csatDescriptionTextColor.toColor(Color(0xFF504F58))
            ) ?: s?.csatDescriptionTextColor.toColor(Color(0xFF504F58))),

            // CTA colors - prefer new structure (submit button)
            "csatCtaBackgroundColor" to (s?.feedbackPage?.submitButton?.colors?.background?.toColor(
                s?.csatCtaBackgroundColor.toColor(Color(0xFF007AFF))
            ) ?: s?.csatCtaBackgroundColor.toColor(Color(0xFF007AFF))),
            "csatCtaTextColor" to (s?.feedbackPage?.submitButton?.colors?.text?.toColor(
                s?.csatCtaTextColor.toColor(Color.White)
            ) ?: s?.csatCtaTextColor.toColor(Color.White)),
            "csatCtaBorderColor" to (s?.feedbackPage?.submitButton?.colors?.border?.toColor(
                s?.csatCtaBorderColor.toColor(Color.Transparent)
            ) ?: s?.csatCtaBorderColor.toColor(Color.Transparent)),

            // Option colors - non-selected
            "csatOptionBoxColour" to (s?.feedbackPage?.options?.nonSelectedOptions?.colors?.background?.toColor(
                s?.csatOptionBoxColour.toColor(Color.White)
            ) ?: s?.csatOptionBoxColour.toColor(Color.White)),
            "csatOptionTextColor" to (s?.feedbackPage?.options?.nonSelectedOptions?.colors?.text?.toColor(
                s?.csatOptionTextColour.toColor(Color.Black)
            ) ?: s?.csatOptionTextColour.toColor(Color.Black)),
            "csatOptionStrokeColor" to (s?.feedbackPage?.options?.nonSelectedOptions?.colors?.border?.toColor(
                s?.csatOptionStrokeColor.toColor(Color(0xFFCCCCCC))
            ) ?: s?.csatOptionStrokeColor.toColor(Color(0xFFCCCCCC))),

            // Option colors - selected
            "csatSelectedOptionBackgroundColor" to (s?.feedbackPage?.options?.selectedOptions?.colors?.background?.toColor(
                s?.csatSelectedOptionBackgroundColor.toColor(Color(0xFFE3F2FD))
            ) ?: s?.csatSelectedOptionBackgroundColor.toColor(Color(0xFFE3F2FD))),
            "csatSelectedOptionTextColor" to (s?.feedbackPage?.options?.selectedOptions?.colors?.text?.toColor(
                s?.csatSelectedOptionTextColor.toColor(Color(0xFF007AFF))
            ) ?: s?.csatSelectedOptionTextColor.toColor(Color(0xFF007AFF))),
            "csatSelectedOptionStrokeColor" to (s?.feedbackPage?.options?.selectedOptions?.colors?.border?.toColor(
                s?.csatSelectedOptionStrokeColor.toColor(Color(0xFF007AFF))
            ) ?: s?.csatSelectedOptionStrokeColor.toColor(Color(0xFF007AFF))),

            // Star colors - prefer new structure
            "csatLowStarColor" to (s?.rating?.low?.background?.toColor(
                s?.csatLowStarColor.toColor(Color(0xFFFF6B6B))
            ) ?: s?.csatLowStarColor.toColor(Color(0xFFFF6B6B))),
            "csatHighStarColor" to (s?.rating?.high?.background?.toColor(
                s?.csatHighStarColor.toColor(Color(0xFFFFD700))
            ) ?: s?.csatHighStarColor.toColor(Color(0xFFFFD700))),
            "csatUnselectedStarColor" to (s?.rating?.unselected?.background?.toColor(
                s?.csatUnselectedStarColor.toColor(Color(0xFFCCCCCC))
            ) ?: s?.csatUnselectedStarColor.toColor(Color(0xFFCCCCCC))),

            // Additional comments colors
            "csatAdditionalTextColor" to (s?.feedbackPage?.additionalComments?.colors?.text?.toColor(
                s?.csatAdditionalTextColor.toColor(Color.Black)
            ) ?: s?.csatAdditionalTextColor.toColor(Color.Black)),
            "csatAdditionalBackgroundColor" to (s?.feedbackPage?.additionalComments?.colors?.background?.toColor(
                Color.White
            ) ?: Color.White),
            "csatAdditionalBorderColor" to (s?.feedbackPage?.additionalComments?.colors?.border?.toColor(
                Color(0xFFCCCCCC)
            ) ?: Color(0xFFCCCCCC)),

            // Thank you page colors
            "thankyouTitleColor" to (s?.thankyouPage?.title?.colors?.toColor(
                s?.csatTitleColor.toColor(Color.Black)
            ) ?: s?.csatTitleColor.toColor(Color.Black)),
            "thankyouSubtitleColor" to (s?.thankyouPage?.subtitle?.colors?.toColor(
                s?.csatDescriptionTextColor.toColor(Color(0xFF504F58))
            ) ?: s?.csatDescriptionTextColor.toColor(Color(0xFF504F58))),
            "thankyouButtonBackgroundColor" to (s?.thankyouPage?.doneButton?.colors?.background?.toColor(
                s?.csatCtaBackgroundColor.toColor(Color(0xFF007AFF))
            ) ?: s?.csatCtaBackgroundColor.toColor(Color(0xFF007AFF))),
            "thankyouButtonTextColor" to (s?.thankyouPage?.doneButton?.colors?.text?.toColor(
                s?.csatCtaTextColor.toColor(Color.White)
            ) ?: s?.csatCtaTextColor.toColor(Color.White)),
            "thankyouButtonBorderColor" to (s?.thankyouPage?.doneButton?.colors?.border?.toColor(
                Color.Transparent
            ) ?: Color.Transparent)
        )
    }

    val feedbackOptions = remember {
        if (csatDetails.feedbackOption?.toList()?.isNotEmpty() == true){
            csatDetails.feedbackOption.toList()
        }else{
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
        color = styling["csatBackgroundColor"]!!,
//        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier.animateContentSize()
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Gray.copy(alpha = 0.2f))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
            }

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

            if (csatDetails.thankyouImage != null){
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
        Text(
            modifier = Modifier.padding(end = 18.dp),
            text = localContent["title"]!!,
            fontSize = ((csatDetails.styling?.fontSize ?: 16) + 6).sp,
            fontWeight = FontWeight.Bold,
            color = styling["csatTitleColor"]!!
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = localContent["description"]!!,
            fontSize = (csatDetails.styling?.fontSize ?: 16).sp,
            color = styling["csatDescriptionTextColor"]!!
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            repeat(5) { index ->
                val starColor = when {
                    index >= selectedStars -> styling["csatUnselectedStarColor"]!!
                    selectedStars >= 4 -> styling["csatHighStarColor"]!!
                    else -> styling["csatLowStarColor"]!!
                }
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Star ${index + 1}",
                    tint = starColor,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onStarSelected(index + 1) }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        }

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
            Text(
                text = feedbackPrompt,
                fontSize = (csatDetails.styling?.fontSize ?: 16).sp,
                color = styling["csatTitleColor"]!!
            )
        }
//        if (feedbackOptions?.toList()?.isNotEmpty() == true) {
//            Spacer(modifier = Modifier.height(4.dp))
//        }

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
                color = if (isSelected) styling["csatSelectedOptionBackgroundColor"]!!
                       else styling["csatOptionBoxColour"]!!,
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) styling["csatSelectedOptionStrokeColor"]!!
                           else styling["csatOptionStrokeColor"]!!
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOptionSelected(option) }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = option,
                        fontSize = optionFontSize,
                        textAlign = optionAlignment,
                        fontFamily = optionFontFamily,
                        modifier = Modifier.fillMaxWidth(),
                        color = if (isSelected) styling["csatSelectedOptionTextColor"]!!
                               else styling["csatOptionTextColor"]!!
                    )
                }
            }
        }

        if (feedbackOptions?.toList()?.isNotEmpty() == true) {
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Only show additional comments if enabled (default to true for backward compatibility)
        val isAdditionalCommentsEnabled = csatDetails.styling?.feedbackPage?.additionalComments?.enabled ?: true

        if (isAdditionalCommentsEnabled) {
            // Extract text style settings for additional comments
            val commentsTextStyle = csatDetails.styling?.feedbackPage?.additionalComments?.textStyle
            val commentsFontSize = (commentsTextStyle?.size ?: csatDetails.styling?.fontSize ?: 14).sp
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
                        color = styling["csatAdditionalBackgroundColor"]!!,
                        shape = RoundedCornerShape(18.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = styling["csatAdditionalBorderColor"]!!,
                        shape = RoundedCornerShape(18.dp)
                    )
            ) {
                TextField(
                    value = additionalComments,
                    onValueChange = onCommentsChanged,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = {
                        Text(
                            "Enter comments",
                            color = Color.Gray,
                            fontSize = commentsFontSize,
                            textAlign = commentsAlignment,
                            modifier = Modifier.align(Alignment.TopStart)
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
                        focusedTextColor = styling["csatAdditionalTextColor"]!!,
                        unfocusedTextColor = styling["csatAdditionalTextColor"]!!,
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }
        }


        Spacer(modifier = Modifier.height(18.dp))

        val submitButtonText = csatDetails.styling?.feedbackPage?.submitButton?.text ?: "Submit"
        val submitButtonRadius = csatDetails.styling?.feedbackPage?.submitButton?.containerRadius
        val submitButtonBorderWidth = csatDetails.styling?.feedbackPage?.submitButton?.containerStyle?.borderWidth ?: 0

        Button(
            onClick = onSubmit,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth()
                .then(
                    if (submitButtonBorderWidth > 0) {
                        Modifier.border(
                            width = submitButtonBorderWidth.dp,
                            color = styling["csatCtaBorderColor"]!!,
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
                containerColor = styling["csatCtaBackgroundColor"]!!
            ),
            contentPadding = PaddingValues(0.dp) // NO PADDING as requested
        ) {
            Text(
                text = submitButtonText,
                fontSize = ((csatDetails.styling?.fontSize ?: 16) + 2).sp,
                color = styling["csatCtaTextColor"]!!
            )
        }
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
    val imageMargin = csatDetails.styling?.thankyouPage?.imageStyle?.margin
    val doneButtonText = csatDetails.styling?.thankyouPage?.doneButton?.text ?:
        (if (selectedStars < 4) csatDetails.lowStarText else csatDetails.highStarText) ?: "Done"
    val doneButtonRadius = csatDetails.styling?.thankyouPage?.doneButton?.containerRadius
    val doneButtonBorderWidth = csatDetails.styling?.thankyouPage?.doneButton?.containerStyle?.borderWidth ?: 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = image.ifEmpty { "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTwlQ-xYqAIcjylz3NUGJ_jcdRmdzk_vMae0w&s"  },
            contentDescription = "Thank you",
            modifier = Modifier
                .size(66.dp)
                .padding(
                    top = (imageMargin?.top ?: 0).dp,
                    bottom = (imageMargin?.bottom ?: 0).dp,
                    start = (imageMargin?.left ?: 0).dp,
                    end = (imageMargin?.right ?: 0).dp
                ), // NO PADDING from imageStyle.padding as requested
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = localContent["thankyouText"]!!,
            fontSize = ((csatDetails.styling?.fontSize ?: 16) + 6).sp,
            fontWeight = FontWeight.Bold,
            color = styling["thankyouTitleColor"]!!
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = localContent["thankyouDescription"]!!,
            fontSize = (csatDetails.styling?.fontSize ?: 16).sp,
            color = styling["thankyouSubtitleColor"]!!,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth()
                .then(
                    if (doneButtonBorderWidth > 0) {
                        Modifier.border(
                            width = doneButtonBorderWidth.dp,
                            color = styling["thankyouButtonBorderColor"]!!,
                            shape = RoundedCornerShape(
                                topStart = (doneButtonRadius?.topLeft ?: 12).dp,
                                topEnd = (doneButtonRadius?.topRight ?: 12).dp,
                                bottomStart = (doneButtonRadius?.bottomLeft ?: 12).dp,
                                bottomEnd = (doneButtonRadius?.bottomRight ?: 12).dp
                            )
                        )
                    } else Modifier
                ),
            onClick = {
                if(csatDetails.link.isNullOrEmpty() || selectedStars < 4){
                    onDone()
                } else {
                    try {
                        trackEvents(csatDetails.campaign, "clicked")
                        val uri = Uri.parse(csatDetails.link)
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(
                            context,
                            "Could not open link",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            shape = RoundedCornerShape(
                topStart = (doneButtonRadius?.topLeft ?: 12).dp,
                topEnd = (doneButtonRadius?.topRight ?: 12).dp,
                bottomStart = (doneButtonRadius?.bottomLeft ?: 12).dp,
                bottomEnd = (doneButtonRadius?.bottomRight ?: 12).dp
            ),
            colors = ButtonDefaults.buttonColors(
                containerColor = styling["thankyouButtonBackgroundColor"]!!
            ),
            contentPadding = PaddingValues(0.dp) // NO PADDING as requested
        ) {
            Text(
                fontSize = ((csatDetails.styling?.fontSize ?: 16) + 2).sp,
                text = doneButtonText,
                color = styling["thankyouButtonTextColor"]!!
            )
        }
    }
}
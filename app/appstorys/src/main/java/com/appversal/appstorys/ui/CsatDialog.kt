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
import com.appversal.appstorys.api.CSATStyling
import com.appversal.appstorys.utils.toColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.MutableInteractionSource
import java.nio.file.WatchEvent

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
        mapOf(
            "csatBackgroundColor" to csatDetails.styling?.csatBackgroundColor.toColor(Color.White),
            "csatTitleColor" to csatDetails.styling?.csatTitleColor.toColor(Color.Black),
            "csatDescriptionTextColor" to csatDetails.styling?.csatDescriptionTextColor.toColor(Color(0xFF504F58)),
            "csatCtaBackgroundColor" to csatDetails.styling?.csatCtaBackgroundColor.toColor(Color(0xFF007AFF)),
            "csatCtaTextColor" to csatDetails.styling?.csatCtaTextColor.toColor(Color.White),
            "csatSelectedOptionBackgroundColor" to csatDetails.styling?.csatSelectedOptionBackgroundColor.toColor(Color(0xFFE3F2FD)),
            "csatOptionStrokeColor" to csatDetails.styling?.csatOptionStrokeColor.toColor(Color(0xFFCCCCCC)),
            "csatSelectedOptionTextColor" to csatDetails.styling?.csatSelectedOptionTextColor.toColor(Color(0xFF007AFF)),
            "csatOptionBoxColour" to csatDetails.styling?.csatOptionBoxColour.toColor(Color(0xFF007AFF)),
            "csatOptionTextColor" to csatDetails.styling?.csatOptionTextColour.toColor(Color.Black),
            "csatLowStarColor" to csatDetails.styling?.csatLowStarColor.toColor(Color.Black),
            "csatHighStarColor" to csatDetails.styling?.csatHighStarColor.toColor(Color.Black),
            "csatUnselectedStarColor" to csatDetails.styling?.csatUnselectedStarColor.toColor(Color.Black),
            "csatSelectedOptionStrokeColor" to csatDetails.styling?.csatSelectedOptionStrokeColor.toColor(Color.Black),
            "csatAdditionalTextColor" to csatDetails.styling?.csatAdditionalTextColor.toColor(Color.Black)
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

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
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
                    csatDetails = csatDetails
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
    csatDetails: CSATDetails
) {
    Column(
        modifier = Modifier
            .padding(24.dp)
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
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .border(
                        width = 1.dp,
                        color =  if (isSelected) styling["csatSelectedOptionStrokeColor"]!! else
                            styling["csatOptionStrokeColor"]!!,
                        shape = RoundedCornerShape(24.dp)
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onOptionSelected(option) },
                color = if (isSelected) styling["csatSelectedOptionBackgroundColor"]!!
                else styling["csatOptionBoxColour"]!!,
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    text = option,
                    fontSize = (csatDetails.styling?.fontSize ?: 16).sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = if (isSelected) styling["csatSelectedOptionTextColor"]!!
                    else styling["csatOptionTextColor"]!!
                )
            }
        }

        if (feedbackOptions?.toList()?.isNotEmpty() == true) {
            Spacer(modifier = Modifier.height(12.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp) // <-- Set your desired height
                .background(
                    color = styling["csatOptionBoxColour"]!!,
                    shape = RoundedCornerShape(18.dp)
                )
                .border(
                    width = 1.dp,
                    color = styling["csatOptionStrokeColor"]!!,
                    shape = RoundedCornerShape(18.dp)
                )
        ) {
            TextField(
                value = additionalComments,
                onValueChange = onCommentsChanged,
                modifier = Modifier
                    .fillMaxSize(),
                placeholder = {
                    Text(
                        "Enter comments",
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                },
                maxLines = Int.MAX_VALUE,
                singleLine = false,
                colors = TextFieldDefaults.colors(
                    focusedTextColor = styling["csatAdditionalTextColor"]!!,
                    unfocusedTextColor = Color.Black,
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        }


        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = onSubmit,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = styling["csatCtaBackgroundColor"]!!
            )
        ) {
            Text(
                modifier = Modifier.padding(vertical = 4.dp),
                text = "Submit",
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = image.ifEmpty { "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTwlQ-xYqAIcjylz3NUGJ_jcdRmdzk_vMae0w&s"  },
            contentDescription = "Thank you",
            modifier = Modifier.size(66.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = localContent["thankyouText"]!!,
            fontSize = ((csatDetails.styling?.fontSize ?: 16) + 6).sp,
            fontWeight = FontWeight.Bold,
            color = styling["csatTitleColor"]!!
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = localContent["thankyouDescription"]!!,
            fontSize = (csatDetails.styling?.fontSize ?: 16).sp,
            color = styling["csatDescriptionTextColor"]!!,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth(),
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
            colors = ButtonDefaults.buttonColors(
                containerColor = styling["csatCtaBackgroundColor"]!!
            )
        ) {
            Text(
                modifier = Modifier.padding(vertical = 4.dp),
                fontSize = ((csatDetails.styling?.fontSize ?: 16) + 2).sp,
                text = (if (selectedStars < 4) csatDetails.lowStarText else csatDetails.highStarText).toString(),
                color = styling["csatCtaTextColor"]!!
            )
        }
    }
}
package com.appversal.appstorys.presentation

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.appversal.appstorys.AppStorys.trackEvent
import com.appversal.appstorys.api.ApiService
import com.appversal.appstorys.api.CsatDetails
import com.appversal.appstorys.api.CsatFeedbackPostRequest
import com.appversal.appstorys.api.CsatStyling
import com.appversal.appstorys.domain.State
import com.appversal.appstorys.domain.State.getAccessToken
import com.appversal.appstorys.domain.State.userId
import com.appversal.appstorys.domain.model.TypedCampaign
import com.appversal.appstorys.domain.rememberCampaign
import com.appversal.appstorys.domain.rememberPadding
import com.appversal.appstorys.domain.usecase.trackEvent
import com.appversal.appstorys.utils.toColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive


@Composable
internal fun Csat(
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp
) {
    val campaign = rememberCampaign<CsatDetails>("CSAT")
    if (campaign != null) {
        Content(
            campaign,
            modifier,
            bottomPadding,
        )
    }
}

@Composable
private fun Content(
    campaign: TypedCampaign<CsatDetails>,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp
) {
    val context = LocalContext.current
    val styling = campaign.details.styling
    val scope = rememberCoroutineScope()

    var selectedStars by remember { mutableIntStateOf(0) }
    var isVisible by remember { mutableStateOf(false) }
    var showThanks by remember { mutableStateOf(false) }

    val bottomPadding =
        styling?.csatBottomPadding?.trim()?.toFloatOrNull()?.dp
            ?: rememberPadding("CSAT", PaddingValues(bottomPadding)).calculateBottomPadding()

    val onDismiss: () -> Unit = remember {
        {
            isVisible = false
            scope.launch {
                delay(500L)
                State.addDisabledCampaign(campaign.id)
            }
        }
    }

    LaunchedEffect(Unit) {
        trackEvent(context, "viewed", campaign.id)
        delay((styling?.displayDelay?.toLongOrNull() ?: 0L).times(1000))
        isVisible = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = bottomPadding),
        contentAlignment = Alignment.BottomCenter,
        content = {
            AnimatedVisibility(
                modifier = Modifier,
                visible = isVisible,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                content = {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = styling?.csatBackgroundColor.toColor(Color.White),
                        shadowElevation = 8.dp,
                        content = {
                            Box {
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp),
                                    content = {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close",
                                            tint = styling.titleColor
                                        )
                                    }
                                )

                                AnimatedVisibility(
                                    visible = !showThanks,
                                    enter = fadeIn(),
                                    exit = fadeOut(),
                                    content = {
                                        MainContent(
                                            campaign = campaign,
                                            selectedStars = selectedStars,
                                            setSelectedStars = { selectedStars = it },
                                            onDismiss = { showThanks = true }
                                        )
                                    }
                                )

                                AnimatedVisibility(
                                    visible = !campaign.details.thankYouImage.isNullOrBlank() && showThanks,
                                    enter = fadeIn(),
                                    exit = fadeOut(),
                                    content = {
                                        ThankYouContent(
                                            campaign = campaign,
                                            onDone = onDismiss,
                                            selectedStars = selectedStars
                                        )
                                    }
                                )
                            }
                        }
                    )
                }
            )
        }
    )
}

@Composable
private fun MainContent(
    campaign: TypedCampaign<CsatDetails>,
    selectedStars: Int,
    modifier: Modifier = Modifier,
    setSelectedStars: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val details = campaign.details
    val styling = details.styling
    val scope = rememberCoroutineScope()

    var selectedOption by remember { mutableStateOf<String?>(null) }
    var additionalComments by remember { mutableStateOf("") }
    var showFeedback by remember { mutableStateOf(false) }

    val feedbackOptions = remember(details) {
        details.feedbackOptions?.mapNotNull {
            it.value.jsonPrimitive.contentOrNull
        }?.filter {
            it.isNotBlank()
        } ?: emptyList()
    }

    val handleSubmitFeedback: () -> Unit = {
        scope.launch {
            submitFeedback(
                campaign,
                selectedStars,
                selectedOption,
                additionalComments,
                onDismiss
            )
        }
    }

    Column(
        modifier = modifier.padding(24.dp),
        content = {
            Text(
                text = details.title.orEmpty(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = styling.titleColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = details.descriptionText.orEmpty(),
                color = styling?.csatDescriptionTextColor.toColor(
                    Color(0xFF504F58)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    8.dp,
                    Alignment.Start
                ),
                content = {
                    repeat(5) { index ->
                        Icon(
                            modifier = Modifier
                                .size(40.dp)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = {
                                        setSelectedStars(index)
                                        when {
                                            index >= 4 -> handleSubmitFeedback()
                                            else -> showFeedback = true
                                        }
                                    }
                                ),
                            imageVector = Icons.Default.Star,
                            contentDescription = "Star ${index + 1}",
                            tint = when {
                                index >= selectedStars -> styling?.csatUnselectedStarColor
                                selectedStars >= 4 -> styling?.csatHighStarColor
                                else -> styling?.csatLowStarColor
                            }.toColor(),
                        )
                    }
                }
            )

            AnimatedVisibility(
                visible = showFeedback,
                content = {
                    Column(
                        modifier = Modifier.padding(top = 16.dp),
                        content = {
                            styling?.csatFeedbackTitleText?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    text = it,
                                    color = styling.titleColor
                                )
                            }

                            if (feedbackOptions.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            feedbackOptions.forEach { option ->
                                val isSelected = option == selectedOption
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .border(
                                            width = 1.dp,
                                            color = when {
                                                isSelected -> styling?.csatSelectedOptionStrokeColor.toColor()

                                                else -> styling?.csatOptionStrokeColor.toColor(
                                                    Color(0xFFCCCCCC)
                                                )
                                            },
                                            shape = RoundedCornerShape(24.dp)
                                        )
                                        .clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() },
                                            onClick = {
                                                selectedOption = option
                                            }
                                        ),

                                    color = when {
                                        isSelected -> styling?.csatSelectedOptionBackgroundColor.toColor(
                                            Color(0xFFE3F2FD)
                                        )

                                        else -> styling?.csatOptionBoxColour.toColor(
                                            Color(0xFF007AFF)
                                        )
                                    },
                                    shape = RoundedCornerShape(24.dp),
                                    content = {
                                        Text(
                                            text = option,
                                            modifier = Modifier.padding(12.dp),

                                            color = when {
                                                isSelected -> styling?.csatSelectedOptionTextColor.toColor(
                                                    Color(0xFF007AFF)
                                                )

                                                else -> styling?.csatOptionTextColour.toColor()
                                            },
                                        )
                                    }
                                )
                            }

                            if (feedbackOptions.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            TextField(
                                value = additionalComments,
                                onValueChange = {
                                    additionalComments = it
                                },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text(
                                        "Enter comments",
                                        color = Color.Gray
                                    )
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = styling?.csatAdditionalTextColor.toColor(),
                                    unfocusedTextColor = Color.Black,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent
                                )
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            Button(
                                onClick = handleSubmitFeedback,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = styling?.csatCtaBackgroundColor.toColor(
                                        Color(0xFF007AFF)
                                    )
                                ),
                                content = {
                                    Text(
                                        text = "Submit",
                                        color = styling?.csatCtaTextColor.toColor(Color.White)
                                    )
                                }
                            )
                        }
                    )
                }
            )
        }
    )
}

@Composable
private fun ThankYouContent(
    campaign: TypedCampaign<CsatDetails>,
    selectedStars: Int,
    onDone: () -> Unit
) {
    val context = LocalContext.current

    val details = campaign.details
    val styling = details.styling

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = {
            AsyncImage(
                model = details.thankYouImage?.ifEmpty {
                    "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTwlQ-xYqAIcjylz3NUGJ_jcdRmdzk_vMae0w&s"
                },
                contentDescription = "Thank you",
                modifier = Modifier.size(66.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = details.thankYouText.orEmpty(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = styling.titleColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = details.thankYouDescription.orEmpty(),
                color = styling?.csatDescriptionTextColor.toColor(Color(0xFF504F58)),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (details.link.isNullOrEmpty() || selectedStars < 4) {
                        onDone()
                    } else {
                        try {
                            trackEvent(details.campaign, "clicked")
                            val uri = details.link.toUri()
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        } catch (_: Exception) {
                            Toast.makeText(
                                context,
                                "Could not open link",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = styling?.csatCtaBackgroundColor.toColor(Color(0xFF007AFF))
                ),
                content = {
                    Text(
                        text = when {
                            selectedStars < 4 -> details.lowStarText
                            else -> details.highStarText
                        }.orEmpty(),
                        color = styling?.csatCtaTextColor.toColor(Color.White)
                    )
                }
            )
        }
    )
}

private val CsatStyling?.titleColor: Color
    get() = this?.csatTitleColor.toColor()

private suspend fun submitFeedback(
    campaign: TypedCampaign<CsatDetails>,
    selectedStars: Int,
    selectedOption: String?,
    additionalComments: String,
    onDismiss: () -> Unit
) = withContext(Dispatchers.IO) {
    val token = getAccessToken()
    if (!token.isNullOrBlank()) {
        launch {
            delay(1000)
            onDismiss()
        }
        ApiService.getInstance().sendCsatResponse(
            token,
            CsatFeedbackPostRequest(
                user_id = userId.value,
                csat = campaign.details.id,
                rating = selectedStars,
                additional_comments = additionalComments,
                feedback_option = selectedOption
            )
        )
        trackEvent(
            campaignId = campaign.id,
            event = "csat captured",
            metadata = mapOf(
                "starCount" to selectedStars,
                "selectedOption" to selectedOption.orEmpty(),
                "additionalComments" to additionalComments
            )
        )
    }
}


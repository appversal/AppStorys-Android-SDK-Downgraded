package com.appversal.appstorys.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.appversal.appstorys.api.ApiService
import com.appversal.appstorys.api.SurveyDetails
import com.appversal.appstorys.api.SurveyFeedbackPostRequest
import com.appversal.appstorys.api.SurveyStyling
import com.appversal.appstorys.domain.State
import com.appversal.appstorys.domain.model.TypedCampaign
import com.appversal.appstorys.domain.rememberCampaign
import com.appversal.appstorys.domain.usecase.launchTask
import com.appversal.appstorys.domain.usecase.trackEvent
import com.appversal.appstorys.utils.toColor


@Composable
internal fun Survey(
    modifier: Modifier = Modifier,
) {
    val campaign = rememberCampaign<SurveyDetails>("SUR")
    val options = remember(campaign?.details) {
        buildList {
            campaign?.details?.options?.forEach { (id, name) ->
                add(id to name)
            }
            if (campaign?.details?.hasOthers == true) {
                val nextOptionId = String(charArrayOf((65 + size).toChar()))
                add(nextOptionId to "Others")
            }
        }.filter {
            it.first.isNotBlank() && it.second.isNotBlank()
        }
    }

    if (campaign != null && options.isNotEmpty()) {
        Content(campaign, options, modifier)
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Content(
    campaign: TypedCampaign<SurveyDetails>,
    options: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val details = campaign.details
    val styling = details.styling

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    val selectedOptions = remember { mutableStateListOf<String>() }
    var othersText by remember { mutableStateOf("") }

    val handleDismiss = remember {
        {
            State.addDisabledCampaign(campaign.id)
        }
    }
    val handleSubmit = remember {
        {
            val finalOptions = selectedOptions.filter { it != "Others" }

            val commentText = othersText.takeIf {
                selectedOptions.contains("Others") && it.isNotBlank()
            }.orEmpty()

            launchTask {
                ApiService.getInstance().sendSurveyResponse(
                    SurveyFeedbackPostRequest(
                        responseOptions = finalOptions,
                        survey = campaign.id,
                        comment = commentText,
                    )
                )
                trackEvent(
                    context,
                    "survey captured",
                    campaign.id,
                    mapOf(
                        "selectedOptions" to finalOptions.joinToString(", "),
                        "otherText" to commentText
                    )
                )
            }

            handleDismiss()
        }
    }

    LaunchedEffect(Unit) {
        trackEvent(context, "viewed", campaign.id)
    }

    ModalBottomSheet(
        modifier = modifier,
        onDismissRequest = handleDismiss,
        containerColor = styling?.backgroundColor.toColor(),
        dragHandle = {},
        sheetState = sheetState,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                content = {
                    // Header with title and close button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        content = {
                            Text(
                                text = details.name ?: "Survey",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    color = styling?.surveyTextColor.toColor(),
                                    fontWeight = FontWeight.Medium
                                ),
                                modifier = Modifier.align(Alignment.Center)
                            )

                            IconButton(
                                onClick = handleDismiss,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .background(
                                        styling?.ctaBackgroundColor.toColor(),
                                        CircleShape
                                    )
                                    .size(32.dp),
                                content = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = styling?.ctaTextIconColor.toColor(),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = details.question ?: "Survey Question",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = styling?.surveyQuestionColor.toColor(),
                            fontWeight = FontWeight.Bold
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Survey options
                    LazyColumn {
                        items(options) { (id, name) ->
                            Item(
                                id = id,
                                name = name,
                                isSelected = selectedOptions.contains(name),
                                styling = styling,
                                onClick = {
                                    if (selectedOptions.contains(name)) {
                                        selectedOptions.remove(name)
                                    } else {
                                        selectedOptions.add(name)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    if (selectedOptions.contains("Others")) {
                        OutlinedTextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            value = othersText,
                            onValueChange = {
                                othersText = it.take(200)
                            },
                            placeholder = {
                                Text(
                                    "Please enter Others text up to 200 chars",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.Black,
                                        fontWeight = FontWeight.Light
                                    )
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = styling?.othersBackgroundColor.toColor(),
                                unfocusedBorderColor = styling?.othersBackgroundColor.toColor(),
                                focusedContainerColor = styling?.othersBackgroundColor.toColor(),
                                unfocusedContainerColor = styling?.othersBackgroundColor.toColor(),
                                focusedTextColor = styling?.othersTextColor.toColor(),
                                unfocusedTextColor = styling?.othersTextColor.toColor(),
                                cursorColor = styling?.othersTextColor.toColor()
                            ),
                            shape = RoundedCornerShape(8.dp),
                            maxLines = 1,
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Button(
                        enabled = selectedOptions.isNotEmpty(),
                        onClick = handleSubmit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = styling?.ctaBackgroundColor.toColor()
                        ),
                        shape = RoundedCornerShape(12.dp),
                        content = {
                            Text(
                                text = "SUBMIT",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = styling?.ctaTextIconColor.toColor(),
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            )
        }
    )
}

@Composable
private fun Item(
    id: String,
    name: String,
    isSelected: Boolean,
    styling: SurveyStyling?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> styling?.selectedOptionColor
                else -> styling?.optionColor
            }.toColor()
        ),
        shape = RoundedCornerShape(12.dp),
        content = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = {
                    Box(
                        modifier = Modifier
                            .background(Color.White, RoundedCornerShape(18.dp))
                            .border(0.8.dp, Color.Black, RoundedCornerShape(18.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        content = {
                            Text(
                                text = id,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.Black,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    )

                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = when {
                                isSelected -> styling?.selectedOptionTextColor
                                else -> styling?.optionTextColor
                            }.toColor()
                        )
                    )
                }
            )
        }
    )
}
package com.appversal.appstorys.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.core.graphics.toColorInt
import com.appversal.appstorys.api.SurveyDetails
import com.appversal.appstorys.api.SurveyStyling
import com.appversal.appstorys.ui.common_components.CTAButton
import com.appversal.appstorys.ui.common_components.CrossButton
import com.appversal.appstorys.ui.common_components.createCTAButtonConfig
import com.appversal.appstorys.ui.common_components.createCrossButtonConfig

data class SurveyFeedback(
    val responseOptions: List<String>? = null,
    val comment: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyBottomSheet(
    onSubmitFeedback: (SurveyFeedback) -> Unit,
    onDismissRequest: () -> Unit,
    surveyDetails: SurveyDetails,
) {
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            sheetState.expand()
        }
    }

    val backgroundColor = Color(surveyDetails.styling?.backgroundColor!!.toColorInt())

    var selectedOptions by remember { mutableStateOf(setOf<String>()) }
    var showInputBox by remember { mutableStateOf(false) }
    var othersText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
//        shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius),
        containerColor = backgroundColor,
        dragHandle = {},
        sheetState = sheetState
    ) {
        SurveyContent(
            surveyDetails = surveyDetails,
            selectedOptions = selectedOptions,
            showInputBox = showInputBox,
            othersText = othersText,
            onOptionSelected = { option ->
                selectedOptions = if (selectedOptions.contains(option)) {
                    selectedOptions - option
                } else {
                    selectedOptions + option
                }
                showInputBox = selectedOptions.contains("Others")
            },
            onOthersTextChanged = { text ->
                othersText = text
            },
            onSubmit = {
                val finalOptions = selectedOptions
                    .filter { it != "Others" }

                val commentText = if (showInputBox && othersText.isNotBlank()) {
                    othersText
                } else {
                    ""
                }

                onSubmitFeedback(
                    SurveyFeedback(
                        responseOptions = finalOptions.toList(),
                        comment = commentText
                    )
                )
                onDismissRequest()
            },
            onClose = onDismissRequest
        )
    }
}

@Composable
private fun SurveyContent(
    surveyDetails: SurveyDetails,
    selectedOptions: Set<String>,
    showInputBox: Boolean,
    othersText: String,
    onOptionSelected: (String) -> Unit,
    onOthersTextChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit
) {
    // Create survey options list
    val surveyOptions = surveyDetails.surveyOptions!!.map { (id, name) ->
        SurveyOption(id, name)
    }.toMutableList()

    // Add "Others" option if enabled
    if (surveyDetails.hasOthers ?: false) {
        val nextOptionId = String(charArrayOf((65 + surveyOptions.size).toChar()))
        surveyOptions.add(SurveyOption(nextOptionId, "Others"))
    }

    val crossConfig = createCrossButtonConfig(
        fillColorString = surveyDetails.styling?.ctaBackgroundColor,
        crossColorString = surveyDetails.styling?.ctaTextIconColor,
        size = 32
    )

    val ctaConfig = createCTAButtonConfig(
        backgroundColorString = surveyDetails.styling?.ctaBackgroundColor,
        textColor = surveyDetails.styling?.ctaTextIconColor,
        textSize = 16,
        height = 56,
        fullWidth = true,
        borderRadiusTopLeft = 12,
        borderRadiusTopRight = 12,
        borderRadiusBottomLeft = 12,
        borderRadiusBottomRight = 12
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        // Header with title and close button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(
                text = surveyDetails.name ?: "Survey",
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = Color(surveyDetails.styling?.surveyTextColor!!.toColorInt()),
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.align(Alignment.Center)
            )

            CrossButton(
                modifier = Modifier.align(Alignment.CenterEnd),
                config = crossConfig,
                onClose = onClose
            )

        }

        Spacer(modifier = Modifier.height(12.dp))

        // Survey question
        Text(
            text = surveyDetails.surveyQuestion ?: "Survey Question",
            style = MaterialTheme.typography.bodyLarge.copy(
                color = Color(surveyDetails.styling?.surveyQuestionColor!!.toColorInt()),
                fontWeight = FontWeight.Bold
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Survey options
        LazyColumn {
            items(surveyOptions) { option ->
                if (option.id.isNotEmpty() && option.name.isNotEmpty()) {
                    SurveyOptionItem(
                        option = option,
                        isSelected = selectedOptions.contains(option.name),
                        styling = surveyDetails.styling,
                        onOptionClick = { onOptionSelected(option.name) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        // Others input box
        if (showInputBox) {
            OutlinedTextField(
                value = othersText,
                onValueChange = onOthersTextChanged,
                placeholder = {
                    Text(
                        "Please enter Others text…..upto 200 chars",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.Black,
                            fontWeight = FontWeight.Light
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(surveyDetails.styling.othersBackgroundColor!!.toColorInt()),
                    unfocusedBorderColor = Color(surveyDetails.styling.othersBackgroundColor.toColorInt()),
                    focusedContainerColor = Color(surveyDetails.styling.othersBackgroundColor.toColorInt()),
                    unfocusedContainerColor = Color(surveyDetails.styling.othersBackgroundColor.toColorInt()),
                    focusedTextColor = Color(surveyDetails.styling.othersTextColor!!.toColorInt()),
                    unfocusedTextColor = Color(surveyDetails.styling.othersTextColor.toColorInt()),
                    cursorColor = Color(surveyDetails.styling.othersTextColor.toColorInt())
                ),
                shape = RoundedCornerShape(8.dp),
                maxLines = 1,
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Submit button
        CTAButton(
            text = "SUBMIT",
            config = ctaConfig,
            onClick = {
                if (selectedOptions.isNotEmpty()) {
                    onSubmit()
                }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SurveyOptionItem(
    option: SurveyOption,
    isSelected: Boolean,
    styling: SurveyStyling,
    onOptionClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onOptionClick()
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                Color(styling.selectedOptionColor!!.toColorInt())
            } else {
                Color(styling.optionColor!!.toColorInt())
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Option ID badge
            Box(
                modifier = Modifier
                    .background(
                        Color.White,
                        RoundedCornerShape(18.dp)
                    )
                    .border(
                        0.8.dp,
                        Color.Black,
                        RoundedCornerShape(18.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = option.id,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.Black,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Option text
            Text(
                text = option.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = if (isSelected) {
                        Color(styling.selectedOptionTextColor!!.toColorInt())
                    } else {
                        Color(styling.optionTextColor!!.toColorInt())
                    }
                )
            )
        }
    }
}

data class SurveyOption(
    val id: String,
    val name: String
)
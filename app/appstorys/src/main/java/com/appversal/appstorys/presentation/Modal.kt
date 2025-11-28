package com.appversal.appstorys.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.appversal.appstorys.api.Modal
import com.appversal.appstorys.api.ModalDetails
import com.appversal.appstorys.domain.State
import com.appversal.appstorys.domain.model.TypedCampaign
import com.appversal.appstorys.domain.rememberCampaign
import com.appversal.appstorys.domain.usecase.ClickEvent
import com.appversal.appstorys.domain.usecase.trackEvent


@Composable
internal fun Modal(
    modifier: Modifier = Modifier,
) {
    val campaign = rememberCampaign<ModalDetails>("MOD")
    val modals = campaign?.details?.modals

    if (campaign != null && !modals.isNullOrEmpty()) {
        Content(
            modifier = modifier,
            campaign = campaign,
            modals = modals
        )
    }
}

@Composable
private fun Content(
    campaign: TypedCampaign<ModalDetails>,
    modals: List<Modal>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var currentIndex by remember { mutableIntStateOf(0) }
    val modal = modals.getOrNull(currentIndex)
    val image = modal?.url

    val handleClose: () -> Unit = remember(currentIndex, modals, campaign.id) {
        {
            if (currentIndex < modals.size - 1) {
                currentIndex++
            } else {
                State.addDisabledCampaign(campaign.id)
            }
        }
    }

    LaunchedEffect(Unit) {
        trackEvent(context, "viewed", campaign.id)
    }

    Dialog(
        onDismissRequest = handleClose,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        content = {
            Box(
                modifier = modifier
                    .wrapContentSize()
                    .background(
                        Color.Black.copy(
                            alpha = modal?.backgroundOpacity?.toFloat() ?: 0.3f
                        )
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = handleClose
                    ),
                contentAlignment = Alignment.Center,
                content = {
                    Box(
                        modifier = Modifier
                            .wrapContentSize()
                            .padding(8.dp)
                            .clickable {
                                ClickEvent(context, modal?.link, campaign.id)
                                handleClose()
                            },
                        content = {
                            if (!image.isNullOrBlank()) {
                                SdkImage(
                                    image = image,
                                    isLottie = image.endsWith(".json", ignoreCase = true),
                                    width = modal.size?.toFloatOrNull()?.dp ?: 100.dp,
                                    shape = RoundedCornerShape(
                                        modal.borderRadius?.toFloatOrNull()?.dp
                                            ?: 12.dp
                                    )
                                )
                            }
                        }
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black)
                            .clickable(onClick = handleClose),
                        contentAlignment = Alignment.Center,
                        content = {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            )
        }
    )
}


package com.appversal.appstorys.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.appversal.appstorys.api.FloaterDetails
import com.appversal.appstorys.domain.model.TypedCampaign
import com.appversal.appstorys.domain.rememberCampaign
import com.appversal.appstorys.domain.rememberPadding
import com.appversal.appstorys.domain.usecase.ClickEvent
import com.appversal.appstorys.domain.usecase.trackEvent
import com.appversal.appstorys.utils.ifNullOrBlank

@Composable
internal fun Floater(
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp
) {
    val campaign = rememberCampaign<FloaterDetails>("FLT")
    if (campaign != null && !campaign.details.image.isNullOrBlank()) {
        Content(
            campaign = campaign,
            modifier = modifier,
            bottomPadding = rememberPadding(
                "FLT",
                PaddingValues(bottomPadding)
            ).calculateBottomPadding()
        )
    }
}

@Composable
private fun Content(
    campaign: TypedCampaign<FloaterDetails>,
    modifier: Modifier,
    bottomPadding: Dp,
) {
    val context = LocalContext.current
    val details = campaign.details
    val styling = details.styling

    val height = details.height?.dp ?: 60.dp
    val width = details.width?.dp ?: 60.dp

    val shape = RoundedCornerShape(
        topStart = (styling?.topLeftRadius?.toFloatOrNull() ?: 0f).dp,
        topEnd = (styling?.topRightRadius?.toFloatOrNull() ?: 0f).dp,
        bottomStart = (styling?.bottomLeftRadius?.toFloatOrNull() ?: 0f).dp,
        bottomEnd = (styling?.bottomRightRadius?.toFloatOrNull() ?: 0f).dp
    )

    LaunchedEffect(campaign.id) {
        trackEvent(context, "viewed", campaign.id)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(
                bottom = styling?.floaterBottomPadding?.toFloatOrNull()?.dp
                    ?: bottomPadding,
                start = styling?.floaterLeftPadding?.toFloatOrNull()?.dp ?: 0.dp,
                end = styling?.floaterRightPadding?.toFloatOrNull()?.dp ?: 0.dp,
            ),
        content = {
            Surface(
                modifier = modifier
                    .align(
                        when (details.position) {
                            "right" -> Alignment.BottomEnd
                            "left" -> Alignment.BottomStart
                            else -> Alignment.BottomStart
                        }
                    )
                    .padding(16.dp)
                    .height(height)
                    .width(width)
                    .clip(shape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (details.link != null) {
                                ClickEvent(context, link = details.link, campaignId = campaign.id)
                            }
                        }
                    ),
                color = Color.Transparent,
                shape = shape,
                content = {
                    val image = details.lottie_data.ifNullOrBlank {
                        details.image
                    }
                    if (!image.isNullOrBlank()) {
                        SdkImage(
                            image = image,
                            width = width,
                            height = height,
                            shape = shape,
                            isLottie = !details.lottie_data.isNullOrBlank(),
                        )
                    }
                }
            )
        }
    )
}

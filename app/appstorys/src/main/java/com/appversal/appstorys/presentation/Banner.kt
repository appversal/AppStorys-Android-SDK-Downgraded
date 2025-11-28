package com.appversal.appstorys.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.appversal.appstorys.api.BannerDetails
import com.appversal.appstorys.domain.State
import com.appversal.appstorys.domain.model.TypedCampaign
import com.appversal.appstorys.domain.rememberCampaign
import com.appversal.appstorys.domain.rememberPadding
import com.appversal.appstorys.domain.usecase.ClickEvent
import com.appversal.appstorys.domain.usecase.trackEvent
import com.appversal.appstorys.utils.ifNullOrBlank


@Composable
internal fun Banner(
    modifier: Modifier = Modifier,
    placeholder: Placeholder? = null,
    bottomPadding: Dp = 0.dp,
) {
    val campaign = rememberCampaign<BannerDetails>("BAN")
    val image = campaign?.details?.lottie_data.ifNullOrBlank {
        campaign?.details?.image
    }
    if (campaign != null && !image.isNullOrBlank()) {
        Content(
            campaign = campaign,
            image = image,
            modifier = modifier,
            placeholder = placeholder,
            bottomPadding = rememberPadding(
                "BAN",
                PaddingValues(bottomPadding)
            ).calculateBottomPadding(),
        )
    }
}

@Composable
private fun Content(
    campaign: TypedCampaign<BannerDetails>,
    image: String,
    bottomPadding: Dp,
    placeholder: Placeholder?,
    modifier: Modifier,
) {
    val details = campaign.details
    val styling = details.styling

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    val height = when {
        details.width != null && details.height != null -> {
            val aspectRatio = details.height.toFloat() / details.width.toFloat()

            val marginLeft = styling?.marginLeft?.dp ?: 0.dp
            val marginRight = styling?.marginRight?.dp ?: 0.dp

            val actualWidth = screenWidth - marginLeft - marginRight

            (actualWidth.value * aspectRatio).dp
        }

        else -> details.height?.dp
    } ?: Dp.Unspecified
    val width = details.width?.dp ?: screenWidth

    val shape = RoundedCornerShape(
        topStart = styling?.topLeftRadius?.dp ?: 0.dp,
        topEnd = styling?.topRightRadius?.dp ?: 0.dp,
        bottomEnd = styling?.bottomRightRadius?.dp ?: 0.dp,
        bottomStart = styling?.bottomLeftRadius?.dp ?: 0.dp
    )

    val contentScale = ContentScale.Fit

    LaunchedEffect(campaign.id) {
        trackEvent(context, "viewed", campaign.id)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = bottomPadding),
        content = {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .clickable {
                        ClickEvent(context, link = details.link, campaignId = campaign.id)
                    }
                    .padding(
                        bottom = styling?.marginBottom?.dp ?: 0.dp,
                        start = styling?.marginLeft?.dp ?: 0.dp,
                        end = styling?.marginRight?.dp ?: 0.dp
                    ),
                shape = shape,
                content = {
                    Box(
                        content = {
                            SdkImage(
                                image = image,
                                width = width,
                                height = height,
                                shape = shape,
                                isLottie = !details.lottie_data.isNullOrBlank(),
                                placeholder = placeholder,
                                contentScale = contentScale
                            )

                            if (styling?.enableCloseButton == true) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x4D000000))
                                        .clickable {
                                            State.addDisabledCampaign(campaign.id)
                                        },
                                    contentAlignment = Alignment.Center,
                                    content = {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
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


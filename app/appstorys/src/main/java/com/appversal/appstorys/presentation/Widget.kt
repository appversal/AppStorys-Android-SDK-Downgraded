package com.appversal.appstorys.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.appversal.appstorys.AppStorys.DoubleWidget
import com.appversal.appstorys.AppStorys.trackEvent
import com.appversal.appstorys.api.WidgetDetails
import com.appversal.appstorys.domain.model.TypedCampaign
import com.appversal.appstorys.domain.rememberCampaign
import com.appversal.appstorys.domain.usecase.ClickEvent
import com.appversal.appstorys.ui.AutoSlidingCarousel
import com.appversal.appstorys.ui.CarousalImage

@Composable
internal fun Widget(
    modifier: Modifier = Modifier,
    placeholder: Placeholder? = null,
    position: String? = null
) {
    val campaign = rememberCampaign<WidgetDetails>("WID", position)
    when {
        campaign?.details?.type == "full" && !campaign.details.widgetImages.isNullOrEmpty() -> FullContent(
            modifier = modifier,
            campaign = campaign,
            placeholder = placeholder,
            contentScale = ContentScale.FillWidth,
        )

        campaign?.details?.type == "half" -> DoubleWidget(
            modifier = modifier,
            staticWidth = LocalConfiguration.current.screenWidthDp.dp,
            position = position,
            placeHolder = placeholder,
        )
    }
}


@Composable
private fun FullContent(
    campaign: TypedCampaign<WidgetDetails>,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.FillWidth,
    placeholder: Placeholder? = null,
) {
    val details = campaign.details
    val images = details.widgetImages ?: emptyList()

    var isVisible by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    val pagerState = rememberPagerState(pageCount = {
        images.size
    })

    val width: Dp? = details.width?.dp

    val calculatedHeight = if (details.width != null && details.height != null) {
        val aspectRatio = details.height.toFloat() / details.width.toFloat()

        val marginLeft = details.styling?.leftMargin?.toFloatOrNull()?.dp ?: 0.dp
        val marginRight =
            details.styling?.rightMargin?.toFloatOrNull()?.dp ?: 0.dp

        val actualWidth = screenWidth - marginLeft - marginRight
        (actualWidth.value.minus(
            32
            // for the new widget
//                            +26
        ) * aspectRatio).dp
    } else {
        details.height?.dp
    }

    LaunchedEffect(pagerState.currentPage, isVisible) {
        if (isVisible) {
            return@LaunchedEffect
        }
        val id = images.getOrNull(pagerState.currentPage)?.id ?: return@LaunchedEffect

        trackEvent(
            campaign.id,
            "viewed",
            mapOf("widget_image" to id)
        )
    }

    AutoSlidingCarousel(
        modifier = modifier
            .padding(
                top = (widgetDetails.styling?.topMargin?.toFloatOrNull() ?: 0f).dp,
                bottom = (widgetDetails.styling?.bottomMargin?.toFloatOrNull() ?: 0f).dp,
                start = (widgetDetails.styling?.leftMargin?.toFloatOrNull() ?: 0f).dp,
                end = (widgetDetails.styling?.rightMargin?.toFloatOrNull() ?: 0f).dp,
            )
            .onGloballyPositioned { layoutCoordinates ->
                val visibilityRect = layoutCoordinates.boundsInWindow()
                val parentHeight =
                    layoutCoordinates.parentLayoutCoordinates?.size?.height ?: 0
                val widgetHeight = layoutCoordinates.size.height
                val isAtLeastHalfVisible = visibilityRect.top < parentHeight &&
                        visibilityRect.bottom > 0 &&
                        (visibilityRect.height >= widgetHeight * 0.5f)

                isVisible = isAtLeastHalfVisible
            },
        widgetDetails = widgetDetails,
        pagerState = pagerState,
        itemsCount = widgetDetails.widgetImages.count(),
        width = staticWidth,
        itemContent = { index ->
            widgetDetails.widgetImages[index].image?.let {
                CarousalImage(
                    modifier = modifier.clickable {
                        ClickEvent(
                            link = widgetDetails.widgetImages[index].link,
                            campaignId = campaign.id,
                            widgetImageId = widgetDetails.widgetImages[index].id
                        )
                    },
                    contentScale = contentScale,
                    imageUrl = widgetDetails.widgetImages[index].image ?: "",
                    lottieUrl = widgetDetails.widgetImages[index].lottie_data ?: "",
                    placeHolder = placeHolder,
                    height = calculatedHeight,
                    width = width ?: staticWidth,
                    placeholderContent = placeholderContent
                )
            }
        }
    )
}
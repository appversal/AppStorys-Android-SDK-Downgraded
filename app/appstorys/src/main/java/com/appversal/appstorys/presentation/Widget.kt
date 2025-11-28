package com.appversal.appstorys.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.appversal.appstorys.api.WidgetDetails
import com.appversal.appstorys.api.WidgetStyling
import com.appversal.appstorys.domain.model.TypedCampaign
import com.appversal.appstorys.domain.rememberCampaign
import com.appversal.appstorys.domain.usecase.ClickEvent
import com.appversal.appstorys.domain.usecase.trackEvent
import com.appversal.appstorys.utils.ifNullOrBlank
import kotlinx.coroutines.delay

@Composable
internal fun Widget(
    modifier: Modifier = Modifier,
    placeholder: Placeholder? = null,
    position: String? = null
) {
    val campaign = rememberCampaign<WidgetDetails>("WID", position)
    val hasImages = !campaign?.details?.images.isNullOrEmpty()

    when {
        hasImages && campaign?.details?.type == "full" -> FullContent(
            modifier = modifier,
            campaign = campaign,
            placeholder = placeholder,
            contentScale = ContentScale.FillWidth,
        )

        hasImages && campaign?.details?.type == "half" -> DoubleContent(
            modifier = modifier,
            campaign = campaign,
            placeholder = placeholder,
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
    val context = LocalContext.current

    val details = campaign.details
    val styling = details.styling
    val images = remember(details.images) {
        details.images?.sortedBy { it.order } ?: emptyList()
    }

    var isVisible by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    val pagerState = rememberPagerState(pageCount = {
        images.size
    })

    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    val width: Dp? = details.width?.dp
    val height = if (details.width != null && details.height != null) {
        val aspectRatio = details.height.toFloat() / details.width.toFloat()

        val marginLeft = styling?.leftMargin?.toFloatOrNull()?.dp ?: 0.dp
        val marginRight = styling?.rightMargin?.toFloatOrNull()?.dp ?: 0.dp

        val actualWidth = screenWidth - marginLeft - marginRight
        (actualWidth.value.minus(
            32
            // for the new widget
//                            +26
        ) * aspectRatio).dp
    } else {
        details.height?.dp
    }

    if (!isDragged) {
        LaunchedEffect(
            key1 = Unit,
            block = {
                repeat(
                    times = Int.MAX_VALUE,
                    action = {
                        delay(AUTO_SLIDE_DURATION)
                        pagerState.animateScrollToPage(
                            page = (pagerState.currentPage + 1).mod(images.size)
                        )
                    }
                )
            }
        )
    }

    LaunchedEffect(pagerState.currentPage, isVisible) {
        if (isVisible) {
            return@LaunchedEffect
        }
        val id = images.getOrNull(pagerState.currentPage)?.id ?: return@LaunchedEffect

        trackEvent(
            context,
            "viewed",
            campaign.id,
            mapOf("widget_image" to id)
        )
    }

    Column(
        modifier = modifier
            .content(styling, onVisible = { isVisible = it })
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = {
            HorizontalPager(
                state = pagerState,
                pageContent = { page ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Transparent,
                        ),
                        shape = styling.shape,
                        content = {
                            val image = images[page].lottie_data.ifNullOrBlank {
                                images[page].image
                            }
                            if (!image.isNullOrBlank()) {
                                SdkImage(
                                    modifier = Modifier.clickable {
                                        ClickEvent(
                                            context,
                                            link = images[page].link,
                                            campaignId = campaign.id,
                                            widgetImageId = images[page].id
                                        )
                                    },
                                    image = image,
                                    width = width ?: LocalConfiguration.current.screenWidthDp.dp,
                                    height = height ?: Dp.Unspecified,
                                    shape = styling.shape,
                                    isLottie = !images[page].lottie_data.isNullOrBlank(),
                                    placeholder = placeholder,
                                    contentScale = contentScale
                                )
                            }
                        }
                    )
                }
            )
            if (images.size > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                DotsIndicator(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    size = images.size,
                    selectedIndex = if (isDragged) pagerState.currentPage else pagerState.targetPage,
                    dotSize = 8.dp,
                    selectedColor = Color.Black,
                    unselectedColor = Color.Gray,
                    selectedLength = 20.dp
                )
            }
        }
    )
}


@Composable
private fun DoubleContent(
    campaign: TypedCampaign<WidgetDetails>,
    modifier: Modifier = Modifier,
    placeholder: Placeholder? = null,
) {
    val context = LocalContext.current

    val details = campaign.details
    val styling = details.styling
    val images = remember(details.images) {
        details.images?.sortedBy { it.order }
            ?.windowed(2, 2, partialWindows = false) { (first, second) ->
                first to second
            } ?: emptyList()
    }

    var isVisible by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    val pagerState = rememberPagerState(pageCount = {
        images.size
    })
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()

    val width: Dp? = details.width?.dp
    val height = if (details.width != null && details.height != null) {
        val aspectRatio = details.height.toFloat() / details.width.toFloat()

        val marginLeft = styling?.leftMargin?.toFloatOrNull()?.dp ?: 0.dp
        val marginRight = styling?.rightMargin?.toFloatOrNull()?.dp ?: 0.dp

        val horizontalMargin = marginLeft + marginRight

        val actualWidth = screenWidth - horizontalMargin
        ((actualWidth.value.minus(12) * aspectRatio).div(2)).dp
    } else {
        (details.height?.minus(12))?.div(2)?.dp
    }

    LaunchedEffect(pagerState.currentPage, isVisible) {
        if (!isVisible) {
            return@LaunchedEffect
        }

        val page = images.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        page.first.id?.let {
            trackEvent(
                context,
                "viewed",
                campaign.id,
                mapOf("widget_image" to it)
            )
        }
        page.second.id?.let {
            trackEvent(
                context,
                campaign.id,
                "viewed",
                mapOf("widget_image" to it)
            )
        }
    }

    LaunchedEffect(!isDragged) {
        if (!isDragged) {
            while (true) {
                delay(AUTO_SLIDE_DURATION)
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % images.size)
            }
        }
    }

    Column(
        modifier = modifier
            .content(styling) { isVisible = it }
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = {
            HorizontalPager(
                state = pagerState,
                pageContent = { page ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Transparent,
                        ),
                        modifier = Modifier,
                        shape = styling.shape,
                        content = {
                            val (left, right) = images[page]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                content = {
                                    val leftImage = left.lottie_data.ifNullOrBlank {
                                        left.image
                                    }
                                    if (!leftImage.isNullOrBlank()) {
                                        SdkImage(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    ClickEvent(
                                                        context,
                                                        link = left.link,
                                                        campaignId = campaign.id,
                                                        widgetImageId = left.id
                                                    )
                                                },
                                            image = leftImage,
                                            height = height ?: Dp.Unspecified,
                                            shape = styling.shape,
                                            isLottie = !left.lottie_data.isNullOrBlank(),
                                            placeholder = placeholder,
                                        )
                                    }

                                    val rightImage = right.lottie_data.ifNullOrBlank {
                                        right.image
                                    }
                                    if (!rightImage.isNullOrBlank()) {
                                        SdkImage(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    ClickEvent(
                                                        context,
                                                        link = right.link,
                                                        campaignId = campaign.id,
                                                        widgetImageId = right.id
                                                    )
                                                },
                                            image = rightImage,
                                            height = height ?: Dp.Unspecified,
                                            shape = styling.shape,
                                            isLottie = !right.lottie_data.isNullOrBlank(),
                                            placeholder = placeholder,
                                        )
                                    }
                                }
                            )
                        }
                    )
                }
            )
            if (images.size > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                DotsIndicator(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()),
                    size = images.size,
                    selectedIndex = pagerState.currentPage % images.size,
                    dotSize = 8.dp,
                    selectedColor = Color.Black,
                    unselectedColor = Color.Gray,
                    selectedLength = 20.dp
                )
            }
        }
    )
}

@Composable
private fun DotsIndicator(
    modifier: Modifier = Modifier,
    size: Int,
    selectedIndex: Int,
    selectedColor: Color,
    unselectedColor: Color,
    dotSize: Dp,
    selectedLength: Dp
) {
    LazyRow(
        modifier = modifier
            .wrapContentWidth()
            .wrapContentHeight(),
        content = {
            items(size) { index ->
                val isSelected = index == selectedIndex
                val color: Color by animateColorAsState(
                    targetValue = if (isSelected) selectedColor else unselectedColor,
                    animationSpec = tween(
                        durationMillis = 300,
                    )
                )
                val width: Dp by animateDpAsState(
                    targetValue = if (isSelected) selectedLength else dotSize,
                    animationSpec = tween(
                        durationMillis = 300,
                    )
                )

                Canvas(
                    modifier = modifier
                        .size(
                            width = width,
                            height = dotSize,
                        ),
                    onDraw = {
                        drawRoundRect(
                            color = color,
                            topLeft = Offset.Zero,
                            size = Size(
                                width = width.toPx(),
                                height = dotSize.toPx(),
                            ),
                            cornerRadius = CornerRadius(
                                x = dotSize.toPx(),
                                y = dotSize.toPx(),
                            ),
                        )
                    }
                )

                if (index != size - 1) {
                    Spacer(modifier = Modifier.padding(horizontal = 2.dp))
                }
            }
        }
    )
}

private fun Modifier.content(styling: WidgetStyling?, onVisible: (Boolean) -> Unit) = then(
    this
        .padding(
            top = (styling?.topMargin?.toFloatOrNull() ?: 0f).dp,
            bottom = (styling?.bottomMargin?.toFloatOrNull() ?: 0f).dp,
            start = (styling?.leftMargin?.toFloatOrNull() ?: 0f).dp,
            end = (styling?.rightMargin?.toFloatOrNull() ?: 0f).dp,
        )
        .onGloballyPositioned { layoutCoordinates ->
            val rect = layoutCoordinates.boundsInWindow()
            val parentHeight = layoutCoordinates.parentLayoutCoordinates?.size?.height ?: 0
            val widgetHeight = layoutCoordinates.size.height
            // Considered visible if at least half of the widget is visible in the parent
            onVisible(
                rect.top < parentHeight &&
                        rect.bottom > 0 &&
                        (rect.height >= widgetHeight * 0.5f)
            )
        }
)

private val WidgetStyling?.shape: RoundedCornerShape
    get() = RoundedCornerShape(
        topStart = (this?.topLeftRadius?.toFloatOrNull() ?: 0f).dp,
        topEnd = (this?.topRightRadius?.toFloatOrNull() ?: 0f).dp,
        bottomStart = (this?.bottomLeftRadius?.toFloatOrNull() ?: 0f).dp,
        bottomEnd = (this?.bottomRightRadius?.toFloatOrNull() ?: 0f).dp
    )

private const val AUTO_SLIDE_DURATION = 5000L
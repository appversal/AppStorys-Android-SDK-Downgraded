package com.appversal.appstorys.ui

import android.graphics.drawable.Drawable
import android.os.Build.VERSION.SDK_INT
import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.appversal.appstorys.api.WidgetDetails
import com.appversal.appstorys.utils.isGifUrl

const val AUTO_SLIDE_DURATION = 5000L

@Composable
internal fun AutoSlidingCarousel(
    modifier: Modifier = Modifier,
    widgetDetails: WidgetDetails,
    autoSlideDuration: Long = AUTO_SLIDE_DURATION,
    autoScrollEnabled: Boolean = true,
    pagerState: PagerState,
    itemsCount: Int,
    itemContent: @Composable (index: Int) -> Unit,
    selectedColor: Color = Color.Black,
    unSelectedColor: Color = Color.Gray,
    selectedLength: Dp = 20.dp,
    dotSize: Dp = 8.dp,
    width: Dp? = null,
    useDots: Boolean? = true
) {
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    if (autoScrollEnabled && !isDragged) {
        LaunchedEffect(
            key1 = Unit,
            block = {
                repeat(
                    times = Int.MAX_VALUE,
                    action = {
                        delay(
                            timeMillis = autoSlideDuration
                        )
                        pagerState.animateScrollToPage(
                            page = (pagerState.currentPage + 1).mod(itemsCount)
                        )
                    }
                )
            }
        )
    }

    Column(
        modifier = modifier.then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
//            pageSpacing = 8.dp,
//            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
//            beyondViewportPageCount = 1,
        ) { page ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent,
                ),
                modifier = Modifier,
                shape = RoundedCornerShape(
                    topStart = (widgetDetails.styling?.topLeftRadius ?: 0).dp,
                    topEnd = (widgetDetails.styling?.topRightRadius ?: 0).dp,
                    bottomStart = (widgetDetails.styling?.bottomLeftRadius ?: 0).dp,
                    bottomEnd = (widgetDetails.styling?.bottomRightRadius ?: 0).dp,
                ),
            ) {
                itemContent(page)
            }
        }
        if (itemsCount > 1 && useDots == true) {
            Spacer(modifier = Modifier.height(12.dp))
            DotsIndicator(
                modifier = Modifier.padding(horizontal = 8.dp),
                totalDots = itemsCount,
                selectedIndex = if (isDragged) pagerState.currentPage else pagerState.targetPage,
                dotSize = dotSize,
                selectedColor = selectedColor,
                unSelectedColor = unSelectedColor,
                selectedLength = selectedLength
            )
        }
    }
}


@Composable
internal fun DotsIndicator(
    modifier: Modifier = Modifier,
    totalDots: Int,
    selectedIndex: Int,
    selectedColor: Color,
    unSelectedColor: Color,
    dotSize: Dp,
    selectedLength: Dp
) {
    LazyRow(
        modifier = modifier
            .wrapContentWidth()
            .wrapContentHeight()
    ) {
        items(totalDots) { index ->
            PageIndicatorView(
                isSelected = index == selectedIndex,
                selectedColor = selectedColor,
                defaultColor = unSelectedColor,
                defaultRadius = dotSize,
                selectedLength = selectedLength,
                animationDurationInMillis = 300
            )
            if (index != totalDots - 1) {
                Spacer(modifier = Modifier.padding(horizontal = 2.dp))
            }
        }
    }
}

@Composable
internal fun PageIndicatorView(
    isSelected: Boolean,
    selectedColor: Color,
    defaultColor: Color,
    defaultRadius: Dp,
    selectedLength: Dp,
    animationDurationInMillis: Int,
    modifier: Modifier = Modifier,
) {
    val color: Color by animateColorAsState(
        targetValue = if (isSelected) {
            selectedColor
        } else {
            defaultColor
        },
        animationSpec = tween(
            durationMillis = animationDurationInMillis,
        )
    )
    val width: Dp by animateDpAsState(
        targetValue = if (isSelected) {
            selectedLength
        } else {
            defaultRadius
        },
        animationSpec = tween(
            durationMillis = animationDurationInMillis,
        )
    )

    Canvas(
        modifier = modifier
            .size(
                width = width,
                height = defaultRadius,
            ),
    ) {
        drawRoundRect(
            color = color,
            topLeft = Offset.Zero,
            size = Size(
                width = width.toPx(),
                height = defaultRadius.toPx(),
            ),
            cornerRadius = CornerRadius(
                x = defaultRadius.toPx(),
                y = defaultRadius.toPx(),
            ),
        )
    }
}


@Composable
internal fun CarousalImage(
    modifier: Modifier = Modifier,
    imageUrl: String,
    lottieUrl: String?,
    contentScale: ContentScale = ContentScale.Crop,
    height: Dp?,
    width: Dp? = null,
    placeHolder: Drawable? = null,
    placeholderContent: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        when {
            !lottieUrl.isNullOrEmpty() -> {
                val composition by rememberLottieComposition(
                    spec = LottieCompositionSpec.Url(lottieUrl)
                )
                LottieAnimation(
                    composition = composition,
                    iterations = LottieConstants.IterateForever,
                    modifier = Modifier
                        .height(height ?: Dp.Unspecified)
                        .width(width ?: Dp.Unspecified)
                )
            }

            !imageUrl.isNullOrEmpty() -> {
                if (isGifUrl(imageUrl)) {
                    val imageLoader = ImageLoader.Builder(context)
                        .components {
                            if (SDK_INT >= 28) {
                                add(ImageDecoderDecoder.Factory())
                            } else {
                                add(GifDecoder.Factory())
                            }
                        }
                        .build()

                    val painter = rememberAsyncImagePainter(
                        ImageRequest.Builder(context)
                            .data(data = imageUrl).memoryCacheKey(imageUrl)
                            .diskCacheKey(imageUrl)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .crossfade(true)
                            .apply(block = { size(coil.size.Size.ORIGINAL) }).build(),
                        imageLoader = imageLoader
                    )

                    Image(
                        painter = painter,
                        contentDescription = null,
                        contentScale = contentScale,
                        modifier = modifier
                            .then(if (height != null) Modifier.height(height) else Modifier)
                            .then(if (width != null) Modifier.width(width) else Modifier)
                    )
                } else {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .memoryCacheKey(imageUrl)
                            .diskCacheKey(imageUrl)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = contentScale,
                        modifier = modifier
                            .then(if (height != null) Modifier.height(height) else Modifier)
                            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()),
                        loading = {
                            if (placeholderContent != null) {
                                Box(
                                    modifier = Modifier
                                        .then(if (height != null) Modifier.height(height) else Modifier)
                                        .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
                                ) {
                                    placeholderContent()
                                }
                            } else if (placeHolder != null) {
                                Image(
                                    painter = rememberAsyncImagePainter(placeHolder),
                                    contentDescription = null,
                                    contentScale = contentScale,
                                    modifier = Modifier
                                        .then(if (height != null) Modifier.height(height) else Modifier)
                                        .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
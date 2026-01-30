package com.appversal.appstorys.ui

import android.graphics.drawable.Drawable
import android.os.Build.VERSION.SDK_INT
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.appversal.appstorys.utils.isGifUrl
import kotlinx.coroutines.delay
import coil.ImageLoader
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

@Composable
internal fun DoubleWidgets(
    modifier: Modifier = Modifier,
    itemContent: @Composable (index: Int) -> Unit,
    pagerState: PagerState,
    itemsCount: Int,
    autoSlideDuration: Long = AUTO_SLIDE_DURATION,
    selectedColor: Color = Color.Black,
    unSelectedColor: Color = Color.Gray,
    selectedLength: Dp = 20.dp,
    dotSize: Dp = 8.dp,
    spacingBetweenImagesAndDots: Dp = 12.dp,
    width: Dp? = null
) {
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(!isDragged) {
        if (!isDragged) {
            while (true) {
                delay(autoSlideDuration)
                val nextPage = (pagerState.currentPage + 1) % itemsCount
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(state = pagerState) { page ->
            itemContent(page)

        }
        if (itemsCount > 1) {
            Spacer(modifier = Modifier.height(spacingBetweenImagesAndDots))
            DotsIndicator(
                modifier = Modifier.padding(horizontal = 8.dp).then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()),
                totalDots = itemsCount,
                selectedIndex = pagerState.currentPage % itemsCount,
                dotSize = dotSize,
                selectedColor = selectedColor,
                unSelectedColor = unSelectedColor,
                selectedLength = selectedLength
            )
        }
    }
}

@Composable
internal fun ImageCard(
    modifier: Modifier = Modifier,
    imageUrl: String,
    lottieUrl: String?,
    widgetDetails: WidgetDetails,
    height: Dp?,
    placeHolder: Drawable? = null,
    placeholderContent: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(
            topStart = (widgetDetails.styling?.topLeftRadius ?: 0).dp,
            topEnd = (widgetDetails.styling?.topRightRadius ?: 0).dp,
            bottomStart = (widgetDetails.styling?.bottomLeftRadius ?: 0).dp,
            bottomEnd = (widgetDetails.styling?.bottomRightRadius ?: 0).dp,
        ),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = modifier
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
                        .then(if (height != null) Modifier.height(height) else Modifier)
                        .fillMaxWidth()
                )
            }

            isGifUrl(imageUrl) -> {
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
                        .data(data = imageUrl)
                        .memoryCacheKey(imageUrl)
                        .diskCacheKey(imageUrl)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .crossfade(true)
                        .apply(block = { size(coil.size.Size.ORIGINAL) })
                        .build(),
                    imageLoader = imageLoader
                )

                Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .then(if (height != null) Modifier.height(height) else Modifier)
                        .fillMaxWidth()
                )
            } else -> {
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
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .then(if (height != null) Modifier.height(height) else Modifier)
                        .fillMaxWidth(),
                    loading = {
                        if (placeholderContent != null) {
                            Box(
                                modifier = Modifier
                                    .then(if (height != null) Modifier.height(height) else Modifier)
                                    .fillMaxWidth()
                            ) {
                                placeholderContent()
                            }
                        } else if (placeHolder != null) {
                            Image(
                                painter = rememberAsyncImagePainter(placeHolder),
                                contentDescription = null,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .then(if (height != null) Modifier.height(height) else Modifier)
                                    .fillMaxWidth()
                            )
                        }
                    }
                )
            }
        }
    }
}

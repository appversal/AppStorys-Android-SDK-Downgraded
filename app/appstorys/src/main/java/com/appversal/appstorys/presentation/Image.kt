package com.appversal.appstorys.presentation

import android.os.Build.VERSION.SDK_INT
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
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
import com.appversal.appstorys.utils.isGif

@Composable
internal fun SdkImage(
    image: String,
    modifier: Modifier = Modifier,
    isLottie: Boolean = false,
    placeholder: Placeholder? = null,
    width: Dp = Dp.Unspecified,
    height: Dp = Dp.Unspecified,
    shape: Shape = RectangleShape,
    contentScale: ContentScale = ContentScale.Fit
) {
    when {
        image.isNotBlank() && isLottie -> {
            val composition by rememberLottieComposition(
                spec = LottieCompositionSpec.Url(image)
            )
            LottieAnimation(
                composition = composition,
                iterations = LottieConstants.IterateForever,
                modifier = modifier
                    .height(height)
                    .width(width)
            )
        }

        image.isNotBlank() -> {
            val context = LocalContext.current
            SubcomposeAsyncImage(
                model = remember(image) {
                    ImageRequest.Builder(context)
                        .data(image)
                        .memoryCacheKey(image)
                        .diskCacheKey(image)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .crossfade(true)
                        .size(coil.size.Size.ORIGINAL)
                        .build()
                },
                imageLoader = ImageLoader.Builder(context).components {
                    if (image.isGif) {
                        add(
                            when {
                                SDK_INT >= 28 -> ImageDecoderDecoder.Factory()
                                else -> GifDecoder.Factory()
                            }
                        )
                    }
                }.build(),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier
                    .height(height)
                    .width(width)
                    .clip(shape),
                loading = {
                    PlaceholderContent(
                        placeholder = placeholder,
                        modifier = modifier,
                        width = width,
                        height = height,
                        contentScale = contentScale
                    )
                },
            )
        }

        else -> PlaceholderContent(
            placeholder = placeholder,
            modifier = modifier,
            width = width,
            height = height,
            contentScale = contentScale
        )
    }
}

@Composable
private fun PlaceholderContent(
    placeholder: Placeholder?,
    modifier: Modifier = Modifier,
    width: Dp = Dp.Unspecified,
    height: Dp = Dp.Unspecified,
    contentScale: ContentScale = ContentScale.Fit
) {
    when (placeholder) {
        is Placeholder.Drawable -> Image(
            painter = rememberAsyncImagePainter(placeholder.value),
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier
                .height(height)
                .width(width)
        )

        is Placeholder.Composable -> Box(
            modifier = modifier
                .height(height)
                .width(width)
        ) {
            placeholder.content()
        }

        else -> {}
    }
}
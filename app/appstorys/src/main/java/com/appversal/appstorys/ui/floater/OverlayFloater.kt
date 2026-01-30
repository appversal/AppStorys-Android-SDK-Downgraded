package com.appversal.appstorys.ui.floater

import android.os.Build.VERSION.SDK_INT
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.appversal.appstorys.utils.isGifUrl

@Composable
internal fun OverlayFloater(
    modifier: Modifier,
    image: String,
    lottieUrl: String?,
    height: Dp,
    width: Dp,
    borderRadiusValues: RoundedCornerShape,
    onClick: () -> Unit
) {
    val url =
        image.ifEmpty { "https://gratisography.com/wp-content/uploads/2024/11/gratisography-augmented-reality-800x525.jpg" }
    val context = LocalContext.current
    val imageRequest = ImageRequest.Builder(context)
        .data(url)
        .memoryCacheKey(url)
        .diskCacheKey(url)
        .diskCachePolicy(CachePolicy.ENABLED)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .crossfade(true)
        .build()

    Surface (
        modifier = modifier
            .padding(16.dp)
            .height(height)
            .width(width)
            .clip(borderRadiusValues)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        color = Color.Transparent,
        shape = borderRadiusValues
    ) {
        when {
            !lottieUrl.isNullOrEmpty() -> {
                val composition by rememberLottieComposition(
                    spec = LottieCompositionSpec.Url(lottieUrl)
                )
                LottieAnimation(
                    composition = composition,
                    iterations = LottieConstants.IterateForever,
                    modifier  = Modifier
                        .fillMaxSize()
                        .clip(borderRadiusValues)
                )
            }

            !image.isNullOrEmpty() -> {
                if (isGifUrl(image)) {
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
                            .data(image)
                            .memoryCacheKey(image)
                            .diskCacheKey(image)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .crossfade(true)
                            .apply { size(Size.ORIGINAL) }
                            .build(),
                        imageLoader = imageLoader
                    )

                    Image(
                        painter = painter,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .height(height)
                            .width(width)
                    )
                } else {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = modifier
                            .fillMaxSize()
                            .clip(borderRadiusValues)
                    )
                }
            }
        }
    }
}
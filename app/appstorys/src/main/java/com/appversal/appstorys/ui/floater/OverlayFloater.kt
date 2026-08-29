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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

/**
 * Accessibility label for the floater's clickable surface. Named so it can be
 * referenced from tests instead of being duplicated as a magic string.
 */
internal const val FLOATER_CONTENT_DESCRIPTION = "Floater"


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
            .height(height)
            .width(width)
            .clip(borderRadiusValues)
            // The floater is a clickable control whose content is purely an
            // image or a Lottie animation — it has no text. Without a label
            // here it was announced as nothing by screen readers and was
            // unreachable by any UI-automation selector, so tests had to guess
            // its screen coordinates. Labelling the clickable container (and
            // leaving the inner image/animation decorative) covers BOTH the
            // image and Lottie branches, which is why it lives here rather
            // than on the Image itself. Matches the labels the SDK already
            // gives its other surfaces: "Banner", "Wheel", "Prize", "Close".
            .semantics { contentDescription = FLOATER_CONTENT_DESCRIPTION }
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
                        // Decorative: the clickable Surface above carries the
                        // label for the whole control (see its semantics).
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .height(height)
                            .width(width)
                    )
                } else {
                    AsyncImage(
                        model = imageRequest,
                        // Decorative — see the Surface's semantics above.
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
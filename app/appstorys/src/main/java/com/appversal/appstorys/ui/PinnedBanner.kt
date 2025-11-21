package com.appversal.appstorys.ui

import android.graphics.drawable.Drawable
import android.os.Build.VERSION.SDK_INT
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import com.appversal.appstorys.utils.isGifUrl

@Composable
internal fun PinnedBanner(
    modifier: Modifier = Modifier,
    imageUrl: String?,
    lottieUrl: String?,
    contentScale: ContentScale,
    width: Dp? = null,
    height: Dp?,
    bottomMargin: Dp = 0.dp,
    leftMargin: Dp = 0.dp,
    rightMargin: Dp = 0.dp,
    exitIcon: Boolean = false,
    exitUnit: () -> Unit,
    shape: RoundedCornerShape = RoundedCornerShape(topEnd = 16.dp, topStart = 16.dp),
    placeHolder: Drawable? = null,
    placeholderContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = modifier.padding(bottom = bottomMargin, start = leftMargin, end = rightMargin),
        shape = shape,
    ) {
        Box(modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )) {
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
                                .data(imageUrl)
                                .memoryCacheKey(imageUrl)
                                .diskCacheKey(imageUrl)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .crossfade(true)
                                .apply { size(coil.size.Size.ORIGINAL) }
                                .build(),
                            imageLoader = imageLoader
                        )

                        Image(
                            painter = painter,
                            contentDescription = null,
                            contentScale = contentScale,
                            modifier = Modifier
                                .height(height ?: Dp.Unspecified)
                                .width(width ?: Dp.Unspecified)
                        )
                    } else {
                        SubcomposeAsyncImage(
                            model = imageUrl,
                            contentDescription = null,
                            contentScale = contentScale,
                            modifier = Modifier
                                .height(height ?: Dp.Unspecified)
                                .width(width ?: Dp.Unspecified),
                            loading = {
                                if (placeholderContent != null) {
                                    Box(
                                        modifier = Modifier
                                            .height(height ?: Dp.Unspecified)
                                            .width(width ?: Dp.Unspecified)
                                    ) {
                                        placeholderContent()
                                    }
                                } else if (placeHolder != null) {
                                    Image(
                                        painter = rememberAsyncImagePainter(placeHolder),
                                        contentDescription = null,
                                        contentScale = contentScale,
                                        modifier = Modifier
                                            .height(height ?: Dp.Unspecified)
                                            .width(width ?: Dp.Unspecified)
                                    )
                                }
                            }
                        )
                    }
                }

                else -> {
                    if (placeholderContent != null) {
                        Box(
                            modifier = Modifier
                                .height(height ?: Dp.Unspecified)
                                .width(width ?: Dp.Unspecified)
                        ) {
                            placeholderContent()
                        }
                    } else if (placeHolder != null) {
                        Image(
                            painter = rememberAsyncImagePainter(placeHolder),
                            contentDescription = null,
                            contentScale = contentScale,
                            modifier = Modifier
                                .height(height ?: Dp.Unspecified)
                                .width(width ?: Dp.Unspecified)
                        )
                    }
                }
            }

            if (exitIcon) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color(0x4D000000))
                        .clickable { exitUnit() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
package com.appversal.appstorys.presentation

import android.os.Build.VERSION.SDK_INT
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.appversal.appstorys.AppStorys.trackEvent
import com.appversal.appstorys.api.FloaterDetails
import com.appversal.appstorys.domain.model.TypedCampaign
import com.appversal.appstorys.domain.rememberCampaign
import com.appversal.appstorys.domain.usecase.ClickEvent
import com.appversal.appstorys.utils.isGifUrl

@Composable
internal fun Floater(
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp
) {
    val campaign = rememberCampaign<FloaterDetails>("FLT")
    if (!campaign?.details?.image.isNullOrBlank()) {
        Content(
            campaign = campaign,
            modifier = modifier,
            bottomPadding = bottomPadding
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

    val image = details.image
    val imageRequest = remember(image) {
        val url = image.orEmpty()
        ImageRequest.Builder(context)
            .data(url)
            .memoryCacheKey(url)
            .diskCacheKey(url)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .size(coil.size.Size.ORIGINAL)
            .build()
    }

    val shape = RoundedCornerShape(
        topStart = (styling?.topLeftRadius?.toFloatOrNull() ?: 0f).dp,
        topEnd = (styling?.topRightRadius?.toFloatOrNull() ?: 0f).dp,
        bottomStart = (styling?.bottomLeftRadius?.toFloatOrNull() ?: 0f).dp,
        bottomEnd = (styling?.bottomRightRadius?.toFloatOrNull() ?: 0f).dp
    )

    LaunchedEffect(campaign.id) {
        trackEvent(campaign.id, "viewed")
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
                                ClickEvent(link = details.link, campaignId = campaign.id)
                            }
                        }
                    ),
                color = Color.Transparent,
                shape = shape,
                content = {
                    when {
                        !details.lottie_data.isNullOrBlank() -> {
                            val composition by rememberLottieComposition(
                                spec = LottieCompositionSpec.Url(details.lottie_data)
                            )
                            LottieAnimation(
                                composition = composition,
                                iterations = LottieConstants.IterateForever,
                                modifier = Modifier
                                    .height(height)
                                    .width(width)
                            )
                        }

                        !image.isNullOrBlank() && isGifUrl(image) -> {
                            Image(
                                painter = rememberAsyncImagePainter(
                                    ImageRequest.Builder(context)
                                        .data(image)
                                        .memoryCacheKey(image)
                                        .diskCacheKey(image)
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .memoryCachePolicy(CachePolicy.ENABLED)
                                        .crossfade(true)
                                        .size(coil.size.Size.ORIGINAL)
                                        .build(),
                                    imageLoader = ImageLoader.Builder(context).components {
                                        add(
                                            when {
                                                SDK_INT >= 28 -> ImageDecoderDecoder.Factory()
                                                else -> GifDecoder.Factory()
                                            }
                                        )
                                    }.build()
                                ),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .height(height)
                                    .width(width)
                            )
                        }

                        !image.isNullOrEmpty() -> AsyncImage(
                            model = imageRequest,
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(shape)
                        )
                    }
                }
            )
        }
    )
}

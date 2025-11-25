package com.appversal.appstorys.presentation

import android.os.Build.VERSION.SDK_INT
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import com.appversal.appstorys.api.BannerDetails
import com.appversal.appstorys.domain.State
import com.appversal.appstorys.domain.model.TypedCampaign
import com.appversal.appstorys.domain.rememberCampaign
import com.appversal.appstorys.domain.usecase.ClickEvent
import com.appversal.appstorys.domain.usecase.trackEvent
import com.appversal.appstorys.utils.isGifUrl


@Composable
internal fun Banner(
    modifier: Modifier = Modifier,
    placeholder: Placeholder? = null,
    bottomPadding: Dp = 0.dp,
) {
    val campaign = rememberCampaign<BannerDetails>("BAN")
    if (campaign != null) {
        Content(
            campaign = campaign,
            modifier = modifier,
            placeholder = placeholder,
            bottomPadding = bottomPadding,
        )
    }
}

@Composable
private fun Content(
    campaign: TypedCampaign<BannerDetails>,
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

    val placeholderContent: @Composable () -> Unit = remember(placeholder, width, height) {
        {
            when (placeholder) {
                is Placeholder.Drawable -> Image(
                    painter = rememberAsyncImagePainter(placeholder.value),
                    contentDescription = null,
                    contentScale = contentScale,
                    modifier = Modifier
                        .height(height)
                        .width(width)
                )

                is Placeholder.Composable -> Box(
                    modifier = Modifier
                        .height(height)
                        .width(width)
                ) {
                    placeholder.content()
                }

                else -> {}
            }
        }
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
                        ClickEvent(link = details.link, campaignId = campaign.id)
                    }
                    .padding(
                        bottom = styling?.marginBottom?.dp ?: 0.dp,
                        start = styling?.marginLeft?.dp ?: 0.dp,
                        end = styling?.marginRight?.dp ?: 0.dp
                    ),
                shape = shape,
                content = {
                    Box(modifier = modifier) {
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

                            !details.image.isNullOrEmpty() -> {
                                if (isGifUrl(details.image)) {
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
                                            .data(details.image)
                                            .memoryCacheKey(details.image)
                                            .diskCacheKey(details.image)
                                            .diskCachePolicy(CachePolicy.ENABLED)
                                            .memoryCachePolicy(CachePolicy.ENABLED)
                                            .crossfade(true)
                                            .size(coil.size.Size.ORIGINAL)
                                            .build(),
                                        imageLoader = imageLoader
                                    )

                                    Image(
                                        painter = painter,
                                        contentDescription = null,
                                        contentScale = contentScale,
                                        modifier = Modifier
                                            .height(height)
                                            .width(width)
                                    )
                                } else {
                                    SubcomposeAsyncImage(
                                        model = details.image,
                                        contentDescription = null,
                                        contentScale = contentScale,
                                        modifier = Modifier
                                            .height(height)
                                            .width(width),
                                        loading = {
                                            placeholderContent()
                                        }
                                    )
                                }
                            }

                            else -> placeholderContent()
                        }

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
                }
            )
        }
    )
}


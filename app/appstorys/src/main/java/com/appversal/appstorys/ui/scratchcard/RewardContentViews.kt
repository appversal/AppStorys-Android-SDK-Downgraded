package com.appversal.appstorys.ui.scratchcard

import android.os.Build.VERSION.SDK_INT
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import androidx.compose.ui.text.AnnotatedString
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.appversal.appstorys.api.CommonMargins
import com.appversal.appstorys.api.TextStyling
import com.appversal.appstorys.ui.common_components.CommonText
import com.appversal.appstorys.utils.isLottieUrl

@Composable
fun OnlyImageView(
    modifier: Modifier = Modifier,
    bannerImageUrl: String,
    cardWidth: Dp,
    cardHeight: Dp
) {
    val context = LocalContext.current

    // The reward image is only ever drawn at card size, so decode it at that size.
    val density = LocalDensity.current
    val targetWidthPx = with(density) { cardWidth.roundToPx() }
    val targetHeightPx = with(density) { cardHeight.roundToPx() }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (bannerImageUrl.isNotEmpty()) {
            if (isGifUrl(bannerImageUrl)) {
                val imageLoader = remember(context) {
                    ImageLoader.Builder(context)
                        .components {
                            if (SDK_INT >= 28) {
                                add(ImageDecoderDecoder.Factory())
                            } else {
                                add(GifDecoder.Factory())
                            }
                        }
                        .build()
                }

                val painter = rememberAsyncImagePainter(
                    ImageRequest.Builder(context)
                        .data(bannerImageUrl)
                        .memoryCacheKey(bannerImageUrl)
                        .diskCacheKey(bannerImageUrl)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .crossfade(true)
                        .size(targetWidthPx, targetHeightPx)
                        .build(),
                    imageLoader = imageLoader
                )

                Image(
                    painter = painter,
                    contentDescription = "Banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (isLottieUrl(bannerImageUrl)) {
                val composition by rememberLottieComposition(
                    spec = LottieCompositionSpec.Url(bannerImageUrl)
                )
                LottieAnimation(
                    composition = composition,
                    iterations = LottieConstants.IterateForever,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                SubcomposeAsyncImage(
                    model = bannerImageUrl,
                    contentDescription = "Banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// Helper function to safely parse color strings

@Composable
fun CashBackInfoView(
    modifier: Modifier = Modifier,
    bannerImageUrl: String,
    offerTitle: String,
    offerSubtitle: String,
    couponCode: String,
    couponBgColor: String,
    couponBorderColor: String,
    couponTextColor: String,
    rewardBgColor: String,
    offerTitleColor: String,
    offerSubtitleColor: String,
    cardHeight: Dp,
    titleFontSize: Int,
    subtitleFontSize: Int,
    // New styling parameters for title
    offerTitleFontFamily: String = "",
    offerTitleFontDecoration: List<String> = listOf(),
    offerTitleTextAlign: String = "center",
    offerTitleMarginTop: Dp = 0.dp,
    offerTitleMarginBottom: Dp = 0.dp,
    offerTitleMarginLeft: Dp = 0.dp,
    offerTitleMarginRight: Dp = 0.dp,
    // New styling parameters for subtitle
    offerSubtitleFontFamily: String = "",
    offerSubtitleFontDecoration: List<String> = listOf(),
    offerSubtitleTextAlign: String = "center",
    offerSubtitleMarginTop: Dp = 0.dp,
    offerSubtitleMarginBottom: Dp = 0.dp,
    offerSubtitleMarginLeft: Dp = 0.dp,
    offerSubtitleMarginRight: Dp = 0.dp,
    // Coupon styling parameters
    couponBorderWidth: Int = 1,
    couponAlignment: String = "center",
    couponCtaFullWidth: Boolean = false,
    couponCtaWidth: Dp = Dp.Unspecified,
    couponHeight: Dp = Dp.Unspecified,
    couponFontSize: Int = 14,
    couponFontFamily: String = "",
    couponFontDecoration: List<String> = listOf(),
    couponTopLeft: Dp = 8.dp,
    couponTopRight: Dp = 8.dp,
    couponBottomLeft: Dp = 8.dp,
    couponBottomRight: Dp = 8.dp,
    couponMarginTop: Dp = 0.dp,
    couponMarginBottom: Dp = 0.dp,
    couponMarginLeft: Dp = 0.dp,
    couponMarginRight: Dp = 0.dp,

    // Image styling (ADD THIS BLOCK)
    imageWidth: Dp = Dp.Unspecified,
    imageHeight: Dp = Dp.Unspecified,
    imageTopLeft: Dp = 0.dp,
    imageTopRight: Dp = 0.dp,
    imageBottomLeft: Dp = 0.dp,
    imageBottomRight: Dp = 0.dp,
    imageMarginTop: Dp = 0.dp,
    imageMarginBottom: Dp = 0.dp,
    imageMarginLeft: Dp = 0.dp,
    imageMarginRight: Dp = 0.dp,
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .background(parseColorSafe(rewardBgColor, Color(0xFF141414))),
        contentAlignment = Alignment.Center
    ) {
        Column(
//            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
//            verticalArrangement = Arrangement.Top
        ) {
            //Spacer(modifier = Modifier.weight(1f))
            if (bannerImageUrl.isNotEmpty()) {

                Box(
                    modifier = Modifier
                        .padding(
                            start = imageMarginLeft,
                            end = imageMarginRight,
                            top = imageMarginTop,
                            bottom = imageMarginBottom
                        )
                        .then(
                            if (imageWidth != Dp.Unspecified && imageHeight != Dp.Unspecified)
                                Modifier.size(imageWidth, imageHeight)
                            else
                                Modifier.sizeIn(maxWidth = cardHeight * 0.3f, maxHeight = cardHeight * 0.3f)
                        )
                        .clip(
                            RoundedCornerShape(
                                topStart = imageTopLeft,
                                topEnd = imageTopRight,
                                bottomStart = imageBottomLeft,
                                bottomEnd = imageBottomRight
                            )
                        )
                ) {
                    SubcomposeAsyncImage(
                        model = bannerImageUrl,
                        contentDescription = "Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }


            //Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (offerTitle.isNotEmpty()) {
                    CommonText(
                        modifier = Modifier.fillMaxWidth(),
                        text = offerTitle,
                        styling = TextStyling(
                            color = offerTitleColor,
                            fontSize = titleFontSize,
                            fontFamily = offerTitleFontFamily,
                            fontDecoration = offerTitleFontDecoration.ifEmpty { listOf("bold") },
                            textAlign = offerTitleTextAlign,
                            margin = CommonMargins(
                                top = offerTitleMarginTop.value.toInt(),
                                bottom = offerTitleMarginBottom.value.toInt(),
                                left = offerTitleMarginLeft.value.toInt(),
                                right = offerTitleMarginRight.value.toInt()
                            )
                        )
                    )
                }

                //Spacer(Modifier.height(cardHeight * 0.06f))

                if (offerSubtitle.isNotEmpty()) {
                    CommonText(
                        modifier = Modifier.fillMaxWidth(),
                        text = offerSubtitle,
                        letterSpacing = 0.1.toFloat(),
                        styling = TextStyling(
                            color = offerSubtitleColor,
                            fontSize = subtitleFontSize,
                            fontFamily = offerSubtitleFontFamily,
                            fontDecoration = offerSubtitleFontDecoration,
                            textAlign = offerSubtitleTextAlign,
                            margin = CommonMargins(
                                top = offerSubtitleMarginTop.value.toInt(),
                                bottom = offerSubtitleMarginBottom.value.toInt(),
                                left = offerSubtitleMarginLeft.value.toInt(),
                                right = offerSubtitleMarginRight.value.toInt()
                            )
                        )
                    )
                }

                //Spacer(Modifier.height(cardHeight * 0.2f))

                // Coupon code display
                if (couponCode.isNotEmpty()) {
                    val clipboardManager = LocalClipboardManager.current

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = couponMarginLeft,
                                end = couponMarginRight,
                                top = couponMarginTop,
                                bottom = couponMarginBottom
                            ),
                        horizontalArrangement = when (couponAlignment.lowercase()) {
                            "left", "start" -> Arrangement.Start
                            "right", "end" -> Arrangement.End
                            else -> Arrangement.Center
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .then(
                                    if (couponCtaFullWidth) Modifier.fillMaxWidth()
                                    else if (couponCtaWidth != Dp.Unspecified) Modifier.width(
                                        couponCtaWidth
                                    )
                                    else Modifier
                                )
                                .then(
                                    if (couponHeight != Dp.Unspecified) Modifier.height(couponHeight)
                                    else Modifier
                                )
                                .background(
                                    color = parseColorSafe(couponBgColor, Color(0xFF1F1F1F)),
                                    shape = RoundedCornerShape(
                                        topStart = couponTopLeft,
                                        topEnd = couponTopRight,
                                        bottomStart = couponBottomLeft,
                                        bottomEnd = couponBottomRight
                                    )
                                )
                                .then(
                                    if (couponBorderColor.isNotEmpty() && couponBorderWidth > 0) {
                                        Modifier.drawWithContent {
                                            drawContent()
                                            drawRoundRect(
                                                color = parseColorSafe(
                                                    couponBorderColor,
                                                    Color(0xFF0066FF)
                                                ),
                                                style = Stroke(width = couponBorderWidth.dp.toPx()),
                                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                                    couponTopLeft.toPx(),
                                                    couponTopRight.toPx()
                                                )
                                            )
                                        }
                                    } else Modifier
                                )
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(couponCode))
                                    Toast.makeText(
                                        context,
                                        "Copied to clipboard",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .padding(
                                    horizontal = 16.dp,
                                    vertical = if (couponHeight != Dp.Unspecified) 0.dp else cardHeight * 0.05f
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CommonText(
                                text = couponCode,
                                letterSpacing = 0.2.toFloat(),
                                styling = TextStyling(
                                    color = couponTextColor,
                                    fontFamily = couponFontFamily,
                                    fontSize = couponFontSize,
                                    fontDecoration = couponFontDecoration
                                )
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Copy Coupon",
                                tint = parseColorSafe(couponTextColor, Color.White),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            //Spacer(modifier = Modifier.weight(1f))
        }
    }
}

private fun isGifUrl(url: String): Boolean {
    return url.lowercase().endsWith(".gif")
}

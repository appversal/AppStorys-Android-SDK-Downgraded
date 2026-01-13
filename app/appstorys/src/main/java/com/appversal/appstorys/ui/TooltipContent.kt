package com.appversal.appstorys.ui

import android.annotation.SuppressLint
import android.graphics.Rect
import android.os.Build.VERSION.SDK_INT
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.appversal.appstorys.AppStorys.dismissTooltip
import com.appversal.appstorys.AppStorys.handleTooltipAction
import com.appversal.appstorys.api.Tooltip
import com.appversal.appstorys.ui.xml.toDp
import com.appversal.appstorys.utils.AppStorysCoordinates
import com.appversal.appstorys.utils.isGifUrl
import com.appversal.appstorys.utils.isLottieUrl
import com.appversal.appstorys.utils.noRippleClickable
import com.appversal.appstorys.utils.toColor
import kotlin.math.roundToInt

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
internal fun TooltipContent(
    tooltip: Tooltip,
    coordinates: AppStorysCoordinates,
) {
    val density = LocalDensity.current.density
    val view = LocalView.current.rootView

    val hasImageDimensions = tooltip.styling?.appearance?.imageDimensions?.let {
        it.width != null && it.height != null
    } == true

    val tooltipWidth = if (hasImageDimensions) {
        tooltip.styling?.appearance?.imageDimensions?.width?.dp ?: 300.dp
    } else {
        280.dp
    }
    val tooltipWidthPx = tooltipWidth.value * density

    val tooltipHeight = if (hasImageDimensions) {
        tooltip.styling?.appearance?.imageDimensions?.height?.dp ?: 200.dp
    } else {
        100.dp
    }
    val tooltipHeightPx = tooltipHeight.value * density
    val spacing = 8 * density


    // Get window bounds to determine if we should show above or below
    val visibleBounds = remember {
        val rect = Rect()
        view.getWindowVisibleDisplayFrame(rect)
        rect
    }

    val targetBounds = remember {
        coordinates.boundsInRoot()
    }
    val spaceBelow = visibleBounds.bottom - targetBounds.bottom
    val spaceAbove = targetBounds.top - visibleBounds.top
    val showBelow = spaceBelow >= tooltipHeightPx + spacing || spaceBelow > spaceAbove

    val arrowHeightPx =
        (tooltip.styling?.appearance?.arrowStyle?.height ?: 8) * density
    val arrowWidthPx = (tooltip.styling?.appearance?.arrowStyle?.width ?: 8) * density

    // Small gap between target element and arrow
    val elementArrowGap = 5 * density

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().clickable(onClick = { dismissTooltip() }),
        content = {
            // Position the arrow centered on the target element with small gap
            // Subtract half the arrow width to center it horizontally
            Box(
                modifier = Modifier.offset {
                    IntOffset(
                        (targetBounds.center.x - (arrowWidthPx + (arrowWidthPx/3))).roundToInt(),
                        when (showBelow) {
                            true -> (targetBounds.bottom + elementArrowGap).roundToInt()
                            else -> (targetBounds.top - arrowHeightPx - elementArrowGap).roundToInt()
                        }
                    )
                },
                content = {
                    Arrow(
                        showBelow = showBelow,
                        height = arrowHeightPx,
                        width = arrowWidthPx,
                        tooltip = tooltip
                    )
                }
            )

            // Calculate tooltip X position, keeping it within screen bounds
            // Try to center the tooltip horizontally on the target, but adjust if it would go off-screen
            val tooltipX = when {
                // If centering would push tooltip off left edge, align to left edge
                targetBounds.center.x - tooltipWidthPx / 2 < visibleBounds.left -> {
                    visibleBounds.left.toFloat()
                }
                // If centering would push tooltip off right edge, align to right edge
                targetBounds.center.x + tooltipWidthPx / 2 > visibleBounds.right -> {
                    visibleBounds.right - tooltipWidthPx
                }
                // Otherwise, center the tooltip horizontally on the target
                else -> targetBounds.center.x - tooltipWidthPx / 2
            }

            // Calculate final tooltip content Y position directly adjacent to arrow
            // Arrow touches content, gap is between element and arrow
            val tooltipYAdjusted = when (showBelow) {
                true -> targetBounds.bottom + elementArrowGap + arrowHeightPx  // Content directly below arrow
                else -> targetBounds.top - arrowHeightPx - tooltipHeightPx - elementArrowGap  // Content directly above arrow
            }

            // Position the tooltip content
            if(hasImageDimensions){
                ImageContent(
                    modifier = Modifier.offset {
                        IntOffset(
                            tooltipX.roundToInt(),
                            tooltipYAdjusted.roundToInt()
                        )
                    }.size(tooltipWidth, tooltipHeight),
                    tooltip = tooltip
                )
            } else {
                TextContent(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) {
                                val actualWidth = placeable.width.toFloat()
                                val tooltipXDynamic = when {
                                    targetBounds.center.x - actualWidth / 2 < visibleBounds.left -> {
                                        visibleBounds.left.toFloat()
                                    }
                                    targetBounds.center.x + actualWidth / 2 > visibleBounds.right -> {
                                        visibleBounds.right - actualWidth
                                    }
                                    else -> targetBounds.center.x - actualWidth / 2
                                }
                                placeable.placeRelative(
                                    tooltipXDynamic.roundToInt(),
                                    tooltipYAdjusted.roundToInt()
                                )
                            }
                        },
                    tooltip = tooltip
                )
            }
        }
    )
}


@Composable
private fun ImageContent(tooltip: Tooltip, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val url = tooltip.url.orEmpty()

    LaunchedEffect(tooltip) {
        handleTooltipAction(tooltip)
    }

    val cornerRadius = RoundedCornerShape(
        topStart = (tooltip.styling?.appearance?.cornerRadius?.topLeft ?: 0f).toDp(),
        topEnd = (tooltip.styling?.appearance?.cornerRadius?.topRight ?: 0f).toDp(),
        bottomStart = (tooltip.styling?.appearance?.cornerRadius?.bottomLeft ?: 0f).toDp(),
        bottomEnd = (tooltip.styling?.appearance?.cornerRadius?.bottomRight ?: 0f).toDp(),
    )

    Box(
        modifier =
            modifier.then(
                tooltip.styling?.let { padding ->
                    Modifier.background(color = tooltip.styling.appearance?.colors?.tooltip.toColor(Color.Transparent), shape = cornerRadius)
                        .clip(cornerRadius)
                        .noRippleClickable(
                            onClick = {
                                handleTooltipAction(tooltip, true)
                            }
                        )
                } ?:
                Modifier
            ),
        content = {
            when {
                isLottieUrl(url) -> {
                    val composition by rememberLottieComposition(
                        LottieCompositionSpec.Url(url)
                    )

                    LottieAnimation(
                        composition = composition,
                        iterations = LottieConstants.IterateForever,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                isGifUrl(url) -> {
                    val imageLoader = remember {
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
                            .data(url)
                            .memoryCacheKey(url)
                            .diskCacheKey(url)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .size(coil.size.Size.ORIGINAL)
                            .crossfade(true)
                            .build(),
                        imageLoader = imageLoader
                    )

                    Image(
                        painter = painter,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {
                    val imageRequest = remember(url) {
                        ImageRequest.Builder(context)
                            .data(url)
                            .memoryCacheKey(url)
                            .diskCacheKey(url)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .crossfade(true)
                            .build()
                    }

                    AsyncImage(
                        model = imageRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    )
}

@Composable
private fun TextContent(tooltip: Tooltip, modifier: Modifier = Modifier) {

    LaunchedEffect(tooltip) {
        handleTooltipAction(tooltip)
    }

    val cornerRadius = RoundedCornerShape(
        topStart = (tooltip.styling?.appearance?.cornerRadius?.topLeft ?: 8f).toDp(),
        topEnd = (tooltip.styling?.appearance?.cornerRadius?.topRight ?: 8f).toDp(),
        bottomStart = (tooltip.styling?.appearance?.cornerRadius?.bottomLeft ?: 8f).toDp(),
        bottomEnd = (tooltip.styling?.appearance?.cornerRadius?.bottomRight ?: 8f).toDp(),
    )

    // Title styling from TooltipText
    val titleStyling = tooltip.styling?.title
    val titleColor = titleStyling?.color.toColor(Color.Black)
    val titleFontSize = (titleStyling?.fontSize ?: 14).sp
    val titleTextAlign = parseTextAlign(titleStyling?.textAlign)

    // Subtitle styling from TooltipText
    val subtitleStyling = tooltip.styling?.subTitle
    val subtitleColor = subtitleStyling?.color.toColor(Color.Gray)
    val subtitleFontSize = (subtitleStyling?.fontSize ?: 12).sp
    val subtitleTextAlign = parseTextAlign(subtitleStyling?.textAlign)

    // CTA styling from TooltipCta
    val ctaStyling = tooltip.styling?.cta
    val ctaTextColor = ctaStyling?.text?.color.toColor(Color.White)
    val ctaTextFontSize = (ctaStyling?.text?.fontSize ?: 12).sp
    val ctaBackgroundColor = ctaStyling?.container?.backgroundColor.toColor(Color.Blue)
    val ctaBorderColor = ctaStyling?.container?.borderColor.toColor(Color.Transparent)
    val ctaBorderWidth = (ctaStyling?.container?.borderWidth ?: 0).dp
    val ctaCornerRadius = RoundedCornerShape(
        topStart = (ctaStyling?.borderRadius?.topLeft ?: 8f).toDp(),
        topEnd = (ctaStyling?.borderRadius?.topRight ?: 8f).toDp(),
        bottomStart = (ctaStyling?.borderRadius?.bottomLeft ?: 8f).toDp(),
        bottomEnd = (ctaStyling?.borderRadius?.bottomRight ?: 8f).toDp(),
    )
    val ctaMargin = ctaStyling?.margin
    val ctaHeight = ctaStyling?.container?.height?.dp
    val ctaWidth = ctaStyling?.container?.ctaWidth?.dp
    val ctaFullWidth = ctaStyling?.container?.ctaFullWidth == true
    val ctaAlignment = when (ctaStyling?.container?.alignment?.lowercase()) {
        "start", "left" -> Alignment.Start
        "end", "right" -> Alignment.End
        else -> Alignment.CenterHorizontally
    }

    Box(
        modifier = modifier
            .wrapContentSize()
            .background(
                color = tooltip.styling?.appearance?.colors?.tooltip.toColor(Color.White),
                shape = cornerRadius
            )
            .clip(cornerRadius)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
        content = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title text
                if (!tooltip.titleText.isNullOrEmpty()) {
                    Text(
                        text = tooltip.titleText,
                        color = titleColor,
                        fontSize = titleFontSize,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = titleTextAlign
                    )
                }

                // Subtitle text
                if (!tooltip.subtitleText.isNullOrEmpty()) {
                    Text(
                        text = tooltip.subtitleText,
                        color = subtitleColor,
                        fontSize = subtitleFontSize,
                        textAlign = subtitleTextAlign
                    )
                }

                // CTA button
                if (!tooltip.ctaText.isNullOrEmpty()) {
                    Box(
                        modifier = Modifier
                            .then(
                                if (ctaFullWidth) Modifier.fillMaxWidth()
                                else if (ctaWidth != null) Modifier.width(ctaWidth)
                                else Modifier.wrapContentSize()
                            )
                            .then(
                                if (ctaHeight != null) Modifier.height(ctaHeight)
                                else Modifier
                            )
                            .padding(
                                start = (ctaMargin?.left ?: 0).dp,
                                end = (ctaMargin?.right ?: 0).dp,
                                top = (ctaMargin?.top ?: 0).dp,
                                bottom = (ctaMargin?.bottom ?: 0).dp
                            )
                            .background(
                                color = ctaBackgroundColor,
                                shape = ctaCornerRadius
                            )
                            .then(
                                if (ctaBorderWidth > 0.dp) {
                                    Modifier.border(
                                        width = ctaBorderWidth,
                                        color = ctaBorderColor,
                                        shape = ctaCornerRadius
                                    )
                                } else Modifier
                            )
                            .clip(ctaCornerRadius)
                            .noRippleClickable(
                                onClick = { handleTooltipAction(tooltip, true) }
                            )
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tooltip.ctaText,
                            color = ctaTextColor,
                            fontSize = ctaTextFontSize,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    )
}

private fun parseTextAlign(alignment: String?): TextAlign {
    return when (alignment?.lowercase()) {
        "left", "start" -> TextAlign.Start
        "right", "end" -> TextAlign.End
        "justify" -> TextAlign.Justify
        else -> TextAlign.Center
    }
}

@Composable
private fun Arrow(
    showBelow: Boolean,
    height: Float,
    width: Float,
    tooltip: Tooltip
) {
    Box(
        modifier = Modifier
            .height(height.dp)
            .width(width.dp)
            .drawBehind {
                val path = Path()
                val centerX = size.width / 2f
                val arrowWidth = width
                val arrowHeight = height

                if (showBelow) {
                    // Arrow pointing up (tooltip is above target)
                    path.moveTo(centerX - arrowWidth / 2, arrowHeight)
                    path.lineTo(centerX, 0f)
                    path.lineTo(centerX + arrowWidth / 2, arrowHeight)
                    path.close()

                } else {
                    // Arrow pointing down (tooltip is below target)
                    path.moveTo(centerX - arrowWidth / 2, 0f)
                    path.lineTo(centerX, arrowHeight)
                    path.lineTo(centerX + arrowWidth / 2, 0f)
                    path.close()
                }
                drawPath(path, tooltip.styling?.appearance?.colors?.arrow.toColor(Color.White))
            }
    )
}
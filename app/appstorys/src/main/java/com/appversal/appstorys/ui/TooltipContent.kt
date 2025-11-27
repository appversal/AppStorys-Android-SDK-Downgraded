package com.appversal.appstorys.ui

import android.annotation.SuppressLint
import android.graphics.Rect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.appversal.appstorys.AppStorys.dismissTooltip
import com.appversal.appstorys.AppStorys.handleTooltipAction
import com.appversal.appstorys.api.Tooltip
import com.appversal.appstorys.utils.AppStorysCoordinates
import kotlin.math.roundToInt

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
internal fun TooltipContent(
    tooltip: Tooltip,
    coordinates: AppStorysCoordinates,
) {
    val density = LocalDensity.current.density
    val view = LocalView.current.rootView
    val tooltipWidth =
        tooltip.styling?.tooltipDimensions?.width?.toIntOrNull()?.dp ?: 300.dp
    val tooltipWidthPx = tooltipWidth.value * density
    val tooltipHeight =
        tooltip.styling?.tooltipDimensions?.height?.toIntOrNull()?.dp ?: 200.dp
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

    val tooltipY = if (showBelow) {
        targetBounds.bottom + spacing
    } else {
        targetBounds.top - tooltipHeightPx - spacing
    }

    val arrowHeightPx =
        (tooltip.styling?.tooltipArrow?.arrowHeight?.toIntOrNull() ?: 8) * density
    val arrowWidthPx = (tooltip.styling?.tooltipArrow?.arrowWidth?.toIntOrNull() ?: 16) * density

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
                        width = arrowWidthPx
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
            Content(
                modifier = Modifier.offset {
                    IntOffset(
                        tooltipX.roundToInt(),
                        tooltipYAdjusted.roundToInt()
                    )
                }.size(tooltipWidth, tooltipHeight),
                tooltip = tooltip
            )
        }
    )
}


@Composable
private fun Content(tooltip: Tooltip, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val imageRequest = remember(tooltip.url) {
        ImageRequest.Builder(context)
            .data(tooltip.url)
            .memoryCacheKey(tooltip.url)
            .diskCacheKey(tooltip.url)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }

    LaunchedEffect(tooltip) {
        handleTooltipAction(tooltip)
    }

    Box(
        modifier = modifier.then(
            tooltip.styling?.spacing?.padding?.let { padding ->
                Modifier.padding(
                    start = padding.paddingLeft?.dp ?: 0.dp,
                    end = padding.paddingRight?.dp ?: 0.dp,
                    top = padding.paddingTop?.dp ?: 0.dp,
                    bottom = padding.paddingBottom?.dp ?: 0.dp
                )
            } ?: Modifier
        ),
        content = {
            val cornerRadius = tooltip.styling?.tooltipDimensions?.cornerRadius?.toIntOrNull()
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().clip(
                    if (cornerRadius != null) RoundedCornerShape(cornerRadius.dp) else MaterialTheme.shapes.medium
                ).clickable(onClick = { handleTooltipAction(tooltip, true) })
            )
            if (tooltip.styling?.closeButton == true) {
                Icon(
                    modifier = Modifier
                        .padding(15.dp)
                        .size(30.dp)
                        .align(Alignment.TopEnd)
                        .clickable(onClick = { dismissTooltip() }),
                    tint = Color.White,
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close Tooltip"
                )
            }
        }
    )
}

@Composable
private fun Arrow(
    showBelow: Boolean,
    height: Float,
    width: Float
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
                drawPath(path, Color.White)
            }
    )
}
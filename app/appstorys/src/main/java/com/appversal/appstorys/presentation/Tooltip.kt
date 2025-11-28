package com.appversal.appstorys.presentation

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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appversal.appstorys.api.Tooltip
import com.appversal.appstorys.api.TooltipsDetails
import com.appversal.appstorys.domain.State
import com.appversal.appstorys.domain.State.constraints
import com.appversal.appstorys.domain.model.AppStorysCoordinates
import com.appversal.appstorys.domain.rememberCampaign
import com.appversal.appstorys.domain.usecase.ClickEvent
import com.appversal.appstorys.domain.usecase.trackEvent
import kotlin.math.roundToInt

@Composable
internal fun Tooltip(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val campaign = rememberCampaign<TooltipsDetails>("TTP")
    val tooltips = campaign?.details?.tooltips

    if (campaign != null && tooltips?.isNotEmpty() == true) {
        val constraints by constraints.collectAsStateWithLifecycle()

        val viewed = remember { mutableStateListOf<String>() }
        val current by remember {
            derivedStateOf {
                tooltips.find {
                    !viewed.contains(it.id) && constraints.containsKey(it.target) && !it.url.isNullOrBlank()
                }
            }
        }
        val coordinates by rememberUpdatedState(
            constraints[current?.target]
        )

        LaunchedEffect(current, coordinates) {
            if (current == null || coordinates == null) {
                State.addDisabledCampaign(campaign.id)
            } else if (!current?.id.isNullOrBlank()) {
                trackEvent(
                    context,
                    "viewed",
                    campaign.id,
                    mapOf("tooltip_id" to current?.id.orEmpty())
                )
            }
        }

        if (current != null && coordinates != null) {
            val handleDismiss = remember(current) {
                {
                    val id = current?.id
                    when {
                        id.isNullOrBlank() -> State.addDisabledCampaign(campaign.id)
                        else -> viewed += id
                    }
                }
            }

            Content(
                modifier = modifier,
                tooltip = current!!,
                coordinates = coordinates!!,
                onDismiss = handleDismiss,
                onClick = {
                    val result = ClickEvent(
                        context = context,
                        link = current?.deepLinkUrl,
                        campaignId = campaign.id,
                    )
                    if (!result) {
                        handleDismiss()
                    }
                }
            )
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun Content(
    tooltip: Tooltip,
    coordinates: AppStorysCoordinates,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current.density
    val view = LocalView.current.rootView

    val styling = tooltip.styling

    val tooltipWidth = styling?.tooltipDimensions?.width?.toIntOrNull()?.dp ?: 300.dp
    val tooltipWidthPx = tooltipWidth.value * density
    val tooltipHeight = styling?.tooltipDimensions?.height?.toIntOrNull()?.dp ?: 200.dp
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

    val arrowHeightPx = (styling?.tooltipArrow?.arrowHeight?.toIntOrNull() ?: 8) * density
    val arrowWidthPx = (styling?.tooltipArrow?.arrowWidth?.toIntOrNull() ?: 16) * density

    Popup(
        popupPositionProvider = object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset = IntOffset.Zero
        },
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        ),
        onDismissRequest = onDismiss,
        content = {
            Showcase(
                visible = true,
                targetCoordinates = coordinates,
                highlight = ShowcaseHighlight.Rectangular(
                    cornerRadius = styling?.highlightRadius?.toIntOrNull()?.dp
                        ?: 8.dp,
                    padding = styling?.highlightPadding?.toIntOrNull()?.dp
                        ?: 8.dp
                )
            )

            // Renders the tooltip content.
            BoxWithConstraints(
                modifier = modifier
                    .fillMaxSize()
                    .clickable(onClick = onDismiss),
                content = {
                    // Position the arrow centered on the target element
                    Box(
                        modifier = Modifier.offset {
                            IntOffset(
                                targetBounds.center.x.roundToInt(),
                                (tooltipY + arrowHeightPx).roundToInt()
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

                    // Calculate final tooltip content Y position with proper spacing from arrow
                    // The multiplier of 3 provides reasonable visual spacing without hiding the arrow
                    val tooltipYAdjusted = when (showBelow) {
                        true -> tooltipY + arrowHeightPx * 3  // Content below arrow when tooltip is below target
                        else -> tooltipY - arrowHeightPx * 3  // Content above arrow when tooltip is above target
                    }

                    // Position the tooltip content
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    tooltipX.roundToInt(),
                                    tooltipYAdjusted.roundToInt()
                                )
                            }
                            .size(tooltipWidth, tooltipHeight)
                            .then(
                                styling?.spacing?.padding?.let { padding ->
                                    Modifier.padding(
                                        start = padding.paddingLeft?.dp ?: 0.dp,
                                        end = padding.paddingRight?.dp ?: 0.dp,
                                        top = padding.paddingTop?.dp ?: 0.dp,
                                        bottom = padding.paddingBottom?.dp ?: 0.dp
                                    )
                                } ?: Modifier
                            ),
                        content = {
                            val cornerRadius =
                                styling?.tooltipDimensions?.cornerRadius?.toIntOrNull()
                            SdkImage(
                                modifier = Modifier.clickable(onClick = onClick),
                                image = tooltip.url.orEmpty(),
                                shape = if (cornerRadius != null) RoundedCornerShape(cornerRadius.dp) else MaterialTheme.shapes.medium,
                            )
                            if (styling?.closeButton == true) {
                                Icon(
                                    modifier = Modifier
                                        .padding(15.dp)
                                        .size(30.dp)
                                        .align(Alignment.TopEnd)
                                        .clickable(onClick = onDismiss),
                                    tint = Color.White,
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close Tooltip"
                                )
                            }
                        }
                    )
                }
            )
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
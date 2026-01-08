package com.appversal.appstorys.ui

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.appversal.appstorys.api.Tooltip
import com.appversal.appstorys.utils.AppStorysCoordinates
import androidx.core.graphics.toColorInt

internal class ShowcaseDuration(val enterMillis: Int, val exitMillis: Int) {
    companion object {
        val Default = ShowcaseDuration(700, 700)
    }
}

internal sealed interface ShowcaseDisplayState {
    data object Appeared : ShowcaseDisplayState
    data object Disappeared : ShowcaseDisplayState
}

class HighlightProperties internal constructor(
    val drawHighlight: DrawScope.(AppStorysCoordinates) -> Unit,
    val highlightBounds: Rect
)

@Composable
internal fun ShowcaseView(
    visible: Boolean,
    targetCoordinates: AppStorysCoordinates,
    duration: ShowcaseDuration = ShowcaseDuration.Default,
    onDisplayStateChanged: (ShowcaseDisplayState) -> Unit = {},
    highlight: ShowcaseHighlight = ShowcaseHighlight.Rectangular(),
    tooltip: Tooltip,
) {
    val transition = remember { MutableTransitionState(false) }
    val highlightDrawer = highlight.create(targetCoordinates = targetCoordinates)

    AnimatedVisibility(
        visibleState = transition,
        enter = fadeIn(tween(duration.enterMillis)),
        exit = fadeOut(tween(duration.exitMillis))
    ) {
        Box {
            ShowcaseBackground(
                coordinates = targetCoordinates,
                drawHighlight = highlightDrawer.drawHighlight,
                tooltip = tooltip
            )
        }
    }
    LaunchedEffect(key1 = visible) {
        transition.targetState = visible
    }
    LaunchedEffect(key1 = transition.isIdle) {
        if (transition.isIdle) {
            if (transition.targetState) {
                onDisplayStateChanged(ShowcaseDisplayState.Appeared)
            } else {
                onDisplayStateChanged(ShowcaseDisplayState.Disappeared)
            }
        }
    }
}

@Composable
private fun ShowcaseBackground(
    coordinates: AppStorysCoordinates,
    drawHighlight: DrawScope.(AppStorysCoordinates) -> Unit,
    tooltip: Tooltip,
) {

    val backdropAlpha: Float =
        if (tooltip.enableBackdrop == true) {
            (tooltip.styling?.appearance?.backdropOpacity ?: 0)
                .coerceIn(0, 100) / 100f
        } else {
            0f
        }

    val backdropColor =
        tooltip.styling?.appearance?.colors?.backdrop
            ?.let { Color(it.toColorInt()) }
            ?: Color.Black

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                    }
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
        ) {
            drawRect(
                color = backdropColor.copy(alpha = backdropAlpha),
                size = Size(size.width, size.height)
            )
            drawHighlight(coordinates)
        }
    }
}


sealed interface ShowcaseHighlight {
    @Composable
    fun create(targetCoordinates: AppStorysCoordinates): HighlightProperties

    data class Rectangular(val cornerRadius: Dp = 8.dp, val padding: Dp = 8.dp) :
        ShowcaseHighlight {

        @Composable
        override fun create(
            targetCoordinates: AppStorysCoordinates
        ): HighlightProperties {
            val highlightBounds = createHighlightBounds(
                targetCoordinates.boundsInRoot(),
                with(LocalDensity.current) { padding.toPx() }
            )
            return HighlightProperties(
                drawHighlight = { rectangularHighlight(cornerRadius.toPx(), highlightBounds) },
                highlightBounds = highlightBounds
            )
        }

        private fun DrawScope.rectangularHighlight(
            cornerRadius: Float,
            highlightBounds: Rect
        ) {
            drawRoundRect(
                color = Color.White,
                size = highlightBounds.size,
                topLeft = highlightBounds.topLeft,
                blendMode = BlendMode.Clear,
                cornerRadius = CornerRadius(cornerRadius, cornerRadius)
            )
        }

        private fun createHighlightBounds(
            targetRect: Rect,
            targetMargin: Float
        ): Rect {
            return Rect(
                top = targetRect.top - targetMargin,
                bottom = targetRect.bottom + targetMargin,
                left = targetRect.left - targetMargin,
                right = targetRect.right + targetMargin
            )
        }
    }

    data class Circular(val targetMargin: Dp = 4.dp) : ShowcaseHighlight {

        @Composable
        override fun create(targetCoordinates: AppStorysCoordinates): HighlightProperties {
            val targetMargin = with(LocalDensity.current) { targetMargin.toPx() }
            return HighlightProperties(
                drawHighlight = { circularHighlight(it, targetMargin) },
                highlightBounds = createHighlightBounds(
                    targetCoordinates.boundsInRoot(),
                    targetMargin = targetMargin
                )
            )
        }

        private fun DrawScope.circularHighlight(
            coordinates: AppStorysCoordinates,
            targetMargin: Float
        ) {
            val targetRect = coordinates.boundsInRoot()
            val xOffset = targetRect.topLeft.x
            val yOffset = targetRect.topLeft.y
            val rectSize = coordinates.boundsInParent().size
            val radius = if (rectSize.width > rectSize.height) {
                rectSize.width / 2
            } else {
                rectSize.height / 2
            }
            drawCircle(
                color = Color.White,
                radius = radius + targetMargin,
                center = Offset(xOffset + rectSize.width / 2, yOffset + rectSize.height / 2),
                blendMode = BlendMode.Clear
            )
        }

        private fun createHighlightBounds(targetRect: Rect, targetMargin: Float): Rect {
            val radius = if (targetRect.width > targetRect.height) {
                targetRect.width / 2
            } else {
                targetRect.height / 2
            }

            return Rect(
                top = targetRect.center.y - radius - targetMargin,
                bottom = targetRect.center.y + radius + targetMargin,
                left = targetRect.center.x - radius - targetMargin,
                right = targetRect.center.x + radius + targetMargin
            )
        }
    }
}
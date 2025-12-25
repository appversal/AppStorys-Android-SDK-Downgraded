package com.appversal.appstorys.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import coil.compose.AsyncImage
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.appversal.appstorys.R
import com.appversal.appstorys.api.ExpandControls

data class ExpandButtonConfig(
    val fillColor: Color = Color.Black.copy(alpha = 0.5f),
    val iconColor: Color = Color.White,
    val strokeColor: Color? = null,
    val marginTop: Dp = 0.dp,
    val marginEnd: Dp = 0.dp,
    val marginBottom: Dp = 0.dp,
    val marginStart: Dp = 0.dp,
    val imageUrl: String? = null
)

@Composable
internal fun ExpandButton(
    size: Dp = 18.dp,
    modifier: Modifier = Modifier,
    isMaximized: Boolean = false,
    expandControls: ExpandControls?,
    maximiseImageUrl: String? = null,
    minimiseImageUrl: String? = null,
    applyMargins: Boolean = true,  // Set to false to ignore backend margins (for maximized view)
    boundaryPadding: Dp? = null,
    onToggle: () -> Unit
) {
    // Get the appropriate config based on current state
    // When minimized (isMaximized = false), show maximize button
    // When maximized (isMaximized = true), show minimize button
    val maximiseConfig = expandControls?.maximise
    val minimiseConfig = expandControls?.minimise
    val safePadding = boundaryPadding ?: 0.dp


    val fillColor = if (isMaximized) {
        try {
            minimiseConfig?.colors?.fill?.let { Color(it.toColorInt()) }
        } catch (_: Exception) {
            null
        }
    } else {
        try {
            maximiseConfig?.colors?.fill?.let { Color(it.toColorInt()) }
        } catch (_: Exception) {
            null
        }
    } ?: Color.Transparent

    val iconColor = if (isMaximized) {
        try {
            minimiseConfig?.colors?.cross?.let { Color(it.toColorInt()) }
        } catch (_: Exception) {
            null
        }
    } else {
        try {
            maximiseConfig?.colors?.cross?.let { Color(it.toColorInt()) }
        } catch (_: Exception) {
            null
        }
    } ?: Color.White

    val strokeColor = if (isMaximized) {
        try {
            minimiseConfig?.colors?.stroke?.let { Color(it.toColorInt()) }
        } catch (_: Exception) {
            null
        }
    } else {
        try {
            maximiseConfig?.colors?.stroke?.let { Color(it.toColorInt()) }
        } catch (_: Exception) {
            null
        }
    } ?: Color.Transparent

    // Extract margins - only apply if applyMargins is true
    val topMargin = if (applyMargins) {
        if (isMaximized) {
            try {
                minimiseConfig?.margin?.top?.toIntOrNull()?.dp
            } catch (_: Exception) {
                null
            }
        } else {
            try {
                maximiseConfig?.margin?.top?.toIntOrNull()?.dp
            } catch (_: Exception) {
                null
            }
        } ?: 0.dp
    } else 0.dp

    val endMargin = if (applyMargins) {
        if (isMaximized) {
            try {
                minimiseConfig?.margin?.right?.toIntOrNull()?.dp
            } catch (_: Exception) {
                null
            }
        } else {
            try {
                maximiseConfig?.margin?.right?.toIntOrNull()?.dp
            } catch (_: Exception) {
                null
            }
        } ?: 0.dp
    } else 0.dp

    val bottomMargin = if (applyMargins) {
        if (isMaximized) {
            try {
                minimiseConfig?.margin?.bottom?.toIntOrNull()?.dp
            } catch (_: Exception) {
                null
            }
        } else {
            try {
                maximiseConfig?.margin?.bottom?.toIntOrNull()?.dp
            } catch (_: Exception) {
                null
            }
        } ?: 0.dp
    } else 0.dp

    val startMargin = if (applyMargins) {
        if (isMaximized) {
            try {
                minimiseConfig?.margin?.left?.toIntOrNull()?.dp
            } catch (_: Exception) {
                null
            }
        } else {
            try {
                maximiseConfig?.margin?.left?.toIntOrNull()?.dp
            } catch (_: Exception) {
                null
            }
        } ?: 0.dp
    } else 0.dp

    // Check if the expand controls are enabled
    val isEnabled = expandControls?.enabled ?: true
    if (!isEnabled) return

    Box(
        modifier = modifier
            .padding(
                top = topMargin + safePadding,
                end = endMargin + safePadding,
                bottom = bottomMargin + safePadding,
                start = startMargin + safePadding
            )
            .size(size)
            .clip(CircleShape)
            .background(fillColor)
            .then(
                if (strokeColor != null) {
                    Modifier.border(1.dp, strokeColor, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        when {
            // When maximized, show minimize button
            isMaximized && !minimiseImageUrl.isNullOrBlank() -> {
                AsyncImage(
                    model = minimiseImageUrl,
                    contentDescription = "Minimize",
                    modifier = Modifier.padding(4.dp)
                )
            }
            // When minimized, show maximize button
            !isMaximized && !maximiseImageUrl.isNullOrBlank() -> {
                AsyncImage(
                    model = maximiseImageUrl,
                    contentDescription = "Maximize",
                    modifier = Modifier.padding(4.dp)
                )
            }
            else -> {
                Icon(
                    painter = if (isMaximized) painterResource(R.drawable.minimize) else painterResource(R.drawable.expand),
                    contentDescription = if (isMaximized) "Minimize" else "Maximize",
                    tint = iconColor,
                    modifier = Modifier.padding(9.dp)
                )
            }
        }
    }
}

/**
 * Helper function to create ExpandButtonConfig from raw values.
 */
fun createExpandButtonConfig(
    fillColorString: String? = null,
    iconColorString: String? = null,
    strokeColorString: String? = null,
    marginTop: Int? = null,
    marginEnd: Int? = null,
    marginBottom: Int? = null,
    marginStart: Int? = null,
    imageUrl: String? = null
): ExpandButtonConfig {
    val fillColor = try {
        fillColorString?.let { Color(it.toColorInt()) }
    } catch (_: Exception) {
        null
    } ?: Color.Black.copy(alpha = 0.5f)

    val iconColor = try {
        iconColorString?.let { Color(it.toColorInt()) }
    } catch (_: Exception) {
        null
    } ?: Color.White

    val strokeColor = try {
        strokeColorString?.let { Color(it.toColorInt()) }
    } catch (_: Exception) {
        null
    }

    return ExpandButtonConfig(
        fillColor = fillColor,
        iconColor = iconColor,
        strokeColor = strokeColor,
        marginTop = marginTop?.dp ?: 0.dp,
        marginEnd = marginEnd?.dp ?: 0.dp,
        marginBottom = marginBottom?.dp ?: 0.dp,
        marginStart = marginStart?.dp ?: 0.dp,
        imageUrl = imageUrl
    )
}


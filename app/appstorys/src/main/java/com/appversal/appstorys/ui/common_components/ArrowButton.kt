package com.appversal.appstorys.ui.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import coil.compose.AsyncImage
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class ArrowButtonConfig(
    val fillColor: Color = Color.Transparent,
    val iconColor: Color = Color.White,  // Maps to "cross" color in JSON
    val strokeColor: Color = Color.Transparent,

    val marginTop: Dp = 0.dp,
    val marginEnd: Dp = 0.dp,
    val marginBottom: Dp = 0.dp,
    val marginStart: Dp = 0.dp,

    val size: Dp = 28.dp,

    val imageUrl: String? = null
)

/**
 * ArrowButton - A back/arrow button component
 * Following the same architecture as CrossButton and other common buttons
 */
@Composable
internal fun ArrowButton(
    modifier: Modifier = Modifier,
    config: ArrowButtonConfig = ArrowButtonConfig(),
    onBack: () -> Unit
) {
    val buttonSize = config.size

    val borderWidth = remember(buttonSize) {
        (buttonSize * 0.05f)//.coerceIn(0.5.dp, 2.dp)
    }

    val iconPadding = (buttonSize * 0.22f).coerceIn(3.dp, 8.dp)

    Box(
        modifier = modifier
            .padding(
                top = config.marginTop,
                end = config.marginEnd,
                bottom = config.marginBottom,
                start = config.marginStart
            )
            .size(buttonSize)
            .clip(CircleShape)
            .background(config.fillColor)
            .then(
                if (config.strokeColor != Color.Transparent) {
                    Modifier.border(borderWidth, config.strokeColor, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable { onBack() },
        contentAlignment = Alignment.Center
    ) {
        if (!config.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = config.imageUrl,
                contentDescription = "Back",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = config.iconColor,
                //modifier = Modifier.padding(iconPadding)
            )
        }
    }
}

/**
 * Factory function to create ArrowButtonConfig from raw values
 * Note: "cross" color in JSON maps to iconColor
 */
fun createArrowButtonConfig(
    fillColorString: String? = null,
    iconColorString: String? = null,  // Maps to "cross" in JSON
    strokeColorString: String? = null,
    marginTop: Int? = null,
    marginEnd: Int? = null,
    marginBottom: Int? = null,
    marginStart: Int? = null,
    size: Int? = null,
    imageUrl: String? = null
): ArrowButtonConfig {
    return ArrowButtonConfig(
        fillColor = parseColorString(fillColorString) ?: Color.Transparent,
        iconColor = parseColorString(iconColorString) ?: Color.White,
        strokeColor = parseColorString(strokeColorString) ?: Color.Transparent,
        marginTop = marginTop?.dp ?: 0.dp,
        marginEnd = marginEnd?.dp ?: 0.dp,
        marginBottom = marginBottom?.dp ?: 0.dp,
        marginStart = marginStart?.dp ?: 0.dp,
        size = size?.dp ?: 28.dp,
        imageUrl = imageUrl
    )
}

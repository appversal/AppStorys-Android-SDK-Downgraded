package com.appversal.appstorys.ui.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
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

data class LikeButtonConfig(
    val fillColor: Color = Color.Transparent,
    val iconColor: Color = Color.White,  // Maps to "arrow" color in JSON
    val strokeColor: Color = Color.Transparent,

    val marginTop: Dp = 0.dp,
    val marginEnd: Dp = 0.dp,
    val marginBottom: Dp = 0.dp,
    val marginStart: Dp = 0.dp,

    val size: Dp = 24.dp,

    val imageUrl: String? = null
)

/**
 * LikeButton - A button for liking content (toggleable)
 * Following the same architecture as CrossButton and other common buttons
 */
@Composable
internal fun LikeButton(
    modifier: Modifier = Modifier,
    likedConfig: LikeButtonConfig = LikeButtonConfig(),
    unlikedConfig: LikeButtonConfig = LikeButtonConfig(),
    isLiked: Boolean,
    onToggle: () -> Unit
) {
    // Select the appropriate config based on current state
    val activeConfig = if (isLiked) likedConfig else unlikedConfig
    val buttonSize = activeConfig.size

    val borderWidth = remember(buttonSize) {
        (buttonSize * 0.05f)//.coerceIn(0.5.dp, 2.dp)
    }

    val iconPadding = (buttonSize * 0.22f).coerceIn(3.dp, 8.dp)

    Box(
        modifier = modifier
            .padding(
                top = activeConfig.marginTop,
                end = activeConfig.marginEnd,
                bottom = activeConfig.marginBottom,
                start = activeConfig.marginStart
            )
            .size(buttonSize)
            .clip(CircleShape)
            .background(activeConfig.fillColor)
            .then(
                if (activeConfig.strokeColor != Color.Transparent) {
                    Modifier.border(borderWidth, activeConfig.strokeColor, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        if (!activeConfig.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = activeConfig.imageUrl,
                contentDescription = if (isLiked) "Liked" else "Like",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else {
            Icon(
                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (isLiked) "Liked" else "Like",
                tint = activeConfig.iconColor,
                //modifier = Modifier.padding(iconPadding)
            )
        }
    }
}

/**
 * Simplified LikeButton for non-toggle usage (single state)
 */
@Composable
internal fun LikeButton(
    modifier: Modifier = Modifier,
    config: LikeButtonConfig = LikeButtonConfig(),
    onLike: () -> Unit
) {
    val buttonSize = config.size

    val borderWidth = remember(buttonSize) {
        (buttonSize * 0.08f).coerceIn(0.5.dp, 2.dp)
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
            .clickable { onLike() },
        contentAlignment = Alignment.Center
    ) {
        if (!config.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = config.imageUrl,
                contentDescription = "Like",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.FavoriteBorder,
                contentDescription = "Like",
                tint = config.iconColor,
                modifier = Modifier.padding(iconPadding)
            )
        }
    }
}

/**
 * Factory function to create LikeButtonConfig from raw values
 * Note: "arrow" color in JSON maps to iconColor
 */
fun createLikeButtonConfig(
    fillColorString: String? = null,
    iconColorString: String? = null,  // Maps to "arrow" in JSON
    strokeColorString: String? = null,
    marginTop: Int? = null,
    marginEnd: Int? = null,
    marginBottom: Int? = null,
    marginStart: Int? = null,
    size: Int? = null,
    imageUrl: String? = null
): LikeButtonConfig {
    return LikeButtonConfig(
        fillColor = parseColorString(fillColorString) ?: Color.Transparent,
        iconColor = parseColorString(iconColorString) ?: Color.White,
        strokeColor = parseColorString(strokeColorString) ?: Color.Transparent,
        marginTop = marginTop?.dp ?: 0.dp,
        marginEnd = marginEnd?.dp ?: 0.dp,
        marginBottom = marginBottom?.dp ?: 0.dp,
        marginStart = marginStart?.dp ?: 0.dp,
        size = size?.dp ?: 24.dp,
        imageUrl = imageUrl
    )
}

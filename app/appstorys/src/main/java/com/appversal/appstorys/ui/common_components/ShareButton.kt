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
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.appversal.appstorys.R

data class ShareButtonConfig(
    val fillColor: Color = Color.Transparent,
    val iconColor: Color = Color.White,
    val strokeColor: Color = Color.Transparent,

    val marginTop: Dp = 0.dp,
    val marginEnd: Dp = 0.dp,
    val marginBottom: Dp = 0.dp,
    val marginStart: Dp = 0.dp,

    val size: Dp = 24.dp,

    val imageUrl: String? = null
)

/**
 * ShareButton - A button for sharing content
 * Following the same architecture as CrossButton and other common buttons
 */
@Composable
internal fun ShareButton(
    modifier: Modifier = Modifier,
    config: ShareButtonConfig = ShareButtonConfig(),
    onShare: () -> Unit
) {
    val buttonSize = config.size

    val borderWidth = remember(buttonSize) {
        (buttonSize * 0.05f)//.coerceIn(0.5.dp, 2.dp)
    }

    val iconPadding = (buttonSize * 0.12f).coerceIn(3.dp, 8.dp)

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
            .clickable { onShare() },
        contentAlignment = Alignment.Center
    ) {
        if (!config.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = config.imageUrl,
                contentDescription = "Share",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.share),
                contentDescription = "Share",
                tint = config.iconColor,
                modifier = Modifier.padding(iconPadding)
            )
        }
    }
}

/**
 * Factory function to create ShareButtonConfig from raw values
 */
fun createShareButtonConfig(
    fillColorString: String? = null,
    iconColorString: String? = null,
    strokeColorString: String? = null,
    marginTop: Int? = null,
    marginEnd: Int? = null,
    marginBottom: Int? = null,
    marginStart: Int? = null,
    size: Int? = null,
    imageUrl: String? = null
): ShareButtonConfig {
    return ShareButtonConfig(
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

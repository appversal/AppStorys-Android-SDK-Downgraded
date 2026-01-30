package com.appversal.appstorys.ui.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import coil.compose.AsyncImage
import com.appversal.appstorys.R

/**
 * Config model for ExpandButton - represents styling for a single state (maximise or minimise)
 */
data class ExpandButtonConfig(
    val fillColor: Color = Color.Transparent,
    val iconColor: Color = Color.White,
    val strokeColor: Color = Color.Transparent,

    val marginTop: Dp = 0.dp,
    val marginEnd: Dp = 0.dp,
    val marginBottom: Dp = 0.dp,
    val marginStart: Dp = 0.dp,

    val size: Dp = 18.dp,

    val imageUrl: String? = null
)

/**
 * ExpandButton - A button that toggles between maximise and minimise states
 * Following the same architecture as CrossButton and SoundToggleButton
 */
@Composable
internal fun ExpandButton(
    modifier: Modifier = Modifier,
    maximiseConfig: ExpandButtonConfig = ExpandButtonConfig(),
    minimiseConfig: ExpandButtonConfig = ExpandButtonConfig(),
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    // Select the appropriate config based on current state
    // When expanded (maximized), show minimise button; when collapsed, show maximise button
    val activeConfig = if (isExpanded) minimiseConfig else maximiseConfig
    val buttonSize = activeConfig.size

    val borderWidth = remember(buttonSize) {
        (buttonSize * 0.05f)//.coerceIn(0.5.dp, 2.dp)
    }

    val iconPadding = (buttonSize * 0.11f)//.coerceIn(3.dp, 8.dp)

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
                } else Modifier
            )
            .clickable { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        if (!activeConfig.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = activeConfig.imageUrl,
                contentDescription = if (isExpanded) "Minimize" else "Maximize",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else {
            Icon(
                painter = painterResource(
                    if (isExpanded) R.drawable.minimize else R.drawable.expand
                ),
                contentDescription = if (isExpanded) "Minimize" else "Maximize",
                tint = activeConfig.iconColor,
                modifier = Modifier.padding(iconPadding)
            )
        }
    }
}

/**
 * Factory function to create ExpandButtonConfig from raw values
 */
fun createExpandButtonConfig(
    fillColorString: String? = null,
    iconColorString: String? = null,
    strokeColorString: String? = null,
    marginTop: Int? = null,
    marginEnd: Int? = null,
    marginBottom: Int? = null,
    marginStart: Int? = null,
    size: Int? = null,
    imageUrl: String? = null
): ExpandButtonConfig {
    return ExpandButtonConfig(
        fillColor = parseColorString(fillColorString) ?: Color.Transparent,
        iconColor = parseColorString(iconColorString) ?: Color.White,
        strokeColor = parseColorString(strokeColorString) ?: Color.Transparent,
        marginTop = marginTop?.dp ?: 0.dp,
        marginEnd = marginEnd?.dp ?: 0.dp,
        marginBottom = marginBottom?.dp ?: 0.dp,
        marginStart = marginStart?.dp ?: 0.dp,
        size = size?.dp ?: 18.dp,
        imageUrl = imageUrl
    )
}
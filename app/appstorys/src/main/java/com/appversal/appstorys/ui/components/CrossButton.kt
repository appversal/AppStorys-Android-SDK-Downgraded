package com.appversal.appstorys.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.appversal.appstorys.api.TooltipPadding

/**
 * Configuration for the cross button styling.
 * This is a generalized model that can be used across different campaign types
 * (banners, PIP video, modals, etc.)
 */
data class CrossButtonConfig(
    val fillColor: Color = Color.Transparent,
    val crossColor: Color = Color.White,
    val strokeColor: Color = Color.Transparent,
    val marginTop: Dp = 8.dp,
    val marginEnd: Dp = 8.dp,
    val imageUrl: String? = null
)


@Composable
internal fun CrossButton(
    size: Dp = 18.dp,
    modifier: Modifier = Modifier,
    config: CrossButtonConfig = CrossButtonConfig(),
    onClose: () -> Unit
) {
    Box(
        modifier = modifier
            .padding(
                top = config.marginTop,
                end = config.marginEnd
            )
            .size(size)
            .clip(CircleShape)
            .background(config.fillColor)
            .then(
                if (config.strokeColor != Color.Transparent) {
                    Modifier.border(1.dp, config.strokeColor, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        if (!config.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = config.imageUrl,
                contentDescription = "Close",
                modifier = Modifier.padding(4.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = null,
                tint = config.crossColor,
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}

/**
 * Helper function to create CrossButtonConfig from raw color strings and margins.
 * Useful when receiving styling from API responses.
 */
fun createCrossButtonConfig(
    fillColorString: String? = null,
    crossColorString: String? = null,
    strokeColorString: String? = null,
    marginTop: Int? = null,
    marginEnd: Int? = null,
    imageUrl: String? = null
): CrossButtonConfig {
    val fillColor = try {
        fillColorString?.let { Color(it.toColorInt()) }
    } catch (_: Exception) {
        null
    } ?: Color.Transparent

    val crossColor = try {
        crossColorString?.let { Color(it.toColorInt()) }
    } catch (_: Exception) {
        null
    } ?: Color.White

    val strokeColor = try {
        strokeColorString?.let { Color(it.toColorInt()) }
    } catch (_: Exception) {
        null
    } ?: Color.Transparent

    return CrossButtonConfig(
        fillColor = fillColor,
        crossColor = crossColor,
        strokeColor = strokeColor,
        marginTop = marginTop?.dp ?: 8.dp,
        marginEnd = marginEnd?.dp ?: 8.dp,
        imageUrl = imageUrl
    )
}

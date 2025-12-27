package com.appversal.appstorys.ui.components

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt

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
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    boundaryPadding: Dp? = null,
    config: CrossButtonConfig = CrossButtonConfig(),
    onClose: () -> Unit
) {
    val safePadding = boundaryPadding ?: 0.dp
    Box(
        modifier = modifier
            .padding(
                top = config.marginTop + safePadding ,
                end = config.marginEnd + safePadding
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
        if (!config.imageUrl.isNullOrBlank() && config.imageUrl.startsWith("http"))  {
            AsyncImage(
                model = config.imageUrl,
                contentDescription = "Close",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
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

fun parseColorString(colorString: String?): Color? {
    return try {
        colorString?.let {
            when (it.trim().lowercase()) {
                "white" -> Color.White
                "black" -> Color.Black
                "red" -> Color.Red
                "green" -> Color.Green
                "blue" -> Color.Blue
                "yellow" -> Color.Yellow
                "gray", "grey" -> Color.Gray
                "transparent" -> Color.Transparent
                else -> Color(it.toColorInt())
            }
        }
    } catch (_: Exception) {
        null
    }
}

fun createCrossButtonConfig(
    fillColorString: String? = null,
    crossColorString: String? = null,
    strokeColorString: String? = null,
    marginTop: Int? = null,
    marginEnd: Int? = null,
    imageUrl: String? = null
): CrossButtonConfig {
    val fillColor = parseColorString(fillColorString) ?: Color.Transparent
    val crossColor = parseColorString(crossColorString) ?: Color.White
    val strokeColor = parseColorString(strokeColorString) ?: Color.Transparent
    return CrossButtonConfig(
        fillColor = fillColor,
        crossColor = crossColor,
        strokeColor = strokeColor,
        marginTop = marginTop?.dp ?: 8.dp,
        marginEnd = marginEnd?.dp ?: 8.dp,
        imageUrl = imageUrl
    )
}
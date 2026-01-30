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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import coil.request.ImageRequest
import com.appversal.appstorys.R

data class CrossButtonConfig(
    val fillColor: Color = Color.Transparent,
    val crossColor: Color = Color.White,
    val strokeColor: Color = Color.Transparent,

    val marginTop: Dp = 0.dp,
    val marginEnd: Dp = 0.dp,

    val size: Dp = 18.dp,

    val imageUrl: String? = null
)

@Composable
internal fun CrossButton(
    modifier: Modifier = Modifier,
    shouldUseSize: Boolean = false,
    size: Dp = 18.dp,
    config: CrossButtonConfig = CrossButtonConfig(),
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val buttonSize = config.size

    val borderWidth = remember(buttonSize) {
        (buttonSize * 0.05f)//.coerceIn(0.5.dp, 2.dp)
    }

    val iconPadding = (buttonSize * 0.11f)//.coerceIn(2.dp, 8.dp)

    Box(
        modifier = modifier
            .padding(
                top = config.marginTop,
                end = config.marginEnd,
            )
            .size(
                if(shouldUseSize){
                    size
                } else {
                    buttonSize
                }
            )
            .clip(CircleShape)
            .background(config.fillColor)
            .then(
                if (config.strokeColor != Color.Transparent) {
                    Modifier.border(borderWidth, config.strokeColor, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        if (!config.imageUrl.isNullOrBlank()) {
            val imageRequest = remember(config.imageUrl) {
                ImageRequest.Builder(context)
                    .data(config.imageUrl)
                    .crossfade(true)
                    .build()
            }

            AsyncImage(
                model = imageRequest,
                contentDescription = "Close",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.cross),
                contentDescription = "Close",
                tint = config.crossColor,
                modifier = Modifier.padding(iconPadding)
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

/**
 * Factory function to create CrossButtonConfig from raw values
 */
fun createCrossButtonConfig(
    fillColorString: String? = null,
    crossColorString: String? = null,
    strokeColorString: String? = null,
    marginTop: Int? = null,
    marginEnd: Int? = null,
    size: Int? = null,
    imageUrl: String? = null
): CrossButtonConfig {
    return CrossButtonConfig(
        fillColor = parseColorString(fillColorString) ?: Color.Transparent,
        crossColor = parseColorString(crossColorString) ?: Color.White,
        strokeColor = parseColorString(strokeColorString) ?: Color.Transparent,
        marginTop = marginTop?.dp ?: 0.dp,
        marginEnd = marginEnd?.dp ?: 0.dp,
        size = size?.dp ?: 18.dp,
        imageUrl = imageUrl
    )
}
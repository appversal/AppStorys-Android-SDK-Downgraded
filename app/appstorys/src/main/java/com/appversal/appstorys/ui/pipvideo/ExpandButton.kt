package com.appversal.appstorys.ui.pipvideo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import coil.compose.AsyncImage
import com.appversal.appstorys.R
import com.appversal.appstorys.api.ExpandControls

/**
 * Config model (UNCHANGED – kept for backward compatibility)
 */
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
    applyMargins: Boolean = true,
    boundaryPadding: Dp? = null,
    onToggle: () -> Unit
) {
    val safePadding = boundaryPadding ?: 0.dp
    val isEnabled = expandControls?.enabled ?: true
    if (!isEnabled) return

    // Pick active backend config ONCE
    val activeConfig = if (isMaximized) {
        expandControls?.minimise
    } else {
        expandControls?.maximise
    }

    // ---------- Helpers ----------
    fun parseColor(value: String?, fallback: Color): Color =
        runCatching { value?.let { Color(it.toColorInt()) } }.getOrNull() ?: fallback

    fun parseStroke(value: String?): Color? =
        runCatching { value?.let { Color(it.toColorInt()) } }.getOrNull()

    fun marginDp(value: String?): Dp =
        if (applyMargins) value?.toIntOrNull()?.dp ?: 0.dp else 0.dp
    // -----------------------------

    val fillColor = parseColor(activeConfig?.colors?.fill, Color.Transparent)
    val iconColor = parseColor(activeConfig?.colors?.cross, Color.White)
    val strokeColor = parseStroke(activeConfig?.colors?.stroke)

    val paddingValues = PaddingValues(
        top = marginDp(activeConfig?.margin?.top) + safePadding,
        end = marginDp(activeConfig?.margin?.right) + safePadding,
        bottom = marginDp(activeConfig?.margin?.bottom) + safePadding,
        start = marginDp(activeConfig?.margin?.left) + safePadding
    )

    /**
     * 🔑 FIX FOR YOUR SCREENSHOT ISSUE
     * Padding scales with button size instead of being hardcoded
     */
    val iconPadding = (size * 0.25f).coerceIn(3.dp, 8.dp)

    Box(
        modifier = modifier
            .padding(paddingValues)
            .size(size)
            .clip(CircleShape)
            .background(fillColor)
            .then(
                strokeColor?.let {
                    Modifier.border(1.dp, it, CircleShape)
                } ?: Modifier
            )
            .clickable { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        when {
            isMaximized && !minimiseImageUrl.isNullOrBlank() -> {
                AsyncImage(
                    model = minimiseImageUrl,
                    contentDescription = "Minimize",
                    modifier = Modifier.padding(iconPadding)
                )
            }

            !isMaximized && !maximiseImageUrl.isNullOrBlank() -> {
                AsyncImage(
                    model = maximiseImageUrl,
                    contentDescription = "Maximize",
                    modifier = Modifier.padding(iconPadding)
                )
            }

            else -> {
                Icon(
                    painter = painterResource(
                        if (isMaximized) R.drawable.minimize else R.drawable.expand
                    ),
                    contentDescription = if (isMaximized) "Minimize" else "Maximize",
                    tint = iconColor,
                    modifier = Modifier.padding(iconPadding)
                )
            }
        }
    }
}

/**
 * Helper function (UNCHANGED behavior)
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
    val fillColor = runCatching {
        fillColorString?.let { Color(it.toColorInt()) }
    }.getOrNull() ?: Color.Black.copy(alpha = 0.5f)

    val iconColor = runCatching {
        iconColorString?.let { Color(it.toColorInt()) }
    }.getOrNull() ?: Color.White

    val strokeColor = runCatching {
        strokeColorString?.let { Color(it.toColorInt()) }
    }.getOrNull()

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

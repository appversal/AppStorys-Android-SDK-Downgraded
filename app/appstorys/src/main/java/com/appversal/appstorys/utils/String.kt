package com.appversal.appstorys.utils

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun isGifUrl(url: String): Boolean {
    return url.endsWith(".gif", true)
}

internal fun String?.toColor(defaultColor: Color = Color.Black) = try {
    when {
        this.isNullOrEmpty() -> defaultColor
        else -> Color(android.graphics.Color.parseColor(this))
    }
} catch (_: Exception) {
    defaultColor
}

internal fun String?.toDp(defaultDp: Dp = 0.dp): Dp = this?.toFloatOrNull()?.dp ?: defaultDp

internal fun Context.pxToDp(px: Float): Dp {
    return (px / resources.displayMetrics.density).dp
}
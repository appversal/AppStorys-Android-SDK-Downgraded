package com.appversal.appstorys.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt


internal fun String?.toColor(defaultColor: Color = Color.Black) = try {
    when {
        this.isNullOrEmpty() -> defaultColor
        else -> Color(toColorInt())
    }
} catch (_: Exception) {
    defaultColor
}

internal fun String?.toDp(defaultDp: Dp = 0.dp): Dp = this?.toFloatOrNull()?.dp ?: defaultDp

internal inline fun String?.ifNullOrBlank(defaultValue: () -> String?): String? {
    return when {
        this.isNullOrBlank() -> defaultValue()
        else -> this
    }
}

internal val String.isGif: Boolean
    get() = this.endsWith(".gif", true)
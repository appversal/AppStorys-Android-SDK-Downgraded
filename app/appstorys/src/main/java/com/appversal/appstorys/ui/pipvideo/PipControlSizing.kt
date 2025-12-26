package com.appversal.appstorys.ui.pipvideo

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun pipControlSize(
    pipWidth: Dp,
    pipHeight: Dp,
    factor: Float = 0.18f,
    min: Dp = 18.dp,
    max: Dp = 44.dp
): Dp {
    val base = minOf(pipWidth, pipHeight)
    val calculated = base * factor
    return calculated.coerceIn(min, max)
}

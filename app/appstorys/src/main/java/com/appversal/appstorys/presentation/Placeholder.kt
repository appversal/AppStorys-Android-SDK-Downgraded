package com.appversal.appstorys.presentation


sealed interface Placeholder {
    data class Drawable(val value: android.graphics.drawable.Drawable) : Placeholder
    data class Composable(
        val content: @androidx.compose.runtime.Composable () -> Unit
    ) : Placeholder
}
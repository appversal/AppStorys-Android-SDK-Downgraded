package com.appversal.appstorys.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.appversal.appstorys.domain.LocalScreenContext
import com.appversal.appstorys.domain.model.ScreenContext
import com.appversal.appstorys.domain.model.ScreenOptions

@Composable
internal fun Screen(
    modifier: Modifier = Modifier,
    name: String,
    options: ScreenOptions? = null,
    content: @Composable () -> Unit
) {
    LaunchedEffect(Unit) {
        // TODO: Track screen
    }

    CompositionLocalProvider(
        LocalScreenContext provides ScreenContext(
            name = name,
            options = options
        ),
        content = content
    )
}
package com.appversal.appstorys.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.appversal.appstorys.domain.LocalScreenContext
import com.appversal.appstorys.domain.model.ScreenContext
import com.appversal.appstorys.domain.model.ScreenOptions
import com.appversal.appstorys.domain.usecase.screenTracked
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun Container(
    modifier: Modifier = Modifier,
    name: String? = null,
    options: ScreenOptions? = null,
    content: @Composable () -> Unit
) {
    var screenName by remember { mutableStateOf(name) }

    LaunchedEffect(Unit) {
        if (!name.isNullOrBlank()) {
            return@LaunchedEffect
        }

        screenTracked.collectLatest {
            screenName = it
        }
    }

    CompositionLocalProvider(
        LocalScreenContext provides ScreenContext(
            name = screenName ?: "Unknown",
            options = options
        ),
        content = {
            Box(
                modifier = modifier,
                content = {
                    content()
                    Overlay()
                }
            )
        }
    )
}
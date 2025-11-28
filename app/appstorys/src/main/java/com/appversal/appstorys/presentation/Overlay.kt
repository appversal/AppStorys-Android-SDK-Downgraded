package com.appversal.appstorys.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appversal.appstorys.domain.LocalScreenContext
import com.appversal.appstorys.domain.State
import kotlinx.coroutines.flow.map

@Composable
internal fun Overlay(modifier: Modifier = Modifier) {
    val screen = LocalScreenContext.current?.name

    val isCapturing by State.isCapturing.map {
        !screen.isNullOrBlank() && it[screen] == true
    }.collectAsStateWithLifecycle(false)

    AnimatedVisibility(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        visible = !screen.isNullOrBlank() && !isCapturing,
        content = {
            Box(
                modifier = Modifier.fillMaxSize(),
                content = {
                    Banner()
                    BottomSheet()
                    CaptureScreenButton()
                    Csat()
                    Floater()
                    Modal()
                    Pip()
                    Survey()
                    Tooltip()
                }
            )
        }
    )
}
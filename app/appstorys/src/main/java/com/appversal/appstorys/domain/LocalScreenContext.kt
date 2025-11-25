package com.appversal.appstorys.domain

import androidx.compose.runtime.staticCompositionLocalOf
import com.appversal.appstorys.domain.model.ScreenContext

internal val LocalScreenContext = staticCompositionLocalOf<ScreenContext> {
    error("No ScreenContext provided")
}
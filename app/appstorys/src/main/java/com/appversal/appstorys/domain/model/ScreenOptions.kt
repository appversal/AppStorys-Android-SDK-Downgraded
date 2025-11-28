package com.appversal.appstorys.domain.model

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp

data class ScreenOptions(
    val positionList: List<String> = emptyList(),
    val padding: PaddingValues? = null,
    val pipPadding: PaddingValues? = null,
    val floaterPadding: Dp? = null,
    val bannerPadding: Dp? = null,
    val csatPadding: Dp? = null,
)

internal data class ScreenContext(
    val name: String,
    val options: ScreenOptions? = null
)
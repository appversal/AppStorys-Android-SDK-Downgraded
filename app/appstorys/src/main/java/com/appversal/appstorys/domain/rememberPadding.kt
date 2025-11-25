package com.appversal.appstorys.domain

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


@Composable
internal fun rememberPadding(
    type: String,
    fallback: PaddingValues = PaddingValues(0.dp)
): PaddingValues {
    val options = LocalScreenContext.current.options
    return remember(options, fallback) {
        fun Dp.toValues() = PaddingValues(this)

        when {
            options == null -> fallback
            type == "PIP" -> options.pipPadding ?: options.padding ?: fallback
            type == "FLT" -> options.floaterPadding?.toValues() ?: options.padding ?: fallback
            type == "BAN" -> options.bannerPadding?.toValues() ?: options.padding ?: fallback
            type == "CSAT" -> options.csatPadding?.toValues() ?: options.padding ?: fallback
            else -> fallback
        }
    }
}
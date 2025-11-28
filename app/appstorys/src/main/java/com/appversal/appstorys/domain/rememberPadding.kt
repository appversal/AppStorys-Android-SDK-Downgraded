package com.appversal.appstorys.domain

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp


@Composable
internal fun rememberPadding(
    type: String,
    fallback: PaddingValues = PaddingValues(0.dp)
): PaddingValues {
    val options = LocalScreenContext.current?.options
    return remember(options, fallback) {
        when {
            options == null -> fallback
            type == "PIP" -> options.pipPadding.merge(options.padding) ?: fallback
            type == "FLT" -> options.floaterPadding?.toValues().merge(options.padding) ?: fallback
            type == "BAN" -> options.bannerPadding?.toValues().merge(options.padding) ?: fallback
            type == "CSAT" -> options.csatPadding?.toValues().merge(options.padding) ?: fallback
            else -> fallback
        }
    }
}

private fun Dp.toValues() = PaddingValues(this)

private fun PaddingValues?.merge(other: PaddingValues?): PaddingValues? {
    if (this == null) {
        return other
    }

    val other = other ?: PaddingValues(0.dp)
    return PaddingValues(
        start = this.calculateStartPadding(layoutDirection = LayoutDirection.Ltr) +
                other.calculateStartPadding(layoutDirection = LayoutDirection.Ltr),
        top = this.calculateTopPadding() + other.calculateTopPadding(),
        end = this.calculateEndPadding(layoutDirection = LayoutDirection.Ltr) +
                other.calculateEndPadding(layoutDirection = LayoutDirection.Ltr),
        bottom = this.calculateBottomPadding() + other.calculateBottomPadding()
    )
}
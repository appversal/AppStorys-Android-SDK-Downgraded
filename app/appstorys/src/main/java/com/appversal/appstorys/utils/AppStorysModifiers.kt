package com.appversal.appstorys.utils

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.semantics
import com.appversal.appstorys.ui.OverlayContainer
import android.util.Log
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow

@Stable
fun Modifier.appstorys(tag: String): Modifier = this
    .semantics {
        appstorysViewTagProperty = tag
    }
    .onGloballyPositioned {
        val bounds = it.boundsInRoot()
        val window = it.boundsInWindow()
        Log.e(
            "TooltipDebug",
            "[Compose] [$tag] w=${it.size.width} h=${it.size.height} " +
                    "boundsInRoot=$bounds boundsInWindow=$window"
        )
        if (it.size.width <= 0 || it.size.height <= 0) {
            Log.e(
                "TooltipDebug",
                "[Compose] [$tag] WARNING: zero dimensions — constraint skipped by Compose layout"
            )
        }
        OverlayContainer.addConstraint(tag, it)
    }
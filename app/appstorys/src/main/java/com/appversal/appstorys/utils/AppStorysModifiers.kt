package com.appversal.appstorys.utils

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.semantics
import com.appversal.appstorys.domain.usecase.appstorysViewTagProperty
import com.appversal.appstorys.ui.OverlayContainer

@Stable
fun Modifier.appstorys(tag: String): Modifier = this
    .semantics {
        appstorysViewTagProperty = tag
    }
    .onGloballyPositioned {
        OverlayContainer.addConstraint(tag, it)
    }
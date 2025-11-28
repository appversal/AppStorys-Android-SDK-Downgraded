package com.appversal.appstorys.presentation.xml

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.Keep
import androidx.compose.ui.viewinterop.AndroidView
import com.appversal.appstorys.AppStorys

@Keep
class ContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : InternalOverlayView(
    context,
    attrs,
    defStyleAttr,
    content = { name, options, view ->
        AppStorys.Container(
            name = name,
            options = options,
            content = {
                AndroidView(factory = { view })
            }
        )
    }
)
package com.appversal.appstorys.ui.xml

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.Keep
import androidx.compose.ui.platform.ComposeView
import com.appversal.appstorys.AppStorys


@Keep class BottomSheetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    init {
        addView(
            ComposeView(context).apply {
                setContent {
                        AppStorys.BottomSheet()
                }
            }
        )
    }
}
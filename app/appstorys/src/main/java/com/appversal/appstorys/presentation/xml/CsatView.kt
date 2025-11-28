package com.appversal.appstorys.presentation.xml

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.Keep
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.withStyledAttributes
import com.appversal.appstorys.AppStorys
import com.appversal.appstorys.R

@Keep class CsatView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private var bottomPadding = 0

    init {
        attrs?.let {
            context.withStyledAttributes(it, R.styleable.CsatView) {
                bottomPadding = getDimensionPixelSize(
                    R.styleable.CsatView_bottomPadding,
                    0
                )
            }
        }
        addView(
            ComposeView(context).apply {
                setContent {
                    AppStorys.Csat(
                        bottomPadding = bottomPadding.toDp()
                    )
                }
            }
        )
    }
}
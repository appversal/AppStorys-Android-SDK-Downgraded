package com.appversal.appstorys.ui.xml

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.Keep
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.withStyledAttributes
import com.appversal.appstorys.AppStorys
import com.appversal.appstorys.R

@Keep
class WidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var position: String? = null
    private var placeholder: Drawable? = null

    private var composed = false

    init {

        attrs?.let {
            context.withStyledAttributes(it, R.styleable.WidgetView) {
                placeholder = getDrawable(R.styleable.WidgetView_placeholder)
                position = getString(R.styleable.WidgetView_position)
            }
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        if (composed) {
            return
        }
        placeholderContent {
            addView(
                ComposeView(context).apply {
                    setContent {
                        AppStorys.Widget(
                            placeholder = placeholder,
                            position = position
                        )
                    }
                }
            )
            composed = true
        }
    }
}


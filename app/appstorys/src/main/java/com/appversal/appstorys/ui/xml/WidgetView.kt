package com.appversal.appstorys.ui.xml

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.Keep
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.withStyledAttributes
import com.appversal.appstorys.AppStorys
import com.appversal.appstorys.R
import com.appversal.appstorys.utils.pxToDp

@Keep class WidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var position: String? = null
    private var placeholder: Drawable? = null

    private var composed = false
    private val staticWidth: MutableState<Dp?> = mutableStateOf(null)

    init {

        attrs?.let {
            context.withStyledAttributes(it, R.styleable.WidgetView) {
                placeholder = getDrawable(R.styleable.WidgetView_placeholder)
                position = getString(R.styleable.WidgetView_position)
            }
        }

        addView(
            ComposeView(context).apply {
                setContent {
                    AppStorys.Widget(
                        modifier = Modifier,
                        placeholder = placeholder,
                        position = position
                    )
                }
            }
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val heightPx = MeasureSpec.getSize(heightMeasureSpec)
        val widthPx = MeasureSpec.getSize(widthMeasureSpec)

        val newWidth = context.pxToDp(widthPx.toFloat())

        if (staticWidth.value != newWidth) {
            staticWidth.value = newWidth
        }
    }


    override fun onFinishInflate() {
        super.onFinishInflate()
    }
}


package com.appversal.appstorys.ui.xml

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.Keep
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.withStyledAttributes
import com.appversal.appstorys.R
import com.appversal.appstorys.presentation.Banner
import com.appversal.appstorys.presentation.Placeholder

@Keep
class BannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private var composed = false
    private var placeholder: Drawable? = null
    private var bottomPadding = 0

    init {
        attrs?.let {
            context.withStyledAttributes(it, R.styleable.BannerView) {
                placeholder = getDrawable(R.styleable.BannerView_placeholder)
                bottomPadding = getDimensionPixelSize(R.styleable.BannerView_bottomPadding, 0)
            }
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        if (composed) {
            return
        }
        placeholderContent { content ->
            addView(
                ComposeView(context).apply {
                    setContent {
                        Banner(
                            placeholder = when {
                                placeholder != null -> Placeholder.Drawable(placeholder!!)
                                content != null -> Placeholder.Composable(content)
                                else -> null
                            },
                            bottomPadding = bottomPadding.toDp()
                        )
                    }
                }
            )
            composed = true
        }
    }
}

package com.appversal.appstorys.presentation.xml

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.children
import androidx.core.view.isNotEmpty

@Composable
internal fun Number.toDp(): Dp {
    return (this.toFloat() / LocalDensity.current.density).dp
}

internal fun ViewGroup.placeholderContent(
    onContent: ((@Composable () -> Unit)?) -> Unit
) = post {
    when {
        // use the children view as the placeholder content if available
        isNotEmpty() -> {
            val originalViews = children.toList()

            onContent {
                AndroidView(
                    factory = { context ->
                        // Create a FrameLayout to hold the children
                        FrameLayout(context).also { androidView ->
                            // Add all children views as placeholder content
                            for (view in originalViews) {
                                // We need to remove the view from any previous parent
                                (view.parent as? ViewGroup)?.removeView(view)
                                // ensure the view has layout params
                                if (view.layoutParams == null) {
                                    view.layoutParams = LayoutParams(
                                        LayoutParams.MATCH_PARENT,
                                        LayoutParams.WRAP_CONTENT
                                    )
                                }
                                androidView.addView(view)
                            }
                        }
                    },
                    update = { view ->
                        view.requestLayout()
                    }
                )
            }
        }

        else -> {
            // No children available, use the default placeholder content
            onContent(null)
        }
    }
}

internal fun View.onLayoutChanges(callback: () -> Unit) {
    val listener = ViewTreeObserver.OnGlobalLayoutListener { callback() }

    viewTreeObserver.addOnGlobalLayoutListener(listener)

    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {}

        override fun onViewDetachedFromWindow(v: View) {
            viewTreeObserver.removeOnGlobalLayoutListener(listener)
            removeOnAttachStateChangeListener(this)
        }
    })
}
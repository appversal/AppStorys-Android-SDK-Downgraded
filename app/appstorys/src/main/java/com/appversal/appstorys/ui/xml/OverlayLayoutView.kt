package com.appversal.appstorys.ui.xml

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.Keep
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.content.withStyledAttributes
import com.appversal.appstorys.AppStorys.tooltipTargetView
import com.appversal.appstorys.R
import com.appversal.appstorys.ui.OverlayContainer
import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.doOnLayout
import com.appversal.appstorys.AppStorys

/**
 * `OverlayLayoutView` is a custom `FrameLayout` that integrates Compose content into a traditional
 * Android View hierarchy. It is responsible for rendering overlays such as tooltips and highlights
 * using the `OverlayContainer` composable.
 *
 * This view listens for tooltip target updates and dynamically updates the constraints for the
 * target views.
 *
 * @constructor Creates an instance of `OverlayLayoutView`.
 * @param context The `Context` in which the view is running.
 * @param attrs The attributes of the XML tag that is inflating the view.
 * @param defStyleAttr An attribute in the current theme that contains a reference to a style resource.
 */
@Keep
class OverlayLayoutView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private var topPadding = 0
    private var bottomPadding = 0
    private var bannerBottomPadding = 0
    private var floaterBottomPadding = 0
    private var pipTopPadding = 0
    private var pipBottomPadding = 0
    private var csatBottomPadding = 0
    private var insideBottomSheet = false

    private var _activity by mutableStateOf<Activity?>(null)

    fun setActivity(activity: Activity) {
        this._activity = activity
    }

    init {
        attrs?.let(::loadPaddings)

        if (insideBottomSheet) {
            // ── Bottom-sheet mode ────────────────────────────────────────────────────
            // Use a 0×0 ComposeView so this view takes no space in the layout.
            // Both TestUserButton and TooltipsOnly() use Popup internally, so they
            // render in their own windows above this BottomSheet window.
            addView(
                ComposeView(context).apply {
                    layoutParams = LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT
                    )
                    setContent {
                        val resolvedActivity = _activity ?: (context as? Activity)
                        ?: generateSequence(context) {
                            (it as? android.content.ContextWrapper)?.baseContext
                        }.filterIsInstance<Activity>().firstOrNull()

                        AppStorys.TestUserButton(activity = resolvedActivity)
                    }
                }
            )

            addView(
                ComposeView(context).apply {
                    layoutParams = LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT
                    )
                    setContent {
                        LaunchedEffect(Unit) {
                            tooltipTargetView.collect { target ->
                                handleTargetView(target?.target)
                            }
                        }
                        OverlayContainer.TooltipsOnly()
                    }
                }
            )
        } else {
            addView(
                ComposeView(context).apply {
                    elevation = 1000f

                    setContent {
                        LaunchedEffect(Unit) {
                            tooltipTargetView.collect { target ->
                                handleTargetView(target?.target)
                            }
                        }

                        val resolvedActivity = _activity ?: (context as? Activity)

                        OverlayContainer.Content(
                            topPadding = topPadding.toDp(),
                            bottomPadding = bottomPadding.toDp(),
                            bannerBottomPadding = bannerBottomPadding.toDp(),
                            floaterBottomPadding = floaterBottomPadding.toDp(),
                            pipTopPadding = pipTopPadding.toDp(),
                            pipBottomPadding = pipBottomPadding.toDp(),
                            csatBottomPadding = csatBottomPadding.toDp(),
                            activity = resolvedActivity
                        )
                    }
                }
            )
        }
    }

    /**
     * Retrieves a `View` by its ID string.
     *
     * @param id The string identifier of the view.
     * @return The `View` object if found, or `null` if not found.
     */
    @SuppressLint("DiscouragedApi")
    private fun getViewId(id: String): View? {
        val viewId = context.resources.getIdentifier(id, "id", context.packageName)
        if (viewId == 0) {
            Log.e("OverlayLayoutView", "View resource ID not found for name: $id")
            return null
        }
        // Search from the window root so we can find sibling views (e.g. cells
        // inside a RecyclerView that sits next to OverlayLayoutView in the layout).
        return rootView?.findViewById(viewId)
            ?: run {
                Log.e("OverlayLayoutView", "View $id not found in window tree")
                null
            }
    }

    /**
     * Handles the target view by adding its constraints to the `OverlayContainer`.
     *
     * @param target The string identifier of the target view.
     */
    private fun handleTargetView(target: String?) {
        if (target == null) {
            Log.e("OverlayLayoutView", "Target view is null")
            return
        }

        val view = getViewId(target)
        if (view == null) {
            Log.e(
                "TooltipDebug",
                "[$target] View ID not found in layout. Is the view in the same window as OverlayLayoutView?"
            )
            return
        }

        fun registerIfReady() {
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)

            val rect = android.graphics.Rect()
            val globallyVisible = view.getGlobalVisibleRect(rect)

            Log.e(
                "TooltipDebug",
                "[$target] w=${view.width} h=${view.height} " +
                        "screenX=${loc[0]} screenY=${loc[1]} " +
                        "isShown=${view.isShown} visibility=${view.visibility} " +
                        "globallyVisible=$globallyVisible visibleRect=$rect"
            )

            if (view.width <= 0 || view.height <= 0) {
                Log.e(
                    "TooltipDebug",
                    "[$target] Skipping — zero dimensions, will retry on next layout pass"
                )
                return
            }

            if (!globallyVisible || rect.width() == 0 || rect.height() == 0) {
                Log.e(
                    "TooltipDebug",
                    "[$target] Skipping — not globally visible yet"
                )
                return
            }

            Log.e("TooltipDebug", "[$target] Registering constraint ✓")
            OverlayContainer.addViewConstraint(target, view)
        }

        // Add the view's constraints to the OverlayContainer.
//        OverlayContainer.addViewConstraint(target, view)
//
//        // Listen for layout changes to update the constraints dynamically.
//        view.onLayoutChanges {
//            OverlayContainer.addViewConstraint(target, view)
//        }

        if (view.width > 0 && view.height > 0) {
            // Already laid out — register immediately
            registerIfReady()
        } else {
            // Not yet laid out — wait for first layout pass
            Log.e(
                "TooltipDebug",
                "[$target] View not yet laid out (w=0/h=0), waiting with doOnLayout"
            )
            view.doOnLayout { registerIfReady() }
        }

// Keep re-registering on any subsequent layout changes (scroll, resize, etc.)
        view.onLayoutChanges {
            registerIfReady()
        }
    }

    private fun loadPaddings(attrs: AttributeSet) {
        context.withStyledAttributes(attrs, R.styleable.OverlayLayoutView) {
            topPadding = getDimensionPixelSize(
                R.styleable.OverlayLayoutView_topPadding,
                topPadding
            )
            bottomPadding = getDimensionPixelSize(
                R.styleable.OverlayLayoutView_bottomPadding,
                bottomPadding
            )
            bannerBottomPadding = getDimensionPixelSize(
                R.styleable.OverlayLayoutView_bannerBottomPadding,
                bannerBottomPadding
            )
            floaterBottomPadding = getDimensionPixelSize(
                R.styleable.OverlayLayoutView_floaterBottomPadding,
                floaterBottomPadding
            )
            pipTopPadding = getDimensionPixelSize(
                R.styleable.OverlayLayoutView_pipTopPadding,
                pipTopPadding
            )
            pipBottomPadding = getDimensionPixelSize(
                R.styleable.OverlayLayoutView_pipBottomPadding,
                pipBottomPadding
            )
            csatBottomPadding = getDimensionPixelSize(
                R.styleable.OverlayLayoutView_csatBottomPadding,
                csatBottomPadding
            )
            insideBottomSheet = getBoolean(
                R.styleable.OverlayLayoutView_insideBottomSheet,
                false
            )
        }
    }
}
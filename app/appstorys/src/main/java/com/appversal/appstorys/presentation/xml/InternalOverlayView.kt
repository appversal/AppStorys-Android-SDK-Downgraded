package com.appversal.appstorys.presentation.xml

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.withStyledAttributes
import com.appversal.appstorys.R
import com.appversal.appstorys.api.TooltipsDetails
import com.appversal.appstorys.domain.State
import com.appversal.appstorys.domain.model.ScreenOptions
import com.appversal.appstorys.domain.rememberCampaign
import timber.log.Timber

open class InternalOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    content: @Composable (String?, ScreenOptions?, FrameLayout) -> Unit = { _, _, _ -> }
) : FrameLayout(context, attrs, defStyleAttr) {
    private val log: Timber.Tree
        get() = Timber.tag("InternalOverlayView")

    private var screenName by mutableStateOf<String?>(null)
    private var _options by mutableStateOf<ScreenOptions?>(null)

    // The single native ViewGroup that will hold ALL children from the XML
    private val wrapperView: FrameLayout = FrameLayout(context)

    init {
        attrs?.let {
            context.withStyledAttributes(it, R.styleable.InternalOverlayView) {
                screenName = getString(R.styleable.InternalOverlayView_screenName)
            }
        }

        super.addView(
            ComposeView(context).apply {
                layoutParams = LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT
                )

                setContent {
                    WatchTargetView()
                    content(screenName, _options, wrapperView)
                }
            }
        )
    }

    override fun addView(child: View, index: Int, params: LayoutParams) {
        // 1. If the system is trying to add our own ComposeView, let it.
        if (child is ComposeView) {
            super.addView(child, index, params)
            return
        }

        // 2. All other children (from XML) are redirected to the wrapperView.
        // We ensure the child retains its original layout parameters.
        wrapperView.addView(child, index, params)
    }


    fun setName(name: String?) {
        screenName = name
    }

    fun setOptions(screenOptions: ScreenOptions?) {
        _options = screenOptions
    }

    @Composable
    private fun WatchTargetView() {
        val campaign = rememberCampaign<TooltipsDetails>("TTP")
        LaunchedEffect(campaign) {
            campaign?.details?.tooltips?.forEach { tooltip ->
                if (!tooltip.target.isNullOrBlank()) {
                    handleTargetView(tooltip.target)
                }
            }
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
        return when {
            viewId != 0 -> wrapperView.findViewById(viewId)
            else -> {
                log.e("View ID not found: $id")
                null
            }
        }
    }

    /**
     * Handles the target view by adding its constraints to the `OverlayContainer`.
     *
     * @param target The string identifier of the target view.
     */
    private fun handleTargetView(target: String?) {
        if (target == null) {
            log.e("Target view is null")
            return
        }

        val view = getViewId(target)
        if (view == null) {
            log.e("Target view not found: $target")
            return
        }

        // Add the view's constraints to the OverlayContainer.
        State.addViewConstraint(target, view)

        // Listen for layout changes to update the constraints dynamically.
        view.onLayoutChanges {
            State.addViewConstraint(target, view)
        }
    }
}
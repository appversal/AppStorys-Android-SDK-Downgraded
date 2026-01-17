package com.appversal.appstorys.ui

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.appversal.appstorys.AppStorys
import com.appversal.appstorys.AppStorys.dismissTooltip
import com.appversal.appstorys.AppStorys.tooltipTargetView
import com.appversal.appstorys.api.Tooltip
import com.appversal.appstorys.utils.AppStorysCoordinates

/**
 * `OverlayContainer` is responsible for managing and rendering overlays such as tooltips
 * and highlights in the application. It maintains constraints and tooltips, and provides
 * utility functions to add constraints and render content.
 */
object OverlayContainer {

    // Stores the mapping of target IDs to their corresponding coordinates.
    private val constraints = mutableStateMapOf<String, AppStorysCoordinates>()

    // Stores the list of active tooltips.
    private val tooltips = mutableStateListOf<Tooltip>()

    /**
     * Adds a constraint for a target using its `LayoutCoordinates`.
     *
     * @param id The unique identifier for the target.
     * @param coordinates The `LayoutCoordinates` of the target.
     */
    fun addConstraint(id: String, coordinates: LayoutCoordinates) {
        constraints[id] = coordinates.toAppStorysCoordinates()
    }

    /**
     * Adds a constraint for a target using its `View` object.
     *
     * @param id The unique identifier for the target.
     * @param view The `View` object representing the target.
     */
    fun addViewConstraint(id: String, view: View) {
        constraints[id] = view.toAppStorysCoordinates()
    }

    /**
     * Adds a tooltip to the list of active tooltips.
     *
     * @param tooltip The `Tooltip` object to be added.
     */
    fun addTooltip(tooltip: Tooltip) {
        if (tooltips.any { it.id == tooltip.id }) {
            return // Tooltip with this ID already exists
        }
        Log.e("OverlayContainer", "Adding tooltip: ${tooltip.id} with target: ${tooltip.target}")
        tooltips.add(tooltip)
    }

    /**
     * A composable function that renders overlays based on the provided constraints.
     * This function should be placed at the root of your composable hierarchy above any other content.
     *
     * @param modifier The `Modifier` to be applied to the overlay container.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    @Composable
    fun Content(
        modifier: Modifier = Modifier,
        topPadding: Dp,
        bottomPadding: Dp,
        bannerBottomPadding: Dp = 0.dp,
        floaterBottomPadding: Dp = 0.dp,
        pipTopPadding: Dp = 0.dp,
        pipBottomPadding: Dp = 0.dp,
        csatBottomPadding: Dp = 0.dp,
        activity: Activity? = null
    ) {
        // Collects the target view for tooltips and updates the tooltip list.
        LaunchedEffect(Unit) {
            tooltipTargetView.collect { target ->
                when (target) {
                    null -> tooltips.clear()
                    else -> addTooltip(target)
                }
            }
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .statusBarsPadding(),
            content = {
                AppStorys.PinnedBanner(
                    bottomPadding = bottomPadding + bannerBottomPadding,
                )

//                AppStorys.Milestone(
//                    topPadding = topPadding,
//                    bottomPadding = bottomPadding
//                )

                AppStorys.Floater(
                    bottomPadding = bottomPadding + floaterBottomPadding,
                )

                AppStorys.Pip(
                    topPadding = topPadding + pipTopPadding,
                    bottomPadding = bottomPadding + pipBottomPadding,
                )

                AppStorys.CSAT(
                    bottomPadding = bottomPadding + csatBottomPadding,
                )

                AppStorys.Milestone(
                    topPadding = topPadding,
                    bottomPadding = bottomPadding,
                    isWidgets = false
                )

                AppStorys.ScratchCard()

                AppStorys.BottomSheet()

                AppStorys.TestUserButton(
                    activity = activity
                )

                AppStorys.Survey()

                val visibleTooltips by remember {
                    derivedStateOf {
                        tooltips.filter { constraints.containsKey(it.target) }
                    }
                }

                visibleTooltips.forEach { tooltip ->
                    val coordinates by rememberUpdatedState(constraints[tooltip.target])
                    if (coordinates != null) {
                        // Renders the tooltip popup.
                        Popup(
                            popupPositionProvider = object : PopupPositionProvider {
                                override fun calculatePosition(
                                    anchorBounds: IntRect,
                                    windowSize: IntSize,
                                    layoutDirection: LayoutDirection,
                                    popupContentSize: IntSize
                                ): IntOffset = IntOffset.Zero
                            },
                            properties = PopupProperties(
                                focusable = true,
                                dismissOnBackPress = true,
                                dismissOnClickOutside = true
                            ),
                            onDismissRequest = ::dismissTooltip,
                            content = {
                                ShowcaseView(
                                    visible = true,
                                    targetCoordinates = coordinates!!,
                                    highlight = ShowcaseHighlight.Rectangular(
                                        cornerRadius = tooltip.styling?.appearance?.highlight?.radius?.dp
                                            ?: 8.dp,
                                        padding = tooltip.styling?.appearance?.highlight?.padding?.dp
                                            ?: 8.dp
                                    ),
                                    tooltip = tooltip
                                )

                                // Renders the tooltip content.
                                TooltipContent(
                                    tooltip = tooltip,
                                    coordinates = coordinates!!
                                )
                            }
                        )
                    }
                }

                AppStorys.Modals()
            }
        )
    }

    /**
     * Converts `LayoutCoordinates` to `AppStorysCoordinates`.
     *
     * @return The converted `AppStorysCoordinates`.
     */
    fun LayoutCoordinates.toAppStorysCoordinates(): AppStorysCoordinates {
        return AppStorysCoordinates(
            x = positionInRoot().x,
            y = positionInRoot().y,
            width = size.width,
            height = size.height,
            boundsInParent = { boundsInParent() },
            boundsInRoot = { boundsInRoot() },
            boundsInWindow = { boundsInWindow() }
        )
    }

    /**
     * Converts a `View` to `AppStorysCoordinates`.
     *
     * This method calculates position values similar to the `positionInRoot()` approach
     * used in Compose's LayoutCoordinates to ensure consistent positioning between
     * XML views and Compose UI elements.
     *
     * @return The converted `AppStorysCoordinates`.
     */
    fun View.toAppStorysCoordinates(): AppStorysCoordinates {
        // Get location in window (similar to Compose's coordinates system)
        val locationInWindow = IntArray(2)
        getLocationInWindow(locationInWindow)

        // Get status bar height to adjust for proper positioning
        val rootRect = Rect()
        getWindowVisibleDisplayFrame(rootRect)
        val statusBarHeight = rootRect.top

        // Calculate position relative to root, adjusting for status bar
        val rootX = locationInWindow[0].toFloat()
        val rootY = (locationInWindow[1] - statusBarHeight).toFloat()

        return AppStorysCoordinates(
            // TODO: Find out why I need to subtract 55 from rootX and rootY
            x = rootX - 55,
            y = rootY - 55,
            width = width,
            height = height,
            boundsInParent = {
                androidx.compose.ui.geometry.Rect(
                    left = left.toFloat(),
                    top = top.toFloat(),
                    right = right.toFloat(),
                    bottom = bottom.toFloat()
                )
            },
            boundsInRoot = {
                androidx.compose.ui.geometry.Rect(
                    left = rootX,
                    top = rootY,
                    right = rootX + width,
                    bottom = rootY + height
                )
            },
            boundsInWindow = {
                val locationOnScreen = IntArray(2)
                getLocationOnScreen(locationOnScreen)
                androidx.compose.ui.geometry.Rect(
                    left = locationOnScreen[0].toFloat(),
                    top = locationOnScreen[1].toFloat(),
                    right = (locationOnScreen[0] + width).toFloat(),
                    bottom = (locationOnScreen[1] + height).toFloat()
                )
            }
        )
    }
}
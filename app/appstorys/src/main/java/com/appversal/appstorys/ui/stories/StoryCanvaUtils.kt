package com.appversal.appstorys.ui.stories

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Studio editor design space: every position/size in `content.*` and per-id
 * `styling.*` arrays is expressed in this coordinate system. The slide viewport
 * is scaled to fit the actual screen using a uniform scale (so circles stay
 * circles and aspect ratios are preserved).
 */
internal const val STORY_DESIGN_WIDTH = 1080f
internal const val STORY_DESIGN_HEIGHT = 2160f

/**
 * Holds the conversion factor from canva-space pixels to actual screen dp.
 *
 * The factor is computed once per slide composition using BoxWithConstraints so
 * everything (positions, sizes, font-sizes, stroke widths, etc.) scales together.
 *
 * NOTE: we use a single uniform scale (the smaller of widthScale/heightScale)
 * to preserve aspect ratio. The remaining axis is letterboxed via the offsets
 * (offsetXDp / offsetYDp), keeping content centred.
 */
@Immutable
internal data class StoryCanvaScope(
    val scale: Float,
    val offsetXDp: Dp,
    val offsetYDp: Dp,
    val density: Density
) {
    /** Convert a canva-space x coordinate to a Dp offset within the slide box. */
    fun xDp(canvaX: Float): Dp = offsetXDp + (canvaX * scale).dp

    /** Convert a canva-space y coordinate to a Dp offset within the slide box. */
    fun yDp(canvaY: Float): Dp = offsetYDp + (canvaY * scale).dp

    /** Convert a canva-space length to Dp (no offset). */
    fun sizeDp(canvaSize: Float): Dp = (canvaSize * scale).dp

    /**
     * Font sizes in the studio are stored in canva-space px. Converting them to
     * sp directly would make text scale with system font scaling AND the device
     * size — usually undesirable for pixel-perfect overlays. We convert to dp,
     * then the caller turns dp into sp via density.
     */
    fun fontDp(canvaFontSize: Float): Dp = (canvaFontSize * scale).dp
}

/**
 * Compute the canva scope for a slide given its rendered size in px.
 * Called from a BoxWithConstraints whose maxWidth/maxHeight reflect the slide area.
 */
internal fun computeCanvaScope(
    maxWidthPx: Float,
    maxHeightPx: Float,
    density: Density
): StoryCanvaScope {
    val widthScale = maxWidthPx / STORY_DESIGN_WIDTH
    val heightScale = maxHeightPx / STORY_DESIGN_HEIGHT
    val scale = minOf(widthScale, heightScale)
    val usedWidth = STORY_DESIGN_WIDTH * scale
    val usedHeight = STORY_DESIGN_HEIGHT * scale
    val offsetXPx = (maxWidthPx - usedWidth) / 2f
    val offsetYPx = (maxHeightPx - usedHeight) / 2f
    return with(density) {
        StoryCanvaScope(
            scale = scale,
            offsetXDp = offsetXPx.toDp(),
            offsetYDp = offsetYPx.toDp(),
            density = density
        )
    }
}

/** Tolerant hex / rgba color parser — returns null on failure rather than throwing. */
internal fun parseStoryColor(value: String?): Color? {
    if (value.isNullOrBlank()) return null
    val v = value.trim()
    return try {
        when {
            v.startsWith("#") -> Color(android.graphics.Color.parseColor(v))
            v.startsWith("rgba", ignoreCase = true) || v.startsWith("rgb", ignoreCase = true) -> {
                val nums = v.substringAfter('(').substringBefore(')')
                    .split(',').map { it.trim() }
                val r = nums[0].toInt()
                val g = nums[1].toInt()
                val b = nums[2].toInt()
                val a = if (nums.size > 3) (nums[3].toFloat() * 255).toInt() else 255
                Color(r, g, b, a)
            }
            else -> Color(android.graphics.Color.parseColor(v))
        }
    } catch (_: Exception) {
        null
    }
}
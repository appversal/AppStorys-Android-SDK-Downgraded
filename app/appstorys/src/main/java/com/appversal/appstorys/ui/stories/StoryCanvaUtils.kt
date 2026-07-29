package com.appversal.appstorys.ui.stories

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull

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
 * FIT (contain) mode — the canvas is scaled uniformly so that the first
 * dimension to reach the screen edge fills it completely (min-scale). The other
 * axis is centred and may be letterboxed.  No clipping occurs; the entire
 * design canvas is always visible.
 *
 * JSON coordinate system:
 *   - position.x / size.width   → percentage of CANVA WIDTH  (use xPctDp / widthPctDp)
 *   - position.y / size.height  → percentage of CANVA HEIGHT (use yPctDp / heightPctDp)
 *   - single values (font size) → percentage of CANVA HEIGHT (use fontPctDp)
 *   - styling properties (borderRadius, strokeWidth, padding, spacing)
 *                               → canva-space px               (use sizeDp / fontDp)
 *
 * offsetXDp / offsetYDp are the positive insets from the container edge to the
 * top-left corner of the rendered canvas.  They are added automatically by the
 * xPctDp / yPctDp helpers so that 0 % always maps to the canvas origin.
 */
@Immutable
internal data class StoryCanvaScope(
    val scale: Float,
    val offsetXDp: Dp,
    val offsetYDp: Dp,
    val density: Density,
    /** Rendered canvas width in dp (= designWidth × scale). */
    val canvaWidthDp: Dp,
    /** Rendered canvas height in dp (= designHeight × scale). */
    val canvaHeightDp: Dp
) {
    // ── Canva-pixel helpers (styling properties: borderRadius, spacing, etc.) ─────

    /** Convert a canva-space x coordinate to a Dp offset within the slide box. */
    fun xDp(canvaX: Float): Dp = offsetXDp + with(density) { (canvaX * scale).toDp() }

    /** Convert a canva-space y coordinate to a Dp offset within the slide box. */
    fun yDp(canvaY: Float): Dp = offsetYDp + with(density) { (canvaY * scale).toDp() }

    /** Convert a canva-space length to Dp (no offset). */
    fun sizeDp(canvaSize: Float): Dp = with(density) { (canvaSize * scale).toDp() }

    /**
     * Font sizes stored as canva-space px (legacy path / styling properties).
     * Converts to dp; caller uses density to get sp.
     */
    fun fontDp(canvaFontSize: Float): Dp = with(density) { (canvaFontSize * scale).toDp() }

    // ── Percentage helpers (layout positions / sizes / font sizes from JSON) ──────

    /**
     * Convert an x-position expressed as a percentage of canva width (0–100) to Dp.
     * Adds the canvas x-offset so that 0 % maps to the canvas left edge, not the
     * container left edge.  Use for position.x values from studio JSON.
     */
    fun xPctDp(pct: Float): Dp = offsetXDp + canvaWidthDp * (pct / 100f)

    /**
     * Convert a y-position expressed as a percentage of canva height (0–100) to Dp.
     * Adds the canvas y-offset so that 0 % maps to the canvas top edge, not the
     * container top edge.  Use for position.y values from studio JSON.
     */
    fun yPctDp(pct: Float): Dp = offsetYDp + canvaHeightDp * (pct / 100f)

    /**
     * Convert a width expressed as a percentage of canva width (0–100) to Dp.
     * Use for size.width / width values from studio JSON.
     */
    fun widthPctDp(pct: Float): Dp = canvaWidthDp * (pct / 100f)

    /**
     * Convert a height expressed as a percentage of canva height (0–100) to Dp.
     * Use for size.height / height values from studio JSON.
     */
    fun heightPctDp(pct: Float): Dp = canvaHeightDp * (pct / 100f)

    /**
     * Convert a single dimension (e.g. font size) expressed as a percentage of
     * canva height (0–100) to Dp.  Use for scalar size values from studio JSON.
     */
    fun fontPctDp(pct: Float): Dp = canvaHeightDp * (pct / 100f)
}

/**
 * Compute the canva scope for a slide given its rendered size in px.
 * Called from a BoxWithConstraints whose maxWidth/maxHeight reflect the slide area.
 *
 * Uses FIT (contain) scaling: the canvas is enlarged uniformly until the first
 * dimension (width or height) fills the available space.  The other dimension is
 * centred with letterboxing.  This guarantees that the entire design canvas is
 * visible and that percentage-based positions are correct within it.
 */
internal fun computeCanvaScope(
    maxWidthPx: Float,
    maxHeightPx: Float,
    density: Density,
    designWidth: Float = STORY_DESIGN_WIDTH,
    designHeight: Float = STORY_DESIGN_HEIGHT
): StoryCanvaScope {
    val safeW = if (designWidth > 0f) designWidth else STORY_DESIGN_WIDTH
    val safeH = if (designHeight > 0f) designHeight else STORY_DESIGN_HEIGHT

    val widthScale = maxWidthPx / safeW
    val heightScale = maxHeightPx / safeH
    // FIT mode: scale so the canvas enlarges to fill the first matching dimension
    // (whichever of width/height is reached first) without overflowing the other.
    // This is ContentScale.Fit — the whole canvas is visible, aspect ratio is
    // preserved, and the remaining axis is centred (letterboxed).
    val scale = minOf(widthScale, heightScale)
    val canvaWidthPx  = safeW * scale   // actual rendered canvas width  in px
    val canvaHeightPx = safeH * scale   // actual rendered canvas height in px
    val offsetXPx = (maxWidthPx  - canvaWidthPx)  / 2f   // >= 0 (letterbox inset)
    val offsetYPx = (maxHeightPx - canvaHeightPx) / 2f   // >= 0 (letterbox inset)
    return with(density) {
        StoryCanvaScope(
            scale        = scale,
            offsetXDp    = offsetXPx.toDp(),
            offsetYDp    = offsetYPx.toDp(),
            density      = density,
            canvaWidthDp  = canvaWidthPx.toDp(),   // canvas width  after scaling
            canvaHeightDp = canvaHeightPx.toDp()   // canvas height after scaling
        )
    }
}

/**
 * Expands the CSS 3-digit hex shorthand ("#abc" -> "#aabbcc"). Any other input is
 * returned untouched, so this is safe to run over rgb()/rgba()/named values too.
 */
private fun expandShortHex(v: String): String {
    if (!v.startsWith("#") || v.length != 4) return v
    val body = v.substring(1)
    if (!body.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return v
    return buildString {
        append('#')
        body.forEach { append(it).append(it) }
    }
}

/** Tolerant hex / rgba color parser — returns null on failure rather than throwing. */
internal fun parseStoryColor(value: String?): Color? {
    if (value.isNullOrBlank()) return null
    // android.graphics.Color.parseColor only understands #RRGGBB / #AARRGGBB, so
    // expand the CSS 3-digit shorthand (#RGB -> #RRGGBB) first — the studio does
    // emit it, and it used to silently parse as null (i.e. the styled colour was
    // dropped and the caller's default was used instead).
    val v = expandShortHex(value.trim())
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

/**
 * Parses a color value that may arrive either as a plain hex/rgba string (legacy
 * backend format) or as `{ "color": "#RRGGBB", "opacity": 0-100 }` (current backend
 * format). Returns null when the value is missing, blank, or unparsable.
 *
 * The object form's `opacity` (0-100) is applied to the resulting Color's alpha
 * channel — this is per-color opacity and is independent of any separate overall
 * widget "opacity" styling field, which callers continue to apply via .alpha().
 */
internal fun parseStoryColorElement(element: JsonElement?): Color? {
    if (element == null) return null
    return when (element) {
        is JsonObject -> {
            val hex = (element["color"] as? JsonPrimitive)?.contentOrNull
            val base = parseStoryColor(hex) ?: return null
            val opacity = (element["opacity"] as? JsonPrimitive)?.floatOrNull ?: 100f
            base.copy(alpha = (opacity / 100f).coerceIn(0f, 1f))
        }
        is JsonPrimitive -> parseStoryColor(element.contentOrNull)
        else -> null
    }
}
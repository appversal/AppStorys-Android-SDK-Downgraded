package com.appversal.appstorys.ui.stories

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.appversal.appstorys.api.StoryInteraction
import com.appversal.appstorys.utils.FontCache
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Duration.Companion.milliseconds

// ----------------------- JSON helpers (defensive) ---------------------------

private fun JsonObject?.obj(key: String): JsonObject? =
    this?.get(key)?.let { runCatching { it.jsonObject }.getOrNull() }

private fun JsonObject?.str(key: String): String? =
    this?.get(key)?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }

private fun JsonObject?.int(key: String): Int? =
    this?.get(key)?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() }

private fun JsonObject?.float(key: String): Float? =
    this?.get(key)?.let { runCatching { it.jsonPrimitive.floatOrNull }.getOrNull() }

private fun JsonObject?.bool(key: String): Boolean? =
    this?.get(key)?.let { runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull() }

// Colors now arrive either as a plain hex string (legacy) or as
// { "color": "#RRGGBB", "opacity": 0-100 } (current backend format).
// parseStoryColorElement (StoryCanvaUtils.kt) handles both transparently.
private fun JsonObject?.color(key: String): Color? =
    parseStoryColorElement(this?.get(key))

private fun JsonObject?.strList(key: String): List<String> =
    this?.get(key)?.let {
        runCatching { it.jsonArray.mapNotNull { el -> el.jsonPrimitive.contentOrNull } }.getOrNull()
    }.orEmpty()

// ----------------------- Interactive-element font fields --------------------
//
// Each interaction's styling now carries per-text-element font objects (e.g.
// "questionFont", "optionFont", "couponCodeFont", "titleFont", "labelFont", or a
// nested { color, font } pair like POLL's "question"/"options") shaped like:
//   { fontDecoration: [...], fontFamily: "...", fontSize: n, fontWeight: n, textAlign: "..." }
// This mirrors StoryContentTextFont (studio text/CTA elements) so the same
// fontFamily/fontWeight/fontDecoration/textAlign semantics apply everywhere.

private data class InteractionFontStyle(
    val fontFamily: String?,
    val fontWeight: FontWeight,
    val fontStyle: FontStyle,
    val textDecoration: TextDecoration?,
    val textAlign: TextAlign?
)

// [key] is the font sub-object (e.g. styling.obj("questionFont")); [defaultWeight] is
// the weight previously hardcoded at each call site, kept as the fallback when the
// backend sends neither "bold" in fontDecoration nor an explicit fontWeight.
private fun JsonObject?.toFontStyle(defaultWeight: FontWeight): InteractionFontStyle {
    val decoration = this.strList("fontDecoration")
    val weightInt = this.int("fontWeight")
    val weight = when {
        decoration.contains("bold") -> FontWeight.Bold
        weightInt != null -> FontWeight(weightInt.coerceIn(100, 900))
        else -> defaultWeight
    }
    val fontStyle = if (decoration.contains("italic")) FontStyle.Italic else FontStyle.Normal
    val textDecoration = if (decoration.contains("underline")) TextDecoration.Underline else null
    val textAlign = when (this.str("textAlign")?.lowercase()) {
        "left", "start" -> TextAlign.Start
        "right", "end" -> TextAlign.End
        "justify" -> TextAlign.Justify
        "center" -> TextAlign.Center
        else -> null
    }
    return InteractionFontStyle(this.str("fontFamily"), weight, fontStyle, textDecoration, textAlign)
}

// Same fontFamily resolution used for studio text/CTA elements: a URL loads/caches a
// custom font file via FontCache, a recognised named family maps to a built-in Compose
// font, blank/unrecognised falls back to the platform default.
private fun namedOrDefaultFontFamily(family: String?): FontFamily = when {
    family.isNullOrBlank() -> FontFamily.SansSerif
    family.startsWith("http://", ignoreCase = true) ||
            family.startsWith("https://", ignoreCase = true) -> FontFamily.SansSerif
    else -> when (family.lowercase()) {
        "serif" -> FontFamily.Serif
        "monospace", "mono" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        "sans-serif", "sans" -> FontFamily.SansSerif
        else -> FontFamily.Default
    }
}

@Composable
private fun rememberInteractionFontFamily(
    fontFamily: String?,
    weight: FontWeight,
    style: FontStyle
): FontFamily {
    val context = LocalContext.current
    val loadScope = rememberCoroutineScope()
    var resolved by remember(fontFamily) { mutableStateOf(namedOrDefaultFontFamily(fontFamily)) }
    LaunchedEffect(fontFamily, weight, style) {
        if (!fontFamily.isNullOrBlank() &&
            (fontFamily.startsWith("http://", ignoreCase = true) ||
                    fontFamily.startsWith("https://", ignoreCase = true))
        ) {
            loadScope.launch {
                resolved = try {
                    FontCache.loadFont(
                        context = context,
                        fontUrl = fontFamily,
                        weight = weight,
                        style = style
                    ) ?: FontFamily.SansSerif
                } catch (e: Exception) {
                    FontFamily.SansSerif
                }
            }
        }
    }
    return resolved
}

// borderRadius is sent by the backend as a PERCENTAGE OF THE CANVA (design) HEIGHT —
// the same unit `StoryCanvaScope.heightPctDp` expects — for every interaction's outer
// container. `heightPctDp` returns a Dp meant for the *outer*, un-overridden density,
// but this file's interaction composables run under a LocalDensity override equal to
// `scope.scale` (see StoryInteractionRenderer, "1.dp == 1 canva-space px" there), so the
// physical size has to be re-expressed in that overridden density's terms: convert the
// percentage to a real px amount first, then divide by `scope.scale` to get the
// equivalent literal Dp number under the overridden density.
private fun StoryCanvaScope.borderRadiusPctToLocalDp(pct: Float): Dp {
    val realPx = with(density) { heightPctDp(pct).toPx() }
    return (realPx / scale).dp
}

// Falls back to the previous ratio-based calculation when the backend doesn't
// provide a borderRadius value.
private fun JsonObject?.borderRadiusDp(scope: StoryCanvaScope, fallback: Dp): Dp =
    this.float("borderRadius")?.let { scope.borderRadiusPctToLocalDp(it) } ?: fallback

// ----------------------- Persisted interaction responses -------------------
//
// Once someone answers an interactive element (poll / quiz / media quiz / rating /
// reaction / input), that answer is locked in permanently — the same durability
// model already used for viewed-story tracking (SharedPreferences), so it survives
// process death and full app restarts, not just recomposition or navigation.
private const val INTERACTION_RESPONSES_PREFS = "AppStoryInteractionResponses"

private fun loadInteractionResponse(context: Context, interactionId: String?): String? {
    if (interactionId.isNullOrEmpty()) return null
    return context
        .getSharedPreferences(INTERACTION_RESPONSES_PREFS, Context.MODE_PRIVATE)
        .getString(interactionId, null)
}

private fun saveInteractionResponse(context: Context, interactionId: String?, value: String) {
    if (interactionId.isNullOrEmpty()) return
    context
        .getSharedPreferences(INTERACTION_RESPONSES_PREFS, Context.MODE_PRIVATE)
        .edit { putString(interactionId, value) }
}

// ----------------------- Local box-fit sizing helpers ---------------------
//
// An interaction's POSITION and OUTER SIZE come from the backend and are
// resolved once in StorySlideContent.kt (scope.xPctDp/yPctDp/widthPctDp/
// heightPctDp) — that box is whatever it is on this device and is left
// untouched. Everything *inside* that box (font sizes, padding, corner
// radius, icon sizes, item spacing) is derived below from the box's own
// measured width/height, captured per-interaction via BoxWithConstraints,
// instead of a percentage of the full slide. That way content is always
// sized for the box it actually ended up with on this screen, rather than
// a box-agnostic fraction of the whole canvas that can overflow when the
// box turns out smaller than whatever size the percentage was tuned for
// (e.g. the countdown digits being clipped on a narrow/short device).
//
// Pattern: every internal dimension is `ratio * unit`, where `unit` is a
// single Dp solved so the assembled content fits the available space. Where
// a row/column lays out a *fixed* number of same-size items with no
// flex/weight to absorb overflow (countdown digits, reaction bubbles, the
// rating star row, stacked quiz/media-quiz rows), `unit` is the minimum of
// the width-fit and height-fit solutions, so content shrinks automatically
// instead of being clipped.

/**
 * Largest `unit` such that `count` items laid out end-to-end — each
 * `itemRatio * unit` long, separated by `count - 1` gaps of `gapRatio * unit`
 * — fit inside `available` length. Used for both row-width and column-height
 * fitting (the math is identical either way). Returns `available` unscaled
 * when count <= 0 or the ratios don't describe a real row/column.
 */
private fun unitFitToSpace(
    available: Dp,
    count: Int,
    itemRatio: Float,
    gapRatio: Float = 0f
): Dp {
    if (count <= 0 || available <= 0.dp) return available
    val denom = itemRatio * count + gapRatio * (count - 1).coerceAtLeast(0)
    if (denom <= 0f) return available
    return available / denom
}

// ----------------------- Options / emoji parsing helpers ------------------

/**
 * Parses options from both new (JsonArray of {id, label/text}) and
 * legacy (JsonObject key→value) formats, returning a list of id→label pairs.
 */
private fun parseOptionPairs(config: JsonObject?): List<Pair<String, String>> =
    runCatching {
        config?.get("options")?.jsonArray?.mapNotNull { elem ->
            val obj = runCatching { elem.jsonObject }.getOrNull() ?: return@mapNotNull null
            val id = obj.str("id") ?: return@mapNotNull null
            val label = obj.str("label") ?: obj.str("text") ?: obj.str("name") ?: ""
            id to label
        }
    }.getOrNull()
        ?: runCatching {
            config?.get("options")?.jsonObject?.entries?.map { (k, v) ->
                k to (runCatching { v.jsonPrimitive.contentOrNull }.getOrNull() ?: k)
            }
        }.getOrNull()
        ?: emptyList()

/**
 * Parses reaction emojis from:
 *   new  → config.emojis  = ["😍","🔥",…]
 *   legacy → config.options = {"k": "😍", …}
 */
private fun parseReactionEmojis(config: JsonObject?): List<Pair<String, String>> =
    runCatching {
        config?.get("emojis")?.jsonArray?.mapIndexed { i, elem ->
            "emoji_$i" to (runCatching { elem.jsonPrimitive.contentOrNull }.getOrNull() ?: "😀")
        }
    }.getOrNull()
        ?: runCatching {
            config?.get("options")?.jsonObject?.entries?.mapNotNull { (k, v) ->
                k to (runCatching { v.jsonPrimitive.contentOrNull }.getOrNull()
                    ?: return@mapNotNull null)
            }
        }.getOrNull()
        ?: listOf("e0" to "😍", "e1" to "🔥", "e2" to "😂", "e3" to "😮", "e4" to "😢")

// ----------------------- Public entry point -------------------------------

/**
 * Renders an interaction inside an absolutely-positioned box. Caller positions
 * the wrapper using canva-space position and size from styling.
 */
@Composable
internal fun StoryInteractionRenderer(
    interaction: StoryInteraction,
    scope: StoryCanvaScope,
    onTrack: (event: String, metadata: Map<String, Any>) -> Unit,
    onInputFocusChanged: (focused: Boolean) -> Unit
) {
    if (interaction.isActive == false) return

    val type = interaction.interactionType?.uppercase() ?: return
    val config = interaction.config
    val styling = interaction.styling

    // ── Uniform scaling (Approach B) ─────────────────────────────────────────
    // The wrapper Box (in StorySlideContent) is already sized from the backend's
    // width/height percentages — that is unchanged and stays authoritative. Here
    // we override LocalDensity for the whole interaction subtree to the canva
    // `scale` factor, which makes 1.dp == 1 canva-space pixel inside the
    // interaction.
    //
    // Effect: the element is laid out once in fixed canva-space and the ENTIRE
    // subtree — fonts, padding, radii, borders, stroke widths, item spacing,
    // everything — is multiplied by exactly the same `scale` on render. So the
    // interaction becomes a mathematically exact scaled copy on every device:
    //   • content measured via BoxWithConstraints now reports the element's
    //     canva-space design size (identical on all devices), so the existing
    //     proportional (ratio × measured-size) math is unchanged AND
    //   • the remaining fixed-dp values (borders/strokes/track height) now scale
    //     with the box too, instead of staying a constant dp that drifted
    //     proportionally between a small phone and a large screen.
    // Text stays crisp because this is a true re-layout at a higher density, not
    // a stretched bitmap. fontScale is preserved so accessibility behaviour is
    // unchanged (callers convert via `with(density){ dp.toSp() }`, which already
    // cancels fontScale, exactly as before).
    val baseDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = scope.scale,
            fontScale = baseDensity.fontScale
        )
    ) {
        when (type) {
            "POLL" -> PollInteraction(interaction.id, config, styling, scope, onTrack)
            "QUIZ" -> QuizInteraction(interaction.id, config, styling, scope, onTrack)
            "MEDIA_QUIZ" -> MediaQuizInteraction(interaction.id, config, styling, scope, onTrack)
            "RATING" -> RatingInteraction(interaction.id, config, styling, scope, onTrack)
            "REACTION" -> ReactionInteraction(interaction.id, config, styling, scope, onTrack)
            "COUNTDOWN" -> CountdownInteraction(interaction.id, config, styling, scope)
            "PROMO" -> PromoInteraction(interaction.id, config, styling, scope, onTrack)
            "INPUT" -> InputInteraction(
                interaction.id,
                config,
                styling,
                scope,
                onTrack,
                onInputFocusChanged
            )
        }
    }
}

// ============================================================
// POLL
// ============================================================
// Matches PollRenderer.jsx:
//  • White container, padding 20, gap 24
//  • isPillLayout: horizontal + exactly 2 options → shared rounded
//    border, zero gap, divider between them
//  • showResults: animated fill bar behind each option label
// ============================================================

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun PollInteraction(
    id: String?,
    config: JsonObject?,
    styling: JsonObject?,
    scope: StoryCanvaScope,
    onTrack: (event: String, metadata: Map<String, Any>) -> Unit
) {
    val question = config.str("question") ?: ""
    val optionPairs = parseOptionPairs(config)
    val showResults = config.bool("showResults") ?: false

    // "transparent" is the backend key; also fall back to legacy "transparentBackground"
    val transparent = styling.bool("transparent") ?: styling.bool("transparentBackground") ?: false

    // opacity: backend sends 0–100, Compose needs 0.0–1.0
    val opacity = ((styling.float("opacity") ?: 100f) / 100f).coerceIn(0f, 1f)

    // layout: can be a string ("horizontal" | "vertical") or {type, columns}
    val layoutType = runCatching {
        config?.get("layout")?.jsonPrimitive?.contentOrNull
    }.getOrNull() ?: runCatching {
        config?.get("layout")?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
    }.getOrNull() ?: "horizontal"

    val isHorizontal = layoutType.lowercase() != "vertical"
    val isPillLayout = isHorizontal && optionPairs.size == 2
    val n = optionPairs.size.coerceAtLeast(1)

    // ── Colors (still entirely backend-driven) ──
    val bg =
        styling.color("containerBgColor") ?: styling.color("background") ?: Color.White
    val activeBar = styling.color("activeResultBarColor") ?: Color(0xFFF97316)
    val inactiveBar = styling.color("inactiveResultBarColor") ?: Color(0xFF404040)
    val optionBg = styling.obj("options").color("background") ?: Color(0xFF1A1A1A)
    val optionTextColor =
        styling.obj("options").color("textColor") ?: styling.obj("options").color("color")
        ?: Color.White
    val questionTextColor =
        styling.obj("question").color("color") ?: Color(0xFF111827)
    val questionFontStyle = styling.obj("question").obj("font").toFontStyle(FontWeight(800))
    val questionFontFamily = rememberInteractionFontFamily(
        questionFontStyle.fontFamily, questionFontStyle.fontWeight, questionFontStyle.fontStyle
    )
    val optionsFontStyle = styling.obj("options").obj("font").toFontStyle(FontWeight(800))
    val optionsFontFamily = rememberInteractionFontFamily(
        optionsFontStyle.fontFamily, optionsFontStyle.fontWeight, optionsFontStyle.fontStyle
    )

    // Demo percentages for showResults (matches React defaults)
    val percentages = when (optionPairs.size) {
        2 -> listOf(60, 40)
        3 -> listOf(50, 30, 20)
        else -> optionPairs.indices.map { 100 / optionPairs.size.coerceAtLeast(1) }
    }

    val context = LocalContext.current
    var selected by remember(id) { mutableStateOf(loadInteractionResponse(context, id)) }
    val density = LocalDensity.current

    // Show result bars when configured from the start OR after the user has voted
    val displayResults = showResults || selected != null

    // Left/right split used only for the >2-option horizontal layout: options in the
    // first half fill from the left edge, options in the second half fill from the
    // right edge — so each bar visually grows inward from the side it sits on.
    val leftGroupCount = (n + 1) / 2
    fun isRightSideOption(index: Int) = isHorizontal && index >= leftGroupCount

    // BoxWithConstraints captures THIS interaction's own measured width/height (the box
    // itself already comes from backend position/size, resolved per-device upstream). All
    // dimension values below — radius, padding, font sizes — are derived from that local
    // box instead of a percentage of the whole slide, so they always fit however this
    // element actually ended up sized on this screen. Only colors/text/booleans stay
    // backend-driven.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = opacity }
    ) {
        val w = maxWidth
        val h = maxHeight

        val unit = minOf(w, h) * 0.08f
        val containerRadius = styling.borderRadiusDp(scope, (w * 0.06f).coerceAtMost(h * 0.22f))
        val containerPadding = unit
        val optionPaddingV = unit * 0.32f
        val optionPaddingH = unit * 0.32f
        // A large fixed radius always produces a full pill/rounded look — Compose clamps
        // any RoundedCornerShape radius to half the element's own size automatically.
        val optionRadius = 500.dp
        val rowGap = unit * 0.4f
        val questionLineHeightMultiplier = 1.25f

        // Question text — bounded by both axes so it never grows past what the box can
        // hold either way.
        val questionSize = minOf(h * 0.15f, w * 0.13f)

        // Option text — scaled from the question for visual hierarchy, then capped by
        // however much room each option actually gets: shared row width for horizontal/
        // pill layouts, shared column height for the vertical layout.
        val optionByHierarchy = questionSize * 0.82f
        val optionByAvailable = if (isHorizontal) {
            unitFitToSpace(available = w - containerPadding * 2, count = n, itemRatio = 5.2f)
        } else {
            unitFitToSpace(
                available = h - containerPadding * 2 - questionSize * 1.4f,
                count = n,
                itemRatio = 2.1f,
                gapRatio = 0.5f
            )
        }
        val optionSize = minOf(optionByHierarchy, optionByAvailable)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(containerRadius))
                .background(if (transparent) Color.Transparent else bg)
                .padding(containerPadding),
            verticalArrangement = Arrangement.spacedBy(rowGap)
        ) {
            if (question.isNotEmpty()) {
                // Rendered with an explicit lineHeight so wrapped (multi-line) questions
                // space their lines apart properly instead of the second line drawing
                // on top of the first.
                BasicText(
                    text = question,
                    color = { questionTextColor },
                    style = TextStyle(
                        fontSize = with(density) { questionSize.toSp() },
                        lineHeight = with(density) { (questionSize * questionLineHeightMultiplier).toSp() },
                        fontFamily = questionFontFamily,
                        fontWeight = questionFontStyle.fontWeight,
                        fontStyle = questionFontStyle.fontStyle,
                        textDecoration = questionFontStyle.textDecoration,
                        textAlign = questionFontStyle.textAlign ?: TextAlign.Center
                    ),
                    modifier = Modifier
                        .padding(top = 8.dp, bottom = 8.dp)
                        .fillMaxWidth()
                )
            }

            when {
                // ── Pill: 2 options sharing one rounded border ──
                // Fill is now a SINGLE bar spanning the whole pill, split into a left
                // segment and a right segment whose widths are fractions of the TOTAL
                // pill width (not of each option's own half) — so a 60% option covers
                // 60% of the entire pill, with the remaining 40% belonging to the other
                // option, meeting at a small gap wherever that split lands.
                isPillLayout -> {
                    val leftPair = optionPairs.getOrNull(0)
                    val rightPair = optionPairs.getOrNull(1)
                    val leftKey = leftPair?.first
                    val leftLabel = leftPair?.second ?: ""
                    val rightKey = rightPair?.first
                    val rightLabel = rightPair?.second ?: ""
                    val leftPct = percentages.getOrElse(0) { 50 }
                    val rightPct = percentages.getOrElse(1) { 50 }
                    val isLeftSelected = selected != null && selected == leftKey
                    val isRightSelected = selected != null && selected == rightKey

                    // Direction of the whole sweep: right selected → both bars fill
                    // right-to-left; left selected (or no selection yet) → both bars
                    // fill left-to-right. Both bars always move together in this same
                    // direction rather than growing from their own independent edges.
                    val sweepFromRight = isRightSelected

                    val fillGap = 3.dp

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(optionRadius))
                            .border(2.dp, Color(0xFFE5E7EB), RoundedCornerShape(optionRadius))
                    ) {
                        val pillWidth = maxWidth
                        val fillableWidth = (pillWidth - fillGap).coerceAtLeast(0.dp)
                        val leftFillTarget = if (displayResults) fillableWidth * (leftPct / 100f) else 0.dp
                        val rightFillTarget = if (displayResults) fillableWidth * (rightPct / 100f) else 0.dp

                        val animatedLeftFill by animateDpAsState(
                            targetValue = leftFillTarget,
                            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                            label = "pollPillLeftFill"
                        )
                        val animatedRightFill by animateDpAsState(
                            targetValue = rightFillTarget,
                            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                            label = "pollPillRightFill"
                        )

                        // ── Layer 1: base background + click targets for each half ──
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(optionBg)
                                    .clickable(
                                        enabled = selected == null,
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        leftKey?.let {
                                            selected = it
                                            saveInteractionResponse(context, id, it)
                                            onTrack(
                                                "clicked", mapOf(
                                                    "interaction_type" to "poll",
                                                    "interaction_id" to (id ?: ""),
                                                    "selected_option" to it
                                                )
                                            )
                                        }
                                    }
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(optionBg)
                                    .clickable(
                                        enabled = selected == null,
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        rightKey?.let {
                                            selected = it
                                            saveInteractionResponse(context, id, it)
                                            onTrack(
                                                "clicked", mapOf(
                                                    "interaction_type" to "poll",
                                                    "interaction_id" to (id ?: ""),
                                                    "selected_option" to it
                                                )
                                            )
                                        }
                                    }
                            )
                        }

                        // ── Layer 2: fill bars — both anchored to the SAME edge and
                        // offset relative to one another, so they visibly grow in one
                        // continuous direction instead of meeting from opposite edges.
                        if (displayResults) {
                            if (!sweepFromRight) {
                                // Left-to-right sweep: left bar starts at the pill's left
                                // edge; gap follows immediately after it; right bar
                                // follows immediately after the gap — all three grow
                                // rightward together as their widths animate.
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .width(animatedLeftFill)
                                        .fillMaxHeight()
                                        .background(if (isLeftSelected) activeBar else inactiveBar)
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .offset(x = animatedLeftFill)
                                        .width(fillGap)
                                        .fillMaxHeight()
                                        .background(Color(0xFFE5E7EB))
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .offset(x = animatedLeftFill + fillGap)
                                        .width(animatedRightFill)
                                        .fillMaxHeight()
                                        .background(if (isRightSelected) activeBar else inactiveBar)
                                )
                            } else {
                                // Right-to-left sweep: mirror image — right bar starts
                                // at the pill's right edge; gap precedes it; left bar
                                // precedes the gap — all three grow leftward together.
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .width(animatedRightFill)
                                        .fillMaxHeight()
                                        .background(if (isRightSelected) activeBar else inactiveBar)
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .offset(x = -animatedRightFill)
                                        .width(fillGap)
                                        .fillMaxHeight()
                                        .background(Color(0xFFE5E7EB))
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .offset(x = -(animatedRightFill + fillGap))
                                        .width(animatedLeftFill)
                                        .fillMaxHeight()
                                        .background(if (isLeftSelected) activeBar else inactiveBar)
                                )
                            }
                        } else {
                            // Resting-state divider at the midpoint, before any vote.
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .fillMaxHeight()
                                    .width(2.dp)
                                    .background(Color(0xFFE5E7EB))
                            )
                        }

                        // ── Layer 3: labels — always centered within their own half,
                        // drawn last so they sit on top of the fill layer. ──
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                BasicTextWrap(
                                    text = if (showResults) "$leftLabel $leftPct%" else leftLabel,
                                    color = optionTextColor,
                                    fontSizeSp = with(density) { optionSize.toSp() },
                                    fontFamily = optionsFontFamily,
                                    fontWeight = optionsFontStyle.fontWeight,
                                    fontStyle = optionsFontStyle.fontStyle,
                                    textDecoration = optionsFontStyle.textDecoration,
                                    align = optionsFontStyle.textAlign ?: TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = optionPaddingH)
                                )
                            }
                            Box(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                BasicTextWrap(
                                    text = if (showResults) "$rightLabel $rightPct%" else rightLabel,
                                    color = optionTextColor,
                                    fontSizeSp = with(density) { optionSize.toSp() },
                                    fontFamily = optionsFontFamily,
                                    fontWeight = optionsFontStyle.fontWeight,
                                    fontStyle = optionsFontStyle.fontStyle,
                                    textDecoration = optionsFontStyle.textDecoration,
                                    align = optionsFontStyle.textAlign ?: TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = optionPaddingH)
                                )
                            }
                        }
                    }
                }

                // ── Horizontal (> 2 options) ──
                isHorizontal -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(rowGap)
                    ) {
                        optionPairs.forEachIndexed { index, (key, label) ->
                            val pct = percentages.getOrElse(index) {
                                100 / optionPairs.size.coerceAtLeast(1)
                            }
                            val isSelected = selected == key
                            val fillFraction = if (displayResults) pct / 100f else 0f
                            val animatedFillFraction by animateFloatAsState(
                                targetValue = fillFraction,
                                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                                label = "pollRowFill-$key"
                            )
                            val fillAlignment =
                                if (isRightSideOption(index)) Alignment.CenterEnd else Alignment.CenterStart

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(optionRadius))
                                    .background(optionBg)
                                    .border(
                                        2.dp,
                                        Color(0xFFE5E7EB),
                                        RoundedCornerShape(optionRadius)
                                    )
                                    .clickable(
                                        enabled = selected == null,
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        selected = key
                                        saveInteractionResponse(context, id, key)
                                        onTrack(
                                            "clicked", mapOf(
                                                "interaction_type" to "poll",
                                                "interaction_id" to (id ?: ""),
                                                "selected_option" to key
                                            )
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (displayResults) {
                                    Box(
                                        modifier = Modifier
                                            .align(fillAlignment)
                                            .fillMaxHeight()
                                            .fillMaxWidth(animatedFillFraction.coerceIn(0f, 1f))
                                            .background(if (isSelected) activeBar else inactiveBar)
                                    )
                                }
                                BasicTextWrap(
                                    text = if (showResults) "$label $pct%" else label,
                                    color = optionTextColor,
                                    fontSizeSp = with(density) { optionSize.toSp() },
                                    fontFamily = optionsFontFamily,
                                    fontWeight = optionsFontStyle.fontWeight,
                                    fontStyle = optionsFontStyle.fontStyle,
                                    textDecoration = optionsFontStyle.textDecoration,
                                    align = optionsFontStyle.textAlign ?: TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = optionPaddingH)
                                )
                            }
                        }
                    }
                }

                // ── Vertical ──
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(rowGap)
                    ) {
                        optionPairs.forEachIndexed { index, (key, label) ->
                            val pct = percentages.getOrElse(index) {
                                100 / optionPairs.size.coerceAtLeast(1)
                            }
                            val isSelected = selected == key
                            val fillFraction = if (displayResults) pct / 100f else 0f
                            val animatedFillFraction by animateFloatAsState(
                                targetValue = fillFraction,
                                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                                label = "pollColFill-$key"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(optionRadius))
                                    .background(optionBg)
                                    .border(
                                        2.dp,
                                        Color(0xFFE5E7EB),
                                        RoundedCornerShape(optionRadius)
                                    )
                                    .clickable(
                                        enabled = selected == null,
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        selected = key
                                        saveInteractionResponse(context, id, key)
                                        onTrack(
                                            "clicked", mapOf(
                                                "interaction_type" to "poll",
                                                "interaction_id" to (id ?: ""),
                                                "selected_option" to key
                                            )
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (displayResults) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .fillMaxHeight()
                                            .fillMaxWidth(animatedFillFraction.coerceIn(0f, 1f))
                                            .background(if (isSelected) activeBar else inactiveBar)
                                    )
                                }
                                BasicTextWrap(
                                    text = if (showResults) "$label $pct%" else label,
                                    color = optionTextColor,
                                    fontSizeSp = with(density) { optionSize.toSp() },
                                    fontFamily = optionsFontFamily,
                                    fontWeight = optionsFontStyle.fontWeight,
                                    fontStyle = optionsFontStyle.fontStyle,
                                    textDecoration = optionsFontStyle.textDecoration,
                                    align = optionsFontStyle.textAlign ?: TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = optionPaddingH)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun QuizInteraction(
    id: String?,
    config: JsonObject?,
    styling: JsonObject?,
    scope: StoryCanvaScope,
    onTrack: (event: String, metadata: Map<String, Any>) -> Unit
) {
    val question = config.str("question") ?: ""
    val correctId =
        config.str("correctAnswerId") ?: config.str("isCorrect") ?: config.str("correctOption")
    val optionPairs = parseOptionPairs(config)
    val showExplanation = config.bool("showExplanation") ?: false
    val explanation = config.str("explanation") ?: ""

    val transparent = styling.bool("transparent") ?: styling.bool("transparentBackground") ?: false
    val opacity = ((styling.float("opacity") ?: 100f) / 100f).coerceIn(0f, 1f)

    val bg =
        styling.color("background") ?: styling.color("containerBgColor") ?: Color.White
    val questionBg = styling.color("questionBgColor") ?: Color.Black
    val questionColor = styling.color("questionColor") ?: Color.White
    val optionBg = styling.color("optionBgColor") ?: Color(0xFFF9FAFB)
    val optionTextColor = styling.color("optionTextColor") ?: Color(0xFF1F2937)
    val correctColor = styling.color("correctColor") ?: Color(0xFF10B981)
    val incorrectColor = styling.color("incorrectColor") ?: Color(0xFFEF4444)
    val questionFontStyle = styling.obj("questionFont").toFontStyle(FontWeight.Bold)
    val questionFontFamily = rememberInteractionFontFamily(
        questionFontStyle.fontFamily, questionFontStyle.fontWeight, questionFontStyle.fontStyle
    )
    val optionFontStyle = styling.obj("optionFont").toFontStyle(FontWeight.SemiBold)
    val optionFontFamily = rememberInteractionFontFamily(
        optionFontStyle.fontFamily, optionFontStyle.fontWeight, optionFontStyle.fontStyle
    )

    val borderRadiusRatio = 0.0574f
    val optionRadiusRatio = 0.174f

    val context = LocalContext.current
    var selected by remember(id) { mutableStateOf(loadInteractionResponse(context, id)) }
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val n = optionPairs.size.coerceAtLeast(1)
    val hasQuestion = question.isNotEmpty()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = opacity }
    ) {
        val w = maxWidth
        val h = maxHeight

        val containerRadius = styling.borderRadiusDp(scope, w * borderRadiusRatio)

        val qHPad = w * 0.08f
        val qVPad = w * 0.05f
        val questionFontByWidth = w * 0.08f
        val questionLineHeightMultiplier = 1.25f

        val questionMaxWidthPx = with(density) { (w - qHPad * 2).roundToPx() }

        fun measureQuestionHeight(fontSize: Dp): Dp {
            if (!hasQuestion || fontSize <= 0.dp) return 0.dp
            val result = textMeasurer.measure(
                text = AnnotatedString(question),
                style = TextStyle(
                    fontSize = with(density) { fontSize.toSp() },
                    lineHeight = with(density) { (fontSize * questionLineHeightMultiplier).toSp() },
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                constraints = Constraints(maxWidth = questionMaxWidthPx)
            )
            return with(density) { result.size.height.toDp() }
        }

        var questionFont = questionFontByWidth
        if (hasQuestion) {
            val heightBudget = (h * 0.35f - qVPad * 2).coerceAtLeast(0.dp)
            var measured = measureQuestionHeight(questionFont)
            var guard = 0
            while (measured > heightBudget && questionFont > 8.dp && guard < 12) {
                questionFont *= 0.92f
                measured = measureQuestionHeight(questionFont)
                guard++
            }
        }

        val headerTextHeight = measureQuestionHeight(questionFont)
        val headerHeight = if (hasQuestion) qVPad * 2 + headerTextHeight else 0.dp

        // Options area — rows now fill 100% of whatever space remains after the header,
        // divided equally via weight(1f) below. optRowH is still computed here (same
        // formula as before) purely to drive font size / padding / radius so the visual
        // proportions stay consistent — it no longer caps or constrains the actual
        // rendered row height, so no leftover space is possible at the bottom.
        val areaHPad = w * 0.04f
        val optGapRatio = 0.04f / 0.22f
        val availableForOpts = (h - headerHeight - areaHPad * 2).coerceAtLeast(0.dp)
        val optRowH = unitFitToSpace(
            available = availableForOpts,
            count = n,
            itemRatio = 1f,
            gapRatio = optGapRatio
        )
        val optGap = optRowH * optGapRatio
        val optionRadius = minOf(w * optionRadiusRatio, optRowH * 0.5f)
        val optHPad = minOf(w * 0.06f, optRowH * 0.3f)
        val labelFont = minOf(w * 0.05f, optRowH * 0.42f)
        val pctFont = minOf(w * 0.10f, optRowH * 0.5f)

        val explanationVisible = showExplanation && selected != null && explanation.isNotEmpty()
        // Bottom corners smoothly flatten as the explanation panel appears, so the two
        // pieces merge into one continuous rounded shape instead of two separate cards
        // with a gap at the seam — same idea as InputInteraction's animatedBottomRadius.
        val animatedCardBottomRadius by animateDpAsState(
            targetValue = if (explanationVisible) 0.dp else containerRadius,
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            label = "quizCardBottomRadius"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(
                    RoundedCornerShape(
                        topStart = containerRadius,
                        topEnd = containerRadius,
                        bottomStart = animatedCardBottomRadius,
                        bottomEnd = animatedCardBottomRadius
                    )
                )
                .background(if (transparent) Color.Transparent else bg)
        ) {
            if (hasQuestion) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(questionBg)
                        .padding(horizontal = qHPad, vertical = qVPad),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = question,
                        color = { questionColor },
                        style = TextStyle(
                            fontSize = with(density) { questionFont.toSp() },
                            lineHeight = with(density) { (questionFont * questionLineHeightMultiplier).toSp() },
                            fontFamily = questionFontFamily,
                            fontWeight = questionFontStyle.fontWeight,
                            fontStyle = questionFontStyle.fontStyle,
                            textDecoration = questionFontStyle.textDecoration,
                            textAlign = questionFontStyle.textAlign ?: TextAlign.Center
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ── Options list — each row now takes weight(1f), so the n rows divide
            // ALL remaining vertical space equally, with optGap between them and zero
            // leftover regardless of option count or container height.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(areaHPad),
                verticalArrangement = Arrangement.spacedBy(optGap)
            ) {
                optionPairs.forEach { (key, label) ->
                    val isSelected = selected == key
                    val isCorrect = key == correctId

                    val borderColor = when {
                        selected != null && isCorrect && !isSelected -> correctColor
                        selected != null && isSelected && !isCorrect -> incorrectColor
                        else -> Color(0xFFE5E7EB)
                    }
                    val borderWidth =
                        if (selected != null && (isCorrect || (isSelected && !isCorrect))) 5.dp else 2.dp

                    val pct: Int? = if (selected != null) (if (isSelected) 100 else 0) else null

                    val fillColor = when {
                        isSelected && isCorrect -> correctColor
                        isSelected && !isCorrect -> incorrectColor
                        else -> Color.Transparent
                    }
                    val fillFraction = (pct ?: 0) / 100f
                    val animatedFillFraction by animateFloatAsState(
                        targetValue = fillFraction,
                        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                        label = "optionFill-$key"
                    )
                    val animatedTextColor by animateColorAsState(
                        targetValue = if (isSelected) Color.White else optionTextColor,
                        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                        label = "optionTextColor-$key"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)                        // fills its equal share of remaining height
                            .clip(RoundedCornerShape(optionRadius))
                            .background(optionBg)
                            .border(borderWidth, borderColor, RoundedCornerShape(optionRadius))
                            .clickable(
                                enabled = selected == null,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                selected = key
                                saveInteractionResponse(context, id, key)
                                onTrack(
                                    "clicked", mapOf(
                                        "interaction_type" to "quiz",
                                        "interaction_id" to (id ?: ""),
                                        "selected_option" to key
                                    )
                                )
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedFillFraction.coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(optionRadius))
                                .background(fillColor)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = optHPad),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            BasicTextWrap(
                                text = label,
                                color = animatedTextColor,
                                fontSizeSp = with(density) { labelFont.toSp() },
                                fontFamily = optionFontFamily,
                                fontWeight = optionFontStyle.fontWeight,
                                fontStyle = optionFontStyle.fontStyle,
                                textDecoration = optionFontStyle.textDecoration,
                                align = optionFontStyle.textAlign ?: TextAlign.Start,
                                modifier = Modifier.weight(1f)
                            )

                            if (pct != null) {
                                Spacer(modifier = Modifier.width(optHPad * 0.5f))
                                BasicTextWrap(
                                    text = "$pct%",
                                    color = animatedTextColor,
                                    fontSizeSp = with(density) { pctFont.toSp() },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Explanation — shown below the last option once the user has answered,
        // expanding the quiz card downward rather than shrinking the options to fit.
        // The parent wrapper Box (StorySlideContent) doesn't clip its children, so
        // this is free to overflow the interaction's own assigned height — same
        // overflow pattern used for the Send button in InputInteraction below.
        AnimatedVisibility(
            visible = explanationVisible,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = h),
            enter = fadeIn(tween(220)) + expandVertically(tween(280, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(160)) + shrinkVertically(tween(180))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = containerRadius, bottomEnd = containerRadius))
                    .background(if (transparent) Color.Transparent else bg)
                    .padding(horizontal = qHPad, vertical = qVPad)
            ) {
                BasicTextWrap(
                    text = explanation,
                    color = optionTextColor,
                    fontSizeSp = with(density) { labelFont.toSp() },
                    lineHeight = with(density) { (labelFont * 1.25f).toSp() },
                    align = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ============================================================
// MEDIA_QUIZ
// ============================================================
// Matches ImageQuizRenderer.jsx:
//  • Full-width question header strip
//  • Grid of image tiles (data.columns controls columns)
//  • Label text below each image
//  • Correct answer → green border; wrong selected → red border
// ============================================================

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun MediaQuizInteraction(
    id: String?,
    config: JsonObject?,
    styling: JsonObject?,
    scope: StoryCanvaScope,
    onTrack: (event: String, metadata: Map<String, Any>) -> Unit
) {
    val question = config.str("question") ?: ""
    val correctId = config.str("correctAnswerId") ?: config.str("correctOption")
    val columns = (config.int("columns") ?: 2).coerceAtLeast(1)

    val optionsArray =
        config?.get("options")?.let { runCatching { it.jsonArray }.getOrNull() } ?: return
    val options: List<MediaQuizOptionData> = optionsArray.mapNotNull {
        val obj = runCatching { it.jsonObject }.getOrNull() ?: return@mapNotNull null
        MediaQuizOptionData(
            id = obj.str("id") ?: return@mapNotNull null,
            label = obj.str("label") ?: "",
            image = obj.str("imageUrl"),
            correct = obj.bool("isCorrect") ?: false
        )
    }

    // transparent: check both keys
    val transparent = styling.bool("transparent") ?: styling.bool("transparentBackground") ?: false

    // opacity: backend sends 0–100, Compose needs 0.0–1.0
    val opacity = ((styling.float("opacity") ?: 100f) / 100f).coerceIn(0f, 1f)

    // ── Colors ──
    val bg =
        styling.color("background") ?: styling.color("containerBgColor") ?: Color.White
    // questionBackground and questionBgColor are both sent by the backend
    val questionBg =
        styling.color("questionBackground") ?: styling.color("questionBgColor")
        ?: Color(0xFF111111)
    val questionColor = styling.color("questionColor") ?: Color.White
    val imageBorderColor =
        styling.color("borderColor") ?: styling.color("imageBorderColor") ?: Color(
            0xFFE5E7EB
        )
    val correctBorderColor = styling.color("correctBorderColor") ?: Color(0xFF10B981)
    val labelColor = styling.color("labelColor") ?: Color(0xFF4B5563)
    val questionFontStyle = styling.obj("questionFont").toFontStyle(FontWeight.Bold)
    val questionFontFamily = rememberInteractionFontFamily(
        questionFontStyle.fontFamily, questionFontStyle.fontWeight, questionFontStyle.fontStyle
    )
    val labelFontStyle = styling.obj("labelFont").toFontStyle(FontWeight.Medium)
    val labelFontFamily = rememberInteractionFontFamily(
        labelFontStyle.fontFamily, labelFontStyle.fontWeight, labelFontStyle.fontStyle
    )

    val context = LocalContext.current
    var selected by remember(id) { mutableStateOf(loadInteractionResponse(context, id)) }
    val density = LocalDensity.current

    // Chunk options into rows
    val rows = options.chunked(columns)
    val rowCount = rows.size.coerceAtLeast(1)
    val hasQuestion = question.isNotEmpty()

    // Fixed ratios (not backend-driven) — applied against this interaction's own
    // measured width/height below.
    val spacingRatio = 0.1f   // gap between tiles, as a fraction of tile size
    val labelGapRatio = 0.06f   // image → label gap, as a fraction of tile size
    val labelFontRatio = 0.15f   // label font size, as a fraction of tile size

    // BoxWithConstraints captures this interaction's own measured width/height. Tile
    // size is solved so the grid fits BOTH the row width (columns × tile) and the
    // available height (rows × tile, label rows included) — so it never overflows
    // regardless of how many images/rows the backend sends or how small the box is.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = opacity }
    ) {
        val w = maxWidth
        val h = maxHeight

        val containerRadius = styling.borderRadiusDp(scope, w * 0.06f)
        val gridPadding = w * 0.035f

        // Question header — sized off width, capped so it can't claim more than a
        // quarter of the box's total height.
        val qHPad = w * 0.06f
        val qVPad = w * 0.06f
        val questionFontByWidth = w * 0.07f
        val questionFontByHeight =
            if (hasQuestion) ((h * 0.25f) - qVPad * 2) / 1.3f else questionFontByWidth
        val questionSize = minOf(questionFontByWidth, questionFontByHeight).coerceAtLeast(0.dp)
        val headerHeight = if (hasQuestion) qVPad * 2 + questionSize * 1.3f else 0.dp

        val availableRowW = (w - gridPadding * 2).coerceAtLeast(0.dp)
        val availableGridH = (h - headerHeight - gridPadding * 2).coerceAtLeast(0.dp)

        val tileSizeByWidth = unitFitToSpace(
            available = availableRowW,
            count = columns,
            itemRatio = 1f,
            gapRatio = spacingRatio
        )
        val tileSizeByHeight = unitFitToSpace(
            available = availableGridH,
            count = rowCount,
            itemRatio = 1f + labelGapRatio + labelFontRatio,
            gapRatio = spacingRatio
        )
        val tileSize = minOf(tileSizeByWidth, tileSizeByHeight)

        val tileSpacing = tileSize * spacingRatio
        val labelSpacing = tileSize * labelGapRatio
        val labelSize = tileSize * labelFontRatio
        val imageRadius = tileSize * 0.16f
        val selectedBorderWidth = tileSize * 0.025f

        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(containerRadius))
                .background(if (transparent) Color.Transparent else bg)
        ) {
            // ── Question header strip ──
            if (hasQuestion) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(questionBg)
                        .padding(horizontal = qHPad, vertical = qVPad),
                    contentAlignment = Alignment.Center
                ) {
                    BasicTextWrap(
                        text = question,
                        color = questionColor,
                        fontSizeSp = with(density) { questionSize.toSp() },
                        fontFamily = questionFontFamily,
                        fontWeight = questionFontStyle.fontWeight,
                        fontStyle = questionFontStyle.fontStyle,
                        textDecoration = questionFontStyle.textDecoration,
                        align = questionFontStyle.textAlign ?: TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ── Image grid — weight(1f) fills remaining Column space, prevents bg bleed ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(gridPadding),
                verticalArrangement = Arrangement.spacedBy(
                    tileSpacing,
                    Alignment.CenterVertically
                )
            ) {
                rows.forEach { rowOptions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(
                            tileSpacing,
                            Alignment.CenterHorizontally
                        )
                    ) {
                        rowOptions.forEach { opt ->
                            val isSelected = selected == opt.id
                            val showBorder = selected != null && (opt.id == correctId || isSelected)
                            val borderColor = when {
                                selected != null && opt.id == correctId -> correctBorderColor
                                selected != null && isSelected && opt.id != correctId -> Color(
                                    0xFFEF4444
                                )

                                else -> imageBorderColor
                            }
                            val borderWidth = if (showBorder) selectedBorderWidth else 2.dp

                            Column(
                                modifier = Modifier.width(tileSize),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(labelSpacing)
                            ) {
                                // Explicit size (not fillMaxWidth + aspectRatio) so the tile
                                // actually shrinks when the grid is height-constrained, not
                                // just width-constrained — this is what keeps many rows from
                                // overflowing a short box.
                                Box(
                                    modifier = Modifier
                                        .size(tileSize)
                                        .clip(RoundedCornerShape(imageRadius))
                                        .border(
                                            borderWidth,
                                            borderColor,
                                            RoundedCornerShape(imageRadius)
                                        )
                                        .background(Color(0xFFF3F4F6))
                                        .clickable(
                                            enabled = selected == null,
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            selected = opt.id
                                            saveInteractionResponse(context, id, opt.id)
                                            onTrack(
                                                "clicked", mapOf(
                                                    "interaction_type" to "media_quiz",
                                                    "interaction_id" to (id ?: ""),
                                                    "selected_option" to opt.id
                                                )
                                            )
                                        }
                                ) {
                                    if (!opt.image.isNullOrEmpty()) {
                                        androidx.compose.foundation.Image(
                                            painter = rememberAsyncImagePainter(opt.image),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                                // Label + optional ✓
                                BasicTextWrap(
                                    text = opt.label,
                                    color = labelColor,
                                    fontSizeSp = with(density) { labelSize.toSp() },
                                    fontFamily = labelFontFamily,
                                    fontWeight = labelFontStyle.fontWeight,
                                    fontStyle = labelFontStyle.fontStyle,
                                    textDecoration = labelFontStyle.textDecoration,
                                    align = labelFontStyle.textAlign ?: TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        // Fill empty slots in the last row so tile columns stay aligned
                        repeat(columns - rowOptions.size) {
                            Spacer(modifier = Modifier.width(tileSize))
                        }
                    }
                }
            }
        }
    }
}

private data class MediaQuizOptionData(
    val id: String,
    val label: String,
    val image: String?,
    val correct: Boolean
)

// ============================================================
// RATING
// ============================================================
// Matches RatingRenderer.jsx:
//  • Slider variant: custom track with gradient fill + emoji thumb
//  • Star/emoji variant: row of emojis, unselected dimmed to 40 %
//  • Title: centred, semi-bold
// ============================================================

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun RatingInteraction(
    id: String?,
    config: JsonObject?,
    styling: JsonObject?,
    scope: StoryCanvaScope,
    onTrack: (event: String, metadata: Map<String, Any>) -> Unit
) {
    val title = config.str("title") ?: ""
    val emoji = config.str("emoji") ?: "⭐"
    val maxRating = config.int("maxRating") ?: 5
    val initialRating = config.int("currentRating") ?: 0
    // React uses data.type; legacy Kotlin used config.variant
    val variant = config.str("type") ?: config.str("variant") ?: "slider"

    val bg =
        styling.color("containerBgColor") ?: styling.color("background") ?: Color.White

    // Flat styling keys (matching React's style.* directly) — colors only
    val titleColor = styling.color("titleColor") ?: Color(0xFF111827)
    val sliderFill =
        styling.color("sliderFill") ?: styling.obj("colors").color("sliderFill")
        ?: Color(0xFFE11D48)
    val sliderTrack =
        styling.color("sliderTrack") ?: styling.obj("colors").color("sliderTrack")
        ?: Color(0xFFF3F4F6)
    val titleFontStyle = styling.obj("typography").obj("titleFont").toFontStyle(FontWeight.SemiBold)
    val titleFontFamily = rememberInteractionFontFamily(
        titleFontStyle.fontFamily, titleFontStyle.fontWeight, titleFontStyle.fontStyle
    )

    val context = LocalContext.current
    var rating by remember(id) {
        mutableIntStateOf(
            loadInteractionResponse(context, id)?.toIntOrNull() ?: initialRating.coerceIn(0, maxRating)
        )
    }
    // Once the person rates, the response is locked — persisted so it survives
    // process death / app restarts, same as viewed-story tracking.
    var answered by remember(id) { mutableStateOf(loadInteractionResponse(context, id) != null) }
    val density = LocalDensity.current
    val n = maxRating.coerceAtLeast(1)

    // --- Flying emoji burst state ---
    val flyingEmojis = remember { mutableStateListOf<FlyingEmojiInstance>() }
    var containerRootPos by remember { mutableStateOf(Offset.Zero) }
    var lastThumbCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val starCoordinates = remember { mutableStateMapOf<Int, LayoutCoordinates>() }

    fun spawnFlyingEmoji(coordinates: LayoutCoordinates, fontSizeSp: TextUnit) {
        val topLeft = coordinates.positionInRoot() - containerRootPos
        flyingEmojis.add(
            FlyingEmojiInstance(
                emojiChar = emoji,
                localOffset = topLeft,
                sizePx = coordinates.size,
                fontSizeSp = fontSizeSp
            )
        )
    }

    // BoxWithConstraints captures this interaction's own measured width/height. Title
    // and emoji sizes are derived from that box; for the star-row variant, the emoji
    // size is additionally capped so `maxRating` items always fit the row width.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { containerRootPos = it.positionInRoot() }
    ) {
        val w = maxWidth
        val h = maxHeight

        val borderRadius = styling.borderRadiusDp(scope, minOf(w, h) * 0.09f)
        val padding = minOf(w, h) * 0.15f
        val rowGap = minOf(w, h) * 0.15f

        val titleSize = minOf(h * 0.15f, w * 0.085f)
        val emojiBaseline = minOf(h * 0.32f, w * 0.16f)
        val emojiByRowWidth =
            unitFitToSpace(available = w - padding * 2, count = n, itemRatio = 1.3f)
        val emojiSize = if (variant == "slider") {
            emojiBaseline
        } else {
            minOf(emojiBaseline, emojiByRowWidth)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(borderRadius))
                .background(bg)
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(
                rowGap,
                Alignment.CenterVertically
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (title.isNotEmpty()) {
                BasicTextWrap(
                    text = title,
                    color = titleColor,
                    fontSizeSp = with(density) { titleSize.toSp() },
                    fontFamily = titleFontFamily,
                    fontWeight = titleFontStyle.fontWeight,
                    fontStyle = titleFontStyle.fontStyle,
                    textDecoration = titleFontStyle.textDecoration,
                    align = titleFontStyle.textAlign ?: TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (variant == "slider") {
                // Custom slider: thin gradient track + emoji thumb positioned at fill %
                // Matches React: track height 10px, gradient #d946ef → sliderFill, emoji as thumb
                val fillFraction = (rating.toFloat() / maxRating.coerceAtLeast(1)).coerceIn(0f, 1f)
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(emojiSize + 10.dp)
                ) {
                    val totalWidth = maxWidth
                    val trackHeight = 28.dp
                    val thumbSize = emojiSize * 1.1f
                    val thumbOffset = maxOf(
                        0.dp, minOf(
                            totalWidth - thumbSize,
                            totalWidth * fillFraction - thumbSize / 2
                        )
                    )

                    // Track + fill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(trackHeight)
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(trackHeight / 2))
                            .background(sliderTrack)
                            .let { m ->
                                if (answered) {
                                    m
                                } else {
                                    m.pointerInput(maxRating, id) {
                                        detectHorizontalDragGestures(
                                            onDragEnd = {
                                                answered = true
                                                saveInteractionResponse(context, id, rating.toString())
                                                lastThumbCoordinates?.let {
                                                    spawnFlyingEmoji(it, with(density) { emojiSize.toSp() })
                                                }
                                            }
                                        ) { change, _ ->
                                            change.consume()
                                            val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                            val newRating =
                                                (fraction * maxRating).roundToInt().coerceIn(0, maxRating)
                                            if (newRating != rating) {
                                                rating = newRating
                                                onTrack(
                                                    "clicked", mapOf(
                                                        "interaction_type" to "rating",
                                                        "interaction_id" to (id ?: ""),
                                                        "value" to newRating
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fillFraction)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(Color(0xFFD946EF), sliderFill)
                                    )
                                )
                        )
                    }

                    // Emoji thumb
                    Box(
                        modifier = Modifier
                            .size(thumbSize)
                            .offset(
                                x = thumbOffset,
                                y = -(thumbSize * 0.1f)
                            )
                            .align(Alignment.CenterStart)
                            .onGloballyPositioned { lastThumbCoordinates = it },
                        contentAlignment = Alignment.Center
                    ) {
                        BasicTextWrap(
                            modifier = Modifier.fillMaxSize(),
                            text = emoji,
                            color = Color.Unspecified,
                            fontSizeSp = with(density) { emojiSize.toSp() },
                            align = TextAlign.Center
                        )
                    }
                }
            } else {
                // Star / emoji row — inactive items are dimmed to 40 %
                // Matches React: filter grayscale(1) opacity(0.4) for inactive, scale(1.1) for active
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    repeat(maxRating) { i ->
                        val isActive = i < rating
                        Box(
                            modifier = Modifier
                                .graphicsLayer { alpha = if (isActive) 1f else 0.4f }
                                .onGloballyPositioned { starCoordinates[i] = it }
                                .clickable(
                                    enabled = !answered,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    rating = i + 1
                                    answered = true
                                    saveInteractionResponse(context, id, rating.toString())
                                    onTrack(
                                        "clicked", mapOf(
                                            "interaction_type" to "rating",
                                            "interaction_id" to (id ?: ""),
                                            "value" to (i + 1)
                                        )
                                    )
                                    starCoordinates[i]?.let {
                                        spawnFlyingEmoji(
                                            it,
                                            with(density) { (emojiSize * 1.1f).toSp() }
                                        )
                                    }
                                }
                        ) {
                            BasicTextWrap(
                                text = emoji,
                                color = Color.Unspecified,
                                fontSizeSp = with(density) { (if (isActive) emojiSize * 1.1f else emojiSize).toSp() },
                                align = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // --- Flying emoji overlay — drawn last so it renders above the card ---
        flyingEmojis.forEach { instance ->
            key(instance.id) {
                FlyingEmojiBurst(
                    instance = instance,
                    onFinished = { flyingEmojis.remove(instance) }
                )
            }
        }
    }
}

private data class FlyingEmojiInstance(
    val id: Long = System.nanoTime(),
    val emojiChar: String,
    val localOffset: Offset, // top-left, relative to container root
    val sizePx: IntSize,     // exact size of the source emoji element
    val fontSizeSp: TextUnit
)

@Composable
private fun FlyingEmojiBurst(
    instance: FlyingEmojiInstance,
    onFinished: () -> Unit
) {
    val translateY = remember { Animatable(0f) }
    val translateX = remember { Animatable(0f) }
    val scale =
        remember { Animatable(1f) } // starts at original size — it IS the emoji, not a new spawn
    val alpha = remember { Animatable(1f) }
    val density = LocalDensity.current

    LaunchedEffect(instance.id) {
        // Doubled rise distance per request (was 90.dp)
        val riseDistance = with(density) { 180.dp.toPx() }
        val drift = with(density) { (-12..12).random().dp.toPx() }

        launch {
            // small pop right as it lifts off, then settle
            scale.animateTo(1.3f, tween(140, easing = FastOutSlowInEasing))
            scale.animateTo(1f, tween(140, easing = LinearOutSlowInEasing))
        }
        launch { translateY.animateTo(-riseDistance, tween(950, easing = LinearOutSlowInEasing)) }
        launch { translateX.animateTo(drift, tween(950, easing = LinearOutSlowInEasing)) }

        delay(500)
        alpha.animateTo(0f, tween(450, easing = LinearEasing))
        onFinished()
    }

    val widthDp = with(density) { instance.sizePx.width.toDp() }
    val heightDp = with(density) { instance.sizePx.height.toDp() }

    Box(
        modifier = Modifier
            .size(widthDp, heightDp)
            .offset {
                // localOffset is the exact top-left of the source emoji, so at translate=0
                // this box renders precisely on top of the original — no jump, no shift.
                IntOffset(
                    x = (instance.localOffset.x + translateX.value).roundToInt(),
                    y = (instance.localOffset.y + translateY.value).roundToInt()
                )
            }
            .graphicsLayer {
                this.alpha = alpha.value
                scaleX = scale.value
                scaleY = scale.value
            },
        contentAlignment = Alignment.Center
    ) {
        BasicTextWrap(
            text = instance.emojiChar,
            color = Color.Unspecified,
            fontSizeSp = instance.fontSizeSp,
            align = TextAlign.Center
        )
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun ReactionInteraction(
    id: String?,
    config: JsonObject?,
    styling: JsonObject?,
    scope: StoryCanvaScope,
    onTrack: (event: String, metadata: Map<String, Any>) -> Unit
) {
    val emojiPairs = parseReactionEmojis(config)
    val showCount = config.bool("showCount") ?: true

    // transparent: check both keys
    val transparent = styling.bool("transparent") ?: styling.bool("transparentBackground") ?: false

    // opacity: backend sends 0–100, Compose needs 0.0–1.0
    val opacity = ((styling.float("opacity") ?: 100f) / 100f).coerceIn(0f, 1f)

    // ── Colors ──
    val bg = styling.color("background") ?: styling.color("containerBgColor")
    ?: Color.Transparent
    // Bubble: white fill, grey circular border
    val bubbleBg = styling.color("bubbleBgColor") ?: Color.White
    val bubbleBorder = styling.color("bubbleBorderColor") ?: Color(0xFFE5E7EB)
    val countColor = styling.color("countColor") ?: Color(0xFF374151)

    val context = LocalContext.current
    var picked by remember(id) { mutableStateOf(loadInteractionResponse(context, id)) }
    val density = LocalDensity.current
    val n = emojiPairs.size.coerceAtLeast(1)

    // --- Flying emoji burst state — reuses the existing FlyingEmojiInstance /
    // FlyingEmojiBurst pair defined alongside RatingInteraction, same pattern. ---
    val flyingEmojis = remember { mutableStateListOf<FlyingEmojiInstance>() }
    var containerRootPos by remember { mutableStateOf(Offset.Zero) }
    val bubbleCoordinates = remember { mutableStateMapOf<String, LayoutCoordinates>() }

    fun spawnFlyingEmoji(emojiChar: String, coordinates: LayoutCoordinates, fontSizeSp: TextUnit) {
        val topLeft = coordinates.positionInRoot() - containerRootPos
        flyingEmojis.add(
            FlyingEmojiInstance(
                emojiChar = emojiChar,
                localOffset = topLeft,
                sizePx = coordinates.size,
                fontSizeSp = fontSizeSp
            )
        )
    }

    // Bubbles are always circular (or pill, once a selection is made and a count line
    // appears below) and are NOT individually flexible — so the emoji size must be
    // solved up front to fit `n` bubbles into the row's actual width, and the bubble
    // height into the box's actual height, rather than a fixed % of the full screen
    // that ignores both.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = opacity }
            .onGloballyPositioned { containerRootPos = it.positionInRoot() }
    ) {
        val w = maxWidth
        val h = maxHeight

        val gapRatio =
            0.4f                       // gap between bubbles, as a fraction of emoji size
        // Restored to the original resting-state ratio — sizing is based purely on the
        // resting circle, same as before expansion was added. The expanded (pill) state
        // is allowed to grow past this budget and overflow the box's own height when it
        // happens, rather than shrinking the resting size to pre-reserve room for it.
        val heightRatio = 2f

        val emojiByWidth =
            unitFitToSpace(available = w, count = n, itemRatio = 2f, gapRatio = gapRatio)
        val emojiByHeight = h / heightRatio
        val emojiBaseline = minOf(h, w)
        val emojiSize = minOf(emojiByWidth, emojiByHeight, emojiBaseline)

        // Bubble WIDTH stays fixed and drives the corner radius. Bubble HEIGHT is what
        // animates — since radius = bubbleSize / 2 never changes, growing the height
        // past the width naturally reads as "circle stretching into a pill" with no
        // separate shape-swap logic needed. Expansion grows FROM this resting size and
        // may extend past the widget's own box bounds — same overflow pattern used for
        // the Send button in InputInteraction.
        val bubbleSize = emojiSize * 2f
        val bubbleCornerRadius = bubbleSize / 2
        val bubbleShape = RoundedCornerShape(bubbleCornerRadius)
        val restingHeight = bubbleSize
        val expandedHeight = bubbleSize * 1.3f
        val countSize = emojiSize * 0.42f
        val gap = emojiSize * gapRatio
        val containerRadius = styling.borderRadiusDp(scope, 0.dp)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(containerRadius))
                .background(if (transparent) Color.Transparent else bg),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                emojiPairs.forEach { (key, emoji) ->
                    val isPicked = picked == key
                    // Every bubble expands together the moment ANY pick is made, so the
                    // row stays visually aligned. When showCount is false, skip the
                    // expansion/count entirely — just the emoji tap animation plays.
                    val isExpanded = picked != null && showCount
                    val animatedBubbleHeight by animateDpAsState(
                        targetValue = if (isExpanded) expandedHeight else restingHeight,
                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                        label = "bubbleHeight-$key"
                    )

                    Column(
                        modifier = Modifier
                            .requiredSize(bubbleSize, animatedBubbleHeight)
                            .clip(bubbleShape)
                            .background(bubbleBg)
                            .border(2.dp, bubbleBorder, bubbleShape)
                            .onGloballyPositioned { bubbleCoordinates[key] = it }
                            .clickable(
                                enabled = picked == null,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                picked = key
                                saveInteractionResponse(context, id, key)
                                onTrack(
                                    "clicked", mapOf(
                                        "interaction_type" to "reaction",
                                        "interaction_id" to (id ?: ""),
                                        "selected_option" to key
                                    )
                                )
                                bubbleCoordinates[key]?.let {
                                    spawnFlyingEmoji(
                                        emoji,
                                        it,
                                        with(density) { (emojiSize * 1.15f).toSp() }
                                    )
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(
                            emojiSize * 0.12f,
                            Alignment.CenterVertically
                        )
                    ) {
                        BasicTextWrap(
                            text = emoji,
                            color = Color.Unspecified,
                            fontSizeSp = with(density) { (if (isPicked) emojiSize * 1.15f else emojiSize).toSp() },
                            align = TextAlign.Center
                        )
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = fadeIn(tween(250, delayMillis = 150)) +
                                    expandVertically(tween(300, delayMillis = 100)),
                            exit = fadeOut(tween(150)) + shrinkVertically(tween(200))
                        ) {
                            BasicTextWrap(
                                text = if (isPicked) "2k" else "0",
                                color = countColor,
                                fontSizeSp = with(density) { countSize.toSp() },
                                fontWeight = FontWeight.Bold,
                                align = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // --- Flying emoji overlay — reuses the shared FlyingEmojiBurst composable,
        // drawn last so it renders above the bubbles. ---
        flyingEmojis.forEach { instance ->
            key(instance.id) {
                FlyingEmojiBurst(
                    instance = instance,
                    onFinished = { flyingEmojis.remove(instance) }
                )
            }
        }
    }
}

// ============================================================
// COUNTDOWN
// ============================================================
// Matches CountdownRenderer.jsx:
//  • Each time unit → two individual digit boxes side-by-side
//  • ":" separator between units
//  • Full label words: "days", "hours", "minutes", "seconds"
//  • Title left-aligned; container has light border
// ============================================================

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun CountdownInteraction(
    id: String?,
    config: JsonObject?,
    styling: JsonObject?,
    scope: StoryCanvaScope
) {
    val title = config.str("title") ?: ""
    val endDate = config.str("endDate") ?: ""
    val endTime = config.str("endTime") ?: "23:59"

    // Support both flat keys (new / React) and nested display object (legacy)
    val display = config.obj("display")
    val showDays = config.bool("showDays") ?: display.bool("showDays") ?: true
    val showHours = config.bool("showHours") ?: display.bool("showHours") ?: true
    val showMinutes = config.bool("showMinutes") ?: display.bool("showMinutes") ?: true
    val showSeconds = config.bool("showSeconds") ?: display.bool("showSeconds") ?: true

    val targetMs = remember(endDate, endTime) {
        runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                .apply { timeZone = TimeZone.getDefault() }
                .parse("$endDate $endTime")?.time ?: 0L
        }.getOrDefault(0L)
    }

    // transparent: check both keys
    val transparent = styling.bool("transparent") ?: styling.bool("transparentBackground") ?: false

    // opacity: backend sends 0–100, Compose needs 0.0–1.0
    val opacity = ((styling.float("opacity") ?: 100f) / 100f).coerceIn(0f, 1f)

    // ── Colors ──
    val bg =
        styling.color("background") ?: styling.color("containerBgColor") ?: Color.White
    val titleColor = styling.color("titleColor") ?: Color(0xFF111827)
    val digitBg =
        styling.color("digitBackground") ?: styling.color("digitBgColor") ?: Color(
            0xFFF3F4F6
        )
    val digitColor =
        styling.color("digitColor") ?: styling.color("digitTextColor") ?: Color(
            0xFF1F2937
        )
    val labelColor = styling.color("labelColor") ?: Color(0xFF9CA3AF)
    val sepColor = styling.color("separatorColor") ?: Color(0xFF1F2937)
    val titleFontStyle = styling.obj("titleFont").toFontStyle(FontWeight(800))
    val titleFontFamily = rememberInteractionFontFamily(
        titleFontStyle.fontFamily, titleFontStyle.fontWeight, titleFontStyle.fontStyle
    )

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val remaining = (targetMs - now).coerceAtLeast(0L)
    val totalSeconds = remaining / 1000
    val days = if (endDate.isEmpty()) 0L else totalSeconds / 86400
    val hours = if (endDate.isEmpty()) 12L else (totalSeconds % 86400) / 3600
    val minutes = if (endDate.isEmpty()) 34L else (totalSeconds % 3600) / 60
    val seconds = if (endDate.isEmpty()) 56L else totalSeconds % 60

    val density = LocalDensity.current

    data class CountUnit(val value: Long, val label: String)

    val units = buildList {
        if (showDays) add(CountUnit(days, "days"))
        if (showHours) add(CountUnit(hours, "hours"))
        if (showMinutes) add(CountUnit(minutes, "minutes"))
        if (showSeconds) add(CountUnit(seconds, "seconds"))
    }
    val numUnits = units.size
    val hasTitle = title.isNotEmpty()

    // Celebrate once the countdown reaches zero — an Instagram-style confetti burst
    // around the countdown box. `celebrated` ensures this fires exactly once even
    // though `remaining` stays at 0 on every subsequent recomposition/tick.
    val isFinished = endDate.isNotEmpty() && remaining <= 0L
    var celebrated by remember(id) { mutableStateOf(false) }
    var confettiTrigger by remember(id) { mutableIntStateOf(0) }
    LaunchedEffect(isFinished) {
        if (isFinished && !celebrated) {
            celebrated = true
            confettiTrigger++
        }
    }

    // ── Ratios (fixed; not backend-driven) — every internal dimension is expressed
    // as a multiple of `digitSize`, then `digitSize` itself is solved below from this
    // interaction's own measured width/height so the assembled row of digit cells +
    // colons always fits — for any number of visible units (1–4), on any device. This
    // is what fixes the countdown being clipped on narrower/shorter boxes: previously
    // digit size only ever scaled off the whole slide's height, never the box's own
    // width, so a long unit count could overflow a box that ended up narrow.
    val digitBoxWRatio = 2f      // digitBoxW    = digitSize * 2
    val digitBoxHRatio = 2.5f    // digitBoxH    = digitSize * 2.5
    val digitSpacingRatio = 0.3f    // digitSpacing = digitSize * 0.3
    val labelSizeRatio = 0.68f   // labelSize    = digitSize * 0.68
    val titleSizeRatio = 1.28f   // titleSize    = digitSize * 1.28
    val colonWidthRatio = 0.6f    // approximate rendered width of ":" at digitSize

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = opacity }
    ) {
        val w = maxWidth
        val h = maxHeight

        val containerRadius = styling.borderRadiusDp(scope, minOf(w, h) * 0.08f)
        val padding = minOf(w, h) * 0.1f
        val titleRowGap = padding * 0.5f

        // One unit cell = two digit boxes + the spacing between them.
        val cellWidthRatio = 2f * digitBoxWRatio + digitSpacingRatio   // 4.3
        val cellHeightRatio = digitBoxHRatio + digitSpacingRatio + labelSizeRatio // 3.48

        val availableWidth = (w - padding * 2).coerceAtLeast(0.dp)
        val digitSizeByWidth = unitFitToSpace(
            available = availableWidth,
            count = numUnits,
            itemRatio = cellWidthRatio,
            gapRatio = colonWidthRatio
        )

        val availableHeight =
            (h - padding * 2 - (if (hasTitle) titleRowGap else 0.dp)).coerceAtLeast(0.dp)
        val heightDenominator = cellHeightRatio + (if (hasTitle) titleSizeRatio * 1.25f else 0f)
        val digitSizeByHeight =
            if (heightDenominator > 0f) availableHeight / heightDenominator else availableHeight

        // availableWidth/availableHeight above are already coerced to >= 0.dp, and
        // unitFitToSpace never returns a negative value for non-negative input, so
        // digitSize is guaranteed >= 0 here with no separate floor needed — sizing
        // stays exactly proportional to the box on every device, all the way down.
        val digitSize = minOf(digitSizeByWidth, digitSizeByHeight)

        val titleSize = digitSize * titleSizeRatio
        val labelSize = digitSize * labelSizeRatio

        // Digit cell box dimensions derived from digitSize so they scale consistently
        val digitBoxH = digitSize * digitBoxHRatio
        val digitBoxW = digitSize * digitBoxWRatio
        val digitCornerR = digitSize * 0.5f
        val digitSpacing = digitSize * digitSpacingRatio

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(containerRadius))
                .background(if (transparent) Color.Transparent else bg)
                .border(2.dp, Color(0xFFE5E7EB), RoundedCornerShape(containerRadius))
                .padding(padding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(titleRowGap)
            ) {
                // Title — left-aligned (matches React alignItems: 'flex-start')
                if (hasTitle) {
                    BasicTextWrap(
                        modifier = Modifier
                            .padding(start = 8.dp, end = 8.dp, top = 8.dp)
                            .fillMaxWidth(),
                        text = title,
                        color = titleColor,
                        fontSizeSp = with(density) { titleSize.toSp() },
                        lineHeight = with(density) { (titleSize * 1.25f).toSp() },
                        fontFamily = titleFontFamily,
                        fontWeight = titleFontStyle.fontWeight,
                        fontStyle = titleFontStyle.fontStyle,
                        textDecoration = titleFontStyle.textDecoration,
                        align = titleFontStyle.textAlign ?: TextAlign.Start
                    )
                }

                // Timer row — weight(1f) fills remaining Column space, prevents bg bleed
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    units.forEachIndexed { index, unit ->
                        CountdownDigitCell(
                            value = unit.value,
                            label = unit.label,
                            digitBg = digitBg,
                            digitColor = digitColor,
                            digitSize = digitSize,
                            digitBoxH = digitBoxH,
                            digitBoxW = digitBoxW,
                            digitCornerR = digitCornerR,
                            digitSpacing = digitSpacing,
                            labelColor = labelColor,
                            labelSize = labelSize
                        )
                        // Colon separator between units — matches digit box height
                        if (index < units.size - 1) {
                            Box(
                                modifier = Modifier.height(digitBoxH),
                                contentAlignment = Alignment.Center
                            ) {
                                BasicTextWrap(
                                    modifier = Modifier.offset(y = (-24).dp),
                                    text = ":",
                                    color = sepColor,
                                    fontSizeSp = with(density) { digitSize.toSp() },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Instagram-style confetti burst around the countdown once it hits zero.
        CountdownConfettiBurst(trigger = confettiTrigger, boxWidth = w, boxHeight = h)
    }
}

@Composable
private fun CountdownDigitCell(
    value: Long,
    label: String,
    digitBg: Color,
    digitColor: Color,
    digitSize: Dp,
    digitBoxH: Dp,
    digitBoxW: Dp,
    digitCornerR: Dp,
    digitSpacing: Dp,
    labelColor: Color,
    labelSize: Dp
) {
    val d1 = (value / 10).toString()
    val d2 = (value % 10).toString()
    val density = LocalDensity.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(digitSpacing)
    ) {
        // Two individual digit boxes side-by-side (matching React's split-digit design)
        Row(horizontalArrangement = Arrangement.spacedBy(digitSpacing)) {
            listOf(d1, d2).forEach { digit ->
                Box(
                    modifier = Modifier
                        .width(digitBoxW)
                        .height(digitBoxH)
                        .clip(RoundedCornerShape(digitCornerR))
                        .background(digitBg)
                        .padding(horizontal = digitSpacing),
                    contentAlignment = Alignment.Center
                ) {
                    BasicTextWrap(
                        text = digit,
                        color = digitColor,
                        fontSizeSp = with(density) { (digitSize * 1.3f).toSp() },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        // Full word label below the digit pair
        BasicTextWrap(
            text = label,
            color = labelColor,
            fontSizeSp = with(density) { labelSize.toSp() },
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ============================================================
// COUNTDOWN — confetti burst (plays once, when the countdown hits zero)
// ============================================================

private data class ConfettiPiece(
    val id: Long = System.nanoTime() + (0..1_000_000).random(),
    val startXFraction: Float,   // 0f..1f across the confetti canvas width
    val color: Color,
    val sizeDp: Dp,
    val delayMs: Int,
    val fallDurationMs: Int,
    val driftPx: Float,
    val rotationDegrees: Float
)

/**
 * A short-lived confetti burst rendered "around" the countdown box, matching the
 * Instagram-style celebration when a countdown finishes. `trigger` is bumped
 * exactly once (by the caller) the moment the countdown hits zero; `key(trigger)`
 * below restarts the whole burst if it's ever bumped again. The overlay is sized
 * larger than the countdown's own box and offset outward on every side — the
 * parent wrapper Box (StorySlideContent) doesn't clip its children, so this is
 * free to render beyond the interaction's own assigned bounds, same overflow
 * pattern used elsewhere in this file (Input's Send button, Quiz's explanation).
 */
@Composable
private fun CountdownConfettiBurst(
    trigger: Int,
    boxWidth: Dp,
    boxHeight: Dp
) {
    if (trigger <= 0) return
    key(trigger) {
        val confettiColors = remember {
            listOf(
                Color(0xFFFF3B30), Color(0xFFFFCC00), Color(0xFF34C759),
                Color(0xFF007AFF), Color(0xFFAF52DE), Color(0xFFFF9500), Color(0xFFFF2D55)
            )
        }
        val pieceCount = 32
        val pieces = remember(trigger) {
            List(pieceCount) {
                ConfettiPiece(
                    startXFraction = (0..100).random() / 100f,
                    color = confettiColors.random(),
                    sizeDp = (5..10).random().dp,
                    delayMs = (0..220).random(),
                    fallDurationMs = (1000..1700).random(),
                    driftPx = (-90..90).random().toFloat(),
                    rotationDegrees = (360..1080).random().toFloat() * (if ((0..1).random() == 0) 1f else -1f)
                )
            }
        }
        var visible by remember(trigger) { mutableStateOf(true) }
        LaunchedEffect(trigger) {
            delay(2000L)
            visible = false
        }

        if (visible) {
            val overlayWidth = boxWidth * 1.3f
            val overlayHeight = boxHeight * 2.1f
            Box(
                modifier = Modifier
                    .offset(x = -boxWidth * 0.15f, y = -boxHeight * 0.7f)
                    .size(width = overlayWidth, height = overlayHeight)
            ) {
                pieces.forEach { piece ->
                    key(piece.id) {
                        ConfettiPieceView(
                            piece = piece,
                            canvasWidth = overlayWidth,
                            canvasHeight = overlayHeight
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfettiPieceView(piece: ConfettiPiece, canvasWidth: Dp, canvasHeight: Dp) {
    val translateY = remember { Animatable(0f) }
    val translateX = remember { Animatable(0f) }
    val rotation = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    val density = LocalDensity.current

    LaunchedEffect(piece.id) {
        val fallDistancePx = with(density) { canvasHeight.toPx() }
        delay(piece.delayMs.toLong())
        launch {
            translateY.animateTo(
                fallDistancePx,
                animationSpec = tween(piece.fallDurationMs, easing = LinearOutSlowInEasing)
            )
        }
        launch {
            translateX.animateTo(
                piece.driftPx,
                animationSpec = tween(piece.fallDurationMs, easing = FastOutSlowInEasing)
            )
        }
        launch {
            rotation.animateTo(
                piece.rotationDegrees,
                animationSpec = tween(piece.fallDurationMs, easing = LinearEasing)
            )
        }
        launch {
            delay((piece.fallDurationMs * 0.6f).toLong())
            alpha.animateTo(0f, animationSpec = tween((piece.fallDurationMs * 0.4f).toInt()))
        }
    }

    Box(
        modifier = Modifier
            .offset(x = canvasWidth * piece.startXFraction, y = 0.dp)
            .offset { IntOffset(translateX.value.roundToInt(), translateY.value.roundToInt()) }
            .size(piece.sizeDp)
            .graphicsLayer {
                rotationZ = rotation.value
                this.alpha = alpha.value
            }
            .background(piece.color, RoundedCornerShape(1.dp))
    )
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun PromoInteraction(
    id: String?,
    config: JsonObject?,
    styling: JsonObject?,
    scope: StoryCanvaScope,
    onTrack: (event: String, metadata: Map<String, Any>) -> Unit
) {
    val code = config.str("couponCode")
        ?.takeIf { it.isNotBlank() }
        ?: config.str("title")?.takeIf { it.isNotBlank() }
        ?: "COUPON"
    // Support both new (showCopyButton) and old (copyButton) keys
    val showCopy = config.bool("showCopyButton") ?: config.bool("copyButton") ?: true

    // transparent: check both keys
    val transparent = styling.bool("transparent") ?: styling.bool("transparentBackground") ?: false

    // opacity: backend sends 0–100, Compose needs 0.0–1.0
    val opacity = ((styling.float("opacity") ?: 100f) / 100f).coerceIn(0f, 1f)

    // ── Colors ──
    val bg = styling.color("backgroundColor") ?: styling.color("containerBgColor")
    ?: styling.color("background") ?: Color(0xFFF3F3F3)
    // textColor comes from the flat "textColor" key or the "text" key (backend sends "text")
    val textColor = styling.color("textColor") ?: styling.color("text") ?: Color.Black
    // borderColor: empty string from backend → null → transparent (no visible border)
    val borderColor = styling.color("borderColor") ?: Color.Transparent
    val couponCodeFontStyle = styling.obj("couponCodeFont").toFontStyle(FontWeight.Bold)
    val couponCodeFontFamily = rememberInteractionFontFamily(
        couponCodeFontStyle.fontFamily, couponCodeFontStyle.fontWeight, couponCodeFontStyle.fontStyle
    )

    val clipboardManager = LocalClipboardManager.current
    var copied by remember(id) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1000.milliseconds)
            copied = false
        }
    }
    val density = LocalDensity.current

    // Promo is a short, wide banner — every internal size is derived primarily from
    // this interaction's own measured HEIGHT (so icons/text never outgrow a short
    // box), with the coupon font additionally capped by width.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = opacity }
    ) {
        val w = maxWidth
        val h = maxHeight

        val borderRadiusDp =
            styling.borderRadiusDp(scope, minOf(w, h) * 0.11f)   // TicketShape clamps it to minOf(w,h)/4 internally
        val notchRadiusDp = h * 0.18f
        val iconSizeDp = h * 0.38f
        val copyIconSizeDp = h * 0.32f
        val hPadding = w * 0.05f
        val codePadding = w * 0.025f
        val codeFontSize = minOf(h * 0.34f, w * 0.10f)

        // Compute px values for TicketShape (createOutline receives px)
        val notchRadiusPx = with(density) { notchRadiusDp.toPx() }
        val cornerRadiusPx = with(density) { borderRadiusDp.toPx() }

        val ticketShape = remember(notchRadiusPx, cornerRadiusPx) {
            TicketShape(notchRadiusPx = notchRadiusPx, cornerRadiusPx = cornerRadiusPx)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(2.dp, borderColor, ticketShape)
                .clip(ticketShape)
                .background(if (transparent) Color.Transparent else bg)
                .clickable(
                    enabled = showCopy,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    clipboardManager.setText(AnnotatedString(code))
                    copied = true

                    onTrack(
                        "clicked",
                        mapOf(
                            "interaction_type" to "promo",
                            "interaction_id" to (id ?: ""),
                            "value" to code
                        )
                    )
                }
                .padding(horizontal = hPadding),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // ── Left: discount icon ──
                Canvas(
                    modifier = Modifier
                        .padding(start = 24.dp)
                        .size(iconSizeDp)
                ) {
                    val cw = size.width
                    val ch = size.height
                    val strokePx = 5f * density.density
                    val thickPx = 7f * density.density
                    val tc = textColor
                    drawRoundRect(
                        color = tc,
                        cornerRadius = CornerRadius(cw * 8f / 48f),
                        style = Stroke(width = strokePx)
                    )
                    drawLine(
                        color = tc,
                        start = Offset(cw * 33.6f / 48f, ch * 14.4f / 48f),
                        end = Offset(cw * 14.4f / 48f, ch * 33.6f / 48f),
                        strokeWidth = thickPx,
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = tc,
                        radius = cw * 4f / 48f,
                        center = Offset(cw * 16.8f / 48f, ch * 16.8f / 48f)
                    )
                    drawCircle(
                        color = tc,
                        radius = cw * 4f / 48f,
                        center = Offset(cw * 31.2f / 48f, ch * 31.2f / 48f)
                    )
                }

                // ── Centre: coupon code ──
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = codePadding),
                    contentAlignment = Alignment.Center
                ) {
                    BasicTextWrap(
                        text = if (copied) "COPIED!" else code.uppercase(),
                        color =
//                            if (copied) Color(0xFF10B981) else
                            textColor,
                        fontSizeSp = with(density) { codeFontSize.toSp() },
                        fontFamily = couponCodeFontFamily,
                        fontWeight = couponCodeFontStyle.fontWeight,
                        fontStyle = couponCodeFontStyle.fontStyle,
                        textDecoration = couponCodeFontStyle.textDecoration,
                        align = couponCodeFontStyle.textAlign ?: TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // ── Right: copy / check icon ──
                if (showCopy) {
                    Canvas(
                        modifier = Modifier
                            .padding(end = 24.dp)
                            .size(copyIconSizeDp)
                    ) {
                        val cw = size.width
                        val ch = size.height
                        val strokePx = 6f * density.density
                        if (copied) {
                            val checkPath = Path().apply {
                                moveTo(cw * 6f / 36f, ch * 18f / 36f)
                                lineTo(cw * 15f / 36f, ch * 28f / 36f)
                                lineTo(cw * 30f / 36f, ch * 8f / 36f)
                            }
                            drawPath(
                                path = checkPath,
                                color = textColor,
//                                    Color(0xFF10B981),
                                style = Stroke(
                                    width = strokePx,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        } else {
                            val r = CornerRadius(cw * 6f / 36f)
                            val s = Stroke(width = strokePx)
                            drawRoundRect(
                                color = textColor,
                                topLeft = Offset(cw * 7.2f / 36f, 0f),
                                size = Size(cw * 28.8f / 36f, ch * 28.8f / 36f),
                                cornerRadius = r,
                                style = s
                            )
                            drawRoundRect(
                                color = textColor,
                                topLeft = Offset(0f, ch * 7.2f / 36f),
                                size = Size(cw * 28.8f / 36f, ch * 28.8f / 36f),
                                cornerRadius = r,
                                style = s
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A Shape that clips content to a ticket outline: a rounded rectangle with
 * semicircular notches cut into the left and right edges at the midpoint.
 * Matches the CSS radial-gradient mask used in PromoRenderer.jsx.
 */
private class TicketShape(
    private val notchRadiusPx: Float,
    private val cornerRadiusPx: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val w = size.width
        val h = size.height
        val cr = cornerRadiusPx.coerceAtMost(minOf(w, h) / 4f)
        val nr = notchRadiusPx.coerceAtMost(h / 3f)

        val path = Path().apply {
            // ── Start: top edge, right of top-left corner ──
            moveTo(cr, 0f)
            // Top edge →
            lineTo(w - cr, 0f)
            // Top-right corner (CW 90°)
            arcTo(Rect(w - 2 * cr, 0f, w, 2 * cr), -90f, 90f, false)
            // Right edge ↓ to right notch
            lineTo(w, h / 2 - nr)
            // Right notch: concave semicircle sweeping inward (CCW = –180°)
            arcTo(Rect(w - nr, h / 2 - nr, w + nr, h / 2 + nr), -90f, -180f, false)
            // Right edge ↓ to bottom-right corner
            lineTo(w, h - cr)
            // Bottom-right corner (CW 90°)
            arcTo(Rect(w - 2 * cr, h - 2 * cr, w, h), 0f, 90f, false)
            // Bottom edge ←
            lineTo(cr, h)
            // Bottom-left corner (CW 90°)
            arcTo(Rect(0f, h - 2 * cr, 2 * cr, h), 90f, 90f, false)
            // Left edge ↑ to left notch
            lineTo(0f, h / 2 + nr)
            // Left notch: concave semicircle sweeping inward (CCW = –180°)
            arcTo(Rect(-nr, h / 2 - nr, nr, h / 2 + nr), 90f, -180f, false)
            // Left edge ↑ to top-left corner
            lineTo(0f, cr)
            // Top-left corner (CW 90°)
            arcTo(Rect(0f, 0f, 2 * cr, 2 * cr), 180f, 90f, false)
            close()
        }
        return Outline.Generic(path)
    }
}

// ============================================================
// INPUT (Question)
// ============================================================
// Matches QuestionRenderer.jsx:
//  • White container, shadow, rounded
//  • Title centred, bold
//  • Grey-background text input box, centred placeholder
//  • Submit button (interactive, not in preview renderer)
// ============================================================
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun InputInteraction(
    id: String?,
    config: JsonObject?,
    styling: JsonObject?,
    scope: StoryCanvaScope,
    onTrack: (event: String, metadata: Map<String, Any>) -> Unit,
    onFocusChanged: (Boolean) -> Unit
) {
    val title = config.str("title") ?: ""
    val placeholder = config.str("placeholder") ?: "Type your answer…"
    val maxLength = config.int("maxLength") ?: 200

    // transparent: check both keys
    val transparent = styling.bool("transparent") ?: styling.bool("transparentBackground") ?: false

    // opacity: backend sends 0–100, Compose needs 0.0–1.0
    val opacity = ((styling.float("opacity") ?: 100f) / 100f).coerceIn(0f, 1f)

    // ── Colors ──
    val bg =
        styling.color("background") ?: styling.color("containerBgColor") ?: Color.White
    val titleColor =
        styling.color("questionColor") ?: styling.color("titleColor") ?: Color(
            0xFF1F2937
        )
    val inputBg =
        styling.color("inputBackground") ?: styling.color("inputBgColor") ?: Color(
            0xFFF3F4F6
        )
    val inputTextColor = styling.color("inputTextColor") ?: Color(0xFF9CA3AF)
    val submitBg = styling.color("submitBackground") ?: Color(0xFFF97316)
    val questionFontStyle = styling.obj("questionFont").toFontStyle(FontWeight.Bold)
    val questionFontFamily = rememberInteractionFontFamily(
        questionFontStyle.fontFamily, questionFontStyle.fontWeight, questionFontStyle.fontStyle
    )

    val context = LocalContext.current
    val persistedValue = remember(id) { loadInteractionResponse(context, id) }
    var value by remember(id) { mutableStateOf(persistedValue ?: "") }
    var submitted by remember(id) { mutableStateOf(persistedValue != null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current

    val hasText = value.isNotBlank()

    var buttonVisible by remember(id) { mutableStateOf(false) }

    LaunchedEffect(hasText, submitted) {
        if (submitted) {
            // Hold the "Submitted ✓" state on screen for a beat, then dismiss.
            delay(1000.milliseconds)
            buttonVisible = false
        } else {
            buttonVisible = hasText
        }
    }

    // Measured button height (px), filled in once it's laid out. Combined with an
    // analytic fallback so the very first animated frame already has a sane target
    // instead of snapping once measurement arrives.
    var measuredButtonHeightPx by remember(id) { mutableStateOf(0f) }

    // BoxWithConstraints captures the fixed interaction width/height (from the parent's
    // .width/.height in StorySlideContent, resolved from backend position/size). Every
    // internal dimension below — radius, padding, font sizes — is derived from that
    // local box, not a percentage of the full screen. The white card always fills the
    // box's height; the Send button is positioned BELOW it via offset — the parent Box
    // in StorySlideContent has no clip modifier so overflow is visible.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = opacity }
    ) {
        val w = maxWidth
        val interactionHeight = maxHeight
        val h = interactionHeight

        val borderRadius = styling.borderRadiusDp(scope, minOf(w, h) * 0.12f)
        val padding = minOf(w, h) * 0.1f
        val optionRadius = minOf(w, h) * 0.09f
        val titleSize = minOf(h * 0.19f, w * 0.08f)
        val inputFontSize = titleSize * 0.9f
        val rowGap = padding * 1.6f
        val fieldHPadding = padding * 0.6f
        val fieldVPadding = padding * 0.9f
        val sendVPadding = padding * 0.8f

        // Analytic fallback so we already know roughly how tall the button will be
        // before it's ever measured (avoids a jump the first time it appears).
        val estimatedButtonHeight = sendVPadding * 2 + inputFontSize * 1.3f
        val buttonHeight = if (measuredButtonHeightPx > 0f) {
            with(density) { measuredButtonHeightPx.toDp() }
        } else {
            estimatedButtonHeight
        }

        // Card + button move together as one block. Shifting that block up by half the
        // button's height keeps its visual center exactly where the card's center was
        // before the button existed — instead of the block growing downward and the
        // center drifting down.
        val targetShift = if (buttonVisible) -(buttonHeight / 2) else 0.dp
        val animatedShift by animateDpAsState(
            targetValue = targetShift,
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            label = "inputCardShift"
        )

        // Bottom corners smoothly interpolate between fully rounded (no button) and
        // pointed/square (button visible, flush against the card's bottom edge).
        val animatedBottomRadius by animateDpAsState(
            targetValue = if (buttonVisible) 0.dp else borderRadius,
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            label = "inputCardBottomRadius"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, animatedShift.roundToPx()) }
        ) {
            // ── White card — always fills the full fixed interaction height ──
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(
                        RoundedCornerShape(
                            topStart = borderRadius,
                            topEnd = borderRadius,
                            // Square off bottom corners to connect with the Send button below
                            bottomStart = animatedBottomRadius,
                            bottomEnd = animatedBottomRadius
                        )
                    )
                    .background(if (transparent) Color.Transparent else bg)
                    .padding(padding)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(rowGap, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (title.isNotEmpty()) {
                        BasicTextWrap(
                            text = title,
                            color = titleColor,
                            fontSizeSp = with(density) { titleSize.toSp() },
                            fontFamily = questionFontFamily,
                            fontWeight = questionFontStyle.fontWeight,
                            fontStyle = questionFontStyle.fontStyle,
                            textDecoration = questionFontStyle.textDecoration,
                            align = questionFontStyle.textAlign ?: TextAlign.Center,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .fillMaxWidth()
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(optionRadius))
                            .background(inputBg)
                            .padding(
                                horizontal = fieldHPadding,
                                vertical = fieldVPadding
                            )
                    ) {
                        BasicTextField(
                            value = value,
                            onValueChange = { if (it.length <= maxLength) value = it },
                            enabled = !submitted,
                            singleLine = false,
                            textStyle = TextStyle(
                                color = inputTextColor,
                                fontSize = with(density) { inputFontSize.toSp() },
                                textAlign = TextAlign.Start
                            ),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChangedOrNoop(onFocusChanged),
                            decorationBox = { inner ->
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    if (value.isEmpty()) {
                                        BasicTextWrap(
                                            text = placeholder,
                                            color = Color(0xFF9CA3AF),
                                            fontSizeSp = with(density) { inputFontSize.toSp() },
                                            align = TextAlign.Start
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                    }
                }
            }

            // ── Send button — offset below the card, overflows the bounding box ──
            // Top corners are square (flush with card bottom); bottom corners match borderRadius.
            AnimatedVisibility(
                visible = buttonVisible,
                enter = fadeIn(tween(220)) + slideInVertically(
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                    initialOffsetY = { -it / 3 }
                ),
                exit = fadeOut(tween(160)) + slideOutVertically(
                    animationSpec = tween(160),
                    targetOffsetY = { -it / 3 }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = interactionHeight)
                    .onGloballyPositioned { coords ->
                        if (coords.size.height > 0) {
                            measuredButtonHeightPx = coords.size.height.toFloat()
                        }
                    }
            ) {
                Button(
                    onClick = {
                        if (!submitted) {
                            submitted = true
                            saveInteractionResponse(context, id, value)
                            keyboard?.hide()
                            focusManager.clearFocus()
                            onFocusChanged(false)
                            onTrack(
                                "clicked", mapOf(
                                    "interaction_type" to "input",
                                    "interaction_id" to (id ?: ""),
                                    "value" to value
                                )
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = submitBg),
                    shape = RoundedCornerShape(
                        topStart = 0.dp,
                        topEnd = 0.dp,
                        bottomStart = borderRadius,
                        bottomEnd = borderRadius
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = sendVPadding)
                ) {
                    BasicTextWrap(
                        text = if (submitted) "Sent" else "Send",
                        color = Color.White,
                        fontSizeSp = with(density) { inputFontSize.toSp() },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ----------------------- Small text helper --------------------------------

@Composable
private fun BasicTextWrap(
    text: String,
    color: Color,
    fontSizeSp: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight = FontWeight.Normal,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily? = null,
    fontStyle: FontStyle? = null,
    textDecoration: TextDecoration? = null
) {
    androidx.compose.material3.Text(
        text = text,
        color = color,
        fontSize = fontSizeSp,
        fontFamily = fontFamily,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        textDecoration = textDecoration,
        textAlign = align,
        lineHeight = lineHeight,
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow
    )
}

private fun Modifier.onFocusChangedOrNoop(cb: (Boolean) -> Unit): Modifier =
    onFocusChanged { cb(it.isFocused) }
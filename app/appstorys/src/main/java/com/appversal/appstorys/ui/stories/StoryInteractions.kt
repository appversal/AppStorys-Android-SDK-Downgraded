package com.appversal.appstorys.ui.stories

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.appversal.appstorys.api.StoryInteraction
import kotlinx.coroutines.delay
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
                k to (runCatching { v.jsonPrimitive.contentOrNull }.getOrNull() ?: return@mapNotNull null)
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

    when (type) {
        "POLL"       -> PollInteraction(interaction.id, config, styling, scope, onTrack)
        "QUIZ"       -> QuizInteraction(interaction.id, config, styling, scope, onTrack)
        "MEDIA_QUIZ" -> MediaQuizInteraction(interaction.id, config, styling, scope, onTrack)
        "RATING"     -> RatingInteraction(interaction.id, config, styling, scope, onTrack)
        "REACTION"   -> ReactionInteraction(interaction.id, config, styling, scope, onTrack)
        "COUNTDOWN"  -> CountdownInteraction(interaction.id, config, styling, scope)
        "PROMO"      -> PromoInteraction(interaction.id, config, styling, scope, onTrack)
        "INPUT"      -> InputInteraction(interaction.id, config, styling, scope, onTrack, onInputFocusChanged)
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

@Composable
private fun PollInteraction(
    id: String?,
    config: JsonObject?,
    styling: JsonObject?,
    scope: StoryCanvaScope,
    onTrack: (event: String, metadata: Map<String, Any>) -> Unit
) {
    val question    = config.str("question") ?: ""
    val optionPairs = parseOptionPairs(config)
    val showResults = config.bool("showResults") ?: false
    val transparent = styling.bool("transparentBackground") ?: false

    // layout: can be a string ("horizontal" | "vertical") or {type, columns}
    val layoutType = runCatching {
        config?.get("layout")?.jsonPrimitive?.contentOrNull
    }.getOrNull() ?: runCatching {
        config?.get("layout")?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
    }.getOrNull() ?: "horizontal"

    val isHorizontal = layoutType.lowercase() != "vertical"
    val isPillLayout = isHorizontal && optionPairs.size == 2

    val bg              = parseStoryColor(styling.str("containerBgColor") ?: styling.str("background")) ?: Color.White
    val containerRadius = scope.sizeDp(styling.float("borderRadius") ?: 24f)
    val questionSize    = scope.fontDp(styling.float("questionFontSize") ?: 32f).coerceAtLeast(12.dp)
    val optionSize      = scope.fontDp(styling.float("optionFontSize")   ?: 28f).coerceAtLeast(11.dp)
    val optionRadius    = scope.sizeDp(styling.float("optionRadius")     ?: 40f)
    val activeBar       = parseStoryColor(styling.str("activeResultBarColor"))   ?: Color(0xFFF97316)
    val inactiveBar     = parseStoryColor(styling.str("inactiveResultBarColor")) ?: Color(0xFF404040)

    // Demo percentages for showResults (matches React defaults)
    val percentages = when (optionPairs.size) {
        2    -> listOf(60, 40)
        3    -> listOf(50, 30, 20)
        else -> optionPairs.indices.map { 100 / optionPairs.size.coerceAtLeast(1) }
    }

    var selected by remember(id) { mutableStateOf<String?>(null) }
    val density = LocalDensity.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(containerRadius))
            .background(if (transparent) Color.Transparent else bg)
            .padding(scope.sizeDp(20f)),
        verticalArrangement = Arrangement.spacedBy(scope.sizeDp(24f))
    ) {
        if (question.isNotEmpty()) {
            BasicTextWrap(
                text       = question,
                color      = Color(0xFF111827),
                fontSizeSp = with(density) { questionSize.toSp() },
                fontWeight = FontWeight(800),
                align      = TextAlign.Center,
                modifier   = Modifier.fillMaxWidth()
            )
        }

        when {
            // ── Pill: 2 options sharing one rounded border ──
            isPillLayout -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(optionRadius))
                        .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(optionRadius))
                ) {
                    optionPairs.forEachIndexed { index, (key, label) ->
                        val pct = percentages.getOrElse(index) { 50 }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    enabled              = selected == null,
                                    interactionSource    = remember { MutableInteractionSource() },
                                    indication           = null
                                ) {
                                    selected = key
                                    onTrack("interaction_response", mapOf(
                                        "interaction_id" to (id ?: ""),
                                        "type"           to "POLL",
                                        "option"         to key,
                                        "label"          to label
                                    ))
                                }
                        ) {
                            if (showResults) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(pct / 100f)
                                        .background(if (index == 0) activeBar else inactiveBar)
                                )
                            }
                            BasicTextWrap(
                                text       = if (showResults) "$label $pct%" else label,
                                color      = Color(0xFF111827),
                                fontSizeSp = with(density) { optionSize.toSp() },
                                fontWeight = FontWeight(800),
                                align      = TextAlign.Center,
                                modifier   = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        vertical   = scope.sizeDp(28f),
                                        horizontal = scope.sizeDp(24f)
                                    )
                            )
                            // Divider between the two options
                            if (index == 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .fillMaxHeight()
                                        .width(2.dp)
                                        .background(Color(0xFFE5E7EB))
                                )
                            }
                        }
                    }
                }
            }

            // ── Horizontal (> 2 options) ──
            isHorizontal -> {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(scope.sizeDp(8f))
                ) {
                    optionPairs.forEachIndexed { index, (key, label) ->
                        val pct = percentages.getOrElse(index) { 100 / optionPairs.size.coerceAtLeast(1) }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(optionRadius))
                                .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(optionRadius))
                                .clickable(
                                    enabled           = selected == null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication        = null
                                ) {
                                    selected = key
                                    onTrack("interaction_response", mapOf(
                                        "interaction_id" to (id ?: ""),
                                        "type"           to "POLL",
                                        "option"         to key,
                                        "label"          to label
                                    ))
                                }
                        ) {
                            if (showResults) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(pct / 100f)
                                        .background(if (index == 0) activeBar else inactiveBar)
                                )
                            }
                            BasicTextWrap(
                                text       = if (showResults) "$label $pct%" else label,
                                color      = Color(0xFF111827),
                                fontSizeSp = with(density) { optionSize.toSp() },
                                fontWeight = FontWeight(800),
                                align      = TextAlign.Center,
                                modifier   = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = scope.sizeDp(28f), horizontal = scope.sizeDp(24f))
                            )
                        }
                    }
                }
            }

            // ── Vertical ──
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(scope.sizeDp(8f))) {
                    optionPairs.forEachIndexed { index, (key, label) ->
                        val pct = percentages.getOrElse(index) { 100 / optionPairs.size.coerceAtLeast(1) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(optionRadius))
                                .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(optionRadius))
                                .clickable(
                                    enabled           = selected == null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication        = null
                                ) {
                                    selected = key
                                    onTrack("interaction_response", mapOf(
                                        "interaction_id" to (id ?: ""),
                                        "type"           to "POLL",
                                        "option"         to key,
                                        "label"          to label
                                    ))
                                }
                        ) {
                            if (showResults) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(pct / 100f)
                                        .background(if (index == 0) activeBar else inactiveBar)
                                )
                            }
                            BasicTextWrap(
                                text       = if (showResults) "$label $pct%" else label,
                                color      = Color(0xFF111827),
                                fontSizeSp = with(density) { optionSize.toSp() },
                                fontWeight = FontWeight(800),
                                align      = TextAlign.Center,
                                modifier   = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = scope.sizeDp(28f), horizontal = scope.sizeDp(24f))
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// QUIZ
// ============================================================
// Matches QuizRenderer.jsx:
//  • Full-width dark header strip for question
//  • Each option: hollow letter circle (A/B/C…) + text
//  • Correct answer → green border + ✓; wrong selected → red
// ============================================================

@Composable
private fun QuizInteraction(
    id: String?,
    config: JsonObject?,
    styling: JsonObject?,
    scope: StoryCanvaScope,
    onTrack: (event: String, metadata: Map<String, Any>) -> Unit
) {
    val question    = config.str("question") ?: ""
    // Support both new (correctAnswerId) and old (isCorrect / correctOption) keys
    val correctId   = config.str("correctAnswerId") ?: config.str("isCorrect") ?: config.str("correctOption")
    val optionPairs = parseOptionPairs(config)

    val bg              = parseStoryColor(styling.str("background") ?: styling.str("containerBgColor")) ?: Color.White
    val containerRadius = scope.sizeDp(styling.float("borderRadius") ?: 16f)
    val questionBg      = parseStoryColor(styling.str("questionBgColor")) ?: Color.Black
    val questionColor   = parseStoryColor(styling.str("questionColor"))   ?: Color.White
    val questionSize    = scope.fontDp(styling.float("questionFontSize") ?: 32f).coerceAtLeast(13.dp)
    val optionBg        = parseStoryColor(styling.str("optionBgColor"))   ?: Color.White
    val optionTextColor = parseStoryColor(styling.str("optionTextColor")) ?: Color(0xFF111827)
    val optionSize      = scope.fontDp(styling.float("optionFontSize")  ?: 28f).coerceAtLeast(12.dp)
    val optionRadius    = scope.sizeDp(styling.float("optionRadius")    ?: 50f)
    val correctColor    = parseStoryColor(styling.str("correctColor"))   ?: Color(0xFF10B981)
    val incorrectColor  = Color(0xFFEF4444)

    var selected by remember(id) { mutableStateOf<String?>(null) }
    val density = LocalDensity.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(containerRadius))
            .background(bg)
    ) {
        // ── Question header strip ──
        if (question.isNotEmpty()) {
            Box(
                modifier          = Modifier
                    .fillMaxWidth()
                    .background(questionBg)
                    .padding(horizontal = scope.sizeDp(20f), vertical = scope.sizeDp(16f)),
                contentAlignment  = Alignment.Center
            ) {
                BasicTextWrap(
                    text       = question,
                    color      = questionColor,
                    fontSizeSp = with(density) { questionSize.toSp() },
                    fontWeight = FontWeight.Bold,
                    align      = TextAlign.Center,
                    modifier   = Modifier.fillMaxWidth()
                )
            }
        }

        // ── Options ──
        Column(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(scope.sizeDp(16f)),
            verticalArrangement   = Arrangement.spacedBy(scope.sizeDp(12f))
        ) {
            optionPairs.forEachIndexed { index, (key, label) ->
                val isSelected = selected == key
                val isCorrect  = key == correctId

                val rowBg = when {
                    selected == null            -> optionBg
                    isCorrect                   -> correctColor
                    isSelected && !isCorrect    -> incorrectColor
                    else                        -> optionBg
                }
                val textColor = if (selected != null && (isCorrect || isSelected)) Color.White else optionTextColor
                val borderColor = when {
                    selected != null && isCorrect   -> correctColor
                    else                            -> Color(0xFFE5E7EB)
                }
                val borderWidth = if (selected != null && isCorrect) 2.dp else 1.dp

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(optionRadius))
                        .background(rowBg)
                        .border(borderWidth, borderColor, RoundedCornerShape(optionRadius))
                        .clickable(
                            enabled           = selected == null,
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = null
                        ) {
                            selected = key
                            onTrack("interaction_response", mapOf(
                                "interaction_id" to (id ?: ""),
                                "type"           to "QUIZ",
                                "option"         to key,
                                "label"          to label,
                                "correct"        to isCorrect
                            ))
                        }
                        .padding(vertical = scope.sizeDp(8f), horizontal = scope.sizeDp(16f)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hollow letter circle: hides after selection (answer revealed by bg color)
                    val circleAlpha = if (selected == null) 1f else 0f
                    Box(
                        modifier         = Modifier
                            .size(scope.sizeDp(28f))
                            .clip(CircleShape)
                            .border(scope.sizeDp(2f), questionBg.copy(alpha = circleAlpha), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicTextWrap(
                            text       = ('A' + index).toString(),
                            color      = questionBg.copy(alpha = circleAlpha),
                            fontSizeSp = with(density) { scope.fontDp(14f).coerceAtLeast(10.dp).toSp() },
                            fontWeight = FontWeight.Bold,
                            align      = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.width(scope.sizeDp(12f)))

                    BasicTextWrap(
                        text       = label,
                        color      = textColor,
                        fontSizeSp = with(density) { optionSize.toSp() },
                        modifier   = Modifier.weight(1f)
                    )

                    if (selected != null && isCorrect) {
                        Spacer(modifier = Modifier.width(scope.sizeDp(8f)))
                        BasicTextWrap(
                            text       = "✓",
                            color      = Color.White,
                            fontSizeSp = with(density) { optionSize.toSp() },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
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

@Composable
private fun MediaQuizInteraction(
    id: String?,
    config: JsonObject?,
    styling: JsonObject?,
    scope: StoryCanvaScope,
    onTrack: (event: String, metadata: Map<String, Any>) -> Unit
) {
    val question  = config.str("question") ?: ""
    val correctId = config.str("correctAnswerId") ?: config.str("correctOption")
    val columns   = (config.int("columns") ?: 2).coerceAtLeast(1)

    val optionsArray = config?.get("options")?.let { runCatching { it.jsonArray }.getOrNull() } ?: return
    val options: List<MediaQuizOptionData> = optionsArray.mapNotNull {
        val obj = runCatching { it.jsonObject }.getOrNull() ?: return@mapNotNull null
        MediaQuizOptionData(
            id      = obj.str("id") ?: return@mapNotNull null,
            label   = obj.str("label") ?: "",
            image   = obj.str("imageUrl"),
            correct = obj.bool("isCorrect") ?: false
        )
    }

    val bg                 = parseStoryColor(styling.str("background") ?: styling.str("containerBgColor")) ?: Color.White
    val containerRadius    = scope.sizeDp(styling.float("borderRadius") ?: 16f)
    val questionBg         = parseStoryColor(styling.str("questionBgColor")) ?: Color(0xFF111111)
    val questionColor      = parseStoryColor(styling.str("questionColor"))   ?: Color.White
    val questionSize       = scope.fontDp(styling.float("questionFontSize") ?: 32f).coerceAtLeast(13.dp)
    val imageBorderColor   = parseStoryColor(styling.str("borderColor") ?: styling.str("imageBorderColor")) ?: Color(0xFFE5E7EB)
    val correctBorderColor = parseStoryColor(styling.str("correctBorderColor")) ?: Color(0xFF10B981)
    val labelColor         = parseStoryColor(styling.str("labelColor"))   ?: Color(0xFF4B5563)
    val labelSize          = scope.fontDp(styling.float("labelFontSize") ?: 24f).coerceAtLeast(10.dp)
    val imageRadius        = scope.sizeDp(styling.float("imageRadius") ?: styling.float("optionRadius") ?: 16f)
    val spacing            = scope.sizeDp(8f)

    var selected by remember(id) { mutableStateOf<String?>(null) }
    val density = LocalDensity.current

    // Chunk options into rows
    val rows = options.chunked(columns)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(containerRadius))
            .background(bg)
    ) {
        // ── Question header strip ──
        if (question.isNotEmpty()) {
            Box(
                modifier         = Modifier
                    .fillMaxWidth()
                    .background(questionBg)
                    .padding(horizontal = spacing, vertical = spacing),
                contentAlignment = Alignment.Center
            ) {
                BasicTextWrap(
                    text       = question,
                    color      = questionColor,
                    fontSizeSp = with(density) { questionSize.toSp() },
                    fontWeight = FontWeight.Bold,
                    align      = TextAlign.Center,
                    modifier   = Modifier.fillMaxWidth()
                )
            }
        }

        // ── Image grid ──
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            rows.forEach { rowOptions ->
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    rowOptions.forEach { opt ->
                        val isSelected = selected == opt.id
                        val showBorder = selected != null && (opt.id == correctId || isSelected)
                        val borderColor = when {
                            selected != null && opt.id == correctId          -> correctBorderColor
                            selected != null && isSelected && opt.id != correctId -> Color(0xFFEF4444)
                            else                                              -> imageBorderColor
                        }
                        val borderWidth = if (showBorder) scope.sizeDp(3f) else 1.dp

                        Column(
                            modifier            = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(scope.sizeDp(6f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(imageRadius))
                                    .border(borderWidth, borderColor, RoundedCornerShape(imageRadius))
                                    .background(Color(0xFFF3F4F6))
                                    .clickable(
                                        enabled           = selected == null,
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication        = null
                                    ) {
                                        selected = opt.id
                                        onTrack("interaction_response", mapOf(
                                            "interaction_id" to (id ?: ""),
                                            "type"           to "MEDIA_QUIZ",
                                            "option"         to opt.id,
                                            "correct"        to opt.correct
                                        ))
                                    }
                            ) {
                                if (!opt.image.isNullOrEmpty()) {
                                    androidx.compose.foundation.Image(
                                        painter      = rememberAsyncImagePainter(opt.image),
                                        contentDescription = null,
                                        modifier     = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            // Label + optional ✓
                            BasicTextWrap(
                                text       = if (opt.id == correctId && selected != null) "${opt.label} ✓" else opt.label,
                                color      = if (opt.id == correctId && selected != null) correctBorderColor else labelColor,
                                fontSizeSp = with(density) { labelSize.toSp() },
                                fontWeight = FontWeight.Medium,
                                align      = TextAlign.Center,
                                modifier   = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    // Fill empty slots in the last row so weights stay balanced
                    repeat(columns - rowOptions.size) {
                        Spacer(modifier = Modifier.weight(1f))
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
    val title         = config.str("title") ?: ""
    val emoji         = config.str("emoji") ?: "⭐"
    val maxRating     = config.int("maxRating") ?: 5
    val initialRating = config.int("currentRating") ?: 0
    // React uses data.type; legacy Kotlin used config.variant
    val variant = config.str("type") ?: config.str("variant") ?: "slider"

    val bg          = parseStoryColor(styling.str("containerBgColor") ?: styling.str("background")) ?: Color.White
    val borderRadius = scope.sizeDp(styling.float("borderRadius") ?: 36f)
    val padding      = scope.sizeDp(styling.float("containerPadding") ?: styling.float("padding") ?: 16f)

    // Flat styling keys (matching React's style.* directly)
    val titleColor  = parseStoryColor(styling.str("titleColor")) ?: Color(0xFF111827)
    val sliderFill  = parseStoryColor(styling.str("sliderFill")  ?: styling.obj("colors").str("sliderFill"))  ?: Color(0xFFE11D48)
    val sliderTrack = parseStoryColor(styling.str("sliderTrack") ?: styling.obj("colors").str("sliderTrack")) ?: Color(0xFFF3F4F6)
    val titleSize   = scope.fontDp(styling.float("titleFontSize") ?: styling.obj("typography").float("titleSize") ?: 28f).coerceAtLeast(12.dp)
    val emojiSize   = scope.fontDp(styling.float("emojiSize") ?: styling.obj("typography").float("emojiSize") ?: 64f).coerceIn(24.dp, 72.dp)

    var rating by remember(id) { mutableIntStateOf(initialRating.coerceIn(0, maxRating)) }
    val density = LocalDensity.current

    Column(
        modifier                  = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(borderRadius))
            .background(bg)
            .padding(padding),
        verticalArrangement       = Arrangement.spacedBy(scope.sizeDp(12f)),
        horizontalAlignment       = Alignment.CenterHorizontally
    ) {
        if (title.isNotEmpty()) {
            BasicTextWrap(
                text       = title,
                color      = titleColor,
                fontSizeSp = with(density) { titleSize.toSp() },
                fontWeight = FontWeight.SemiBold,
                align      = TextAlign.Center,
                modifier   = Modifier.fillMaxWidth()
            )
        }

        if (variant == "slider") {
            // Custom slider: thin gradient track + emoji thumb positioned at fill %
            // Matches React: track height 10px, gradient #d946ef → sliderFill, emoji as thumb
            val fillFraction = (rating.toFloat() / maxRating.coerceAtLeast(1)).coerceIn(0f, 1f)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = scope.sizeDp(20f))
            ) {
                val totalWidth  = maxWidth
                val trackHeight = 10.dp
                val thumbSize   = emojiSize
                val thumbOffset = maxOf(0.dp, minOf(
                    totalWidth - thumbSize,
                    totalWidth * fillFraction - thumbSize / 2
                ))

                // Track + fill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(trackHeight)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(trackHeight / 2))
                        .background(sliderTrack)
                        .pointerInput(maxRating, id) {
                            detectHorizontalDragGestures { change, _ ->
                                change.consume()
                                val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                val newRating = (fraction * maxRating).roundToInt().coerceIn(0, maxRating)
                                if (newRating != rating) {
                                    rating = newRating
                                    onTrack("interaction_response", mapOf(
                                        "interaction_id" to (id ?: ""),
                                        "type"           to "RATING",
                                        "rating"         to newRating
                                    ))
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
                    modifier         = Modifier
                        .size(thumbSize)
                        .align(Alignment.CenterStart)
                        .offset(x = thumbOffset),
                    contentAlignment = Alignment.Center
                ) {
                    BasicTextWrap(
                        text       = emoji,
                        color      = Color.Unspecified,
                        fontSizeSp = with(density) { emojiSize.toSp() },
                        align      = TextAlign.Center
                    )
                }
            }
        } else {
            // Star / emoji row — inactive items are dimmed to 40 %
            // Matches React: filter grayscale(1) opacity(0.4) for inactive, scale(1.1) for active
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(maxRating) { i ->
                    val isActive = i < rating
                    Box(
                        modifier = Modifier
                            .graphicsLayer { alpha = if (isActive) 1f else 0.4f }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication        = null
                            ) {
                                rating = i + 1
                                onTrack("interaction_response", mapOf(
                                    "interaction_id" to (id ?: ""),
                                    "type"           to "RATING",
                                    "rating"         to (i + 1)
                                ))
                            }
                    ) {
                        BasicTextWrap(
                            text       = emoji,
                            color      = Color.Unspecified,
                            fontSizeSp = with(density) { (if (isActive) emojiSize * 1.1f else emojiSize).toSp() },
                            align      = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// REACTION
// ============================================================
// Matches ReactionRenderer.jsx:
//  • Row of bubble circles, each containing an emoji
//  • showCount: bubble is 1.5 × taller (pill), count shown inside
//  • Bubble bg + border come from styling
// ============================================================

@Composable
private fun ReactionInteraction(
    id: String?,
    config: JsonObject?,
    styling: JsonObject?,
    scope: StoryCanvaScope,
    onTrack: (event: String, metadata: Map<String, Any>) -> Unit
) {
    val emojiPairs = parseReactionEmojis(config)
    val showCount  = config.bool("showCount") ?: false

    val transparent   = styling.bool("transparentBackground") ?: false
    val bg            = parseStoryColor(styling.str("background") ?: styling.str("containerBgColor")) ?: Color.Transparent
    val borderRadius  = scope.sizeDp(styling.float("borderRadius") ?: 0f)
    val bubbleBg      = parseStoryColor(styling.str("bubbleBgColor") ?: styling.str("background") ?: styling.str("containerBgColor")) ?: Color.White
    val bubbleBorder  = parseStoryColor(styling.str("bubbleBorderColor")) ?: Color(0xFFE5E7EB)
    val countColor    = parseStoryColor(styling.str("countColor")) ?: Color.Black
    val containerGap  = scope.sizeDp(styling.float("gap") ?: 10f)
    val bubbleSize    = scope.sizeDp(styling.float("bubbleSize") ?: 80f).coerceIn(28.dp, 120.dp)
    val emojiSize     = scope.fontDp(styling.float("emojiSize") ?: (styling.float("bubbleSize") ?: 80f) * 0.65f).coerceIn(18.dp, 72.dp)

    val bubbleHeight  = if (showCount) bubbleSize * 1.5f else bubbleSize
    val countSize     = scope.fontDp(bubbleSize.value * 0.28f * scope.density.density / scope.scale).coerceAtLeast(10.dp)

    var picked by remember(id) { mutableStateOf<String?>(null) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(borderRadius))
            .background(if (transparent) Color.Transparent else bg)
    ) {
        Row(
            modifier              = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(containerGap, Alignment.CenterHorizontally),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            emojiPairs.forEach { (key, emoji) ->
                val isPicked = picked == key
                Column(
                    modifier            = Modifier
                        .size(bubbleSize, bubbleHeight)
                        .clip(RoundedCornerShape(bubbleSize / 2))
                        .background(bubbleBg)
                        .border(2.dp, bubbleBorder, RoundedCornerShape(bubbleSize / 2))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = null
                        ) {
                            picked = key
                            onTrack("interaction_response", mapOf(
                                "interaction_id" to (id ?: ""),
                                "type"           to "REACTION",
                                "option"         to key,
                                "emoji"          to emoji
                            ))
                        }
                        .padding(vertical = if (showCount) bubbleSize * 0.08f else 0.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    BasicTextWrap(
                        text       = emoji,
                        color      = Color.Unspecified,
                        fontSizeSp = with(density) { (if (isPicked) emojiSize * 1.15f else emojiSize).toSp() },
                        align      = TextAlign.Center
                    )
                    if (showCount) {
                        BasicTextWrap(
                            text       = "2k",
                            color      = countColor,
                            fontSizeSp = with(density) { countSize.toSp() },
                            fontWeight = FontWeight.Bold,
                            align      = TextAlign.Center
                        )
                    }
                }
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
    val display     = config.obj("display")
    val showDays    = config.bool("showDays")    ?: display.bool("showDays")    ?: true
    val showHours   = config.bool("showHours")   ?: display.bool("showHours")   ?: true
    val showMinutes = config.bool("showMinutes") ?: display.bool("showMinutes") ?: true
    val showSeconds = config.bool("showSeconds") ?: display.bool("showSeconds") ?: true

    val targetMs = remember(endDate, endTime) {
        runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                .apply { timeZone = TimeZone.getDefault() }
                .parse("$endDate $endTime")?.time ?: 0L
        }.getOrDefault(0L)
    }

    val bg             = parseStoryColor(styling.str("background") ?: styling.str("containerBgColor")) ?: Color.White
    val containerRadius = scope.sizeDp(styling.float("borderRadius") ?: 12f)
    val padV           = scope.sizeDp(styling.float("padding") ?: 24f)
    val padH           = scope.sizeDp(32f)
    val titleColor     = parseStoryColor(styling.str("titleColor")) ?: Color(0xFF111827)
    val titleSize      = scope.fontDp(styling.float("titleFontSize") ?: 36f).coerceAtLeast(13.dp)
    val digitBg        = parseStoryColor(styling.str("digitBackground") ?: styling.str("digitBgColor")) ?: Color(0xFFF3F4F6)
    val digitColor     = parseStoryColor(styling.str("digitColor") ?: styling.str("digitTextColor")) ?: Color(0xFF111827)
    val digitSize      = scope.fontDp(styling.float("digitSize") ?: styling.float("digitFontSize") ?: 48f).coerceAtLeast(14.dp)
    val labelColor     = parseStoryColor(styling.str("labelColor")) ?: Color(0xFF111827)
    val labelSize      = scope.fontDp(styling.float("labelFontSize") ?: 28f).coerceAtLeast(10.dp)
    val sepColor       = parseStoryColor(styling.str("separatorColor")) ?: Color(0xFF111827)

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val remaining    = (targetMs - now).coerceAtLeast(0L)
    val totalSeconds = remaining / 1000
    val days         = if (endDate.isEmpty()) 0L else totalSeconds / 86400
    val hours        = if (endDate.isEmpty()) 12L else (totalSeconds % 86400) / 3600
    val minutes      = if (endDate.isEmpty()) 34L else (totalSeconds % 3600) / 60
    val seconds      = if (endDate.isEmpty()) 56L else totalSeconds % 60

    val density = LocalDensity.current

    // Collect visible units in order
    data class CountUnit(val value: Long, val label: String)
    val units = buildList {
        if (showDays)    add(CountUnit(days,    "days"))
        if (showHours)   add(CountUnit(hours,   "hours"))
        if (showMinutes) add(CountUnit(minutes, "minutes"))
        if (showSeconds) add(CountUnit(seconds, "seconds"))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(containerRadius))
            .background(bg)
            .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(containerRadius))
            .padding(vertical = padV, horizontal = padH)
    ) {
        Column(
            modifier            = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(scope.sizeDp(16f))
        ) {
            // Title – left-aligned (matches React alignItems: 'flex-start')
            if (title.isNotEmpty()) {
                BasicTextWrap(
                    text       = title,
                    color      = titleColor,
                    fontSizeSp = with(density) { titleSize.toSp() },
                    fontWeight = FontWeight(800)
                )
            }

            // Timer row
            Row(
                modifier          = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Top
            ) {
                units.forEachIndexed { index, unit ->
                    // Digit group
                    CountdownDigitCell(
                        value      = unit.value,
                        label      = unit.label,
                        digitBg    = digitBg,
                        digitColor = digitColor,
                        digitSize  = digitSize,
                        labelColor = labelColor,
                        labelSize  = labelSize,
                        scope      = scope,
                        density    = density
                    )
                    // Colon separator between units
                    if (index < units.size - 1) {
                        Box(
                            modifier         = Modifier.height(scope.sizeDp(46f)),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicTextWrap(
                                text       = ":",
                                color      = sepColor,
                                fontSizeSp = with(density) { scope.fontDp(40f).coerceAtLeast(14.dp).toSp() },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CountdownDigitCell(
    value: Long,
    label: String,
    digitBg: Color,
    digitColor: Color,
    digitSize: Dp,
    labelColor: Color,
    labelSize: Dp,
    scope: StoryCanvaScope,
    density: androidx.compose.ui.unit.Density
) {
    val d1 = (value / 10).toString()
    val d2 = (value % 10).toString()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(scope.sizeDp(4f))
    ) {
        // Two individual digit boxes side by side (matching React's split-digit design)
        Row(horizontalArrangement = Arrangement.spacedBy(scope.sizeDp(4f))) {
            listOf(d1, d2).forEach { digit ->
                Box(
                    modifier         = Modifier
                        .widthIn(min = scope.sizeDp(32f))
                        .height(scope.sizeDp(46f))
                        .clip(RoundedCornerShape(scope.sizeDp(8f)))
                        .background(digitBg)
                        .padding(horizontal = scope.sizeDp(4f)),
                    contentAlignment = Alignment.Center
                ) {
                    BasicTextWrap(
                        text       = digit,
                        color      = digitColor,
                        fontSizeSp = with(density) { digitSize.toSp() },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        // Full word label below the digit pair
        BasicTextWrap(
            text       = label,
            color      = labelColor,
            fontSizeSp = with(density) { labelSize.toSp() },
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ============================================================
// PROMO
// ============================================================
// Matches PromoRenderer.jsx:
//  • Ticket shape: rounded rectangle with semicircular notches
//    cut out of the left and right edges at the midpoint
//  • Left: percentage/discount icon drawn on Canvas
//  • Centre: coupon code text (bold, uppercase)
//  • Right: copy icon; switches to checkmark after copying
// ============================================================

@Composable
private fun PromoInteraction(
    id: String?,
    config: JsonObject?,
    styling: JsonObject?,
    scope: StoryCanvaScope,
    onTrack: (event: String, metadata: Map<String, Any>) -> Unit
) {
    val code     = config.str("couponCode") ?: config.str("title") ?: ""
    // Support both new (showCopyButton) and old (copyButton) keys
    val showCopy = config.bool("showCopyButton") ?: config.bool("copyButton") ?: true

    val bg          = parseStoryColor(styling.str("backgroundColor") ?: styling.str("containerBgColor") ?: styling.str("background")) ?: Color(0xFFF3F3F3)
    val textColor   = parseStoryColor(styling.str("textColor") ?: styling.str("text")) ?: Color.Black
    val borderColor = parseStoryColor(styling.str("borderColor")) ?: Color(0xFFE5E7EB)
    val borderRadius = scope.sizeDp(styling.float("borderRadius") ?: 20f)

    val clipboardManager = LocalClipboardManager.current
    var copied by remember(id) { mutableStateOf(false) }
    val density = LocalDensity.current

    val notchRadiusDp   = scope.sizeDp(14f)
    val iconSizeDp      = scope.sizeDp(48f)
    val copyIconSizeDp  = scope.sizeDp(36f)
    val codeFontSize    = scope.fontDp(40f).coerceAtLeast(16.dp)

    // Compute px values for the TicketShape (createOutline receives px)
    val notchRadiusPx   = with(density) { notchRadiusDp.toPx() }
    val cornerRadiusPx  = with(density) { borderRadius.toPx() }

    val ticketShape = remember(notchRadiusPx, cornerRadiusPx) {
        TicketShape(notchRadiusPx = notchRadiusPx, cornerRadiusPx = cornerRadiusPx)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, borderColor, ticketShape)
            .clip(ticketShape)
            .background(bg)
            .clickable(
                enabled           = showCopy,
                interactionSource = remember { MutableInteractionSource() },
                indication        = null
            ) {
                if (!copied) {
                    clipboardManager.setText(AnnotatedString(code))
                    copied = true
                    onTrack("interaction_response", mapOf(
                        "interaction_id" to (id ?: ""),
                        "type"           to "PROMO",
                        "code"           to code,
                        "action"         to "copied"
                    ))
                }
            }
            .padding(horizontal = scope.sizeDp(24f)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // ── Left: discount icon ──
            Canvas(modifier = Modifier.size(iconSizeDp)) {
                val w = size.width
                val h = size.height
                val strokePx = 2f * density.density
                val thickPx  = 4f * density.density
                val tc = textColor
                // Rounded rectangle outline
                drawRoundRect(
                    color        = tc,
                    cornerRadius = CornerRadius(w * 8f / 48f),
                    style        = Stroke(width = strokePx)
                )
                // Diagonal line
                drawLine(
                    color       = tc,
                    start       = Offset(w * 33.6f / 48f, h * 14.4f / 48f),
                    end         = Offset(w * 14.4f / 48f, h * 33.6f / 48f),
                    strokeWidth = thickPx,
                    cap         = StrokeCap.Round
                )
                // Top-left circle
                drawCircle(color = tc, radius = w * 4f / 48f, center = Offset(w * 16.8f / 48f, h * 16.8f / 48f))
                // Bottom-right circle
                drawCircle(color = tc, radius = w * 4f / 48f, center = Offset(w * 31.2f / 48f, h * 31.2f / 48f))
            }

            // ── Centre: coupon code ──
            Box(
                modifier         = Modifier
                    .weight(1f)
                    .padding(horizontal = scope.sizeDp(12f)),
                contentAlignment = Alignment.Center
            ) {
                BasicTextWrap(
                    text       = if (copied) "COPIED!" else code.uppercase(),
                    color      = if (copied) Color(0xFF10B981) else textColor,
                    fontSizeSp = with(density) { codeFontSize.toSp() },
                    fontWeight = FontWeight.Bold,
                    align      = TextAlign.Center
                )
            }

            // ── Right: copy / check icon ──
            if (showCopy) {
                Canvas(modifier = Modifier.size(copyIconSizeDp)) {
                    val w = size.width
                    val h = size.height
                    val strokePx = 3f * density.density
                    if (copied) {
                        // Checkmark path
                        val checkPath = Path().apply {
                            moveTo(w * 10f / 36f, h * 18f / 36f)
                            lineTo(w * 15f / 36f, h * 23f / 36f)
                            lineTo(w * 26f / 36f, h * 12f / 36f)
                        }
                        drawPath(
                            path  = checkPath,
                            color = Color(0xFF10B981),
                            style = Stroke(
                                width = strokePx,
                                cap   = StrokeCap.Round,
                                join  = StrokeJoin.Round
                            )
                        )
                    } else {
                        val r = CornerRadius(w * 6f / 36f)
                        val s = Stroke(width = strokePx)
                        // Back rect (offset right+up)
                        drawRoundRect(
                            color     = textColor,
                            topLeft   = Offset(w * 7.2f / 36f, 0f),
                            size      = Size(w * 28.8f / 36f, h * 28.8f / 36f),
                            cornerRadius = r,
                            style     = s
                        )
                        // Front rect (offset left+down)
                        drawRoundRect(
                            color     = textColor,
                            topLeft   = Offset(0f, h * 7.2f / 36f),
                            size      = Size(w * 28.8f / 36f, h * 28.8f / 36f),
                            cornerRadius = r,
                            style     = s
                        )
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
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w  = size.width
        val h  = size.height
        val cr = cornerRadiusPx.coerceAtMost(minOf(w, h) / 4f)
        val nr = notchRadiusPx.coerceAtMost(h / 3f)

        val path = Path().apply {
            // ── Start: top edge, right of top-left corner ──
            moveTo(cr, 0f)
            // Top edge →
            lineTo(w - cr, 0f)
            // Top-right corner (CW 90°)
            arcTo(Rect(w - 2 * cr, 0f, w, 2 * cr),          -90f,  90f, false)
            // Right edge ↓ to right notch
            lineTo(w, h / 2 - nr)
            // Right notch: concave semicircle sweeping inward (CCW = –180°)
            arcTo(Rect(w - nr, h / 2 - nr, w + nr, h / 2 + nr), -90f, -180f, false)
            // Right edge ↓ to bottom-right corner
            lineTo(w, h - cr)
            // Bottom-right corner (CW 90°)
            arcTo(Rect(w - 2 * cr, h - 2 * cr, w, h),          0f,   90f, false)
            // Bottom edge ←
            lineTo(cr, h)
            // Bottom-left corner (CW 90°)
            arcTo(Rect(0f, h - 2 * cr, 2 * cr, h),              90f,  90f, false)
            // Left edge ↑ to left notch
            lineTo(0f, h / 2 + nr)
            // Left notch: concave semicircle sweeping inward (CCW = –180°)
            arcTo(Rect(-nr, h / 2 - nr, nr, h / 2 + nr),       90f, -180f, false)
            // Left edge ↑ to top-left corner
            lineTo(0f, cr)
            // Top-left corner (CW 90°)
            arcTo(Rect(0f, 0f, 2 * cr, 2 * cr),                180f,  90f, false)
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

@Composable
private fun InputInteraction(
    id: String?,
    config: JsonObject?,
    styling: JsonObject?,
    scope: StoryCanvaScope,
    onTrack: (event: String, metadata: Map<String, Any>) -> Unit,
    onFocusChanged: (Boolean) -> Unit
) {
    val title       = config.str("title") ?: ""
    val placeholder = config.str("placeholder") ?: "Type your answer…"
    val maxLength   = config.int("maxLength") ?: 200

    val bg            = parseStoryColor(styling.str("background") ?: styling.str("containerBgColor")) ?: Color.White
    val borderRadius  = scope.sizeDp(styling.float("borderRadius") ?: 24f)
    val padding       = scope.sizeDp(styling.float("padding") ?: 40f)
    val titleColor    = parseStoryColor(styling.str("questionColor") ?: styling.str("titleColor")) ?: Color(0xFF1F2937)
    val titleSize     = scope.fontDp(styling.float("questionSize") ?: styling.float("titleFontSize") ?: 32f).coerceAtLeast(14.dp)
    val inputBg       = parseStoryColor(styling.str("inputBackground") ?: styling.str("inputBgColor")) ?: Color(0xFFF3F4F6)
    val inputTextColor = parseStoryColor(styling.str("inputTextColor")) ?: Color(0xFF111827)
    val submitBg      = parseStoryColor(styling.str("submitBackground")) ?: Color(0xFFF97316)

    var value     by remember(id) { mutableStateOf("") }
    var submitted by remember(id) { mutableStateOf(false) }
    val keyboard    = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val density     = LocalDensity.current

    Column(
        modifier              = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(borderRadius))
            .background(bg)
            .padding(padding),
        verticalArrangement   = Arrangement.spacedBy(scope.sizeDp(16f)),
        horizontalAlignment   = Alignment.CenterHorizontally
    ) {
        if (title.isNotEmpty()) {
            BasicTextWrap(
                text       = title,
                color      = titleColor,
                fontSizeSp = with(density) { titleSize.toSp() },
                fontWeight = FontWeight.Bold,
                align      = TextAlign.Center,
                modifier   = Modifier.fillMaxWidth()
            )
        }

        // Input field
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(scope.sizeDp(16f)))
                .background(inputBg)
                .padding(horizontal = scope.sizeDp(20f), vertical = scope.sizeDp(16f))
        ) {
            BasicTextField(
                value          = value,
                onValueChange  = { if (it.length <= maxLength) value = it },
                enabled        = !submitted,
                singleLine     = false,
                textStyle      = TextStyle(
                    color    = inputTextColor,
                    fontSize = with(density) { scope.fontDp(28f).coerceAtLeast(13.dp).toSp() },
                    textAlign = TextAlign.Center
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction      = ImeAction.Done
                ),
                modifier       = Modifier
                    .fillMaxWidth()
                    .onFocusChangedOrNoop(onFocusChanged),
                decorationBox  = { inner ->
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                        if (value.isEmpty()) {
                            BasicTextWrap(
                                text       = placeholder,
                                color      = Color(0xFF9CA3AF),
                                fontSizeSp = with(density) { scope.fontDp(28f).coerceAtLeast(13.dp).toSp() },
                                align      = TextAlign.Center
                            )
                        }
                        inner()
                    }
                }
            )
        }

        // Submit button
        Button(
            onClick = {
                if (value.isNotBlank() && !submitted) {
                    submitted = true
                    keyboard?.hide()
                    focusManager.clearFocus()
                    onFocusChanged(false)
                    onTrack("interaction_response", mapOf(
                        "interaction_id" to (id ?: ""),
                        "type"           to "INPUT",
                        "answer"         to value
                    ))
                }
            },
            colors   = ButtonDefaults.buttonColors(containerColor = submitBg),
            shape    = RoundedCornerShape(scope.sizeDp(12f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            BasicTextWrap(
                text       = if (submitted) "Submitted" else "Submit",
                color      = Color.White,
                fontSizeSp = with(density) { scope.fontDp(28f).coerceAtLeast(14.dp).toSp() }
            )
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
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Text(
        text       = text,
        color      = color,
        fontSize   = fontSizeSp,
        fontWeight = fontWeight,
        textAlign  = align,
        modifier   = modifier
    )
}

private fun Modifier.onFocusChangedOrNoop(cb: (Boolean) -> Unit): Modifier =
    onFocusChanged { cb(it.isFocused) }
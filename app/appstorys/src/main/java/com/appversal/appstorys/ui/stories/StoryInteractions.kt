package com.appversal.appstorys.ui.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.appversal.appstorys.api.StoryInteraction
import kotlinx.coroutines.delay
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
import androidx.compose.ui.focus.onFocusChanged

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

// ----------------------- Public entry point -------------------------------

/**
 * Renders an interaction inside an absolutely-positioned box. Caller positions
 * the wrapper using canva-space `position` and `size` from styling — this
 * Composable just paints the chrome and forwards user actions via callbacks.
 *
 * @param onInputFocusChanged Reports whether the (INPUT-only) text field has
 *        keyboard focus, so the slide timer can be paused while typing.
 * @param onTrack Forwarded to AppStorys.trackEvents for analytics.
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

// ----------------------- POLL ---------------------------------------------

@Composable
private fun PollInteraction(
    id: String?,
    config: JsonObject?,
    styling: JsonObject?,
    scope: StoryCanvaScope,
    onTrack: (event: String, metadata: Map<String, Any>) -> Unit
) {
    val question = config.str("question") ?: ""
    val options = config?.get("options")?.let { runCatching { it.jsonObject }.getOrNull() }
    val optionPairs: List<Pair<String, String>> = options?.entries?.map { (key, value) ->
        key to (runCatching { value.jsonPrimitive.contentOrNull }.getOrNull() ?: key)
    } ?: emptyList()

    val bg = parseStoryColor(styling.str("background")) ?: Color.White
    val borderRadius = scope.sizeDp(styling.float("borderRadius") ?: 24f)
    val padding = scope.sizeDp(styling.float("padding") ?: 40f)
    val opacity = styling.float("opacity") ?: 1f
    val transparent = styling.bool("transparent") ?: false

    val questionObj = styling.obj("question")
    val questionColor = parseStoryColor(questionObj.str("color")) ?: Color.Black
    val questionFontSize = scope.fontDp(questionObj.float("fontSize") ?: 18f)

    val optionsObj = styling.obj("options")
    val optionBg = parseStoryColor(optionsObj.str("background")) ?: Color(0xFF1A1A1A)
    val optionTextColor = parseStoryColor(optionsObj.str("textColor")) ?: Color.White
    val optionRadius = scope.sizeDp(optionsObj.float("radius") ?: 8f)
    val optionFontSize = scope.fontDp(optionsObj.float("fontSize") ?: 16f)

    var selected by remember(id) { mutableStateOf<String?>(null) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(borderRadius))
            .background(if (transparent) Color.Transparent else bg.copy(alpha = opacity))
            .padding(padding)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(scope.sizeDp(20f))
        ) {
            if (question.isNotEmpty()) {
                BasicTextWrap(
                    text = question,
                    color = questionColor,
                    fontSizeSp = with(density) { questionFontSize.toSp() },
                    fontWeight = FontWeight.SemiBold
                )
            }
            optionPairs.forEach { (key, label) ->
                val isSelected = selected == key
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(optionRadius))
                        .background(if (isSelected) optionBg.copy(alpha = 0.85f) else optionBg)
                        .clickable(
                            enabled = selected == null,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            selected = key
                            onTrack(
                                "interaction_response",
                                mapOf(
                                    "interaction_id" to (id ?: ""),
                                    "type" to "POLL",
                                    "option" to key,
                                    "label" to label
                                )
                            )
                        }
                        .padding(vertical = scope.sizeDp(28f), horizontal = scope.sizeDp(20f))
                ) {
                    BasicTextWrap(
                        text = label,
                        color = optionTextColor,
                        fontSizeSp = with(density) { optionFontSize.toSp() },
                        align = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ----------------------- QUIZ (text-options) ------------------------------

@Composable
private fun QuizInteraction(
    id: String?,
    config: JsonObject?,
    styling: JsonObject?,
    scope: StoryCanvaScope,
    onTrack: (event: String, metadata: Map<String, Any>) -> Unit
) {
    val question = config.str("question") ?: ""
    val correctId = config.str("isCorrect") ?: config.str("correctOption")
    val explanation = config.str("explanation")
    val showExplanation = config.bool("showExplanation") ?: false
    val options = config?.get("options")?.let { runCatching { it.jsonObject }.getOrNull() }
    val optionPairs: List<Pair<String, String>> = options?.entries?.map { (key, value) ->
        key to (runCatching { value.jsonPrimitive.contentOrNull }.getOrNull() ?: key)
    } ?: emptyList()

    val bg = parseStoryColor(styling.str("background")) ?: Color.White
    val borderRadius = scope.sizeDp(styling.float("borderRadius") ?: 16f)
    val padding = scope.sizeDp(styling.float("containerPadding") ?: 20f)
    val questionBg = parseStoryColor(styling.str("questionBgColor")) ?: Color.Black
    val questionColor = parseStoryColor(styling.str("questionColor")) ?: Color.White
    val questionFontSize = scope.fontDp(styling.float("questionFontSize") ?: 32f)
    val optionBg = parseStoryColor(styling.str("optionBgColor")) ?: Color(0xFFF9FAFB)
    val optionTextColor = parseStoryColor(styling.str("optionTextColor")) ?: Color(0xFF1F2937)
    val optionFontSize = scope.fontDp(styling.float("optionFontSize") ?: 28f)
    val optionRadius = scope.sizeDp(styling.float("optionRadius") ?: 8f)
    val correctColor = parseStoryColor(styling.str("correctColor")) ?: Color(0xFF10B981)
    val incorrectColor = parseStoryColor(styling.str("incorrectColor")) ?: Color(0xFFEF4444)

    var selected by remember(id) { mutableStateOf<String?>(null) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(borderRadius))
            .background(bg)
            .padding(padding)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(scope.sizeDp(16f))
        ) {
            if (question.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(optionRadius))
                        .background(questionBg)
                        .padding(scope.sizeDp(24f))
                ) {
                    BasicTextWrap(
                        text = question,
                        color = questionColor,
                        fontSizeSp = with(density) { questionFontSize.toSp() },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            optionPairs.forEach { (key, label) ->
                val isSelected = selected == key
                val isCorrect = key == correctId
                val targetBg = when {
                    selected == null -> optionBg
                    isCorrect -> correctColor
                    isSelected && !isCorrect -> incorrectColor
                    else -> optionBg
                }
                val targetTextColor =
                    if (selected != null && (isCorrect || isSelected)) Color.White else optionTextColor

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(optionRadius))
                        .background(targetBg)
                        .clickable(
                            enabled = selected == null,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            selected = key
                            onTrack(
                                "interaction_response",
                                mapOf(
                                    "interaction_id" to (id ?: ""),
                                    "type" to "QUIZ",
                                    "option" to key,
                                    "label" to label,
                                    "correct" to isCorrect
                                )
                            )
                        }
                        .padding(scope.sizeDp(24f))
                ) {
                    BasicTextWrap(
                        text = label,
                        color = targetTextColor,
                        fontSizeSp = with(density) { optionFontSize.toSp() }
                    )
                }
            }
            if (showExplanation && selected != null && !explanation.isNullOrEmpty()) {
                BasicTextWrap(
                    text = explanation,
                    color = optionTextColor,
                    fontSizeSp = with(density) { optionFontSize.toSp() * 0.85f }
                )
            }
        }
    }
}

// ----------------------- MEDIA_QUIZ (image-options) -----------------------

@Composable
private fun MediaQuizInteraction(
    id: String?,
    config: JsonObject?,
    styling: JsonObject?,
    scope: StoryCanvaScope,
    onTrack: (event: String, metadata: Map<String, Any>) -> Unit
) {
    val question = config.str("question") ?: ""
    val correctId = config.str("correctOption") ?: config.str("correctAnswerId")
    val layout = config.str("layout") ?: "columns"

    val optionsArray =
        config?.get("options")?.let { runCatching { it.jsonArray }.getOrNull() } ?: return
    // Each option is held as a quadruple of primitives (id, label, imageUrl, isCorrect)
    // to avoid passing a private data class across @Composable boundaries.
    val options: List<MediaQuizOptionData> = optionsArray.mapNotNull {
        val obj = runCatching { it.jsonObject }.getOrNull() ?: return@mapNotNull null
        MediaQuizOptionData(
            id = obj.str("id") ?: return@mapNotNull null,
            label = obj.str("label") ?: "",
            image = obj.str("imageUrl"),
            correct = obj.bool("isCorrect") ?: false
        )
    }

    val bg = parseStoryColor(styling.str("background")) ?: Color.White
    val borderRadius = scope.sizeDp(styling.float("borderRadius") ?: 23f)
    val padding = scope.sizeDp(styling.float("padding") ?: 60f)
    val optionRadius = scope.sizeDp(styling.float("optionRadius") ?: 24f)
    val borderColor = parseStoryColor(styling.str("borderColor")) ?: Color(0xFFE5E7EB)
    val correctBorderColor = parseStoryColor(styling.str("correctBorderColor")) ?: Color(0xFF10B981)
    val questionColor = parseStoryColor(styling.str("questionColor")) ?: Color.White

    var selected by remember(id) { mutableStateOf<String?>(null) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(borderRadius))
            .background(bg)
            .padding(padding)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(scope.sizeDp(20f))
        ) {
            if (question.isNotEmpty()) {
                BasicTextWrap(
                    text = question,
                    color = questionColor,
                    fontSizeSp = with(density) { scope.fontDp(40f).toSp() },
                    fontWeight = FontWeight.Bold,
                    align = TextAlign.Center
                )
            }
            // Layout: columns => side-by-side, otherwise stacked
            if (layout == "columns") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    horizontalArrangement = Arrangement.spacedBy(scope.sizeDp(24f))
                ) {
                    options.forEach { opt ->
                        MediaQuizOption(
                            opt = opt,
                            correctId = correctId,
                            selected = selected,
                            radius = optionRadius,
                            borderColor = borderColor,
                            correctBorderColor = correctBorderColor,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (selected == null) {
                                    selected = opt.id
                                    onTrack(
                                        "interaction_response",
                                        mapOf(
                                            "interaction_id" to (id ?: ""),
                                            "type" to "MEDIA_QUIZ",
                                            "option" to opt.id,
                                            "correct" to opt.correct
                                        )
                                    )
                                }
                            }
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(scope.sizeDp(16f))) {
                    options.forEach { opt ->
                        MediaQuizOption(
                            opt = opt,
                            correctId = correctId,
                            selected = selected,
                            radius = optionRadius,
                            borderColor = borderColor,
                            correctBorderColor = correctBorderColor,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (selected == null) {
                                    selected = opt.id
                                    onTrack(
                                        "interaction_response",
                                        mapOf(
                                            "interaction_id" to (id ?: ""),
                                            "type" to "MEDIA_QUIZ",
                                            "option" to opt.id,
                                            "correct" to opt.correct
                                        )
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaQuizOption(
    opt: MediaQuizOptionData,
    correctId: String?,
    selected: String?,
    radius: androidx.compose.ui.unit.Dp,
    borderColor: Color,
    correctBorderColor: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val isSelected = selected == opt.id
    val showBorder = selected != null && (opt.id == correctId || isSelected)
    val finalBorderColor = when {
        selected != null && opt.id == correctId -> correctBorderColor
        selected != null && isSelected && opt.id != correctId -> Color(0xFFEF4444)
        else -> borderColor
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .border(
                if (showBorder) 3.dp else 1.dp,
                finalBorderColor,
                RoundedCornerShape(radius)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        if (!opt.image.isNullOrEmpty()) {
            androidx.compose.foundation.Image(
                painter = rememberAsyncImagePainter(opt.image),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(radius)),
                contentScale = ContentScale.Crop
            )
        }
    }
}

/** Plain data carrier so we can pass options across @Composable boundaries cleanly. */
private data class MediaQuizOptionData(
    val id: String,
    val label: String,
    val image: String?,
    val correct: Boolean
)

// ----------------------- RATING (emoji slider) ----------------------------

@Composable
private fun RatingInteraction(
    id: String?,
    config: JsonObject?,
    styling: JsonObject?,
    scope: StoryCanvaScope,
    onTrack: (event: String, metadata: Map<String, Any>) -> Unit
) {
    val title = config.str("title") ?: ""
    val emoji = config.str("emoji") ?: "😍"
    val maxRating = config.int("maxRating") ?: 5
    val initialRating = config.int("currentRating") ?: 0
    val variant = config.str("variant") ?: "slider"

    val bg = parseStoryColor(styling.str("background")) ?: Color.White
    val borderRadius = scope.sizeDp(styling.float("borderRadius") ?: 36f)
    val padding = scope.sizeDp(styling.float("padding") ?: 60f)
    val colors = styling.obj("colors")
    val cardBg = parseStoryColor(colors.str("cardBackground")) ?: bg
    val sliderFill = parseStoryColor(colors.str("sliderFill")) ?: Color(0xFFE11D48)
    val sliderTrack = parseStoryColor(colors.str("sliderTrack")) ?: Color(0xFFF3F4F6)
    val titleColor = parseStoryColor(colors.str("titleColor")) ?: Color.Black
    val typo = styling.obj("typography")
    val titleSize = scope.fontDp(typo.float("titleSize") ?: 42f)
    val emojiSize = scope.fontDp(typo.float("emojiSize") ?: 120f)

    var rating by remember(id) { mutableIntStateOf(initialRating.coerceIn(0, maxRating)) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(borderRadius))
            .background(cardBg)
            .padding(padding)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(scope.sizeDp(16f)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (title.isNotEmpty()) {
                BasicTextWrap(
                    text = title,
                    color = titleColor,
                    fontSizeSp = with(density) { titleSize.toSp() },
                    fontWeight = FontWeight.SemiBold,
                    align = TextAlign.Center
                )
            }
            if (variant == "slider") {
                Slider(
                    value = rating.toFloat(),
                    onValueChange = { rating = it.toInt() },
                    onValueChangeFinished = {
                        onTrack(
                            "interaction_response",
                            mapOf(
                                "interaction_id" to (id ?: ""),
                                "type" to "RATING",
                                "rating" to rating
                            )
                        )
                    },
                    valueRange = 0f..maxRating.toFloat(),
                    steps = (maxRating - 1).coerceAtLeast(0),
                    colors = SliderDefaults.colors(
                        thumbColor = sliderFill,
                        activeTrackColor = sliderFill,
                        inactiveTrackColor = sliderTrack
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                BasicTextWrap(
                    text = emoji,
                    color = Color.Unspecified,
                    fontSizeSp = with(density) { emojiSize.toSp() },
                    align = TextAlign.Center
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    repeat(maxRating) { i ->
                        val selected = i < rating
                        BasicTextWrap(
                            text = emoji,
                            color = if (selected) Color.Unspecified else Color.Unspecified,
                            fontSizeSp = with(density) { emojiSize.toSp() * 0.6f },
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    rating = i + 1
                                    onTrack(
                                        "interaction_response",
                                        mapOf(
                                            "interaction_id" to (id ?: ""),
                                            "type" to "RATING",
                                            "rating" to (i + 1)
                                        )
                                    )
                                }
                        )
                    }
                }
            }
        }
    }
}

// ----------------------- REACTION (emoji tap) -----------------------------

@Composable
private fun ReactionInteraction(
    id: String?,
    config: JsonObject?,
    styling: JsonObject?,
    scope: StoryCanvaScope,
    onTrack: (event: String, metadata: Map<String, Any>) -> Unit
) {
    val options = config?.get("options")?.let { runCatching { it.jsonObject }.getOrNull() }
    val pairs: List<Pair<String, String>> = options?.entries?.mapNotNull { (k, v) ->
        val emoji =
            runCatching { v.jsonPrimitive.contentOrNull }.getOrNull() ?: return@mapNotNull null
        k to emoji
    } ?: emptyList()

    val transparent = styling.bool("transparentBackground") ?: false
    val bg = parseStoryColor(styling.str("background")) ?: Color.White
    val borderRadius = scope.sizeDp(styling.float("borderRadius") ?: 0f)
    val emojiSize = scope.fontDp(styling.float("emojiSize") ?: 120f)
    val padding = scope.sizeDp(styling.float("padding") ?: 0f)

    var picked by remember(id) { mutableStateOf<String?>(null) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(borderRadius))
            .background(if (transparent) Color.Transparent else bg)
            .padding(padding)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            pairs.forEach { (key, emoji) ->
                val isPicked = picked == key
                BasicTextWrap(
                    text = emoji,
                    color = Color.Unspecified,
                    fontSizeSp = with(density) { (if (isPicked) emojiSize * 1.15f else emojiSize).toSp() },
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        picked = key
                        onTrack(
                            "interaction_response",
                            mapOf(
                                "interaction_id" to (id ?: ""),
                                "type" to "REACTION",
                                "option" to key,
                                "emoji" to emoji
                            )
                        )
                    }
                )
            }
        }
    }
}

// ----------------------- COUNTDOWN ----------------------------------------

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
    val display = config.obj("display")
    val showDays = display.bool("showDays") ?: true
    val showHours = display.bool("showHours") ?: true
    val showMinutes = display.bool("showMinutes") ?: true
    val showSeconds = display.bool("showSeconds") ?: true

    val targetMs = remember(endDate, endTime) {
        runCatching {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
                timeZone = TimeZone.getDefault()
            }
            sdf.parse("$endDate $endTime")?.time ?: 0L
        }.getOrDefault(0L)
    }

    val bg = parseStoryColor(styling.str("background")) ?: Color.White
    val borderRadius = scope.sizeDp(styling.float("borderRadius") ?: 12f)
    val padding = scope.sizeDp(styling.float("padding") ?: 24f)
    val titleColor = parseStoryColor(styling.str("titleColor")) ?: Color(0xFF1F2937)
    val digitBg = parseStoryColor(styling.str("digitBackground")) ?: Color(0xFFF3F4F6)
    val digitColor = parseStoryColor(styling.str("digitColor")) ?: Color(0xFF1F2937)
    val digitSize = scope.fontDp(styling.float("digitSize") ?: 28f)
    val labelColor = parseStoryColor(styling.str("labelColor")) ?: Color(0xFF9CA3AF)

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val remaining = (targetMs - now).coerceAtLeast(0L)
    val totalSeconds = remaining / 1000
    val days = totalSeconds / 86400
    val hours = (totalSeconds % 86400) / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(borderRadius))
            .background(bg)
            .padding(padding)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(scope.sizeDp(12f)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (title.isNotEmpty()) {
                BasicTextWrap(
                    text = title,
                    color = titleColor,
                    fontSizeSp = with(density) { (digitSize * 0.9f).toSp() },
                    fontWeight = FontWeight.SemiBold,
                    align = TextAlign.Center
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showDays) DigitCell(
                    "D",
                    days.toString().padStart(2, '0'),
                    digitBg,
                    digitColor,
                    labelColor,
                    digitSize,
                    density,
                    scope
                )
                if (showHours) DigitCell(
                    "H",
                    hours.toString().padStart(2, '0'),
                    digitBg,
                    digitColor,
                    labelColor,
                    digitSize,
                    density,
                    scope
                )
                if (showMinutes) DigitCell(
                    "M",
                    minutes.toString().padStart(2, '0'),
                    digitBg,
                    digitColor,
                    labelColor,
                    digitSize,
                    density,
                    scope
                )
                if (showSeconds) DigitCell(
                    "S",
                    seconds.toString().padStart(2, '0'),
                    digitBg,
                    digitColor,
                    labelColor,
                    digitSize,
                    density,
                    scope
                )
            }
        }
    }
}

@Composable
private fun DigitCell(
    label: String,
    value: String,
    bg: Color,
    digitColor: Color,
    labelColor: Color,
    digitSize: androidx.compose.ui.unit.Dp,
    density: androidx.compose.ui.unit.Density,
    scope: StoryCanvaScope
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(scope.sizeDp(4f))
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(scope.sizeDp(8f)))
                .background(bg)
                .padding(horizontal = scope.sizeDp(16f), vertical = scope.sizeDp(8f))
        ) {
            BasicTextWrap(
                text = value,
                color = digitColor,
                fontSizeSp = with(density) { digitSize.toSp() },
                fontWeight = FontWeight.Bold
            )
        }
        BasicTextWrap(
            text = label,
            color = labelColor,
            fontSizeSp = with(density) { (digitSize * 0.45f).toSp() }
        )
    }
}

// ----------------------- PROMO (coupon code) ------------------------------

@Composable
private fun PromoInteraction(
    id: String?,
    config: JsonObject?,
    styling: JsonObject?,
    scope: StoryCanvaScope,
    onTrack: (event: String, metadata: Map<String, Any>) -> Unit
) {
    val code = config.str("couponCode") ?: ""
    val showCopy = config.bool("copyButton") ?: true

    val bg = parseStoryColor(styling.str("background")) ?: Color(0xFFF3F3F3)
    val textColor = parseStoryColor(styling.str("text")) ?: Color.Black
    val borderColor = parseStoryColor(styling.str("borderColor")) ?: Color.Transparent
    val borderRadius = scope.sizeDp(styling.float("borderRadius") ?: 20f)

    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by remember(id) { mutableStateOf(false) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(borderRadius))
            .background(bg)
            .let {
                if (borderColor != Color.Transparent) it.border(
                    1.dp,
                    borderColor,
                    RoundedCornerShape(borderRadius)
                ) else it
            }
            .padding(scope.sizeDp(20f)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(scope.sizeDp(12f))
        ) {
            BasicTextWrap(
                text = code,
                color = textColor,
                fontSizeSp = with(density) { scope.fontDp(40f).toSp() },
                fontWeight = FontWeight.Bold
            )
            if (showCopy) {
                Button(
                    onClick = {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(code))
                        copied = true
                        onTrack(
                            "interaction_response",
                            mapOf(
                                "interaction_id" to (id ?: ""),
                                "type" to "PROMO",
                                "code" to code,
                                "action" to "copied"
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = textColor,
                        contentColor = bg
                    ),
                    shape = RoundedCornerShape(scope.sizeDp(8f))
                ) {
                    BasicTextWrap(
                        text = if (copied) "Copied!" else "Copy",
                        color = bg,
                        fontSizeSp = with(density) { scope.fontDp(28f).toSp() }
                    )
                }
            }
        }
    }
}

// ----------------------- INPUT (text question) ----------------------------

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
    val placeholder = config.str("placeholder") ?: "Type your answer..."
    val maxLength = config.int("maxLength") ?: 200

    val bg = parseStoryColor(styling.str("background")) ?: Color.White
    val borderRadius = scope.sizeDp(styling.float("borderRadius") ?: 24f)
    val padding = scope.sizeDp(styling.float("padding") ?: 40f)
    val questionColor = parseStoryColor(styling.str("questionColor")) ?: Color(0xFF1F2937)
    val questionSize = scope.fontDp(styling.float("questionSize") ?: 32f)
    val inputBg = parseStoryColor(styling.str("inputBackground")) ?: Color(0xFFF3F4F6)
    val inputTextColor = parseStoryColor(styling.str("inputTextColor")) ?: Color(0xFF111827)
    val submitBg = parseStoryColor(styling.str("submitBackground")) ?: Color(0xFFF97316)

    var value by remember(id) { mutableStateOf("") }
    var submitted by remember(id) { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(borderRadius))
            .background(bg)
            .padding(padding)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(scope.sizeDp(20f))
        ) {
            if (title.isNotEmpty()) {
                BasicTextWrap(
                    text = title,
                    color = questionColor,
                    fontSizeSp = with(density) { questionSize.toSp() },
                    fontWeight = FontWeight.SemiBold
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(scope.sizeDp(16f)))
                    .background(inputBg)
                    .padding(horizontal = scope.sizeDp(20f), vertical = scope.sizeDp(16f))
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = { if (it.length <= maxLength) value = it },
                    enabled = !submitted,
                    singleLine = false,
                    textStyle = TextStyle(
                        color = inputTextColor,
                        fontSize = with(density) { scope.fontDp(28f).toSp() }
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChangedOrNoop(onFocusChanged),
                    decorationBox = { inner ->
                        Box {
                            if (value.isEmpty()) {
                                BasicTextWrap(
                                    text = placeholder,
                                    color = Color(0xFF9CA3AF),
                                    fontSizeSp = with(density) { scope.fontDp(28f).toSp() }
                                )
                            }
                            inner()
                        }
                    }
                )
            }
            Button(
                onClick = {
                    if (value.isNotBlank() && !submitted) {
                        submitted = true
                        keyboard?.hide()
                        focusManager.clearFocus()
                        onFocusChanged(false)
                        onTrack(
                            "interaction_response",
                            mapOf(
                                "interaction_id" to (id ?: ""),
                                "type" to "INPUT",
                                "answer" to value
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = submitBg),
                shape = RoundedCornerShape(scope.sizeDp(12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                BasicTextWrap(
                    text = if (submitted) "Submitted" else "Submit",
                    color = Color.White,
                    fontSizeSp = with(density) { scope.fontDp(28f).toSp() }
                )
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
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Text(
        text = text,
        color = color,
        fontSize = fontSizeSp,
        fontWeight = fontWeight,
        textAlign = align,
        modifier = modifier
    )
}

private fun Modifier.onFocusChangedOrNoop(
    cb: (Boolean) -> Unit
): Modifier = onFocusChanged {
    cb(it.isFocused)
}
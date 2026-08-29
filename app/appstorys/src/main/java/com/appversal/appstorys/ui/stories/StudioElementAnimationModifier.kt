package com.appversal.appstorys.ui.stories

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.appversal.appstorys.api.StoryAnimation
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Compose port of the iOS `StudioElementAnimationModifier` (SwiftUI).
 *
 * Drives the entrance / continuous animation of a studio-editor element
 * (text, image, video, shape, cta …) from:
 *   - [animation].type      → "classic" | "slide" | "fade" | "rotate" | "bounce"
 *   - [animation].direction → "up" | "down" | "left" | "right" | "clockwise" | "anticlockwise"
 *   - the element's [duration] window (`{ "start": s, "end": e }`)
 *   - the slide's [currentTime] (seconds since the slide started)
 *
 * Behaviour parity with the Swift version:
 *
 * All five share one duration, [ENTRANCE_MILLIS], and one easing curve:
 *
 *   classic  scale 0.05 → 1.0 + fade in
 *   slide    directional offset (60dp) → 0 + fade in
 *   fade     directional offset (40dp) → 0 + fade in
 *   rotate   ±[ROTATE_SETTLE_DEGREES]° → 0 + fade in
 *   bounce   y 0 → -12 → 0, one bob (easeInOut, so the bob eases at both ends)
 *   <other>  visible only while inside the duration window (if a window is set)
 *
 * Entrance animations (classic / slide / fade) re-trigger whenever the element
 * (re)enters its duration window, exactly like the SwiftUI `onChange(isInDuration)`.
 *
 * When the element has **no** duration window (start == 0 and end == null) it is
 * always "in duration", so entrance/continuous animations play immediately and
 * [currentTime] is irrelevant — matching the Swift defaults.
 */
fun Modifier.studioElementAnimation(
    animation: StoryAnimation?,
    duration: JsonElement? = null,
    currentTime: Double = 0.0,
): Modifier {
    val (start, end) = parseStudioDuration(duration)
    return studioElementAnimation(
        animation = animation,
        durationStart = start,
        durationEnd = end,
        currentTime = currentTime,
    )
}

/**
 * Lower-level entry point when start/end are already known.
 */
fun Modifier.studioElementAnimation(
    animation: StoryAnimation?,
    durationStart: Double,
    durationEnd: Double?,
    currentTime: Double = 0.0,
): Modifier = composed {

    val type = (animation?.type ?: "none").trim().lowercase()
    val dir = (animation?.direction ?: "none").trim().lowercase()

    val isInDuration = currentTime >= durationStart &&
            (durationEnd == null || currentTime <= durationEnd)
    val hasDuration = durationStart > 0.0 || durationEnd != null

    // ONE entrance curve for every animation type. Each type used to bring its own
    // timing (two different springs, a 550 ms tween, a 2 s spin), so a slide mixing
    // them settled at four different moments. They now all finish together.
    val floatSpec: AnimationSpec<Float> = tween(ENTRANCE_MILLIS, easing = EaseOut)
    val dpSpec: AnimationSpec<Dp> = tween(ENTRANCE_MILLIS, easing = EaseOut)

    when (type) {

        // ---- classic: scale-up + fade-in entrance ----
        "classic" -> {
            val appeared = rememberEntrance(isInDuration)
            val scaleV by animateFloatAsState(
                targetValue = if (appeared) 1f else 0.05f,
                animationSpec = floatSpec,
                label = "studioClassicScale",
            )
            val op by animateFloatAsState(
                targetValue = if (appeared && isInDuration) 1f else 0f,
                animationSpec = floatSpec,
                label = "studioClassicOpacity",
            )
            this.scale(scaleV).alpha(op)
        }

        // ---- slide: directional slide-in + fade-in entrance ----
        "slide" -> {
            val appeared = rememberEntrance(isInDuration)
            val (sx, sy) = directionalOffset(dir, distance = 60.dp)
            val ox by animateDpAsState(
                targetValue = if (appeared) 0.dp else sx,
                animationSpec = dpSpec,
                label = "studioSlideX",
            )
            val oy by animateDpAsState(
                targetValue = if (appeared) 0.dp else sy,
                animationSpec = dpSpec,
                label = "studioSlideY",
            )
            val op by animateFloatAsState(
                targetValue = if (appeared && isInDuration) 1f else 0f,
                animationSpec = floatSpec,
                label = "studioSlideOpacity",
            )
            this.offset(x = ox, y = oy).alpha(op)
        }

        // ---- fade: gentle directional fade-in entrance ----
        "fade" -> {
            val appeared = rememberEntrance(isInDuration)
            val (sx, sy) = directionalOffset(dir, distance = 40.dp)
            val ox by animateDpAsState(
                targetValue = if (appeared) 0.dp else sx,
                animationSpec = dpSpec,
                label = "studioFadeX",
            )
            val oy by animateDpAsState(
                targetValue = if (appeared) 0.dp else sy,
                animationSpec = dpSpec,
                label = "studioFadeY",
            )
            val op by animateFloatAsState(
                targetValue = if (appeared && isInDuration) 1f else 0f,
                animationSpec = floatSpec,
                label = "studioFadeOpacity",
            )
            this.offset(x = ox, y = oy).alpha(op)
        }

        // ---- rotate: angular settle + fade-in entrance ----
        "rotate" -> {
            val appeared = rememberEntrance(isInDuration)
            val from =
                if (dir == "anticlockwise") -ROTATE_SETTLE_DEGREES else ROTATE_SETTLE_DEGREES
            val angle by animateFloatAsState(
                targetValue = if (appeared) 0f else from,
                animationSpec = floatSpec,
                label = "studioRotateAngle",
            )
            val op by animateFloatAsState(
                targetValue = if (appeared && isInDuration) 1f else 0f,
                animationSpec = floatSpec,
                label = "studioRotateOpacity",
            )
            this.rotate(angle).alpha(op)
        }

        // ---- bounce: one vertical bob, bounded to the entrance ----
        "bounce" -> {
            val dy = remember { Animatable(0f) }
            LaunchedEffect(isInDuration) {
                dy.snapTo(0f)
                if (!isInDuration) return@LaunchedEffect
                dy.animateTo(
                    targetValue = -12f,
                    animationSpec = repeatable(
                        // Two half-cycles = down and back up. Must stay even: with
                        // RepeatMode.Reverse an odd count parks the element at the top
                        // of the bob instead of back on its baseline.
                        iterations = 2,
                        animation = tween(
                            durationMillis = ENTRANCE_MILLIS / 2,
                            easing = EaseInOut,
                        ),
                        repeatMode = RepeatMode.Reverse,
                    ),
                )
            }
            this.offset(y = dy.value.dp).alpha(if (isInDuration) 1f else 0f)
        }

        // ---- none / unknown: only gate visibility on the duration window ----
        else -> {
            if (hasDuration) this.alpha(if (isInDuration) 1f else 0f) else this
        }
    }
}

/**
 * Every studio animation is an ENTRANCE animation: it plays once when the
 * element appears and then holds still, so a 5-second slide and a 30-second
 * slide look identical.
 *
 * Both numbers are measured off the studio's own preview: the element group
 * moves for 0.96 s and is then pixel-for-pixel static for the rest of the
 * slide, and "rotate" travels +0.48° → -22.39° — a small decelerating settle
 * back to the element's authored angle, not a spin. (The SDK used to read
 * "rotate" as a continuous 0 → ±360° turn, which is a different animation.)
 *
 * These are the tuning knobs: ENTRANCE_MILLIS sets how long every entrance
 * runs, ROTATE_SETTLE_DEGREES how far the settle swings.
 */
private const val ENTRANCE_MILLIS = 950
private const val ROTATE_SETTLE_DEGREES = 24f

/**
 * Mirrors the SwiftUI `appeared` @State + `onAppear` / `onChange(isInDuration)`
 * combo. Returns `true` once the brief settle delay has elapsed while the
 * element is inside its duration window; flips back to `false` when it leaves.
 */
@Composable
private fun rememberEntrance(isInDuration: Boolean): Boolean {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(isInDuration) {
        if (isInDuration) {
            appeared = false
            delay(20) // matches the 0.02s asyncAfter settle in the Swift version
            appeared = true
        } else {
            appeared = false
        }
    }
    return appeared
}

/**
 * Start offset for slide / fade entrances. Positive Y = downward in Compose,
 * so "up" starts below the final position and travels up into place — identical
 * to the SwiftUI mapping.
 */
private fun directionalOffset(direction: String, distance: Dp): Pair<Dp, Dp> =
    when (direction) {
        "up" -> 0.dp to distance
        "down" -> 0.dp to -distance
        "left" -> distance to 0.dp
        "right" -> -distance to 0.dp
        else -> 0.dp to distance
    }


/**
 * Parses a studio `duration` payload (`{ "start": s, "end": e }`) into a
 * (start, end?) pair of seconds. Tolerant of missing / malformed fields:
 * defaults start to 0 and end to null (i.e. "no upper bound").
 */
internal fun parseStudioDuration(element: JsonElement?): Pair<Double, Double?> {
    val obj = element as? JsonObject ?: return 0.0 to null
    val start = (obj["start"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
    val end = (obj["end"] as? JsonPrimitive)?.content?.toDoubleOrNull()
    return start to end
}
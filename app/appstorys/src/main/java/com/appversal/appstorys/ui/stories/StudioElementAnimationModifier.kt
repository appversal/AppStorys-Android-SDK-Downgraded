package com.appversal.appstorys.ui.stories

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
 *   classic  scale 0.05 → 1.0 + fade in   (spring, response 0.45 / damping 0.58)
 *   slide    directional offset → 0 + fade (spring, response 0.50 / damping 0.72)
 *   fade     directional offset → 0 + fade (easeOut, 0.55s)
 *   rotate   continuous 0 → ±360°          (linear, 2.0s, forever)
 *   bounce   continuous y 0 → -12 → 0      (easeInOut, 0.55s, forever, autoreverse)
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

    when (type) {

        // ---- classic: scale-up + fade-in entrance ----
        "classic" -> {
            val appeared = rememberEntrance(isInDuration)
            val spec = swiftSpring<Float>(response = 0.45, damping = 0.58)
            val scaleV by animateFloatAsState(
                targetValue = if (appeared) 1f else 0.05f,
                animationSpec = spec,
                label = "studioClassicScale",
            )
            val op by animateFloatAsState(
                targetValue = if (appeared && isInDuration) 1f else 0f,
                animationSpec = spec,
                label = "studioClassicOpacity",
            )
            this.scale(scaleV).alpha(op)
        }

        // ---- slide: directional slide-in + fade-in entrance ----
        "slide" -> {
            val appeared = rememberEntrance(isInDuration)
            val floatSpec = swiftSpring<Float>(response = 0.50, damping = 0.72)
            val dpSpec = swiftSpring<Dp>(response = 0.50, damping = 0.72)
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
            val floatSpec: AnimationSpec<Float> = tween(durationMillis = 550, easing = EaseOut)
            val dpSpec: AnimationSpec<Dp> = tween(durationMillis = 550, easing = EaseOut)
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

        // ---- rotate: continuous spin ----
        "rotate" -> {
            val transition = rememberInfiniteTransition(label = "studioRotate")
            val target = if (dir == "anticlockwise") -360f else 360f
            val angle by transition.animateFloat(
                initialValue = 0f,
                targetValue = target,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "studioRotateAngle",
            )
            this.rotate(angle).alpha(if (isInDuration) 1f else 0f)
        }

        // ---- bounce: continuous vertical bob ----
        "bounce" -> {
            val transition = rememberInfiniteTransition(label = "studioBounce")
            val dy by transition.animateFloat(
                initialValue = 0f,
                targetValue = -12f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 550, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "studioBounceY",
            )
            this.offset(y = dy.dp).alpha(if (isInDuration) 1f else 0f)
        }

        // ---- none / unknown: only gate visibility on the duration window ----
        else -> {
            if (hasDuration) this.alpha(if (isInDuration) 1f else 0f) else this
        }
    }
}

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
 * Converts a SwiftUI `spring(response:dampingFraction:)` into a Compose
 * [SpringSpec]. Natural frequency ω = 2π / response, and Compose stiffness
 * k = ω² (unit mass), with dampingRatio == dampingFraction.
 */
private fun <T> swiftSpring(response: Double, damping: Double): SpringSpec<T> {
    val omega = (2.0 * Math.PI) / response
    val stiffness = (omega * omega).toFloat()
    return spring(dampingRatio = damping.toFloat(), stiffness = stiffness)
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
package com.appversal.appstorys.ui.spinwheel

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.appversal.appstorys.api.CommonMargins
import com.appversal.appstorys.api.SpinTheWheelDetails
import com.appversal.appstorys.api.SpinWheelRewardConfig
import com.appversal.appstorys.api.WheelRewardStyling
import com.appversal.appstorys.api.TextStyling
import com.appversal.appstorys.api.WheelSlice
import com.appversal.appstorys.ui.common_components.CommonText
import com.appversal.appstorys.ui.common_components.CrossButton
import com.appversal.appstorys.ui.common_components.createCrossButtonConfig
import com.appversal.appstorys.ui.scratchcard.RewardMedia
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Parses a color string to a Compose Color, with fallback
 */
private fun parseColor(colorString: String?, fallback: Color = Color.Unspecified): Color {
    return try {
        if (!colorString.isNullOrBlank()) {
            // Handle special color keywords
            when (colorString.lowercase().trim()) {
                "transparent" -> Color.Transparent
                "white" -> Color.White
                "black" -> Color.Black
                "red" -> Color.Red
                "green" -> Color.Green
                "blue" -> Color.Blue
                "yellow" -> Color.Yellow
                "cyan" -> Color.Cyan
                "magenta" -> Color.Magenta
                "gray", "grey" -> Color.Gray
                else -> {
                    // Parse hex color. The dashboard sends 8 digits as #RRGGBBAA, but
                    // android.graphics.Color.parseColor reads them as #AARRGGBB — so an
                    // opaque "#000000ff" would otherwise come back fully transparent.
                    val normalizedColor =
                        if (colorString.startsWith("#")) colorString else "#$colorString"
                    val androidColor = if (normalizedColor.length == 9) {
                        "#${normalizedColor.substring(7, 9)}${normalizedColor.substring(1, 7)}"
                    } else normalizedColor
                    Color(android.graphics.Color.parseColor(androidColor))
                }
            }
        } else {
            fallback
        }
    } catch (_: Exception) {
        fallback
    }
}

/**
 * Extracts text alignment from styling string
 */
private fun parseTextAlign(alignment: String?): TextAlign {
    return when (alignment?.lowercase()) {
        "left" -> TextAlign.Left
        "right" -> TextAlign.Right
        "center" -> TextAlign.Center
        else -> TextAlign.Center
    }
}

/**
 * Extracts font weight from styling string
 */
private fun parseFontWeight(weight: String?): FontWeight {
    return when (weight?.lowercase()) {
        "bold" -> FontWeight.Bold
        "normal" -> FontWeight.Normal
        "light" -> FontWeight.Light
        "medium" -> FontWeight.Medium
        "semibold" -> FontWeight.SemiBold
        "extrabold" -> FontWeight.ExtraBold
        else -> FontWeight.Normal
    }
}

/**
 * Extracts font style from styling string
 */
private fun parseFontStyle(style: String?): FontStyle {
    return when (style?.lowercase()) {
        "italic" -> FontStyle.Italic
        else -> FontStyle.Normal
    }
}

/**
 * Parses text decorations from list
 */
private fun parseTextDecoration(decorations: List<String>?): TextDecoration? {
    if (decorations.isNullOrEmpty()) return null
    val decorationList = decorations.mapNotNull { decoration ->
        when (decoration.lowercase()) {
            "underline" -> TextDecoration.Underline
            "linethrough", "line-through", "strikethrough" -> TextDecoration.LineThrough
            else -> null
        }
    }
    return if (decorationList.isEmpty()) null else TextDecoration.combine(decorationList)
}

/**
 * A spin in flight, or one that has landed. Held by [com.appversal.appstorys.AppStorys]
 * rather than by the composition, so a configuration change — a screen rotation, most
 * often — cannot cancel it. The wheel's angle is derived from wall-clock time against
 * [startedAt], so after the activity is recreated the animation picks up exactly where
 * it should be instead of snapping back to rest.
 */
data class SpinRun(
    val winningIndex: Int,
    val fromAngle: Float,
    val toAngle: Float,
    val durationMs: Int,
    val startedAt: Long,
    /** Set once the wheel has stopped; the spin is charged and reported exactly here. */
    val finished: Boolean = false
)

/** The easing the wheel decelerates with. Shared so a resumed spin follows the same curve. */
private val SpinEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/** Where the wheel should be right now for [run]. */
private fun SpinRun.angleAt(nowMs: Long): Float {
    val fraction = ((nowMs - startedAt).toFloat() / durationMs).coerceIn(0f, 1f)
    return fromAngle + (toAngle - fromAngle) * SpinEasing.transform(fraction)
}

/**
 * The prize name the user is shown: the reward's, falling back to the slice label.
 * Analytics must report the same value, so both read it from here.
 */
internal fun WheelSlice.displayPrizeName(): String? =
    rewards?.firstOrNull()?.prizeName?.takeIf { it.isNotEmpty() } ?: prizeLabel

/** The coupon the user is shown — reward first, slice second. See [displayPrizeName]. */
internal fun WheelSlice.displayCoupon(): String? =
    rewards?.firstOrNull()?.couponCode?.takeIf { it.isNotEmpty() } ?: coupon

@RequiresApi(Build.VERSION_CODES.M)
@Composable
fun SpinTheWheel(
    isPresented: Boolean,
    onDismiss: () -> Unit,
    spinTheWheelDetails: SpinTheWheelDetails,
    // Spin count is hoisted by the caller (AppStorys object) so it persists
    // across recompositions, screen navigation and app restarts.
    spinsLeft: Int,
    onSpinUsed: () -> Unit,
    // The spin in flight, hoisted by the caller so it survives activity recreation.
    spinRun: SpinRun? = null,
    onSpinStarted: (SpinRun) -> Unit = {},
    onSpinResolved: () -> Unit = {},
    onRewardDismissed: () -> Unit = {},
    onCtaClick: (String?) -> Unit = {},
    onSpinComplete: (prizeLabel: String?, couponCode: String?) -> Unit = { _, _ -> }
) {
    if (!isPresented) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Direct fields from backend
    val slices = spinTheWheelDetails.slices.orEmpty()
    val content = spinTheWheelDetails.content
    val styling = spinTheWheelDetails.styling

    // Extract styling values
    val mainStyling = styling?.spinTheWheel
    val visualTextStyling = mainStyling?.visualTextCommunication
    val crossButtonConfig = mainStyling?.crossButton
    val spinButtonStyle = visualTextStyling?.spinButton
    val titleStyle = visualTextStyling?.title?.textStyle
    val subtitleStyle = visualTextStyling?.subtitle?.textStyle
    val availableSpinTextStyle = visualTextStyling?.availableSpinText?.textStyle

    // spinsLeft comes from the hoisted AppStorys state — no local copy, no reset.

    // All three follow the hoisted run, so they are restored after a rotation.
    val isSpinning = spinRun != null && !spinRun.finished
    val selectedSlice = spinRun?.let { slices.getOrNull(it.winningIndex) }
    val showResultDialog = spinRun?.finished == true
    var showConfetti by remember { mutableStateOf(false) }

    // Haptic feedback from content.userInteraction
    val enableHapticFeedback = content?.userInteraction?.hapticFeedback ?: false

    // Animation state — seeded from the run so a recreated wheel starts where it was.
    val rotation = remember {
        Animatable(spinRun?.angleAt(System.currentTimeMillis()) ?: 0f)
    }

    // Drives the wheel from wall-clock time. Restarting this effect after a rotation
    // resumes the same spin rather than beginning a new one.
    LaunchedEffect(spinRun?.startedAt) {
        val run = spinRun ?: return@LaunchedEffect
        if (run.finished) {
            rotation.snapTo(run.toAngle)
            return@LaunchedEffect
        }
        while (true) {
            val now = System.currentTimeMillis()
            rotation.snapTo(run.angleAt(now))
            if (now - run.startedAt >= run.durationMs) break
            withFrameNanos { }
        }

        if (enableHapticFeedback) {
            delay(100)
            triggerHapticFeedback(context, duration = 200)
        }

        val winner = slices.getOrNull(run.winningIndex)
        val confettiStyle = styling?.rewardConfiguration?.confetti?.selectedStyle
        if (winner?.noPrize != true && !confettiStyle.equals("none", true)) {
            showConfetti = true
            delay(300)
        }

        // Charged and reported here, once: the run is marked finished by the caller, so
        // a later recreation takes the early return above instead of paying twice.
        onSpinUsed()
        onSpinResolved()
        onSpinComplete(winner?.displayPrizeName(), winner?.displayCoupon())
    }

    // Pulse animation for button
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Starting a spin only records where the wheel must end up and when it began; the
    // effect above does the turning. Nothing about the spin lives in this composition.
    val performSpin = {
        if (spinsLeft > 0 && !isSpinning && slices.isNotEmpty()) {
            // Winning slice by probability weight (the 'weight' field from the backend)
            val totalWeight = slices.sumOf { maxOf(it.weight ?: 0, 0) }
            val winningSlice = if (totalWeight > 0) {
                val randomValue = Random.nextInt(totalWeight)
                var cumulativeWeight = 0
                var picked = slices.firstOrNull()
                for (slice in slices) {
                    cumulativeWeight += maxOf(slice.weight ?: 0, 0)
                    if (randomValue < cumulativeWeight) {
                        picked = slice
                        break
                    }
                }
                picked
            } else {
                // Fallback: equal probability when no weights are defined
                slices.randomOrNull()
            }

            val sliceAngle = 360f / slices.size
            val winningSliceIndex = slices.indexOf(winningSlice)

            // Middle of winning slice (pointer is at top = -90° base)
            val sliceMiddleAngle = -90f + (winningSliceIndex * sliceAngle) + (sliceAngle / 2f)

            // We want this slice middle to land at 270° (top position in canvas)
            val desiredStopAngle = 270f - sliceMiddleAngle

            val spinDirection = content?.wheelConfiguration?.spinDirection ?: "clockwise"
            val directionMultiplier =
                if (spinDirection.lowercase() == "anti-clockwise") -1 else 1

            val fullSpins = directionMultiplier * 360f * (6 + Random.nextInt(3))

            val current = rotation.value
            val currentNormalized = (current % 360f + 360f) % 360f

            var delta = desiredStopAngle - currentNormalized
            if (directionMultiplier == 1) {
                if (delta < 0) delta += 360f
            } else {
                if (delta > 0) delta -= 360f
            }

            // Haptic feedback at start if enabled
            if (enableHapticFeedback) {
                triggerHapticFeedback(context)
            }

            onSpinStarted(
                SpinRun(
                    winningIndex = winningSliceIndex,
                    fromAngle = current,
                    toAngle = current + fullSpins + delta,
                    durationMs = 4000 + Random.nextInt(500),
                    startedAt = System.currentTimeMillis()
                )
            )
        }
    }

    Dialog(
        onDismissRequest = {
            if (showResultDialog) {
                // Back press while reward is showing → go back to wheel
                showConfetti = false
                onRewardDismissed()
            } else {
                // Back press on wheel → close the whole campaign
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // A dialog window dims whatever is behind it by default. That dim is not the
        // dashboard's, and it composited under every backdrop below — so "backdrop off"
        // still looked dimmed and an opacity of 70 looked closer to 90. Clearing it makes
        // the backdrop exactly the colour and opacity the backend sent, and nothing else.
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(dialogWindow) {
            dialogWindow?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        val enableBackdrop = spinTheWheelDetails.enableBackdrop ?: true

        // Backdrop — switches between spin backdrop and reward backdrop based on state
        val spinBackdropColor = parseColor(visualTextStyling?.backdropColor, Color.Black)
        val spinBackdropOpacity = (visualTextStyling?.backdropOpacity ?: 70) / 100f
        // Reward backdrop: the alpha is the last pair of the hex (e.g. #000000ff), there
        // is no separate opacity field for it.
        val rewardBackdropColor = parseColor(styling?.rewardConfiguration?.backdropColor, Color.Black.copy(alpha = 0.6f))
        val rewardEnableBackdrop = content?.rewardConfiguration?.rewardEnableBackdrop ?: true

        val activeBackdropModifier = when {
            showResultDialog && rewardEnableBackdrop ->
                Modifier.background(rewardBackdropColor)
            showResultDialog && !rewardEnableBackdrop ->
                Modifier
            enableBackdrop ->
                Modifier.background(spinBackdropColor.copy(alpha = spinBackdropOpacity))
            else -> Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(activeBackdropModifier)
        ) {
            // Confetti overlay
            if (showConfetti) {
                ConfettiEffect(
                    modifier = Modifier.fillMaxSize(),
                    confettiConfig = styling?.rewardConfiguration?.confetti,
                    onComplete = { showConfetti = false }
                )
            }

            // Show reward content inline (replacing wheel) after spin completes
            if (showResultDialog && selectedSlice != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    RewardContent(
                        slice = selectedSlice!!,
                        rewardConfiguration = content?.rewardConfiguration,
                        rewardStyling = styling?.rewardConfiguration,
                        mainLink = spinTheWheelDetails.link,
                        onLinkClick = { link -> onCtaClick(link) },
                        onDismiss = {
                            showConfetti = false
                            onRewardDismissed()
                            if (spinsLeft <= 0) {
                                onDismiss()
                            }
                        }
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Close button with styling from backend
                    val crossButtonEnabled = crossButtonConfig?.enabled ?: true
                    val crossButtonSize = crossButtonConfig?.size ?: 30
                    val crossButtonAlignment = crossButtonConfig?.alignment ?: "right"
                    val crossFillColor = crossButtonConfig?.color?.fill ?: "#000000"
                    val crossCrossColor = crossButtonConfig?.color?.cross ?: "#FFFFFF"
                    val crossStrokeColor = crossButtonConfig?.color?.stroke ?: "#FFFFFF"
                    val crossButtonMargin = crossButtonConfig?.margin

                    if (crossButtonEnabled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    top = (crossButtonMargin?.top ?: 0).dp,
                                    bottom = (crossButtonMargin?.bottom ?: 0).dp,
                                    start = (crossButtonMargin?.left ?: 0).dp,
                                    end = (crossButtonMargin?.right ?: 0).dp
                                ),
                            horizontalArrangement = when (crossButtonAlignment.lowercase()) {
                                "left" -> Arrangement.Start
                                "center" -> Arrangement.Center
                                else -> Arrangement.End
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(crossButtonSize.dp)
                                    .shadow(4.dp, CircleShape)
                                    .background(
                                        parseColor(crossFillColor, Color.Black),
                                        CircleShape
                                    )
                                    .clickable { onDismiss() },
                                contentAlignment = Alignment.Center
                            ) {
                                CrossButton(
                                    config = createCrossButtonConfig(
                                        fillColorString = crossFillColor,
                                        crossColorString = crossCrossColor,
                                        strokeColorString = crossStrokeColor,
                                        size = crossButtonSize,
                                        imageUrl = crossButtonConfig?.image
                                    ),
                                    onClose = onDismiss
                                )
                            }
                        }
                    }

                    // Title with styling from backend (using direct popupTitle field)
                    val popupTitle = spinTheWheelDetails.popupTitle ?: ""
                    val titleMargin = titleStyle?.margin
                    if (popupTitle.isNotEmpty()) {
                        CommonText(
                            modifier = Modifier.fillMaxWidth(),
                            text = popupTitle,
                            styling = TextStyling(
                                color = titleStyle?.color ?: "#FFFFFF",
                                fontFamily = titleStyle?.fontFamily,
                                fontSize = titleStyle?.fontSize ?: 28,
                                textAlign = titleStyle?.textAlign ?: "center",
                                fontDecoration = listOfNotNull(
                                    titleStyle?.fontWeight ?: "bold",
                                    titleStyle?.fontStyle
                                ) + titleStyle?.fontDecoration.orEmpty(),
                                margin = CommonMargins(
                                    top = titleMargin?.top,
                                    bottom = titleMargin?.bottom,
                                    left = titleMargin?.left,
                                    right = titleMargin?.right
                                )
                            )
                        )
                    }

                    // Description with styling from backend (using direct popupDescription field)
                    val popupDescription = spinTheWheelDetails.popupDescription
                    if (!popupDescription.isNullOrEmpty()) {
                        val subtitleMargin = subtitleStyle?.margin
                        CommonText(
                            modifier = Modifier.fillMaxWidth(),
                            text = popupDescription,
                            lineHeight = ((subtitleStyle?.fontSize ?: 15) + 5).toFloat(),
                            styling = TextStyling(
                                color = subtitleStyle?.color ?: "#E6FFFFFF",
                                fontFamily = subtitleStyle?.fontFamily,
                                fontSize = subtitleStyle?.fontSize ?: 15,
                                textAlign = subtitleStyle?.textAlign ?: "center",
                                fontDecoration = listOfNotNull(
                                    subtitleStyle?.fontWeight,
                                    subtitleStyle?.fontStyle
                                ) + subtitleStyle?.fontDecoration.orEmpty(),
                                margin = CommonMargins(
                                    top = subtitleMargin?.top,
                                    bottom = subtitleMargin?.bottom,
                                    left = subtitleMargin?.left,
                                    right = subtitleMargin?.right
                                )
                            )
                        )
                    }

                    // Spins left indicator with styling from backend
                    val spinTextColor = parseColor(availableSpinTextStyle?.color, Color.White)
                    val spinTextAlign =
                        parseTextAlign(availableSpinTextStyle?.textAlign ?: "center")
                    val spinTextFontSize = availableSpinTextStyle?.fontSize ?: 14
                    val spinTextFontWeight =
                        parseFontWeight(availableSpinTextStyle?.fontWeight ?: "bold")
                    val spinTextFontStyle = parseFontStyle(availableSpinTextStyle?.fontStyle)

                    val availableSpinsMargin = availableSpinTextStyle?.margin
                    // Dynamic: always re-evaluated when spinsLeft changes.
                    // If backend provides a template (e.g. "{spinsLeft} spins left"), replace the placeholder.
                    // Otherwise, fall back to a default string built from the live spinsLeft value.
                    val availableSpinsTemplate =
                        content?.availableSpinsText?.takeIf { it.isNotBlank() }
                            ?: "Available Spins"
                    val hasSpinsPlaceholder = availableSpinsTemplate.contains("{spinsLeft}")
                    val availableSpinsLabel =
                        availableSpinsTemplate.replace("{spinsLeft}", spinsLeft.toString())

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = (availableSpinsMargin?.top ?: 0).dp,
                                bottom = (availableSpinsMargin?.bottom ?: 0).dp,
                                start = (availableSpinsMargin?.left ?: 0).dp,
                                end = (availableSpinsMargin?.right ?: 0).dp
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {

                        // Label from backend
                        CommonText(
                            text = availableSpinsLabel,
                            styling = TextStyling(
                                color = availableSpinTextStyle?.color ?: "#FFFFFF",
                                fontFamily = availableSpinTextStyle?.fontFamily,
                                fontSize = spinTextFontSize,
                                textAlign = availableSpinTextStyle?.textAlign ?: "center",
                                fontDecoration = listOfNotNull(
                                    availableSpinTextStyle?.fontWeight ?: "bold",
                                    availableSpinTextStyle?.fontStyle
                                ) + availableSpinTextStyle?.fontDecoration.orEmpty()
                            )
                        )

                        if (!hasSpinsPlaceholder) Spacer(modifier = Modifier.width(6.dp))

                        // Dynamic spins number — only when the label has no placeholder
                        if (!hasSpinsPlaceholder) CommonText(
                            text = spinsLeft.toString(),
                            styling = TextStyling(
                                color = availableSpinTextStyle?.color ?: "#FFFFFF",
                                fontFamily = availableSpinTextStyle?.fontFamily,
                                fontSize = spinTextFontSize,
                                fontDecoration = listOf("bold")
                            )
                        )
                    }

                    // Enhanced Wheel Container with glow effect
                    val wheelConfigStyling = mainStyling?.wheelConfiguration
                    val wheelBorderColor = parseColor(wheelConfigStyling?.borderColor, Color.White)
                    val wheelBorderWidth = wheelConfigStyling?.borderWidth ?: 5
                    // Clamp to the screen: an oversized dashboard value used to push the
                    // spin button out of reach on narrow or short screens.
                    val wheelSize = (wheelConfigStyling?.size ?: 350).dp
                        .coerceAtMost((LocalConfiguration.current.screenWidthDp - 32).dp)

                    Box(
                        modifier = Modifier
                            .size(wheelSize)
                            .shadow(
                                elevation = 30.dp,
                                shape = CircleShape,
                                clip = false
                            ),
                        contentAlignment = Alignment.Center
                    ) {
//                    // Shadow ring
//                    Box(
//                        modifier = Modifier
//                            .fillMaxSize()
//                            .shadow(20.dp, CircleShape)
//                    )

                        // Wheel
                        WheelView(
                            slices = slices,
                            rotation = rotation.value,
                            wheelImage = wheelConfigStyling?.backgroundImage,
                            wheelImageAlpha = wheelConfigStyling?.backgroundImageOpacity ?: 1f,
                            backgroundColor = wheelConfigStyling?.backgroundColor,
                            borderColor = wheelBorderColor,
                            borderWidth = wheelBorderWidth,
                            modifier = Modifier.fillMaxSize()
                        )
                    }


                    // Extract spin button styling
                    val buttonContainer = spinButtonStyle?.container
                    val buttonText = spinButtonStyle?.text
                    val buttonMargin = spinButtonStyle?.margin
                    val buttonBackgroundColor =
                        parseColor(buttonContainer?.backgroundColor, Color(0xFFFFB545))
                    val buttonBorderColor =
                        parseColor(buttonContainer?.borderColor, Color.Transparent)
                    val buttonBorderWidth = buttonContainer?.borderWidth ?: 0
                    val buttonCornerRadius = buttonContainer?.cornerRadius
                    val buttonHeight = buttonContainer?.height ?: 50
                    val buttonWidth = buttonContainer?.width ?: 160
                    val buttonFullWidth = buttonContainer?.fullWidth ?: false
                    val buttonTextColor = parseColor(buttonText?.color, Color.White)
                    val buttonTextSize = buttonText?.fontSize ?: 16
                    val buttonAlignment = buttonContainer?.alignment ?: "center"

                    val buttonShape = RoundedCornerShape(
                        topStart = (buttonCornerRadius?.topLeft ?: 12).dp,
                        topEnd = (buttonCornerRadius?.topRight ?: 12).dp,
                        bottomStart = (buttonCornerRadius?.bottomLeft ?: 12).dp,
                        bottomEnd = (buttonCornerRadius?.bottomRight ?: 12).dp
                    )


                    val isEnabled = spinsLeft > 0 && !isSpinning

                    val interactionSource = remember { MutableInteractionSource() }

                    Spacer(modifier = Modifier.height((buttonMargin?.top ?: 0).dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = (buttonMargin?.left ?: 0).dp,
                                end = (buttonMargin?.right ?: 0).dp
                            ),
                        horizontalArrangement = when (buttonAlignment.lowercase()) {
                            "left" -> Arrangement.Start
                            "right" -> Arrangement.End
                            else -> Arrangement.Center
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                //scale(if (isEnabled) pulseScale else 1f)
                                .then(
                                    if (buttonFullWidth) Modifier.fillMaxWidth()
                                    else Modifier.width(buttonWidth.dp)
                                )
                                .height(buttonHeight.dp)
                                .clip(buttonShape)
                                .background(
                                    if (isEnabled)
                                        Brush.verticalGradient(
                                            listOf(
                                                buttonBackgroundColor,
                                                buttonBackgroundColor.copy(alpha = 0.9f)
                                            )
                                        )
                                    else
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Gray.copy(alpha = 0.4f),
                                                Color.Gray.copy(alpha = 0.3f)
                                            )
                                        )
                                )
                                .border(
                                    if (buttonBorderWidth > 0) buttonBorderWidth.dp else 0.dp,
                                    buttonBorderColor,
                                    buttonShape
                                )
                                .clickable(
                                    enabled = isEnabled,
                                    interactionSource = interactionSource,
                                    indication = null
                                ) {
                                    performSpin()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSpinning) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.5.dp,
                                    color = buttonTextColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            } else {
                                CommonText(
                                    text = spinTheWheelDetails.spinButtonText ?: "SPIN",
                                    letterSpacing = 0.5f,
                                    styling = TextStyling(
                                        color = buttonText?.color ?: "#FFFFFF",
                                        fontFamily = buttonText?.fontFamily,
                                        fontSize = buttonTextSize,
                                        fontDecoration = listOfNotNull(
                                            buttonText?.fontWeight ?: "semibold",
                                            buttonText?.fontStyle
                                        ) + buttonText?.fontDecoration.orEmpty()
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height((buttonMargin?.bottom ?: 0).dp))
                }
            } // end else (wheel view)
        } // end outer Box
    } // end outer Dialog
} // end SpinTheWheel function

@Composable
private fun RewardContent(
    slice: WheelSlice,
    rewardConfiguration: SpinWheelRewardConfig?,
    rewardStyling: WheelRewardStyling?,
    mainLink: String?,
    onLinkClick: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var isCopied by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }

    // Reset copy state after 2 seconds
    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(2000)
            isCopied = false
        }
    }

    // Get the first reward from the rewards array (primary reward)
    val reward = slice.rewards?.firstOrNull()
    val rewardStylingFromSlice = reward?.styling

    val isWin = slice.noPrize != true
    val prizeName = slice.displayPrizeName() ?: if (isWin) "You Won!" else "No Prize"
    val couponCode = slice.displayCoupon()
    val subText = reward?.subText?.takeIf { it.isNotEmpty() } ?: slice.subText
    val buttonCtaText = reward?.buttonCta?.takeIf { it.isNotEmpty() }
        ?: slice.buttonCtaText?.takeIf { it.isNotEmpty() }
        ?: if (isWin) "Claim Reward" else "Try Again"
    val tncCtaText = reward?.tNcCta?.takeIf { it.isNotEmpty() }
        ?: slice.tncCtaText?.takeIf { it.isNotEmpty() }
        ?: "Terms & Conditions"
    val termsContent = reward?.termsNConditions?.takeIf { it.isNotEmpty() }
        ?: slice.termsAndConditions
    val rewardMedia = reward?.sliceRewardMedia?.takeIf { it.isNotEmpty() } ?: slice.sliceMedia
    val redirectLink = reward?.link?.takeIf { it.isNotEmpty() }
        ?: slice.link?.takeIf { it.isNotEmpty() }
        ?: mainLink

    // Extract per-slice styling or use defaults
    val priceLabelStyle = rewardStylingFromSlice?.priceLabel?.textStyle
    val subtitleTextStyle = rewardStylingFromSlice?.subtitleText?.textStyle
    val ctaStyling = rewardStylingFromSlice?.cta
    val couponCtaStyling = rewardStylingFromSlice?.couponCodeCta

    // Extract reward styling from global config
    val globalTitleStyle = rewardStyling?.title?.textStyle
    val globalSubtitleStyle = rewardStyling?.subtitle?.textStyle
    val crossButtonConfig = rewardStyling?.crossButton

    // Parse title styling (use per-slice if available, then global, then defaults)
    val titleColor = parseColor(
        priceLabelStyle?.color ?: globalTitleStyle?.color,
        if (isWin) Color(0xFF1A1A1A) else Color(0xFF424242)
    )
    val titleFontSize = priceLabelStyle?.fontSize ?: globalTitleStyle?.fontSize ?: 24
    val titleTextAlign =
        parseTextAlign(priceLabelStyle?.textAlign ?: globalTitleStyle?.textAlign ?: "center")
    val titleTextDecoration =
        parseTextDecoration(priceLabelStyle?.fontDecoration ?: globalTitleStyle?.fontDecoration)

    // Parse subtitle styling
    val subtitleColor = parseColor(
        subtitleTextStyle?.color ?: globalSubtitleStyle?.color,
        Color(0xFF6B7280)
    )
    val subtitleFontSize = subtitleTextStyle?.fontSize ?: globalSubtitleStyle?.fontSize ?: 14
    val subtitleTextAlign =
        parseTextAlign(subtitleTextStyle?.textAlign ?: globalSubtitleStyle?.textAlign ?: "center")

    // Cross button styling
    val crossButtonEnabled = crossButtonConfig?.enabled ?: true
    val crossButtonSize = crossButtonConfig?.size ?: 32
    val crossMargin = crossButtonConfig?.margin
    val crossButtonAlignment = crossButtonConfig?.alignment ?: "right"
    val crossButtonImage = crossButtonConfig?.image

    // CTA Button styling
    val ctaContainer = ctaStyling?.container
    val ctaText = ctaStyling?.text
    val ctaCornerRadius = ctaStyling?.cornerRadius
    val ctaMargin = ctaStyling?.margin
    val ctaBackgroundColor = parseColor(
        ctaContainer?.backgroundColor,
        if (isWin) Color(0xFF2563EB) else Color(0xFF6B7280)
    )
    val ctaBorderColor = parseColor(ctaContainer?.borderColor, Color.Transparent)
    val ctaBorderWidth = ctaContainer?.borderWidth ?: 0
    val ctaHeight = ctaContainer?.height ?: 52
    val ctaFullWidth = ctaContainer?.ctaFullWidth ?: true
    val ctaWidth = ctaContainer?.ctaWidth ?: 200
    val ctaTextColor = parseColor(ctaText?.color, Color.White)
    val ctaTextSize = ctaText?.fontSize ?: 16
    val ctaTextDecoration = parseTextDecoration(ctaText?.fontDecoration)
    val ctaShape = RoundedCornerShape(
        topStart = (ctaCornerRadius?.topLeft ?: 12).dp,
        topEnd = (ctaCornerRadius?.topRight ?: 12).dp,
        bottomStart = (ctaCornerRadius?.bottomLeft ?: 12).dp,
        bottomEnd = (ctaCornerRadius?.bottomRight ?: 12).dp
    )

    // Coupon code styling
    val couponContainer = couponCtaStyling?.container
    val couponText = couponCtaStyling?.text
    val couponCornerRadius = couponCtaStyling?.cornerRadius
    val couponBackgroundColor = parseColor(couponContainer?.backgroundColor, Color(0xFFFFF7ED))
    val couponBorderColor = parseColor(couponContainer?.borderColor, Color(0xFFFD5F03))
    val couponBorderWidth = couponContainer?.borderWidth ?: 1
    val couponTextColor = parseColor(couponText?.color, Color(0xFFFD5F03))
    val couponTextSize = couponText?.fontSize ?: 14
    val couponShape = RoundedCornerShape(
        topStart = (couponCornerRadius?.topLeft ?: 8).dp,
        topEnd = (couponCornerRadius?.topRight ?: 8).dp,
        bottomStart = (couponCornerRadius?.bottomLeft ?: 8).dp,
        bottomEnd = (couponCornerRadius?.bottomRight ?: 8).dp
    )

    val couponDecorations = couponText?.fontDecoration


    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {


        // CARD — scale + fade
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Box(
                modifier = Modifier.wrapContentSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                // 🔥 MAIN CONTENT
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ✅ REWARD TITLE — outside card
                    rewardConfiguration?.rewardPopupTitle?.takeIf { it.isNotBlank() }
                        ?.let { title ->
                            CommonText(
                                modifier = Modifier.fillMaxWidth(),
                                text = title,
                                styling = TextStyling(
                                    color = globalTitleStyle?.color ?: "#FF6B35",
                                    fontFamily = globalTitleStyle?.fontFamily,
                                    fontSize = globalTitleStyle?.fontSize ?: 22,
                                    textAlign = globalTitleStyle?.textAlign ?: "center",
                                    fontDecoration = listOfNotNull(
                                        globalTitleStyle?.fontWeight ?: "bold",
                                        globalTitleStyle?.fontStyle
                                    ) + globalTitleStyle?.fontDecoration.orEmpty(),
                                    margin = CommonMargins(
                                        top = globalTitleStyle?.margin?.top,
                                        bottom = globalTitleStyle?.margin?.bottom,
                                        left = globalTitleStyle?.margin?.left,
                                        right = globalTitleStyle?.margin?.right
                                    )
                                )
                            )
                        }

                    // ✅ REWARD SUBTITLE — outside card
                    rewardConfiguration?.rewardPopupDescription?.takeIf { it.isNotBlank() }
                        ?.let { subtitle ->
                            CommonText(
                                modifier = Modifier.fillMaxWidth(),
                                text = subtitle,
                                styling = TextStyling(
                                    color = globalSubtitleStyle?.color ?: "#808080",
                                    fontFamily = globalSubtitleStyle?.fontFamily,
                                    fontSize = globalSubtitleStyle?.fontSize ?: 14,
                                    textAlign = globalSubtitleStyle?.textAlign ?: "center",
                                    fontDecoration = listOfNotNull(
                                        globalSubtitleStyle?.fontWeight,
                                        globalSubtitleStyle?.fontStyle
                                    ) + globalSubtitleStyle?.fontDecoration.orEmpty(),
                                    margin = CommonMargins(
                                        top = globalSubtitleStyle?.margin?.top,
                                        bottom = globalSubtitleStyle?.margin?.bottom,
                                        left = globalSubtitleStyle?.margin?.left,
                                        right = globalSubtitleStyle?.margin?.right
                                    )
                                )
                            )
                        }

                    // 🔥 CARD starts here
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .shadow(32.dp, RoundedCornerShape(28.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(28.dp))
                                .background(parseColor(rewardStyling?.cardBackgroundColor, Color.White)),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {


                            // Brand band with the prize artwork in a circular badge
                            // that straddles its lower edge. The badge keeps the artwork
                            // a consistent shape whatever the asset's aspect ratio is.
                            val bandHeight = 96.dp
                            // A landscape tile, because uploaded prize art is rectangular
                            // — a rectangle inside a circle always leaves gaps at the
                            // corners and reads as a mistake.
                            val badgeWidth = 156.dp
                            val badgeHeight = 104.dp
                            val badgeShape = RoundedCornerShape(18.dp)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(bandHeight + badgeHeight / 2)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(bandHeight)
                                        .background(
                                            if (isWin) {
                                                Brush.linearGradient(
                                                    colors = listOf(
                                                        Color(0xFF667EEA),
                                                        Color(0xFF764BA2)
                                                    )
                                                )
                                            } else {
                                                Brush.linearGradient(
                                                    colors = listOf(
                                                        Color(0xFF9CA3AF),
                                                        Color(0xFF6B7280)
                                                    )
                                                )
                                            }
                                        )
                                )

                                // Close sits on the band, inset from the card edge. It used
                                // to be pulled onto the card's rounded corner by a negative
                                // offset taken from its bottom margin.
                                if (crossButtonEnabled) {
                                    Box(
                                        modifier = Modifier
                                            .align(
                                                when (crossButtonAlignment.lowercase()) {
                                                    "left" -> Alignment.TopStart
                                                    "center" -> Alignment.TopCenter
                                                    else -> Alignment.TopEnd
                                                }
                                            )
                                            .padding(
                                                top = ((crossMargin?.top ?: 0) + 10).dp,
                                                start = ((crossMargin?.left ?: 0) + 10).dp,
                                                end = ((crossMargin?.right ?: 0) + 10).dp
                                            )
                                    ) {
                                        CrossButton(
                                            config = createCrossButtonConfig(
                                                fillColorString =
                                                    crossButtonConfig?.color?.fill ?: "#FFFFFF33",
                                                crossColorString =
                                                    crossButtonConfig?.color?.cross ?: "#FFFFFF",
                                                strokeColorString =
                                                    crossButtonConfig?.color?.stroke ?: "#FFFFFF33",
                                                size = crossButtonSize,
                                                imageUrl = crossButtonImage
                                            ),
                                            onClose = onDismiss
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .width(badgeWidth)
                                        .height(badgeHeight)
                                        .shadow(10.dp, badgeShape)
                                        .clip(badgeShape)
                                        .background(Color.White)
                                        .padding(6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!rewardMedia.isNullOrBlank()) {
                                        val badgeW = with(LocalDensity.current) {
                                            badgeWidth.roundToPx()
                                        }
                                        val badgeH = with(LocalDensity.current) {
                                            badgeHeight.roundToPx()
                                        }
                                        // Crop fills the tile, so a rectangular upload has
                                        // no empty corners. Shared with the scratch card so
                                        // GIF and Lottie prizes animate.
                                        RewardMedia(
                                            bannerImageUrl = rewardMedia,
                                            targetWidthPx = badgeW,
                                            targetHeightPx = badgeH,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(13.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text(
                                            text = if (isWin) "🎁" else "✨",
                                            fontSize = 40.sp
                                        )
                                    }
                                }
                            }

                            val priceLabelMargin = priceLabelStyle?.margin

                            // Content section
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 22.dp)
                                    .padding(top = 14.dp, bottom = 22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Prize name
                                CommonText(
                                    text = prizeName,
                                    lineHeight = (titleFontSize + 6).toFloat(),
                                    styling = TextStyling(
                                        color = priceLabelStyle?.color
                                            ?: globalTitleStyle?.color
                                            ?: if (isWin) "#1A1A1A" else "#424242",
                                        fontFamily = priceLabelStyle?.fontFamily
                                            ?: globalTitleStyle?.fontFamily,
                                        fontSize = titleFontSize,
                                        textAlign = priceLabelStyle?.textAlign
                                            ?: globalTitleStyle?.textAlign ?: "center",
                                        fontDecoration = listOf("bold") +
                                            (priceLabelStyle?.fontDecoration
                                                ?: globalTitleStyle?.fontDecoration).orEmpty(),
                                        margin = CommonMargins(
                                            top = priceLabelMargin?.top,
                                            bottom = priceLabelMargin?.bottom,
                                            left = priceLabelMargin?.left,
                                            right = priceLabelMargin?.right
                                        )
                                    )
                                )

                                val subtitleMargin = subtitleTextStyle?.margin

                                // Sub text / description
                                subText?.takeIf { it.isNotEmpty() }?.let { text ->
                                    CommonText(
                                        text = text,
                                        lineHeight = (subtitleFontSize + 5).toFloat(),
                                        styling = TextStyling(
                                            color = subtitleTextStyle?.color
                                                ?: globalSubtitleStyle?.color ?: "#6B7280",
                                            fontFamily = subtitleTextStyle?.fontFamily
                                                ?: globalSubtitleStyle?.fontFamily,
                                            fontSize = subtitleFontSize,
                                            textAlign = subtitleTextStyle?.textAlign
                                                ?: globalSubtitleStyle?.textAlign ?: "center",
                                            fontDecoration = (subtitleTextStyle?.fontDecoration
                                                ?: globalSubtitleStyle?.fontDecoration).orEmpty(),
                                            margin = CommonMargins(
                                                top = subtitleMargin?.top,
                                                bottom = subtitleMargin?.bottom,
                                                left = subtitleMargin?.left,
                                                right = subtitleMargin?.right
                                            )
                                        )
                                    )
                                }


                                // Coupon Code Section - Modern dashed border style
                                couponCode?.takeIf { it.isNotEmpty() && isWin }?.let { code ->

                                    // Coupon code card
                                    val couponMargin = couponCtaStyling?.margin
                                    val couponAlignment = couponContainer?.alignment ?: "center"

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = when (couponAlignment.lowercase()) {
                                            "left" -> Arrangement.Start
                                            "right" -> Arrangement.End
                                            else -> Arrangement.Center
                                        }
                                    ) {
                                    val dashStroke = with(LocalDensity.current) {
                                        couponBorderWidth.dp.toPx().coerceAtLeast(1f)
                                    }
                                    val dashRadius = with(LocalDensity.current) {
                                        (couponCornerRadius?.topLeft ?: 8).dp.toPx()
                                    }
                                    Row(
                                        modifier = Modifier
                                            .padding(
                                                top = (couponMargin?.top ?: 0).dp,
                                                bottom = (couponMargin?.bottom ?: 0).dp,
                                                start = (couponMargin?.left ?: 0).dp,
                                                end = (couponMargin?.right ?: 0).dp
                                            )
                                            .then(
                                                if (couponContainer?.ctaFullWidth == true)
                                                    Modifier.fillMaxWidth()
                                                else
                                                    Modifier.width((couponContainer?.ctaWidth ?: 200).dp)
                                            )
                                            .clip(couponShape)
                                            .background(couponBackgroundColor)
                                            // Dashed outline — the ticket look. A solid
                                            // border reads as an input field instead.
                                            .drawBehind {
                                                drawRoundRect(
                                                    color = couponBorderColor,
                                                    style = Stroke(
                                                        width = dashStroke,
                                                        pathEffect = PathEffect.dashPathEffect(
                                                            floatArrayOf(
                                                                dashStroke * 6f,
                                                                dashStroke * 5f
                                                            )
                                                        )
                                                    ),
                                                    cornerRadius = CornerRadius(dashRadius, dashRadius)
                                                )
                                            }
                                            .clickable {
                                                try {
                                                    val clipboard =
                                                        context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                                    clipboard?.setPrimaryClip(
                                                        android.content.ClipData.newPlainText(
                                                            "Coupon Code",
                                                            code
                                                        )
                                                    )
                                                    isCopied = true
                                                } catch (_: Exception) {
                                                }
                                            }
                                            .padding(horizontal = 18.dp, vertical = 13.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CommonText(
                                            modifier = Modifier.weight(1f),
                                            text = code.uppercase(),
                                            letterSpacing = 2f,
                                            styling = TextStyling(
                                                color = couponText?.color ?: "#FD5F03",
                                                fontFamily = couponText?.fontFamily,
                                                fontSize = couponTextSize,
                                                textAlign = "left",
                                                fontDecoration = couponDecorations.orEmpty()
                                            )
                                        )

                                        // Copy affordance: the two-sheets glyph, drawn
                                        // rather than pulled from material-icons-extended
                                        // (only the core icon set is a dependency here).
                                        val glyphColor =
                                            if (isCopied) Color(0xFF10B981) else couponTextColor
                                        Canvas(modifier = Modifier.size(18.dp)) {
                                            val r = size.minDimension * 0.12f
                                            val w = size.minDimension * 0.62f
                                            val line = size.minDimension * 0.09f
                                            drawRoundRect(
                                                color = glyphColor,
                                                topLeft = Offset(0f, size.height - w),
                                                size = Size(w, w),
                                                cornerRadius = CornerRadius(r, r),
                                                style = Stroke(width = line)
                                            )
                                            drawRoundRect(
                                                color = glyphColor,
                                                topLeft = Offset(size.width - w, 0f),
                                                size = Size(w, w),
                                                cornerRadius = CornerRadius(r, r),
                                                style = Stroke(width = line)
                                            )
                                        }
                                    } // end inner coupon Row
                                    } // end alignment Row


                                }

                                // CTA Button with per-slice styling
                                Button(
                                    onClick = {
                                        if (!redirectLink.isNullOrEmpty()) onLinkClick(redirectLink)
                                        onDismiss()
                                    },
                                    modifier = Modifier
                                        .padding(
                                            top = (ctaMargin?.top ?: 0).coerceAtLeast(0).dp,
                                            bottom = (ctaMargin?.bottom ?: 0).coerceAtLeast(0).dp,
                                            start = (ctaMargin?.left ?: 0).coerceAtLeast(0).dp,
                                            end = (ctaMargin?.right ?: 0).coerceAtLeast(0).dp
                                        )
                                        .then(
                                            if (ctaFullWidth) Modifier.fillMaxWidth()
                                            else Modifier.width(ctaWidth.dp)
                                        )
                                        .height(ctaHeight.dp)
                                        .then(
                                            if (ctaBorderWidth > 0)
                                                Modifier.border(
                                                    ctaBorderWidth.dp,
                                                    ctaBorderColor,
                                                    ctaShape
                                                )
                                            else Modifier
                                        ),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ctaBackgroundColor
                                    ),
                                    shape = ctaShape,
                                    elevation = ButtonDefaults.buttonElevation(
                                        defaultElevation = 6.dp,
                                        pressedElevation = 2.dp
                                    ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    CommonText(
                                        text = buttonCtaText,
                                        letterSpacing = 0.5f,
                                        styling = TextStyling(
                                            color = ctaText?.color ?: "#FFFFFF",
                                            fontFamily = ctaText?.fontFamily,
                                            fontSize = ctaTextSize,
                                            fontDecoration = listOf("semibold") +
                                                ctaText?.fontDecoration.orEmpty()
                                        )
                                    )
                                }

                                // Terms & Conditions link
                                val hasTermsContent = !termsContent.isNullOrEmpty()
                                if (isWin && hasTermsContent) {
                                    Text(
                                        text = tncCtaText,
                                        fontSize = 13.sp,
                                        color = Color(0xFF6B7280).copy(alpha = 0.9f),
                                        fontWeight = FontWeight.Medium,
                                        textDecoration = TextDecoration.Underline,
                                        modifier = Modifier.clickable {
                                            showTermsDialog = true
                                        }
                                    )
                                }
                            }
                        } // end card content Column
                    } // end card Box
                } // end main content Column
            } // end wrapContentSize Box
        }
    }

    // Terms & Conditions Dialog - Modern design
    if (showTermsDialog && !termsContent.isNullOrEmpty()) {
        Dialog(
            onDismissRequest = { showTermsDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .fillMaxHeight(0.75f)
                        .shadow(24.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .padding(horizontal = 24.dp)
                        .padding(top = 24.dp, bottom = 16.dp)
                ) {
                    // Fixed header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Terms & Conditions",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1F2937)
                        )
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF3F4F6))
                                .clickable { showTermsDialog = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✕",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF6B7280)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Fixed divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFFE5E7EB))
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Scrollable terms content
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        item {
                            Text(
                                text = termsContent,
                                fontSize = 14.sp,
                                color = Color(0xFF4B5563),
                                lineHeight = 22.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            )
                        }
                    }

                    // Fixed divider above button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFFE5E7EB))
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Fixed "Got it" button
                    Button(
                        onClick = { showTermsDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF667EEA)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Got it",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.M)
private fun triggerHapticFeedback(context: android.content.Context, duration: Long = 100) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator?.vibrate(
                VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            val vibrator =
                context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(
                VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator =
                context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
            @Suppress("DEPRECATION")
            vibrator?.vibrate(duration)
        }
    } catch (_: Exception) {
        // Haptic feedback not available
    }
}

/**
 * Confetti for a win, in the four types the dashboard offers:
 *
 *  - none        nothing is drawn
 *  - basic       a light fall of paper from the top edge
 *  - random      a heavier fall, thrown in from both sides as well as above
 *  - fireworks   three staggered bursts that radiate from a point and then drop
 *
 * Colours come from the dashboard fill / cross / stroke slots; blank slots fall back to
 * a bright default so a half-configured campaign still looks deliberate. All distances
 * are dp, so the effect is the same physical size on every screen.
 */
@Composable
private fun ConfettiEffect(
    modifier: Modifier = Modifier,
    confettiConfig: com.appversal.appstorys.api.WheelConfettiConfig? = null,
    onComplete: () -> Unit = {}
) {
    val style = confettiConfig?.selectedStyle?.lowercase()?.trim() ?: "basic"
    if (style == "none") {
        LaunchedEffect(Unit) { onComplete() }
        return
    }

    val colors = remember(confettiConfig) {
        listOfNotNull(
            confettiConfig?.color?.fill,
            confettiConfig?.color?.cross,
            confettiConfig?.color?.stroke
        ).mapNotNull { raw -> parseColor(raw).takeIf { it != Color.Unspecified } }
            .ifEmpty {
                listOf(
                    Color(0xFFFFD700), Color(0xFFFF4081), Color(0xFF00BCD4),
                    Color(0xFF4CAF50), Color(0xFF9C27B0), Color(0xFFFF9800)
                )
            }
    }

    val density = LocalDensity.current
    val particles = remember { mutableStateListOf<ConfettiParticle>() }
    var tick by remember { mutableLongStateOf(0L) }
    var canvas by remember { mutableStateOf(Size.Zero) }

    LaunchedEffect(style, canvas.width == 0f) {
        if (canvas.width <= 0f) return@LaunchedEffect
        particles.clear()
        particles += spawnConfetti(style, canvas, colors, density)

        val startedAt = System.currentTimeMillis()
        var previous = startedAt
        while (particles.any { !it.isDead }) {
            val now = System.currentTimeMillis()
            // clamp so a stalled frame cannot teleport everything off-screen
            val dt = ((now - previous).coerceIn(1L, 48L)) / 1000f
            previous = now
            particles.forEach { it.update(dt) }
            tick = now
            delay(16)
            if (now - startedAt > 6000) break     // hard stop, whatever happens
        }
        particles.clear()
        onComplete()
    }

    Canvas(modifier = modifier) {
        canvas = size
        tick.let { }                              // read so each tick redraws
        particles.forEach { p ->
            if (p.isDead) return@forEach
            rotate(degrees = p.rotationDegrees, pivot = p.position) {
                drawRect(
                    color = p.color.copy(alpha = p.alpha),
                    topLeft = Offset(p.position.x - p.width / 2f, p.position.y - p.height / 2f),
                    size = Size(p.width, p.height)
                )
            }
        }
    }
}

/**
 * Builds the whole burst up front — each piece carries its own delay, so a style is
 * described by where its pieces start and how they are thrown rather than by a spawn
 * loop. See [ConfettiEffect] for what each style looks like.
 */
private fun spawnConfetti(
    style: String,
    canvas: Size,
    colors: List<Color>,
    density: androidx.compose.ui.unit.Density
): List<ConfettiParticle> {
    val dp = { v: Float -> with(density) { v.dp.toPx() } }
    val pieces = mutableListOf<ConfettiParticle>()

    fun piece(
        position: Offset,
        velocity: Offset,
        life: Float,
        delay: Float = 0f,
        gravity: Float = 900f
    ) = ConfettiParticle(
        position = position,
        velocity = velocity,
        color = colors.random(),
        width = dp(5f + Random.nextFloat() * 5f),
        height = dp(8f + Random.nextFloat() * 6f),
        rotation = Random.nextFloat() * 360f,
        rotationSpeed = (Random.nextFloat() - 0.5f) * 540f,
        lifespan = life,
        startDelay = delay,
        gravity = dp(gravity)
    )

    when (style) {
        "basic" -> repeat(70) {
            pieces += piece(
                position = Offset(Random.nextFloat() * canvas.width, -dp(20f)),
                velocity = Offset(dp((Random.nextFloat() - 0.5f) * 90f), dp(120f + Random.nextFloat() * 140f)),
                life = 2.6f,
                delay = Random.nextFloat() * 0.9f,
                gravity = 500f
            )
        }

        "random" -> {
            repeat(70) {
                pieces += piece(
                    position = Offset(Random.nextFloat() * canvas.width, -dp(20f)),
                    velocity = Offset(dp((Random.nextFloat() - 0.5f) * 260f), dp(140f + Random.nextFloat() * 220f)),
                    life = 3f,
                    delay = Random.nextFloat() * 0.8f,
                    gravity = 700f
                )
            }
            // thrown in from the sides as well, so it does not read as plain rain
            repeat(30) {
                val fromLeft = Random.nextBoolean()
                pieces += piece(
                    position = Offset(
                        if (fromLeft) -dp(20f) else canvas.width + dp(20f),
                        canvas.height * (0.35f + Random.nextFloat() * 0.3f)
                    ),
                    velocity = Offset(
                        dp((if (fromLeft) 1f else -1f) * (250f + Random.nextFloat() * 200f)),
                        dp(-(200f + Random.nextFloat() * 200f))
                    ),
                    life = 3f,
                    delay = Random.nextFloat() * 0.5f,
                    gravity = 700f
                )
            }
        }

        else -> {   // fireworks
            repeat(3) { burst ->
                val origin = Offset(
                    canvas.width * (0.25f + Random.nextFloat() * 0.5f),
                    canvas.height * (0.2f + Random.nextFloat() * 0.25f)
                )
                val delay = burst * 0.45f
                repeat(45) { i ->
                    val angle = (i / 45f) * 2f * Math.PI.toFloat() +
                        Random.nextFloat() * 0.15f
                    val speed = 260f + Random.nextFloat() * 260f
                    pieces += piece(
                        position = origin,
                        velocity = Offset(
                            dp(kotlin.math.cos(angle) * speed),
                            dp(kotlin.math.sin(angle) * speed)
                        ),
                        life = 2.4f,
                        delay = delay,
                        gravity = 800f
                    )
                }
            }
        }
    }
    return pieces
}

private class ConfettiParticle(
    var position: Offset,
    var velocity: Offset,
    val color: Color,
    val width: Float,
    val height: Float,
    rotation: Float,
    private val rotationSpeed: Float,
    private val lifespan: Float,
    private val startDelay: Float,
    private val gravity: Float
) {
    var rotationDegrees = rotation
        private set
    private var age = 0f

    fun update(dt: Float) {
        age += dt
        if (age < startDelay) return
        velocity = Offset(velocity.x, velocity.y + gravity * dt)
        position += velocity * dt
        rotationDegrees += rotationSpeed * dt
    }

    /** Fades out over the last third of its life. */
    val alpha: Float
        get() {
            val lived = (age - startDelay) / lifespan
            return ((1f - lived) * 3f).coerceIn(0f, 1f)
        }

    val isDead: Boolean get() = age - startDelay >= lifespan

}

// ─────────────────────────────────────────────────────────────────
// SharedPreferences helpers — mirrors saveScratchedCampaigns pattern
// ─────────────────────────────────────────────────────────────────

/**
 * Persists the remaining spin count for a given campaign to SharedPreferences.
 * Key format: "spin_count_<campaignId>"
 */
fun saveSpinCount(
    campaignId: String,
    count: Int,
    sharedPreferences: android.content.SharedPreferences
) {
    sharedPreferences.edit().putInt("spin_count_$campaignId", count).apply()
}

/**
 * Retrieves the persisted spin count for a campaign.
 * Returns null if no value has been stored yet (i.e. first launch for this campaign).
 */
fun getSpinCount(
    campaignId: String,
    sharedPreferences: android.content.SharedPreferences
): Int? {
    return if (sharedPreferences.contains("spin_count_$campaignId")) {
        sharedPreferences.getInt("spin_count_$campaignId", 0)
    } else {
        null
    }
}

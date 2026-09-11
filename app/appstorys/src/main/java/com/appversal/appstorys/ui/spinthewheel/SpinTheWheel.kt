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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.layout.onSizeChanged
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
import coil.imageLoader
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
/**
 * How long the wheel waits for its slice artwork before showing regardless.
 * A dead CDN must delay the wheel, never suppress it.
 */
internal const val STW_MEDIA_TIMEOUT_MS = 3000L

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

    // ── Hold the wheel back until its prize artwork has arrived ───────────────
    // The first fetch of each slice image starts when the wheel is composed, so
    // without this the wheel paints six empty tiles and the prizes pop in one at
    // a time. Measured cold: no artwork in the wheel's first frame, all six in
    // the next. Waiting is the lesser of the two — and it is bounded, so a dead
    // CDN delays the wheel rather than suppressing it.
    var mediaReady by remember(spinTheWheelDetails) { mutableStateOf(false) }
    val sliceMediaPx = with(LocalDensity.current) {
        // The slice slot is 15% of the wheel, and the wheel fills the dialog.
        (LocalConfiguration.current.screenWidthDp * 0.15f).dp.roundToPx()
    }
    LaunchedEffect(spinTheWheelDetails) {
        val urls = slices
            .flatMap {
                listOfNotNull(it.sliceMedia, it.rewards?.firstOrNull()?.sliceRewardMedia)
            }
            .filter { it.isNotBlank() }
            .distinct()
        if (urls.isNotEmpty()) {
            val loader = context.imageLoader
            withTimeoutOrNull(STW_MEDIA_TIMEOUT_MS) {
                // fully qualified: `coroutineScope` above is a value, not this.
                kotlinx.coroutines.coroutineScope {
                    urls.map { url ->
                        async {
                            runCatching {
                                loader.execute(
                                    ImageRequest.Builder(context)
                                        .data(url)
                                        .size(sliceMediaPx, sliceMediaPx)
                                        .build()
                                )
                            }
                        }
                    }.awaitAll()
                }
            } ?: Log.w(
                "AppStorys",
                "STW artwork not ready in ${STW_MEDIA_TIMEOUT_MS}ms, showing the wheel anyway"
            )
        }
        mediaReady = true
    }
    if (!mediaReady) return
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
            // Dismissing the reward ends the campaign, spins left or not — the
            // same as tapping its close button.
            showConfetti = false
            if (showResultDialog) onRewardDismissed()
            onDismiss()
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
        // Reward backdrop: colour and opacity are two fields, the same pair the spin
        // backdrop above uses. 60 keeps the old default when the dashboard sends none.
        val rewardBackdropOpacity =
            (styling?.rewardConfiguration?.backdropOpacity ?: 60).coerceIn(0, 100) / 100f
        val rewardBackdropColor =
            parseColor(styling?.rewardConfiguration?.backdropColor, Color.Black)
                .copy(alpha = rewardBackdropOpacity)
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
                            // Clear the spin first so a re-open starts clean, then
                            // take the whole campaign down regardless of spins left.
                            onRewardDismissed()
                            onDismiss()
                        }
                    )
                }
            } else {
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

                // Same shape as the reward screen: the wheel and its headings are
                // centred on their OWN height, and the spin button flows below them.
                // Centring every child together meant spinButton.margin.top made the
                // block taller and the slack was split evenly, so raising the gap
                // pushed the WHEEL up as much as it pushed the button down.
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val wheelViewport = this@BoxWithConstraints.maxHeight
                    var wheelHeaderHeight by remember { mutableStateOf(0.dp) }
                    val wheelHeaderDensity = LocalDensity.current
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // The button and its bottom margin have to fit under the
                        // wheel, so the wheel centres no lower than that allows.
                        val buttonBlock = buttonHeight.dp + (buttonMargin?.bottom ?: 0).dp
                        val wheelHeaderTop = minOf(
                            (wheelViewport - wheelHeaderHeight) / 2,
                            wheelViewport - wheelHeaderHeight - buttonBlock
                        ).coerceAtLeast(0.dp)
                        // Capped rather than scrolled away, as on the reward screen.
                        val buttonGap = (buttonMargin?.top ?: 0).dp
                            .coerceAtMost(
                                (wheelViewport - wheelHeaderTop - wheelHeaderHeight
                                    - buttonBlock).coerceAtLeast(0.dp)
                            )

                        Spacer(modifier = Modifier.height(wheelHeaderTop))
                        Column(
                            modifier = Modifier.onSizeChanged {
                                wheelHeaderHeight =
                                    with(wheelHeaderDensity) { it.height.toDp() }
                            },
                            horizontalAlignment = Alignment.CenterHorizontally
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
                                // Head for the landing angle, so labels are upright there
                                // from the first frame rather than snapping at the end.
                                restAngle = spinRun?.toAngle ?: rotation.value,
                                wheelImage = wheelConfigStyling?.backgroundImage,
                                wheelImageAlpha = wheelConfigStyling?.backgroundImageOpacity ?: 1f,
                                backgroundColor = wheelConfigStyling?.backgroundColor,
                                borderColor = wheelBorderColor,
                                borderWidth = wheelBorderWidth,
                                modifier = Modifier.fillMaxSize()
                            )
                        }


                        } // end headings + wheel, the part that stays put


                        val isEnabled = spinsLeft > 0 && !isSpinning

                        val interactionSource = remember { MutableInteractionSource() }

                        Spacer(modifier = Modifier.height(buttonGap))

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
                                    // The dashboard's colour whatever the state — spinning
                                    // greyed it out to a hardcoded Color.Gray, which no
                                    // dashboard setting could reach.
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                buttonBackgroundColor,
                                                buttonBackgroundColor.copy(alpha = 0.9f)
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

    val titleFontSize = priceLabelStyle?.fontSize ?: globalTitleStyle?.fontSize ?: 24
    val subtitleFontSize = subtitleTextStyle?.fontSize ?: globalSubtitleStyle?.fontSize ?: 14

    // Cross button styling
    val crossButtonEnabled = crossButtonConfig?.enabled ?: true
    val crossButtonSize = crossButtonConfig?.size ?: 32
    val crossMargin = crossButtonConfig?.margin
    val crossButtonAlignment = crossButtonConfig?.alignment ?: "right"
    val crossButtonImage = crossButtonConfig?.image

    // CTA Button styling. The dashboard configures ONE CTA under Reward
    // Configuration that every slice shares; the per-slice block is what older
    // payloads carried, so it stays as the fallback rather than the source.
    val commonCta = rewardStyling?.cta
    val ctaContainer = commonCta?.container
    val legacyContainer = ctaStyling?.container
    val ctaText = commonCta?.text
    val legacyText = ctaStyling?.text
    val ctaCornerRadius =
        commonCta?.cornerRadius ?: ctaContainer?.cornerRadius ?: ctaStyling?.cornerRadius
    val ctaMargin = commonCta?.margin ?: ctaStyling?.margin
    val ctaBackgroundColor = parseColor(
        ctaContainer?.backgroundColor ?: legacyContainer?.backgroundColor,
        if (isWin) Color(0xFF2563EB) else Color(0xFF6B7280)
    )
    val ctaBorderColor = parseColor(
        ctaContainer?.borderColor ?: legacyContainer?.borderColor,
        Color.Transparent
    )
    val ctaBorderWidth = ctaContainer?.borderWidth ?: legacyContainer?.borderWidth ?: 0
    val ctaHeight = ctaContainer?.height ?: legacyContainer?.height ?: 52
    val ctaFullWidth = ctaContainer?.fullWidth ?: legacyContainer?.ctaFullWidth ?: true
    val ctaWidth = ctaContainer?.width ?: legacyContainer?.ctaWidth ?: 200
    val ctaTextSize = ctaText?.fontSize ?: legacyText?.fontSize ?: 16
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


    // Full-screen reward. The dashboard is dropping its pop-up mode, so this is the
    // only layout there is — nothing branches on rewardDisplayMode.
    val cardWidth = 0.9f

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
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
            // The headings and card are centred on their OWN height, and the CTA
            // flows below them. Centring the whole lot together (Arrangement.Center
            // over every child) meant a bigger cta.margin.top made the block taller
            // and the extra space was split evenly, so raising the gap pushed the
            // card UP as much as it pushed the CTA down and the CTA could never
            // reach the bottom. Anchoring the card keeps the gap doing only what it
            // says. The leading Spacer is what does the centring, so the CTA below
            // still occupies real scrollable space and stays reachable when the gap
            // is large enough to push it past the fold.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val viewportHeight = this@BoxWithConstraints.maxHeight
                var headerHeight by remember { mutableStateOf(0.dp) }
                val headerDensity = LocalDensity.current
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // The CTA and its bottom margin have to fit under the headings,
                    // so the headings centre no lower than that leaves room for.
                    val ctaBlock = ctaHeight.dp + (ctaMargin?.bottom ?: 0).dp
                    val headerTop = minOf(
                        (viewportHeight - headerHeight) / 2,
                        viewportHeight - headerHeight - ctaBlock
                    ).coerceAtLeast(0.dp)
                    // A gap wider than the screen can hold is capped rather than
                    // scrolled away: the dashboard value is a request, the screen
                    // has the final say.
                    val ctaGap = (ctaMargin?.top ?: 0).coerceAtLeast(0).dp
                        .coerceAtMost(
                            (viewportHeight - headerTop - headerHeight - ctaBlock)
                                .coerceAtLeast(0.dp)
                        )

                    Spacer(modifier = Modifier.height(headerTop))
                    Column(
                        modifier = Modifier.onSizeChanged {
                            headerHeight = with(headerDensity) { it.height.toDp() }
                        },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                    // Close — placed the way the wheel places its own: a row above the
                    // content, aligned by the dashboard's alignment, margins honoured.
                    if (crossButtonEnabled) {
                        val crossFillColor = crossButtonConfig?.color?.fill ?: "#000000"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    top = (crossMargin?.top ?: 0).dp,
                                    bottom = (crossMargin?.bottom ?: 0).dp,
                                    start = (crossMargin?.left ?: 0).dp,
                                    end = (crossMargin?.right ?: 0).dp
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
                                    .background(parseColor(crossFillColor, Color.Black), CircleShape)
                                    .clickable { onDismiss() },
                                contentAlignment = Alignment.Center
                            ) {
                                CrossButton(
                                    config = createCrossButtonConfig(
                                        fillColorString = crossFillColor,
                                        crossColorString = crossButtonConfig?.color?.cross ?: "#FFFFFF",
                                        strokeColorString =
                                            crossButtonConfig?.color?.stroke ?: "#FFFFFF",
                                        size = crossButtonSize,
                                        imageUrl = crossButtonImage
                                    ),
                                    onClose = onDismiss
                                )
                            }
                        }
                    }

                    // Campaign-level heading — styling.rewardConfiguration.title
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

                    // Campaign-level sub-heading — styling.rewardConfiguration.subtitle
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

                    // ── CARD ───────────────────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(cardWidth)
                            .shadow(32.dp, RoundedCornerShape(28.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(28.dp))
                                .background(
                                    parseColor(
                                        rewardStyling?.cardBodyColor
                                            ?: rewardStyling?.cardBackgroundColor,
                                        Color.White
                                    )
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Brand band with the prize artwork straddling its lower edge.
                            // The artwork keeps its own shape — fixed width, height from the
                            // image — capped, the same way the scratch card sizes its cover
                            // and for the same reason: a tall upload (1000x5000) would
                            // otherwise size the card past the bottom of the screen.
                            val bandHeight = 96.dp
                            // Container styling — styling.rewardConfiguration.rewardImage,
                            // the same knobs the story circle exposes. Defaults reproduce
                            // what used to be hardcoded here.
                            val imageStyling = rewardStyling?.rewardImage
                            val badgeWidth = (imageStyling?.width ?: 156).dp
                            val badgeBorder = (imageStyling?.borderWidth ?: 6).dp
                            val badgeBorderColor =
                                parseColor(imageStyling?.borderColor, Color.White)
                            // Inner radius is what the dashboard sets; the frame's outer
                            // radius is that plus its own width, so the two stay
                            // concentric — the story circle does the same with ringWidth.
                            val artworkShape = RoundedCornerShape(
                                topStart = (imageStyling?.cornerRadius?.topLeft ?: 12).dp,
                                topEnd = (imageStyling?.cornerRadius?.topRight ?: 12).dp,
                                bottomStart =
                                    (imageStyling?.cornerRadius?.bottomLeft ?: 12).dp,
                                bottomEnd =
                                    (imageStyling?.cornerRadius?.bottomRight ?: 12).dp
                            )
                            // The badge hangs half below the band, so anything over 2x the
                            // band would poke out of its top. This is the cap the scratch
                            // card learned it needed: without one, a 1000x5000 upload sizes
                            // the container off the bottom of the screen.
                            val badgeMaxHeight = bandHeight * 1.8f
                            val badgeShape = RoundedCornerShape(
                                topStart = (imageStyling?.cornerRadius?.topLeft ?: 12).dp
                                    + badgeBorder,
                                topEnd = (imageStyling?.cornerRadius?.topRight ?: 12).dp
                                    + badgeBorder,
                                bottomStart =
                                    (imageStyling?.cornerRadius?.bottomLeft ?: 12).dp
                                        + badgeBorder,
                                bottomEnd =
                                    (imageStyling?.cornerRadius?.bottomRight ?: 12).dp
                                        + badgeBorder
                            )
                            val density = LocalDensity.current
                            var badgeHeight by remember { mutableStateOf(0.dp) }

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
                                            // Dashboard first; the old hardcoded pair is
                                            // only the fallback now.
                                            parseColor(
                                                rewardStyling?.cardHeaderColor,
                                                if (isWin) Color(0xFF7C3AED) else Color(0xFF9CA3AF)
                                            )
                                        )
                                )

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        // The tile hugs the artwork in BOTH directions. It used
                                        // to be pinned to badgeWidth, so a portrait upload was
                                        // scaled down to the height cap and then sat in a tile
                                        // still 156dp wide — the slack showed up as thick white
                                        // bars either side of the image.
                                        //
                                        // required*, so the artwork's own size wins over the
                                        // parent it is in the middle of resizing. The floor only
                                        // stops the tile collapsing while the image loads.
                                        .requiredSizeIn(
                                            minWidth = 56.dp,
                                            minHeight = 56.dp,
                                            maxWidth = badgeWidth,
                                            maxHeight = badgeMaxHeight
                                        )
                                        .onSizeChanged {
                                            badgeHeight = with(density) { it.height.toDp() }
                                        }
                                        .shadow(10.dp, badgeShape)
                                        .clip(badgeShape)
                                        .background(badgeBorderColor)
                                        .padding(badgeBorder),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!rewardMedia.isNullOrBlank()) {
                                        RewardMedia(
                                            bannerImageUrl = rewardMedia,
                                            targetWidthPx = with(density) { badgeWidth.roundToPx() },
                                            targetHeightPx =
                                                with(density) { badgeMaxHeight.roundToPx() },
                                            // No fillMaxWidth: the image reports its own scaled
                                            // size and the tile wraps it.
                                            modifier = Modifier.clip(artworkShape),
                                            // Fit sizes the tile to the image's own shape, and
                                            // once the cap bites it shrinks to fit rather than
                                            // cropping the artwork.
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Text(text = if (isWin) "🎁" else "✨", fontSize = 40.sp)
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 22.dp)
                                    .padding(top = 14.dp, bottom = 22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Prize name — reward.styling.priceLabel
                                val priceLabelMargin = priceLabelStyle?.margin
                                CommonText(
                                    text = prizeName,
                                    lineHeight = (titleFontSize + 6).toFloat(),
                                    styling = TextStyling(
                                        color = priceLabelStyle?.color
                                            ?: if (isWin) "#1A1A1A" else "#424242",
                                        fontFamily = priceLabelStyle?.fontFamily,
                                        fontSize = titleFontSize,
                                        textAlign = priceLabelStyle?.textAlign ?: "center",
                                        fontDecoration = listOf("bold") +
                                            priceLabelStyle?.fontDecoration.orEmpty(),
                                        margin = CommonMargins(
                                            top = priceLabelMargin?.top,
                                            bottom = priceLabelMargin?.bottom,
                                            left = priceLabelMargin?.left,
                                            right = priceLabelMargin?.right
                                        )
                                    )
                                )

                                // Sub text — reward.styling.subtitleText
                                val subtitleMargin = subtitleTextStyle?.margin
                                subText?.takeIf { it.isNotEmpty() }?.let { text ->
                                    CommonText(
                                        text = text,
                                        lineHeight = (subtitleFontSize + 5).toFloat(),
                                        styling = TextStyling(
                                            color = subtitleTextStyle?.color ?: "#6B7280",
                                            fontFamily = subtitleTextStyle?.fontFamily,
                                            fontSize = subtitleFontSize,
                                            textAlign = subtitleTextStyle?.textAlign ?: "center",
                                            fontDecoration =
                                                subtitleTextStyle?.fontDecoration.orEmpty(),
                                            margin = CommonMargins(
                                                top = subtitleMargin?.top,
                                                bottom = subtitleMargin?.bottom,
                                                left = subtitleMargin?.left,
                                                right = subtitleMargin?.right
                                            )
                                        )
                                    )
                                }

                                // Coupon — reward.styling.couponCodeCta
                                couponCode?.takeIf { it.isNotEmpty() && isWin }?.let { code ->
                                    val couponMargin = couponCtaStyling?.margin
                                    val dashStroke = with(density) {
                                        couponBorderWidth.dp.toPx().coerceAtLeast(1f)
                                    }
                                    val dashRadius = with(density) {
                                        (couponCornerRadius?.topLeft ?: 8).dp.toPx()
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement =
                                            when ((couponContainer?.alignment ?: "center").lowercase()) {
                                                "left" -> Arrangement.Start
                                                "right" -> Arrangement.End
                                                else -> Arrangement.Center
                                            }
                                    ) {
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
                                                        Modifier.width(
                                                            (couponContainer?.ctaWidth ?: 200).dp
                                                        )
                                                )
                                                // container.height was parsed and then ignored.
                                                .height((couponContainer?.height ?: 46).dp)
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
                                                        cornerRadius =
                                                            CornerRadius(dashRadius, dashRadius)
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
                                                .padding(horizontal = 18.dp),
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
                                        }
                                    }
                                }

                                // Terms & Conditions link
                                if (isWin && !termsContent.isNullOrEmpty()) {
                                    Text(
                                        text = tncCtaText,
                                        fontSize = 13.sp,
                                        color = Color(0xFF6B7280).copy(alpha = 0.9f),
                                        fontWeight = FontWeight.Medium,
                                        textDecoration = TextDecoration.Underline,
                                        modifier = Modifier.clickable { showTermsDialog = true }
                                    )
                                }
                            }
                        }
                    }

                    } // end headings + card, the part that stays put

                    // ── CTA, outside the card — reward.styling.cta ─────────────────
                    // The gap between card and button is cta.margin.top, so it is a
                    // dashboard setting rather than a number picked here.
                    Button(
                        onClick = {
                            if (!redirectLink.isNullOrEmpty()) onLinkClick(redirectLink)
                            onDismiss()
                        },
                        modifier = Modifier
                            .padding(
                                top = ctaGap,
                                bottom = (ctaMargin?.bottom ?: 0).coerceAtLeast(0).dp,
                                start = (ctaMargin?.left ?: 0).coerceAtLeast(0).dp,
                                end = (ctaMargin?.right ?: 0).coerceAtLeast(0).dp
                            )
                            .then(
                                if (ctaFullWidth) Modifier.fillMaxWidth(cardWidth)
                                else Modifier.width(ctaWidth.dp)
                            )
                            .height(ctaHeight.dp)
                            .then(
                                if (ctaBorderWidth > 0)
                                    Modifier.border(ctaBorderWidth.dp, ctaBorderColor, ctaShape)
                                else Modifier
                            ),
                        colors = ButtonDefaults.buttonColors(containerColor = ctaBackgroundColor),
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
                                color = ctaText?.color ?: legacyText?.color ?: "#FFFFFF",
                                fontFamily = ctaText?.fontFamily ?: legacyText?.fontFamily,
                                fontSize = ctaTextSize,
                                fontDecoration = listOf("semibold") +
                                    (ctaText?.fontDecoration
                                        ?: legacyText?.fontDecoration).orEmpty()
                            )
                        )
                    }
                }
            }
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

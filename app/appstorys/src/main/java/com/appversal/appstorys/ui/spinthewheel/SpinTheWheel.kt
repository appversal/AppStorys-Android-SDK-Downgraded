package com.appversal.appstorys.ui.spinwheel

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.appversal.appstorys.api.SpinTheWheelDetails
import com.appversal.appstorys.api.SpinWheelRewardConfig
import com.appversal.appstorys.api.WheelRewardStyling
import com.appversal.appstorys.api.WheelSlice
import com.appversal.appstorys.ui.common_components.CrossButton
import com.appversal.appstorys.ui.common_components.createCrossButtonConfig
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
                    // Parse hex color
                    val normalizedColor =
                        if (colorString.startsWith("#")) colorString else "#$colorString"
                    Color(android.graphics.Color.parseColor(normalizedColor))
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

@RequiresApi(Build.VERSION_CODES.M)
@Composable
fun SpinTheWheel(
    isPresented: Boolean,
    onDismiss: () -> Unit,
    spinTheWheelDetails: SpinTheWheelDetails,
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

    // Parse backdrop styling
    val backdropColor = parseColor(visualTextStyling?.backdropColor, Color.Black)
    val backdropOpacity = (visualTextStyling?.backdropOpacity ?: 70) / 100f

    // State management - use availableSpins from root or content.userInteraction.numberSpin
    val initialSpins = spinTheWheelDetails.availableSpins
        ?: content?.userInteraction?.numberSpin
        ?: 3
    var spinsLeft by remember { mutableStateOf(initialSpins) }

    LaunchedEffect(isPresented) {
        if (isPresented) {
            spinsLeft = initialSpins
        }
    }

    var isSpinning by remember { mutableStateOf(false) }
    var selectedSlice by remember { mutableStateOf<com.appversal.appstorys.api.WheelSlice?>(null) }
    var showResultDialog by remember { mutableStateOf(false) }
    var showConfetti by remember { mutableStateOf(false) }

    // Haptic feedback from content.userInteraction
    val enableHapticFeedback = content?.userInteraction?.hapticFeedback ?: false

    // Animation state
    val rotation = remember { Animatable(0f) }

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

    // Spin function with enhanced animations
    val performSpin = {
        if (spinsLeft > 0 && !isSpinning && slices.isNotEmpty()) {
            isSpinning = true
            spinsLeft--

            // Calculate winning slice based on probability weights (using 'weight' field from backend)
            val totalWeight = slices.sumOf { maxOf(it.weight ?: 0, 0) }

            // Handle case where totalWeight is 0 or negative - use equal probability for all slices
            val winningSlice = if (totalWeight > 0) {
                val randomValue = Random.nextInt(totalWeight)
                var cumulativeWeight = 0
                var selectedSlice = slices.firstOrNull()

                for (slice in slices) {
                    cumulativeWeight += maxOf(slice.weight ?: 0, 0)
                    if (randomValue < cumulativeWeight) {
                        selectedSlice = slice
                        break
                    }
                }
                selectedSlice
            } else {
                // Fallback: random selection with equal probability when weights are not defined
                slices.randomOrNull()
            }

            // Calculate slice angle
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

            val finalAngle = current + fullSpins + delta



            // Haptic feedback at start if enabled
            if (enableHapticFeedback) {
                triggerHapticFeedback(context)
            }

            // Animate rotation with spring-like deceleration
            coroutineScope.launch {
                rotation.animateTo(
                    targetValue = finalAngle,
                    animationSpec = tween(
                        durationMillis = 4000 + Random.nextInt(500),
                        easing = CubicBezierEasing(
                            0.22f, 1f,
                            0.36f, 1f
                        )
                    )
                )

                selectedSlice = winningSlice

                // Add haptic feedback on stop
                if (enableHapticFeedback) {
                    delay(100)
                    triggerHapticFeedback(context, duration = 200)
                }

                isSpinning = false

                // Show confetti for wins (using 'noPrize' field from backend)
                if (winningSlice?.noPrize != true) {
                    showConfetti = true
                    delay(300)
                }

                showResultDialog = true
                onSpinComplete(winningSlice?.prizeLabel, winningSlice?.coupon)
            }
        }
    }

    val rewardDisplayMode = spinTheWheelDetails.rewardDisplayMode ?: "pop-up"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val enableBackdrop = spinTheWheelDetails.enableBackdrop ?: true

        // Full screen backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (enableBackdrop)
                        Modifier.background(backdropColor.copy(alpha = backdropOpacity))
                    else
                        Modifier.background(Color.Transparent)
                )
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
                val rewardBg = if (rewardDisplayMode.lowercase() == "full-screen" ||
                    rewardDisplayMode.lowercase() == "full screen"
                ) {
                    Modifier.fillMaxSize().background(Color.White)
                } else {
                    Modifier.fillMaxSize()
                }
                Box(modifier = rewardBg) {
                    RewardContent(
                        slice = selectedSlice!!,
                        rewardConfiguration = content?.rewardConfiguration,
                        rewardStyling = styling?.rewardConfiguration,
                        mainLink = spinTheWheelDetails.link,
                        onLinkClick = { link -> onCtaClick(link) },
                        onDismiss = {
                            showResultDialog = false
                            showConfetti = false
                            if (spinsLeft <= 0) {
                                onDismiss()
                            }
                        }
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize(),
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
                                .background(parseColor(crossFillColor, Color.Black), CircleShape)
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            CrossButton(
                                config = createCrossButtonConfig(
                                    fillColorString = crossFillColor,
                                    crossColorString = crossCrossColor,
                                    strokeColorString = crossStrokeColor,
                                    size = crossButtonSize
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
                    Text(
                        text = popupTitle,
                        fontSize = (titleStyle?.fontSize ?: 28).sp,
                        fontWeight = parseFontWeight(titleStyle?.fontWeight ?: "bold"),
                        fontStyle = parseFontStyle(titleStyle?.fontStyle),
                        textAlign = parseTextAlign(titleStyle?.textAlign ?: "center"),
                        textDecoration = parseTextDecoration(titleStyle?.fontDecoration),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = (titleMargin?.top ?: 0).dp,
                                bottom = (titleMargin?.bottom ?: 0).dp,
                                start = (titleMargin?.left ?: 0).dp,
                                end = (titleMargin?.right ?: 0).dp
                            ),
                        color = parseColor(titleStyle?.color, Color.White)
                    )
                }

                // Description with styling from backend (using direct popupDescription field)
                val popupDescription = spinTheWheelDetails.popupDescription
                if (!popupDescription.isNullOrEmpty()) {
                    val subtitleMargin = subtitleStyle?.margin
                    Text(
                        text = popupDescription,
                        fontSize = (subtitleStyle?.fontSize ?: 15).sp,
                        fontWeight = parseFontWeight(subtitleStyle?.fontWeight),
                        fontStyle = parseFontStyle(subtitleStyle?.fontStyle),
                        textAlign = parseTextAlign(subtitleStyle?.textAlign ?: "center"),
                        textDecoration = parseTextDecoration(subtitleStyle?.fontDecoration),
                        color = parseColor(subtitleStyle?.color, Color.White.copy(alpha = 0.9f)),
                        lineHeight = ((subtitleStyle?.fontSize ?: 15) + 5).sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = (subtitleMargin?.top ?: 0).dp,
                                bottom = (subtitleMargin?.bottom ?: 0).dp,
                                start = (subtitleMargin?.left ?: 0).dp,
                                end = (subtitleMargin?.right ?: 0).dp
                            )
                    )
                }

                // Spins left indicator with styling from backend
                val spinTextColor = parseColor(availableSpinTextStyle?.color, Color.White)
                val spinTextAlign = parseTextAlign(availableSpinTextStyle?.textAlign ?: "center")
                val spinTextFontSize = availableSpinTextStyle?.fontSize ?: 14
                val spinTextFontWeight = parseFontWeight(availableSpinTextStyle?.fontWeight ?: "bold")
                val spinTextFontStyle = parseFontStyle(availableSpinTextStyle?.fontStyle)

                val availableSpinsMargin = availableSpinTextStyle?.margin
                // Dynamic: always re-evaluated when spinsLeft changes.
                // If backend provides a template (e.g. "{spinsLeft} spins left"), replace the placeholder.
                // Otherwise, fall back to a default string built from the live spinsLeft value.
                    val availableSpinsLabel =
                        content?.availableSpinsText?.takeIf { it.isNotBlank() }
                            ?: "Available Spins"

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
                        Text(
                            text = availableSpinsLabel,
                            fontSize = spinTextFontSize.sp,
                            fontWeight = spinTextFontWeight,
                            fontStyle = spinTextFontStyle,
                            color = spinTextColor,
                            textAlign = spinTextAlign
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Dynamic spins number
                        Text(
                            text = spinsLeft.toString(),
                            fontSize = spinTextFontSize.sp,
                            fontWeight = FontWeight.Bold,
                            color = spinTextColor
                        )
                    }

                // Enhanced Wheel Container with glow effect
                val wheelConfigStyling = mainStyling?.wheelConfiguration
                val wheelBorderColor = parseColor(wheelConfigStyling?.borderColor, Color.White)
                val wheelBorderWidth = wheelConfigStyling?.borderWidth ?: 5
                val wheelSize = (wheelConfigStyling?.size ?: 350).dp

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
                        wheelImage = null,
                        backgroundColor = wheelConfigStyling?.backgroundColor,
                        borderColor = wheelBorderColor,
                        borderWidth = wheelBorderWidth,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Spin button with styling from backend
                val buttonScale = if (spinsLeft > 0 && !isSpinning) pulseScale else 1f

                // Extract spin button styling
                val buttonContainer = spinButtonStyle?.container
                val buttonText = spinButtonStyle?.text
                val buttonMargin = spinButtonStyle?.margin
                val buttonBackgroundColor = parseColor(buttonContainer?.backgroundColor, Color(0xFFFFB545))
                val buttonBorderColor = parseColor(buttonContainer?.borderColor, Color.Transparent)
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

                // ---------------------------
                // INDUSTRY STANDARD SPIN CTA
                // ---------------------------

                val isEnabled = spinsLeft > 0 && !isSpinning

                val interactionSource = remember { MutableInteractionSource() }

//                val animatedElevation by animateDpAsState(
//                    targetValue = if (isSpinning) 2.dp else 8.dp,
//                    label = "elevation"
//                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = (buttonMargin?.top ?: 0).dp,
                            bottom = (buttonMargin?.bottom ?: 0).dp,
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
                            .then(
                                if (buttonFullWidth) Modifier.fillMaxWidth()
                                else Modifier.width(buttonWidth.dp)
                            )
                            .height(buttonHeight.dp)
                            //.shadow(animatedElevation, buttonShape)
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

                        // Animated content swap (no jump)
                        AnimatedContent(
                            targetState = isSpinning,
                            transitionSpec = {
                                fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                            },
                            label = "spinContent"
                        ) { spinning ->

                            if (spinning) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.5.dp,
                                    color = buttonTextColor,
                                    modifier = Modifier.size(22.dp)
                                )

                            } else {
                                Text(
                                    text = spinTheWheelDetails.spinButtonText ?: "SPIN",
                                    fontSize = buttonTextSize.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontStyle = parseFontStyle(buttonText?.fontStyle),
                                    textDecoration = parseTextDecoration(buttonText?.fontDecoration),
                                    color = buttonTextColor,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }
        } // end else (wheel view)
    } // end outer Box
} // end outer Dialog
}

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

    val rewardEnableBackdrop = rewardConfiguration?.rewardEnableBackdrop ?: true
    val isWin = slice.noPrize != true

    // Use reward data if available, otherwise fallback to slice data
    val prizeName = reward?.prizeName?.takeIf { it.isNotEmpty() }
        ?: slice.prizeLabel
        ?: if (isWin) "You Won!" else "No Prize"
    val couponCode = reward?.couponCode?.takeIf { it.isNotEmpty() } ?: slice.coupon
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
    val titleTextAlign = parseTextAlign(priceLabelStyle?.textAlign ?: globalTitleStyle?.textAlign ?: "center")
    val titleTextDecoration = parseTextDecoration(priceLabelStyle?.fontDecoration ?: globalTitleStyle?.fontDecoration)

    // Parse subtitle styling
    val subtitleColor = parseColor(
        subtitleTextStyle?.color ?: globalSubtitleStyle?.color,
        Color(0xFF6B7280)
    )
    val subtitleFontSize = subtitleTextStyle?.fontSize ?: globalSubtitleStyle?.fontSize ?: 14
    val subtitleTextAlign = parseTextAlign(subtitleTextStyle?.textAlign ?: globalSubtitleStyle?.textAlign ?: "center")

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
    val ctaBackgroundColor = parseColor(ctaContainer?.backgroundColor, if (isWin) Color(0xFF2563EB) else Color(0xFF6B7280))
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
        val rewardBackdropColor = try {
            rewardStyling?.backdropColor?.let {
                Color(android.graphics.Color.parseColor(it))
            } ?: Color.Black.copy(alpha = 0.6f)
        } catch (_: Exception) {
            Color.Black.copy(alpha = 0.6f)
        }

        // Backdrop Container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (rewardEnableBackdrop)
                        Modifier.background(rewardBackdropColor.copy(alpha = 0.6f))
                    else
                        Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            // Main Card - Modern glassmorphism-inspired design
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .shadow(32.dp, RoundedCornerShape(28.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.White),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header section with visual hierarchy
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
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
                    ) {
                        // Close button
                        if (crossButtonEnabled) {
                            val crossAlignment = when (crossButtonAlignment.lowercase()) {
                                "left" -> Alignment.TopStart
                                "center" -> Alignment.TopCenter
                                else -> Alignment.TopEnd
                            }
                            Box(
                                modifier = Modifier
                                    .align(crossAlignment)
                                    .padding(
                                        top = (crossMargin?.top ?: 0).coerceAtLeast(0).dp,
                                        start = (crossMargin?.left ?: 0).coerceAtLeast(0).dp,
                                        end = (crossMargin?.right ?: 0).coerceAtLeast(0).dp
                                    )
                            ) {
                                CrossButton(
                                    config = createCrossButtonConfig(
                                        fillColorString = crossButtonConfig?.color?.fill ?: "#FFFFFF33",
                                        crossColorString = crossButtonConfig?.color?.cross ?: "#FFFFFF",
                                        strokeColorString = crossButtonConfig?.color?.stroke ?: "#FFFFFF33",
                                        size = crossButtonSize,
                                        imageUrl = crossButtonImage
                                    ),
                                    onClose = onDismiss
                                )
                            }
                        }

                        // Status indicator
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Animated emoji or icon
                            val emojiScale by rememberInfiniteTransition(label = "emoji")
                                .animateFloat(
                                    initialValue = 1f,
                                    targetValue = 1.1f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(600, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "emojiPulse"
                                )

                            Text(
                                text = if (isWin) "🎉" else "💫",
                                fontSize = 48.sp,
                                modifier = Modifier.scale(emojiScale)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = if (isWin) "Congratulations!" else "Better Luck Next Time!",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // Prize image - floating card effect
                    if (!rewardMedia.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .offset(y = (-32).dp)
                                .size(100.dp)
                                .shadow(16.dp, RoundedCornerShape(20.dp))
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White)
                                .border(4.dp, Color.White, RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(rewardMedia)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Prize",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                contentScale = ContentScale.Fit,
                                loading = {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF667EEA)
                                    )
                                }
                            )
                        }
                    } else {
                        // Default prize icon
                        Box(
                            modifier = Modifier
                                .offset(y = (-32).dp)
                                .size(80.dp)
                                .shadow(12.dp, CircleShape)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = if (isWin) {
                                            listOf(Color(0xFFFFD700), Color(0xFFFFA500))
                                        } else {
                                            listOf(Color(0xFFE5E7EB), Color(0xFFD1D5DB))
                                        }
                                    )
                                )
                                .border(3.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isWin) "🎁" else "✨",
                                fontSize = 32.sp
                            )
                        }
                    }

                    // Content section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-16).dp)
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Prize name
                        Text(
                            text = prizeName,
                            fontSize = titleFontSize.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = titleTextAlign,
                            textDecoration = titleTextDecoration,
                            color = titleColor,
                            lineHeight = (titleFontSize + 6).sp
                        )

                        // Sub text / description
                        subText?.takeIf { it.isNotEmpty() }?.let { text ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = text,
                                fontSize = subtitleFontSize.sp,
                                fontWeight = FontWeight.Normal,
                                textAlign = subtitleTextAlign,
                                color = subtitleColor,
                                lineHeight = (subtitleFontSize + 5).sp
                            )
                        }

                        // Reward popup description from config
                        rewardConfiguration?.rewardPopupDescription?.takeIf { it.isNotEmpty() }?.let { desc ->
                            if (subText.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = desc,
                                    fontSize = subtitleFontSize.sp,
                                    fontWeight = FontWeight.Normal,
                                    textAlign = subtitleTextAlign,
                                    color = subtitleColor,
                                    lineHeight = (subtitleFontSize + 5).sp
                                )
                            }
                        }

                        // Coupon Code Section - Modern dashed border style
                        couponCode?.takeIf { it.isNotEmpty() && isWin }?.let { code ->
                            Spacer(modifier = Modifier.height(24.dp))

                            // Coupon label
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(1.dp)
                                        .background(Color(0xFFE5E7EB))
                                )
                                Text(
                                    text = "YOUR CODE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF9CA3AF),
                                    letterSpacing = 2.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(1.dp)
                                        .background(Color(0xFFE5E7EB))
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Coupon code card
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(couponShape)
                                    .background(couponBackgroundColor)
                                    .border(couponBorderWidth.dp, couponBorderColor, couponShape)
                                    .clickable {
                                        try {
                                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Coupon Code", code))
                                            isCopied = true
                                        } catch (_: Exception) {}
                                    }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = code.uppercase(),
                                    fontSize = couponTextSize.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = couponTextColor,
                                    letterSpacing = 2.sp,
                                    modifier = Modifier.weight(1f)
                                )

                                // Copy button with animation
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isCopied) Color(0xFF10B981) else couponTextColor
                                        )
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isCopied) "✓ Copied" else "Copy",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Tap to copy code",
                                fontSize = 11.sp,
                                color = Color(0xFFADB5BD),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // CTA Button with per-slice styling
                        Button(
                            onClick = {
                                if (!redirectLink.isNullOrEmpty()) onLinkClick(redirectLink)
                                onDismiss()
                            },
                            modifier = Modifier
                                .then(
                                    if (ctaFullWidth) Modifier.fillMaxWidth()
                                    else Modifier.width(ctaWidth.dp)
                                )
                                .height(ctaHeight.dp)
                                .padding(
                                    top = (ctaMargin?.top ?: 0).coerceAtLeast(0).dp,
                                    bottom = (ctaMargin?.bottom ?: 0).coerceAtLeast(0).dp,
                                    start = (ctaMargin?.left ?: 0).coerceAtLeast(0).dp,
                                    end = (ctaMargin?.right ?: 0).coerceAtLeast(0).dp
                                )
                                .then(
                                    if (ctaBorderWidth > 0)
                                        Modifier.border(ctaBorderWidth.dp, ctaBorderColor, ctaShape)
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
                            Text(
                                text = buttonCtaText,
                                fontSize = ctaTextSize.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ctaTextColor,
                                textDecoration = ctaTextDecoration,
                                letterSpacing = 0.5.sp
                            )
                        }

                        // Terms & Conditions link
                        val hasTermsContent = !termsContent.isNullOrEmpty()
                        if (isWin && hasTermsContent) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = tncCtaText,
                                fontSize = 13.sp,
                                color = Color(0xFF6B7280),
                                fontWeight = FontWeight.Medium,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier.clickable {
                                    showTermsDialog = true
                                }
                            )
                        }
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
                        .shadow(24.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .padding(24.dp)
                ) {
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

                    Spacer(modifier = Modifier.height(20.dp))

                    // Divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFFE5E7EB))
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = termsContent,
                        fontSize = 14.sp,
                        color = Color(0xFF4B5563),
                        lineHeight = 22.sp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

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
 * Confetti animation effect for celebration
 */
@Composable
private fun ConfettiEffect(
    modifier: Modifier = Modifier,
    confettiConfig: com.appversal.appstorys.api.WheelConfettiConfig? = null,
    onComplete: () -> Unit = {}
) {
    // Parse confetti colors from backend or use defaults
    val confettiColors = remember(confettiConfig) {
        val customColors = mutableListOf<Color>()

        // Try to parse colors from config
        confettiConfig?.color?.let { colorConfig ->
            listOf(colorConfig.fill, colorConfig.cross, colorConfig.stroke).forEach { colorStr ->
                if (!colorStr.isNullOrBlank()) {
                    try {
                        val normalizedColor = if (colorStr.startsWith("#")) colorStr else "#$colorStr"
                        customColors.add(Color(android.graphics.Color.parseColor(normalizedColor)))
                    } catch (_: Exception) { }
                }
            }
        }

        // Use custom colors if available, otherwise use default palette
        if (customColors.isNotEmpty()) {
            customColors
        } else {
            listOf(
                Color(0xFFFFD700), // Gold
                Color(0xFFFF1744), // Red
                Color(0xFF00BCD4), // Cyan
                Color(0xFF4CAF50), // Green
                Color(0xFFFF4081), // Pink
                Color(0xFF9C27B0)  // Purple
            )
        }
    }

    val confettiParticles = remember {
        List(50) { _ ->
            ConfettiParticle(
                x = Random.nextFloat(),
                y = -0.1f,
                color = confettiColors.random(),
                velocity = 0.5f + Random.nextFloat() * 1.5f,
                rotation = Random.nextFloat() * 360f
            )
        }
    }

    var animationProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        while (animationProgress < 1f) {
            val elapsed = System.currentTimeMillis() - startTime
            animationProgress = (elapsed / 2000f).coerceAtMost(1f)
            delay(16)
        }
        delay(500)
        onComplete()
    }

    androidx.compose.foundation.Canvas(
        modifier = modifier
    ) {
        confettiParticles.forEach { particle ->
            val progress = animationProgress
            val currentY = particle.y + (particle.velocity * progress)
            val alpha = (1f - progress).coerceAtLeast(0f)

            if (currentY < 1.2f) {
                drawCircle(
                    color = particle.color.copy(alpha = alpha),
                    radius = 8f,
                    center = androidx.compose.ui.geometry.Offset(
                        x = particle.x * size.width,
                        y = currentY * size.height
                    )
                )
            }
        }
    }
}

private data class ConfettiParticle(
    val x: Float,
    val y: Float,
    val color: Color,
    val velocity: Float,
    val rotation: Float
)
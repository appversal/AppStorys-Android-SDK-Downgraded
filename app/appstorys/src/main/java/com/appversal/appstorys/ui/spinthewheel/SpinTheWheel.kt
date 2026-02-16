package com.appversal.appstorys.ui.spinwheel

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
            val normalizedColor = if (colorString.startsWith("#")) colorString else "#$colorString"
            Color(android.graphics.Color.parseColor(normalizedColor))
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
    var currentRotation by remember { mutableStateOf(0f) }
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
            val totalWeight = slices.sumOf { it.weight ?: 0 }

            // Handle case where totalWeight is 0 or negative - use equal probability for all slices
            val winningSlice = if (totalWeight > 0) {
                val randomValue = Random.nextInt(totalWeight)
                var cumulativeWeight = 0
                var selectedSlice = slices.firstOrNull()

                for (slice in slices) {
                    cumulativeWeight += slice.weight ?: 0
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

            // Calculate rotation
            val sliceAngle = if (slices.isNotEmpty()) 360f / slices.size else 360f
            val winningSliceIndex = slices.indexOf(winningSlice)

            // Calculate the middle of the winning slice
            // Slices are drawn starting at -90° (top), so first slice middle is at -90° + sliceAngle/2
            val sliceStartAngle = -90f + (winningSliceIndex * sliceAngle)
            val sliceMiddleAngle = sliceStartAngle + (sliceAngle / 2f)

            // Add multiple full rotations for realistic effect (8-10 rotations)
            val extraRotations = 360f * (8 + Random.nextFloat() * 2)

            // To make the slice middle point to the top (0°/360°), we need to rotate the wheel
            // so that sliceMiddleAngle ends up at 0°
            // Since wheel rotates clockwise, we need: finalRotation % 360 = -sliceMiddleAngle
            val targetAngle = -sliceMiddleAngle

            // Normalize to 0-360 range
            val normalizedTarget = ((targetAngle % 360f) + 360f) % 360f

            // Calculate final angle: add full rotations + adjust to target
            val baseRotation = currentRotation % 360f
            val adjustment = normalizedTarget - baseRotation
            val finalAngle = currentRotation + extraRotations + adjustment

            // Haptic feedback at start if enabled
            if (enableHapticFeedback) {
                triggerHapticFeedback(context)
            }

            // Animate rotation with spring-like deceleration
            coroutineScope.launch {
                rotation.animateTo(
                    targetValue = finalAngle,
                    animationSpec = tween(
                        durationMillis = 5000, // 5 seconds for more rotations
                        easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
                    )
                )
                currentRotation = finalAngle % 360f
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backdropColor.copy(alpha = backdropOpacity)),
            contentAlignment = Alignment.Center
        ) {
            // Confetti overlay
            if (showConfetti) {
                ConfettiEffect(
                    modifier = Modifier.fillMaxSize(),
                    onComplete = { showConfetti = false }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight()
                    .shadow(24.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .padding(20.dp),
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
                                bottom = (crossButtonMargin?.bottom ?: 4).dp,
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

                Spacer(modifier = Modifier.height(4.dp))

                // Title with styling from backend (using direct popupTitle field)
                spinTheWheelDetails.popupTitle?.let { title ->
                    val titleMargin = titleStyle?.margin
                    Text(
                        text = title,
                        fontSize = (titleStyle?.fontSize ?: 28).sp,
                        fontWeight = parseFontWeight(titleStyle?.fontWeight),
                        fontStyle = parseFontStyle(titleStyle?.fontStyle),
                        textAlign = parseTextAlign(titleStyle?.textAlign),
                        textDecoration = parseTextDecoration(titleStyle?.fontDecoration),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = (titleMargin?.top ?: 0).dp,
                                bottom = (titleMargin?.bottom ?: 8).dp,
                                start = (titleMargin?.left ?: 0).dp,
                                end = (titleMargin?.right ?: 0).dp
                            ),
                        color = parseColor(titleStyle?.color, Color(0xFF1A1A1A))
                    )
                }

                // Description with styling from backend (using direct popupDescription field)
                spinTheWheelDetails.popupDescription?.let { description ->
                    val subtitleMargin = subtitleStyle?.margin
                    Text(
                        text = description,
                        fontSize = (subtitleStyle?.fontSize ?: 15).sp,
                        fontWeight = parseFontWeight(subtitleStyle?.fontWeight),
                        fontStyle = parseFontStyle(subtitleStyle?.fontStyle),
                        textAlign = parseTextAlign(subtitleStyle?.textAlign),
                        textDecoration = parseTextDecoration(subtitleStyle?.fontDecoration),
                        color = parseColor(subtitleStyle?.color, Color(0xFF666666)),
                        lineHeight = ((subtitleStyle?.fontSize ?: 15) + 5).sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = (subtitleMargin?.top ?: 0).dp,
                                bottom = (subtitleMargin?.bottom ?: 12).dp,
                                start = (subtitleMargin?.left ?: 0).dp,
                                end = (subtitleMargin?.right ?: 0).dp
                            )
                    )
                }

                // Spins left indicator with styling from backend
                val spinTextColor = parseColor(availableSpinTextStyle?.color, Color.Black)
                val spinTextAlign = parseTextAlign(availableSpinTextStyle?.textAlign)
                val spinTextFontSize = availableSpinTextStyle?.fontSize ?: 12
                val spinTextFontWeight = parseFontWeight(availableSpinTextStyle?.fontWeight)
                val spinTextFontStyle = parseFontStyle(availableSpinTextStyle?.fontStyle)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = when (spinTextAlign) {
                        TextAlign.Left -> Arrangement.Start
                        TextAlign.Right -> Arrangement.End
                        else -> Arrangement.Center
                    }
                ) {
                    Text(
                        text = "🎯 Spins left: $spinsLeft",
                        fontSize = spinTextFontSize.sp,
                        fontWeight = spinTextFontWeight,
                        fontStyle = spinTextFontStyle,
                        color = spinTextColor,
                        textAlign = spinTextAlign
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Enhanced Wheel Container with glow effect
                // Extract wheel configuration styling
                val wheelConfigStyling = mainStyling?.wheelConfiguration
                val wheelBorderColor = parseColor(wheelConfigStyling?.borderColor, Color.White)
                val wheelBorderWidth = wheelConfigStyling?.borderWidth ?: 5

                Box(
                    modifier = Modifier
                        .size(320.dp)
                        .drawBehind {
                            // Outer glow effect
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0x40FF1744),
                                        Color.Transparent
                                    ),
                                    radius = size.width / 2 + 40f
                                ),
                                radius = size.width / 2 + 40f
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Shadow ring
                    Box(
                        modifier = Modifier
                            .size(310.dp)
                            .shadow(16.dp, CircleShape)
                            .background(Color.White, CircleShape)
                    )

                    // Wheel
                    Box(
                        modifier = Modifier.size(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        WheelView(
                            slices = slices,
                            rotation = rotation.value,
                            wheelImage = null, // No custom wheel image in current backend structure
                            backgroundColor = wheelConfigStyling?.backgroundColor,
                            borderColor = wheelBorderColor,
                            borderWidth = wheelBorderWidth,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

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
                val buttonWidth = buttonContainer?.width ?: 120
                val buttonFullWidth = buttonContainer?.fullWidth ?: false
                val buttonTextColor = parseColor(buttonText?.color, Color.White)
                val buttonTextSize = buttonText?.fontSize ?: 12

                val buttonShape = RoundedCornerShape(
                    topStart = (buttonCornerRadius?.topLeft ?: 12).dp,
                    topEnd = (buttonCornerRadius?.topRight ?: 12).dp,
                    bottomStart = (buttonCornerRadius?.bottomLeft ?: 12).dp,
                    bottomEnd = (buttonCornerRadius?.bottomRight ?: 12).dp
                )

                Button(
                    onClick = { performSpin() },
                    enabled = spinsLeft > 0 && !isSpinning,
                    modifier = Modifier
                        .then(
                            if (buttonFullWidth) Modifier.fillMaxWidth()
                            else Modifier.width(buttonWidth.dp)
                        )
                        .height(buttonHeight.dp)
                        .padding(
                            top = (buttonMargin?.top ?: 4).dp,
                            bottom = (buttonMargin?.bottom ?: 4).dp,
                            start = (buttonMargin?.left ?: 4).dp,
                            end = (buttonMargin?.right ?: 4).dp
                        )
                        .scale(buttonScale)
                        .shadow(8.dp, buttonShape)
                        .then(
                            if (buttonBorderWidth > 0) Modifier.border(
                                buttonBorderWidth.dp,
                                buttonBorderColor,
                                buttonShape
                            ) else Modifier
                        ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (spinsLeft > 0 && !isSpinning) buttonBackgroundColor else Color.Gray,
                        disabledContainerColor = Color.LightGray.copy(alpha = 0.3f)
                    ),
                    shape = buttonShape,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSpinning) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = buttonTextColor,
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Spinning...",
                                    fontSize = buttonTextSize.sp,
                                    fontWeight = parseFontWeight(buttonText?.fontWeight),
                                    fontStyle = parseFontStyle(buttonText?.fontStyle),
                                    color = buttonTextColor
                                )
                            }
                        } else {
                            Text(
                                text = spinTheWheelDetails.spinButtonText ?: "🎰 SPIN NOW!",
                                fontSize = buttonTextSize.sp,
                                fontWeight = parseFontWeight(buttonText?.fontWeight),
                                fontStyle = parseFontStyle(buttonText?.fontStyle),
                                textDecoration = parseTextDecoration(buttonText?.fontDecoration),
                                color = buttonTextColor,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                // Note: Redirect button is not present in current backend structure
                // Can be added later if needed
            }
        }
    }

    // Enhanced result dialog
    if (showResultDialog && selectedSlice != null) {
        SpinResultDialog(
            slice = selectedSlice!!,
            rewardConfiguration = content?.rewardConfiguration,
            rewardStyling = styling?.rewardConfiguration,
            mainLink = spinTheWheelDetails.link,
            onLinkClick = { link ->
                onCtaClick(link)
            },
            onDismiss = {
                showResultDialog = false
                showConfetti = false
            }
        )
    }
}

@Composable
private fun SpinResultDialog(
    slice: com.appversal.appstorys.api.WheelSlice,
    rewardConfiguration: com.appversal.appstorys.api.SpinWheelRewardConfig?,
    rewardStyling: com.appversal.appstorys.api.WheelRewardStyling?,
    mainLink: String? = null,
    onLinkClick: (String?) -> Unit = {},
    onDismiss: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }

    // Determine the redirect link - slice link takes priority over main link
    val redirectLink = slice.link?.takeIf { it.isNotEmpty() } ?: mainLink

    val isWin = slice.noPrize != true

    // Extract reward styling from backend JSON
    val titleStyle = rewardStyling?.title?.textStyle
    val subtitleStyle = rewardStyling?.subtitle?.textStyle
    val crossButtonConfig = rewardStyling?.crossButton

    // Parse colors from styling with fallbacks
    val titleColor = parseColor(titleStyle?.color, Color(0xFF333333))
    val subtitleColor = parseColor(subtitleStyle?.color, Color(0xFF666666))
    val titleFontSize = titleStyle?.fontSize ?: 20
    val subtitleFontSize = subtitleStyle?.fontSize ?: 14

    // Cross button styling
    val crossButtonEnabled = crossButtonConfig?.enabled ?: true
    val crossButtonSize = crossButtonConfig?.size ?: 30
    val crossFillColor = parseColor(crossButtonConfig?.color?.fill, Color(0xFFF5F5F5))
    val crossColor = parseColor(crossButtonConfig?.color?.cross, Color(0xFF666666))
    val crossMargin = crossButtonConfig?.margin

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
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
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Main card - white background with rounded corners
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .shadow(16.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                ) {
                    // Close button positioned at top-right outside the padding
                    if (crossButtonEnabled) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(
                                    top = (crossMargin?.top ?: 12).dp,
                                    end = (crossMargin?.right ?: 12).dp
                                )
                                .size(crossButtonSize.dp)
                                .clip(CircleShape)
                                .border(1.dp, Color(0xFFE0E0E0), CircleShape)
                                .background(crossFillColor)
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✕",
                                fontSize = (crossButtonSize * 0.5f).sp,
                                color = crossColor,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top icons row - Prize image card + Gift box with stars
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.offset(y = (-5).dp)
                            ) {
                                // Left card - Prize image or discount badge
                                Box(
                                    modifier = Modifier
                                        .size(width = 65.dp, height = 50.dp)
                                        .offset(x = 10.dp, y = 5.dp)
                                        .shadow(4.dp, RoundedCornerShape(8.dp))
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFE3F2FD))
                                        .border(1.dp, Color(0xFFBBDEFB), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!slice.sliceMedia.isNullOrBlank()) {
                                        SubcomposeAsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(slice.sliceMedia)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "Prize",
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        // Default discount badge
                                        Text(
                                            text = if (isWin) "🎯" else "💫",
                                            fontSize = 24.sp
                                        )
                                    }
                                }

                                // Right - Gift box
                                Box(
                                    modifier = Modifier
                                        .size(65.dp)
                                        .offset(x = (-5).dp)
                                        .shadow(6.dp, RoundedCornerShape(14.dp))
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color(0xFFFFE082),
                                                    Color(0xFFFFCA28)
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isWin) "🎁" else "😔",
                                        fontSize = 32.sp
                                    )
                                }
                            }

                            // Decorative stars
                            Text(
                                text = "✨",
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-30).dp, y = 5.dp)
                            )
                            Text(
                                text = "⭐",
                                fontSize = 10.sp,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-15).dp, y = 25.dp)
                            )
                            Text(
                                text = "✨",
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .offset(x = 25.dp, y = (-10).dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Title - Prize Label from slice
                        Text(
                            text = rewardConfiguration?.rewardPopupTitle
                                ?: slice.prizeLabel
                                ?: if (isWin) "You Won!" else "No Prize",
                            fontSize = titleFontSize.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = titleColor,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Description/Subtitle from reward configuration
                        val description = rewardConfiguration?.rewardPopupDescription?.takeIf { it.isNotEmpty() }
                            ?: if (isWin) "Congratulations on your win!" else "Better luck next time!"

                        Text(
                            text = description,
                            fontSize = subtitleFontSize.sp,
                            textAlign = TextAlign.Center,
                            color = subtitleColor,
                            lineHeight = (subtitleFontSize + 6).sp,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Coupon Code Section - only show if coupon exists
                        slice.coupon?.takeIf { it.isNotEmpty() }?.let { code ->
                            Spacer(modifier = Modifier.height(20.dp))

                            // Coupon code box with dashed/orange border
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = 1.5.dp,
                                        color = Color(0xFFFFB74D),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .background(
                                        Color(0xFFFFF8E1),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = code.uppercase(),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE65100),
                                        letterSpacing = 1.5.sp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    // Copy icon
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFFFFE0B2))
                                            .clickable {
                                                try {
                                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                                    if (clipboard != null) {
                                                        val clip = android.content.ClipData.newPlainText("Coupon Code", code)
                                                        clipboard.setPrimaryClip(clip)
                                                    }
                                                } catch (_: Exception) {
                                                    // Clipboard service not available
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "📋",
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Use this code at checkout for your discount.",
                                fontSize = 11.sp,
                                color = Color(0xFF9E9E9E),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Primary CTA Button - "Claim Reward"
                        Button(
                            onClick = {
                                if (!redirectLink.isNullOrEmpty()) {
                                    onLinkClick(redirectLink)
                                }
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            ),
                            shape = RoundedCornerShape(25.dp),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 4.dp
                            )
                        ) {
                            Text(
                                text = if (isWin) "Claim Reward" else "Try Again",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }

                        // Secondary link - "Know more to claim"
                        if (isWin) {
                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Know more to claim",
                                fontSize = 13.sp,
                                color = Color(0xFF1976D2),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable {
                                    if (!redirectLink.isNullOrEmpty()) {
                                        onLinkClick(redirectLink)
                                    }
                                }
                            )
                        }
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
            val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator?.vibrate(
                VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(
                VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
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
    onComplete: () -> Unit = {}
) {
    val confettiParticles = remember {
        List(50) { _ ->
            ConfettiParticle(
                x = Random.nextFloat(),
                y = -0.1f,
                color = listOf(
                    Color(0xFFFFD700), // Gold
                    Color(0xFFFF1744), // Red
                    Color(0xFF00BCD4), // Cyan
                    Color(0xFF4CAF50), // Green
                    Color(0xFFFF4081), // Pink
                    Color(0xFF9C27B0)  // Purple
                ).random(),
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

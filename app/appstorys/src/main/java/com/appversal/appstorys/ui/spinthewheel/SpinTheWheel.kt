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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val visualText = spinTheWheelDetails.visualAndTextCommunication
    val wheelConfig = spinTheWheelDetails.wheelConfiguration
    val slices = spinTheWheelDetails.slices.orEmpty()
    val userInteraction = spinTheWheelDetails.userInteraction

    // State management
    var spinsLeft by remember { mutableStateOf(userInteraction?.availableSpins ?: 3) }
    var isSpinning by remember { mutableStateOf(false) }
    var currentRotation by remember { mutableStateOf(0f) }
    var selectedSlice by remember { mutableStateOf<com.appversal.appstorys.api.WheelSlice?>(null) }
    var showResultDialog by remember { mutableStateOf(false) }
    var showConfetti by remember { mutableStateOf(false) }

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
        if (spinsLeft > 0 && !isSpinning) {
            isSpinning = true
            spinsLeft--

            // Calculate winning slice based on probability weights
            val totalWeight = slices.sumOf { it.probabilityWeight ?: 0 }
            val randomValue = Random.nextInt(totalWeight)
            var cumulativeWeight = 0
            var winningSlice = slices.firstOrNull()

            for (slice in slices) {
                cumulativeWeight += slice.probabilityWeight ?: 0
                if (randomValue < cumulativeWeight) {
                    winningSlice = slice
                    break
                }
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
            val finalAngle = currentRotation + extraRotations + normalizedTarget - (currentRotation % 360f)

            // Haptic feedback at start if enabled
            if (userInteraction?.enableHapticFeedback == true) {
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
                if (userInteraction?.enableHapticFeedback == true) {
                    delay(100)
                    triggerHapticFeedback(context, duration = 200)
                }

                isSpinning = false

                // Show confetti for wins
                if (winningSlice?.enableNoPrize != true) {
                    showConfetti = true
                    delay(300)
                }

                showResultDialog = true
                onSpinComplete(winningSlice?.prizeLabel, winningSlice?.couponCode)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.85f),
                            Color.Black.copy(alpha = 0.75f)
                        )
                    )
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* Prevent backdrop clicks */ },
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
                    .shadow(24.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Transparent
//                        Brush.verticalGradient(
//                            colors = listOf(
//                                Color(0xFFFAFAFA),
//                                Color.Transparent
//                            )
//                        )
                    )
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Close button with modern styling
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .shadow(4.dp, CircleShape)
                            .background(Color.White, CircleShape)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        CrossButton(
                            config = createCrossButtonConfig(
                                fillColorString = "#FFFFFF",
                                crossColorString = "#666666",
                                strokeColorString = "#E0E0E0",
                                size = 36
                            ),
                            onClose = onDismiss
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Title with gradient text effect
                visualText?.popupTitle?.let { title ->
                    Text(
                        text = title,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0x20FF1744),
                                        Color.Transparent
                                    )
                                ),
                                radius = size.width / 2
                            )
                        },
                        color = Color(0xFF1A1A1A)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Description
                visualText?.popupDescription?.let { description ->
                    Text(
                        text = description,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        color = Color(0xFF666666),
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Spins left indicator with modern badge
                Box(
                    modifier = Modifier
                        .shadow(4.dp, RoundedCornerShape(20.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF6200EE),
                                    Color(0xFF9C27B0)
                                )
                            ),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "🎯 Spins left: ",
                            fontSize = 15.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "$spinsLeft",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Enhanced Wheel Container with glow effect
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
                            wheelImage = wheelConfig?.wheelImage,
                            backgroundColor = wheelConfig?.backgroundColor,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Modern gradient spin button with pulse effect
                val buttonScale = if (spinsLeft > 0 && !isSpinning) pulseScale else 1f

                Button(
                    onClick = { performSpin() },
                    enabled = spinsLeft > 0 && !isSpinning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .scale(buttonScale)
                        .shadow(8.dp, RoundedCornerShape(30.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        disabledContainerColor = Color.LightGray.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(30.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (spinsLeft > 0 && !isSpinning) {
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFFFF1744),
                                            Color(0xFFFF4081)
                                        )
                                    )
                                } else {
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Gray,
                                            Color.Gray
                                        )
                                    )
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSpinning) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Spinning...",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        } else {
                            Text(
                                text = visualText?.buttonCtaText ?: "🎰 SPIN NOW!",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                // Optional redirect button with modern styling
                if (!visualText?.buttonRedirectTo?.url.isNullOrBlank() ||
                    !visualText?.buttonRedirectTo?.pageName.isNullOrBlank()
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = {
                            val redirectUrl = visualText?.buttonRedirectTo?.url
                                ?: visualText?.buttonRedirectTo?.pageName
                            onCtaClick(redirectUrl)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "ℹ️ Learn More",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF6200EE)
                        )
                    }
                }
            }
        }
    }

    // Enhanced result dialog
    if (showResultDialog && selectedSlice != null) {
        SpinResultDialog(
            slice = selectedSlice!!,
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
    onDismiss: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }

    val isWin = slice.enableNoPrize != true

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
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .shadow(32.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        if (isWin) {
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFFFF9C4),
                                    Color(0xFFFFFFFF)
                                )
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFEEEEEE),
                                    Color(0xFFFFFFFF)
                                )
                            )
                        }
                    )
                    .padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Animated icon/emoji
                    val infiniteTransition = rememberInfiniteTransition(label = "bounce")
                    val bounce by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = -12f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "bounce"
                    )

                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .offset(y = bounce.dp)
                            .shadow(8.dp, CircleShape)
                            .background(
                                if (isWin) {
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFFFFD700),
                                            Color(0xFFFFA500)
                                        )
                                    )
                                } else {
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFFBDBDBD),
                                            Color(0xFF9E9E9E)
                                        )
                                    )
                                },
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isWin) "🎉" else "😔",
                            fontSize = 44.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Title
                    Text(
                        text = if (isWin) "🎊 Congratulations! 🎊" else "Better Luck Next Time!",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        color = if (isWin) Color(0xFFFF6F00) else Color(0xFF666666)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Prize details
                    if (isWin) {
                        slice.prizeLabel?.let { label ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(4.dp, RoundedCornerShape(16.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color(0xFFFF1744),
                                                Color(0xFFFF4081)
                                            )
                                        ),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "You Won:",
                                        fontSize = 14.sp,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = label,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        slice.couponCode?.let { code ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(2.dp, RoundedCornerShape(12.dp))
                                    .background(Color.White, RoundedCornerShape(12.dp))
                                    .padding(16.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Your Coupon Code:",
                                        fontSize = 13.sp,
                                        color = Color(0xFF666666),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                Color(0xFFF5F5F5),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                    ) {
                                        Text(
                                            text = code,
                                            fontSize = 20.sp,
                                            color = Color(0xFF6200EE),
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 2.sp
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    } else {
                        Text(
                            text = "No prize this time.\nKeep trying!",
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            color = Color(0xFF666666),
                            lineHeight = 24.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Display image if available
                    if (!slice.image.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .shadow(8.dp, RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                        ) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(slice.image)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Prize Image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Close button
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(6.dp, RoundedCornerShape(28.dp)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent
                        ),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = if (isWin) {
                                            listOf(
                                                Color(0xFF4CAF50),
                                                Color(0xFF8BC34A)
                                            )
                                        } else {
                                            listOf(
                                                Color(0xFF6200EE),
                                                Color(0xFF9C27B0)
                                            )
                                        }
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isWin) "✓ Awesome!" else "Try Again",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 1.sp
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

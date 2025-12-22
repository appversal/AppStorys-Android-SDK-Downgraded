//package com.appversal.appstorys.ui
//
//import androidx.compose.animation.core.*
//import androidx.compose.foundation.Canvas
//import androidx.compose.foundation.background
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.shape.CircleShape
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material3.Icon
//import androidx.compose.material3.Text
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.draw.clip
//import androidx.compose.ui.geometry.Size
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.graphics.Path
//import androidx.compose.ui.graphics.drawscope.Fill
//import androidx.compose.ui.res.painterResource
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import androidx.core.graphics.toColorInt
//import com.appversal.appstorys.R
//import com.appversal.appstorys.api.MilestoneDetails
//
//data class MilestonePoint(
//    val value: Double,
//    val icon: String,
//    val color: Color
//)
//
//@Composable
//internal fun MilestoneProgressBar(
//    modifier: Modifier = Modifier,
//    milestoneDetails: MilestoneDetails,
//    onDismiss: () -> Unit = {}
//) {
//    val currentStep = milestoneDetails.currentStep ?: 0
//    val totalSteps = milestoneDetails.totalSteps ?: 0
//    val milestoneValues = milestoneDetails.milestoneValues ?: emptyList()
//    val stepLabels = milestoneDetails.stepLabels ?: emptyList()
//    val styling = milestoneDetails.styling
//
//    // Calculate progress based on milestone VALUE, not step count
//    val progress = remember(currentStep, milestoneValues) {
//        when {
//            milestoneValues.isEmpty() -> 0f
//            currentStep == 0 -> 0f
//            currentStep >= milestoneValues.size -> 1f
//            else -> {
//                val completedIndex = currentStep - 1
//                if (completedIndex >= 0 && completedIndex < milestoneValues.size) {
//                    val maxValue = milestoneValues.lastOrNull() ?: 1.0
//                    val currentValue = milestoneValues[completedIndex]
//                    (currentValue / maxValue).toFloat()
//                } else 0f
//            }
//        }
//    }
//
//    // Animate progress
//    val animatedProgress by animateFloatAsState(
//        targetValue = progress,
//        animationSpec = tween(
//            durationMillis = styling?.animationDuration?.toIntOrNull() ?: 350,
//            easing = FastOutSlowInEasing
//        ),
//        label = "progress"
//    )
//
//    // Get current step label
//    val currentLabel = when {
//        currentStep == 0 -> stepLabels.firstOrNull() ?: "Start"
//        currentStep < stepLabels.size -> stepLabels[currentStep]
//        currentStep == totalSteps -> "Completed"
//        else -> "Step $currentStep"
//    }
//
//    // Create milestone points with colors
//    val uiMilestones = milestoneValues.mapIndexed { i, value ->
//        val defaultIcons = listOf("flag.fill", "star.circle.fill", "bolt.fill", "trophy.fill")
//        val defaultColors = listOf(Color.Blue, Color(0xFFFFA500), Color(0xFF800080), Color(0xFFFFC0CB))
//
//        val iconName = styling?.icons?.getOrNull(i) ?: defaultIcons.getOrNull(i) ?: "flag.fill"
//        val colorString = styling?.iconColors?.getOrNull(i)
//        val color = try {
//            colorString?.let { Color(it.toColorInt()) } ?: defaultColors.getOrNull(i) ?: Color.Blue
//        } catch (e: Exception) {
//            defaultColors.getOrNull(i) ?: Color.Blue
//        }
//
//        MilestonePoint(
//            value = value,
//            icon = iconName,
//            color = color
//        )
//    }
//
//    val backgroundColor = try {
//        styling?.backgroundColor?.let { Color(it.toColorInt()) } ?: Color.White
//    } catch (e: Exception) {
//        Color.White
//    }
//
//    val textColor = try {
//        styling?.textColor?.let { Color(it.toColorInt()) } ?: Color.Black
//    } catch (e: Exception) {
//        Color.Black
//    }
//
//    val currencySymbol = styling?.currencySymbol ?: "₹"
//    val showCurrency = styling?.showCurrency ?: true
//
//    Row(
//        modifier = modifier
//            .fillMaxWidth()
//            .background(backgroundColor, RoundedCornerShape(12.dp))
//            .padding(
//                start = styling?.marginLeft?.toIntOrNull()?.dp ?: 16.dp,
//                end = styling?.marginRight?.toIntOrNull()?.dp ?: 16.dp,
//                top = styling?.marginTop?.toIntOrNull()?.dp ?: 12.dp,
//                bottom = styling?.marginBottom?.toIntOrNull()?.dp ?: 12.dp
//            ),
//        verticalAlignment = Alignment.Top,
//        horizontalArrangement = Arrangement.spacedBy(8.dp)
//    ) {
//        // Dollar/Currency icon
//        Icon(
//            painter = painterResource(id = R.drawable.volume), // Replace with actual dollar icon
//            contentDescription = "Currency",
//            modifier = Modifier.size(40.dp),
//            tint = Color(0xFFFFD700) // Gold color
//        )
//
//        Column(
//            modifier = Modifier.weight(1f),
//            verticalArrangement = Arrangement.spacedBy(8.dp)
//        ) {
//            // Current step label
//            Text(
//                text = currentLabel,
//                fontSize = 14.sp,
//                fontWeight = FontWeight.Medium,
//                color = textColor
//            )
//
//            // Progress gauge
//            MilestoneGauge(
//                progress = animatedProgress,
//                milestones = uiMilestones,
//                progressColor = try {
//                    styling?.progressColor?.let { Color(it.toColorInt()) } ?: Color.Green
//                } catch (e: Exception) {
//                    Color.Green
//                },
//                trackColor = try {
//                    styling?.trackColor?.let { Color(it.toColorInt()) } ?: Color.LightGray.copy(alpha = 0.3f)
//                } catch (e: Exception) {
//                    Color.LightGray.copy(alpha = 0.3f)
//                },
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .height(16.dp)
//            )
//
//            // Step labels with values
//            if (showCurrency) {
//                StepLabelsBar(
//                    milestoneValues = milestoneValues,
//                    currencySymbol = currencySymbol,
//                    textColor = textColor,
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .height(20.dp)
//                )
//            }
//        }
//    }
//}
//
//@Composable
//private fun StepLabelsBar(
//    milestoneValues: List<Double>,
//    currencySymbol: String,
//    textColor: Color,
//    modifier: Modifier = Modifier
//) {
//    Box(modifier = modifier) {
//        if (milestoneValues.isNotEmpty()) {
//            val maxValue = milestoneValues.lastOrNull() ?: 1.0
//
//            milestoneValues.forEachIndexed { index, value ->
//                val percent = (value / maxValue).toFloat()
//
//                Box(
//                    modifier = Modifier
//                        .fillMaxWidth(percent)
//                        .fillMaxHeight(),
//                    contentAlignment = Alignment.CenterEnd
//                ) {
//                    Text(
//                        text = "$currencySymbol${value.toInt()}",
//                        fontSize = 10.sp,
//                        color = textColor,
//                        fontWeight = FontWeight.Medium
//                    )
//                }
//            }
//        }
//    }
//}
//
//@Composable
//private fun MilestoneGauge(
//    progress: Float,
//    milestones: List<MilestonePoint>,
//    progressColor: Color,
//    trackColor: Color,
//    modifier: Modifier = Modifier
//) {
//    Box(modifier = modifier) {
//        Canvas(modifier = Modifier.fillMaxSize()) {
//            val width = size.width
//            val height = size.height
//            val progressWidth = width * progress
//            val maxValue = milestones.lastOrNull()?.value ?: 1.0
//
//            // Background track (capsule shape)
//            drawRoundRect(
//                color = trackColor,
//                size = Size(width, height),
//                cornerRadius = androidx.compose.ui.geometry.CornerRadius(height / 2)
//            )
//
//            // Progress fill (capsule shape)
//            if (progressWidth > 0) {
//                drawRoundRect(
//                    color = progressColor,
//                    size = Size(progressWidth, height),
//                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(height / 2)
//                )
//            }
//        }
//
//        // Animated stripes overlay
//        if (progress > 0) {
//            Box(
//                modifier = Modifier
//                    .fillMaxWidth(progress)
//                    .fillMaxHeight()
//                    .clip(RoundedCornerShape(percent = 50))
//            ) {
//                StripedPattern()
//            }
//        }
//
//        // Milestone icons
//        milestones.forEach { milestone ->
//            val maxValue = milestones.lastOrNull()?.value ?: 1.0
//            val percent = (milestone.value / maxValue).toFloat()
//
//            Box(
//                modifier = Modifier
//                    .fillMaxWidth(percent)
//                    .fillMaxHeight(),
//                contentAlignment = Alignment.CenterEnd
//            ) {
//                MilestoneIcon(
//                    iconName = milestone.icon,
//                    color = milestone.color,
//                    modifier = Modifier.size(24.dp)
//                )
//            }
//        }
//    }
//}
//
//@Composable
//private fun MilestoneIcon(
//    iconName: String,
//    color: Color,
//    modifier: Modifier = Modifier
//) {
//    // Map icon names to drawable resources
//    val iconRes = when (iconName) {
//        "flag.fill" -> android.R.drawable.ic_menu_compass
//        "star.circle.fill" -> android.R.drawable.btn_star_big_on
//        "bolt.fill" -> android.R.drawable.ic_menu_add
//        "trophy.fill" -> android.R.drawable.btn_star
//        else -> android.R.drawable.ic_menu_compass
//    }
//
//    Box(
//        modifier = modifier
//            .background(Color.White, CircleShape)
//            .padding(2.dp),
//        contentAlignment = Alignment.Center
//    ) {
//        Icon(
//            painter = painterResource(id = iconRes),
//            contentDescription = iconName,
//            tint = color,
//            modifier = Modifier.size(16.dp)
//        )
//    }
//}
//
//@Composable
//private fun StripedPattern() {
//    val infiniteTransition = rememberInfiniteTransition(label = "stripes")
//
//    val animationOffset by infiniteTransition.animateFloat(
//        initialValue = 0f,
//        targetValue = 40f,
//        animationSpec = infiniteRepeatable(
//            animation = tween(1333, easing = LinearEasing),
//            repeatMode = RepeatMode.Restart
//        ),
//        label = "stripe_offset"
//    )
//
//    Canvas(modifier = Modifier.fillMaxSize()) {
//        val stripeWidth = 20f
//        val stripeSpacing = 20f
//        val totalWidth = stripeWidth + stripeSpacing
//        val startX = -totalWidth + animationOffset
//        val height = size.height
//
//        var x = startX
//        while (x < size.width + totalWidth) {
//            val path = Path().apply {
//                moveTo(x, 0f)
//                lineTo(x + stripeWidth, 0f)
//                lineTo(x + stripeWidth + height, height)
//                lineTo(x + height, height)
//                close()
//            }
//
//            drawPath(
//                path = path,
//                color = Color.White.copy(alpha = 0.25f),
//                style = Fill
//            )
//
//            x += totalWidth
//        }
//    }
//}
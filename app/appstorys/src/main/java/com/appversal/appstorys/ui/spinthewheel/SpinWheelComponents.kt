package com.appversal.appstorys.ui.spinwheel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.appversal.appstorys.api.WheelSlice

/**
 * Composable to render the spinning wheel with slices including text and images
 */
@Composable
fun WheelView(
    slices: List<WheelSlice>,
    rotation: Float,
    wheelImage: String?,
    @Suppress("UNUSED_PARAMETER") backgroundColor: String?,
    borderColor: Color = Color.White,
    borderWidth: Int = 5,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (!wheelImage.isNullOrBlank()) {
            // Use custom wheel image
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(wheelImage)
                    .crossfade(true)
                    .build(),
                contentDescription = "Wheel",
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(rotation),
                contentScale = ContentScale.Fit,
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(40.dp))
                    }
                }
            )
        } else {
            // Draw wheel programmatically with slices
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(rotation),
                contentAlignment = Alignment.Center
            ) {
                // Background canvas for slices
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (slices.isEmpty()) return@Canvas

                    val sliceAngle = 360f / slices.size
                    val radius = size.minDimension / 2
                    val centerOffset = center

                    // Draw wheel base background color from backend
                    backgroundColor?.let { bg ->
                        try {
                            val parsed = Color(
                                android.graphics.Color.parseColor(
                                    if (bg.startsWith("#")) bg else "#$bg"
                                )
                            )
                            drawCircle(
                                color = parsed,
                                radius = radius,
                                center = centerOffset
                            )
                        } catch (_: Exception) {
                            // ignore invalid color
                        }
                    }

                    slices.forEachIndexed { index, slice ->
                        val startAngle = index * sliceAngle - 90f
                        val sweepAngle = sliceAngle

                        // Parse slice color from backend styling
                        val sliceStylingColor = slice.styling?.wheelStyling?.color?.background
                        val sliceColor = parseSliceColor(
                            sliceStylingColor,
                            index,
                            slices.size
                        )

                        // Get slice stroke color and width from styling
                        val sliceStrokeColor = try {
                            val strokeColorString = slice.styling?.wheelStyling?.color?.stroke
                            strokeColorString?.let {
                                Color(android.graphics.Color.parseColor(
                                    if (it.startsWith("#")) it else "#$it"
                                ))
                            } ?: Color.White
                        } catch (_: Exception) {
                            Color.White
                        }
                        val sliceStrokeWidth = slice.styling?.wheelStyling?.strokeWidth ?: 2

                        // Draw slice with gradient for 3D effect
                        drawArc(
                            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                colors = listOf(
                                    sliceColor.copy(alpha = 0.95f),
                                    sliceColor,
                                    sliceColor.copy(alpha = 0.85f)
                                ),
                                center = centerOffset,
                                radius = radius
                            ),
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = true,
                            size = size
                        )

                        // Draw slice border using stroke color and width from backend
                        drawArc(
                            color = sliceStrokeColor,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = true,
                            size = size,
                            style = Stroke(width = sliceStrokeWidth.toFloat())
                        )
                    }

                    // Draw outer ring for premium look (using border styling from backend)
                    drawCircle(
                        color = borderColor,
                        radius = radius,
                        center = centerOffset,
                        style = Stroke(width = borderWidth.toFloat() * 2)
                    )

                    // Draw inner decorative ring
                    drawCircle(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFFFFF),
                                Color(0xFFF5F5F5),
                                Color(0xFFE0E0E0)
                            ),
                            center = centerOffset,
                            radius = radius * 0.2f
                        ),
                        radius = radius * 0.2f,
                        center = centerOffset
                    )

                    // Draw center circle border
                    drawCircle(
                        color = borderColor,
                        radius = radius * 0.2f,
                        center = centerOffset,
                        style = Stroke(width = 3f)
                    )

                    // Draw center dot
                    drawCircle(
                        color = Color(0xFF6200EE),
                        radius = radius * 0.08f,
                        center = centerOffset
                    )
                }

                // Render text labels and images on each slice
                slices.forEachIndexed { index, slice ->
                    WheelSliceContent(
                        slice = slice,
                        index = index,
                        totalSlices = slices.size,
                        wheelSizeDp = 300.dp,
                        modifier = Modifier.fillMaxSize()
                    )

                }
            }
        }

        // Enhanced Pointer/Indicator at top with shadow
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-8).dp)
        ) {
            WheelPointer()
        }
    }
}

/**
 * Renders content (text and image) for a single wheel slice
 */
@Composable
private fun WheelSliceContent(
    slice: WheelSlice,
    index: Int,
    totalSlices: Int,
    wheelSizeDp: Dp,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        val sliceAngle = 360f / totalSlices
        // Calculate the MIDDLE of the slice, not the start
        val sliceMiddleAngle = (index * sliceAngle) + (sliceAngle / 2f)

        // Calculate position for content (65% from center)
        val angleInRadians = Math.toRadians((sliceMiddleAngle - 90).toDouble())
        val contentRadiusPercent = 0.65f

        val radius = wheelSizeDp.value / 2f


        // Get styling from backend
        val sliceStyling = slice.styling?.wheelStyling
        val priceLabelStyle = sliceStyling?.priceLabel?.textStyle

        // Text color from backend styling or default white
        val textColor = try {
            val colorString = priceLabelStyle?.color
            colorString?.let {
                Color(android.graphics.Color.parseColor(
                    if (it.startsWith("#")) it else "#$it"
                ))
            } ?: Color.White
        } catch (_: Exception) {
            Color.White
        }

        // Font size from backend styling
        val fontSize = priceLabelStyle?.fontSize ?: 10

        Box(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.Center)
                .offset(
                    x = (contentRadiusPercent * radius * kotlin.math.cos(angleInRadians)).dp,
                    y = (contentRadiusPercent * radius * kotlin.math.sin(angleInRadians)).dp
                )
                .size(80.dp)
                .rotate(sliceMiddleAngle), // Rotate to middle angle
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                // Get image styling from backend
                val imageStyling = sliceStyling?.image
                val imageCornerRadius = imageStyling?.cornerRadius
                val imageRotation = imageStyling?.rotation ?: 0

                // Determine the shape based on corner radius
                val imageShape = if (imageCornerRadius != null) {
                    RoundedCornerShape(
                        topStart = (imageCornerRadius.topLeft ?: 12).dp,
                        topEnd = (imageCornerRadius.topRight ?: 12).dp,
                        bottomStart = (imageCornerRadius.bottomLeft ?: 12).dp,
                        bottomEnd = (imageCornerRadius.bottomRight ?: 12).dp
                    )
                } else {
                    RoundedCornerShape(8.dp)
                }

                // Render image if available (using sliceMedia field from backend)
                if (!slice.sliceMedia.isNullOrBlank()) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(slice.sliceMedia)
                            .crossfade(true)
                            .build(),
                        contentDescription = slice.prizeLabel,
                        modifier = Modifier
                            .size(36.dp)
                            .rotate(imageRotation.toFloat())
                            .clip(imageShape)
                            .background(Color.White.copy(alpha = 0.2f), imageShape),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.2f), imageShape),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            }
                        },
                        error = {
                            // Show placeholder emoji if image fails to load
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.15f), imageShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (slice.noPrize == true) "😔" else "🎁",
                                    fontSize = 18.sp
                                )
                            }
                        }
                    )

                    if (!slice.prizeLabel.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                // Render label (prizeLabel is used for both prize and no-prize text from backend)
                val displayLabel = slice.prizeLabel ?: if (slice.noPrize == true) "Better Luck" else null

                if (!displayLabel.isNullOrBlank()) {
                    Text(
                        text = displayLabel,
                        color = textColor,
                        fontSize = fontSize.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        lineHeight = (fontSize + 1).sp,
                        modifier = Modifier.width(70.dp),
                        style = TextStyle(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.5f),
                                offset = Offset(1f, 1f),
                                blurRadius = 2f
                            )
                        )
                    )
                }
            }
        }
    }
}

/**
 * Triangle pointer indicator for the wheel with enhanced styling
 */
@Composable
fun WheelPointer(
    color: Color = Color(0xFFFF1744)
) {
    Box(
        modifier = Modifier.size(width = 40.dp, height = 40.dp)
    ) {
        // Shadow layer
        Canvas(
            modifier = Modifier
                .size(width = 40.dp, height = 40.dp)
                .offset(y = 2.dp)
        ) {
            val shadowPath = Path().apply {
                moveTo(size.width / 2, size.height)
                lineTo(0f, 0f)
                lineTo(size.width, 0f)
                close()
            }
            drawPath(
                path = shadowPath,
                color = Color.Black.copy(alpha = 0.3f)
            )
        }

        // Main pointer with gradient
        Canvas(
            modifier = Modifier.size(width = 40.dp, height = 40.dp)
        ) {
            val path = Path().apply {
                moveTo(size.width / 2, size.height)
                lineTo(0f, 0f)
                lineTo(size.width, 0f)
                close()
            }
            drawPath(
                path = path,
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFF1744),
                        Color(0xFFFF4081)
                    )
                )
            )
            // Border
            drawPath(
                path = path,
                color = Color.White,
                style = Stroke(width = 3f)
            )
        }
    }
}

/**
 * Parse color string with fallback to modern vibrant colors
 */
private fun parseSliceColor(colorString: String?, index: Int, totalSlices: Int): Color {
    return try {
        if (!colorString.isNullOrBlank()) {
            Color(android.graphics.Color.parseColor(
                if (colorString.startsWith("#")) colorString else "#$colorString"
            ))
        } else {
            // Generate vibrant modern colors palette
            val modernColors = listOf(
                Color(0xFFFF1744), // Vibrant Red
                Color(0xFFE91E63), // Pink
                Color(0xFF9C27B0), // Purple
                Color(0xFF673AB7), // Deep Purple
                Color(0xFF3F51B5), // Indigo
                Color(0xFF2196F3), // Blue
                Color(0xFF00BCD4), // Cyan
                Color(0xFF009688), // Teal
                Color(0xFF4CAF50), // Green
                Color(0xFF8BC34A), // Light Green
                Color(0xFFFFEB3B), // Yellow
                Color(0xFFFFC107), // Amber
                Color(0xFFFF9800), // Orange
                Color(0xFFFF5722)  // Deep Orange
            )
            modernColors[index % modernColors.size]
        }
    } catch (_: Exception) {
        // Fallback to modern colors
        val modernColors = listOf(
            Color(0xFFFF1744), Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7),
            Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF00BCD4), Color(0xFF009688),
            Color(0xFF4CAF50), Color(0xFF8BC34A), Color(0xFFFFEB3B), Color(0xFFFFC107),
            Color(0xFFFF9800), Color(0xFFFF5722)
        )
        modernColors[index % modernColors.size]
    }
}


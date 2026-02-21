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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Fill
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
import kotlin.math.min

/**
 * Composable to render the spinning wheel with slices including text and images
 */
@Composable
fun WheelView(
    modifier: Modifier = Modifier,
    slices: List<WheelSlice>,
    rotation: Float,
    wheelImage: String?,
    backgroundColor: String?,
    borderColor: Color = Color.White,
    borderWidth: Int = 5,
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

                    val fullRadius = size.minDimension / 2
                    val gapPercent = 0.04f
                    val gapBetweenRingAndWheel = fullRadius * gapPercent
                    val sliceRadius = fullRadius - gapBetweenRingAndWheel

                    val centerOffset = center

                    // Draw wheelbase background color from backend
                    backgroundColor?.let { bg ->
                        try {
                            val parsed = Color(
                                android.graphics.Color.parseColor(
                                    if (bg.startsWith("#")) bg else "#$bg"
                                )
                            )
                            drawCircle(
                                color = parsed,
                                radius = fullRadius,
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

                        val cornerRadiusConfig = slice.styling?.wheelStyling?.cornerRadius

                        val outerCornerRadiusDp = maxOf(
                            cornerRadiusConfig?.topLeft ?: 0,
                            cornerRadiusConfig?.topRight ?: 0
                        )

                        val cornerPx = min(
                            outerCornerRadiusDp.dp.toPx(),
                            sliceRadius * 0.25f
                        )

                        val path = Path()

                        val outerRect = Rect(
                            centerOffset.x - sliceRadius,
                            centerOffset.y - sliceRadius,
                            centerOffset.x + sliceRadius,
                            centerOffset.y + sliceRadius
                        )

// Convert angles to radians
                        val startRad = Math.toRadians(startAngle.toDouble())
                        val endRad = Math.toRadians((startAngle + sweepAngle).toDouble())

// Points exactly on the outer arc corners
                        val startOuter = Offset(
                            (centerOffset.x + sliceRadius * kotlin.math.cos(startRad)).toFloat(),
                            (centerOffset.y + sliceRadius * kotlin.math.sin(startRad)).toFloat()
                        )

                        val endOuter = Offset(
                            (centerOffset.x + sliceRadius * kotlin.math.cos(endRad)).toFloat(),
                            (centerOffset.y + sliceRadius * kotlin.math.sin(endRad)).toFloat()
                        )

                        // Points along each radial line, pulled back from the outer arc by cornerPx
                        // (these are where the straight radial lines end before the rounded corner begins)
                        val startTrim = Offset(
                            (centerOffset.x + (sliceRadius - cornerPx) * kotlin.math.cos(startRad)).toFloat(),
                            (centerOffset.y + (sliceRadius - cornerPx) * kotlin.math.sin(startRad)).toFloat()
                        )

                        val endTrim = Offset(
                            (centerOffset.x + (sliceRadius - cornerPx) * kotlin.math.cos(endRad)).toFloat(),
                            (centerOffset.y + (sliceRadius - cornerPx) * kotlin.math.sin(endRad)).toFloat()
                        )

                        // cornerAngleDelta (radians) = arc length consumed by one rounded corner
                        // We trim this amount from BOTH the start and end of the arc so that
                        // each radial-to-arc junction has room for a smooth quadratic curve.
                        val cornerAngleDelta = (cornerPx / sliceRadius).toFloat() // radians
                        val cornerAngleDeg = Math.toDegrees(cornerAngleDelta.toDouble()).toFloat()

                        // Point on the arc just AFTER the start corner (arc entry)
                        val startArcEntryRad = startRad + cornerAngleDelta
                        val startArcEntry = Offset(
                            (centerOffset.x + sliceRadius * kotlin.math.cos(startArcEntryRad)).toFloat(),
                            (centerOffset.y + sliceRadius * kotlin.math.sin(startArcEntryRad)).toFloat()
                        )

                        // The arc is trimmed on both ends by cornerAngleDeg via safeSweep below

                        // Guard: if cornerAngleDeg is too large for the sweep, skip rounding
                        val safeSweep = sweepAngle - 2f * cornerAngleDeg

                        // Build path with symmetric outer arc corner rounding:
                        //  center → straight radial to startTrim
                        //  → quadratic(startOuter → startArcEntry)  [rounds start corner]
                        //  → arcTo from startArcEntry to endArcExit [main arc, trimmed both ends]
                        //  → quadratic(endOuter → endTrim)           [rounds end corner]
                        //  → straight radial back to center
                        path.moveTo(centerOffset.x, centerOffset.y)

                        if (safeSweep > 0f && cornerPx > 0f) {
                            // Straight radial line, stops cornerPx short of the outer arc
                            path.lineTo(startTrim.x, startTrim.y)

                            // Round start outer corner
                            path.quadraticTo(
                                startOuter.x, startOuter.y,
                                startArcEntry.x, startArcEntry.y
                            )

                            // Main arc: starts after start-corner, stops before end-corner
                            path.arcTo(
                                rect = outerRect,
                                startAngleDegrees = startAngle + cornerAngleDeg,
                                sweepAngleDegrees = safeSweep,
                                forceMoveTo = false
                            )

                            // Round end outer corner
                            path.quadraticTo(
                                endOuter.x, endOuter.y,
                                endTrim.x, endTrim.y
                            )

                            // Straight radial line back to center
                            path.lineTo(centerOffset.x, centerOffset.y)
                        } else {
                            // No corner rounding — plain pie slice
                            path.lineTo(startOuter.x, startOuter.y)
                            path.arcTo(
                                rect = outerRect,
                                startAngleDegrees = startAngle,
                                sweepAngleDegrees = sweepAngle,
                                forceMoveTo = false
                            )
                            path.lineTo(centerOffset.x, centerOffset.y)
                        }

                        path.close()

                        drawPath(
                            path = path,
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    sliceColor.copy(alpha = 0.95f),
                                    sliceColor,
                                    sliceColor.copy(alpha = 0.85f)
                                ),
                                center = centerOffset,
                                radius = sliceRadius
                            )
                        )

                        drawPath(
                            path = path,
                            color = sliceStrokeColor,
                            style = Stroke(
                                width = sliceStrokeWidth.toFloat(),
                            )
                        )
                    }

                    // Draw outer ring for premium look (using border styling from backend)
                    drawCircle(
                        color = borderColor,
                        radius = fullRadius - borderWidth.dp.toPx() / 2,
                        center = centerOffset,
                        style = Stroke(width = borderWidth.dp.toPx())
                    )


                    // Draw inner decorative ring
                    // ---- CENTER HUB DESIGN (Industry Standard) ----

                    val hubOuterRadius = fullRadius * 0.10f
                    val hubInnerRadius = fullRadius * 0.07f
                    val hubCoreRadius  = fullRadius * 0.04f

                    // 1️⃣ Subtle inner shadow for depth
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.15f)
                            ),
                            center = centerOffset,
                            radius = hubOuterRadius
                        ),
                        radius = hubOuterRadius,
                        center = centerOffset
                    )

                    // 2️⃣ Metallic outer hub
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFDFDFD),
                                Color(0xFFEAEAEA),
                                Color(0xFFD6D6D6)
                            ),
                            center = centerOffset,
                            radius = hubOuterRadius
                        ),
                        radius = hubOuterRadius,
                        center = centerOffset
                    )

                    // 3️⃣ Hub border (scaled properly)
                    drawCircle(
                        color = borderColor.copy(alpha = 0.8f),
                        radius = hubOuterRadius,
                        center = centerOffset,
                        style = Stroke(width = fullRadius * 0.015f)
                    )

                    // 4️⃣ Inner hub layer (adds depth)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White,
                                Color(0xFFE0E0E0)
                            ),
                            center = centerOffset,
                            radius = hubInnerRadius
                        ),
                        radius = hubInnerRadius,
                        center = centerOffset
                    )

                    // 5️⃣ Core accent (brand color / premium center)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF7C4DFF),
                                Color(0xFF5E35B1)
                            ),
                            center = centerOffset,
                            radius = hubCoreRadius
                        ),
                        radius = hubCoreRadius,
                        center = centerOffset
                    )

                }

                // Render text labels and images on each slice
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val actualWheelSize = minOf(this.maxWidth, this.maxHeight)
                    slices.forEachIndexed { index, slice ->
                        WheelSliceContent(
                            slice = slice,
                            index = index,
                            totalSlices = slices.size,
                            wheelSizeDp = actualWheelSize,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
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
 * Renders content (text and image) for a single wheel slice.
 * Text is placed OUTWARD (near the rim) and image is placed INWARD (near the center).
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
        val sliceMiddleAngle = (index * sliceAngle) + (sliceAngle / 2f)
        val angleInRadians = Math.toRadians((sliceMiddleAngle - 90).toDouble())

        val gapBetweenRingAndWheel = (wheelSizeDp.value / 2f) * 0.06f
        val radius = (wheelSizeDp.value / 2f) - gapBetweenRingAndWheel

        // Get styling from backend
        val sliceStyling = slice.styling?.wheelStyling
        val priceLabelStyle = sliceStyling?.priceLabel?.textStyle
        val priceLabelMargin = sliceStyling?.priceLabel?.margin

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

        // Dynamic font scaling based on wheel size
        val dynamicFontSize = (wheelSizeDp.value * 0.045f)

        // Use backend value if present, otherwise dynamic
        val fontSize = priceLabelStyle?.fontSize?.toFloat()
            ?: dynamicFontSize

        // Rotation so content reads along the slice direction (pointing outward)
        val contentRotation = when {
            sliceMiddleAngle > 90 && sliceMiddleAngle <= 270 -> sliceMiddleAngle + 180f
            else -> sliceMiddleAngle
        }

        // ── Percentage-based sizes — scale with the wheel, no hardcoded dp ──────
        val imageSize   = wheelSizeDp * 0.15f   // 15% of wheel diameter
        val textWidth   = wheelSizeDp * 0.28f   // 28% of wheel diameter
        val imageRadiusPercent = 0.50f
        val textRadiusPercent  = 0.85f

        val imageStyling = sliceStyling?.image
        val imageCornerRadius = imageStyling?.cornerRadius
        val imageRotation = imageStyling?.rotation ?: 0
        val sliceMargin = sliceStyling?.margin

        val imageShape = if (imageCornerRadius != null) {
            RoundedCornerShape(
                topStart = (imageCornerRadius.topLeft ?: 8).dp,
                topEnd = (imageCornerRadius.topRight ?: 8).dp,
                bottomStart = (imageCornerRadius.bottomLeft ?: 8).dp,
                bottomEnd = (imageCornerRadius.bottomRight ?: 8).dp
            )
        } else {
            RoundedCornerShape(8.dp)
        }

        val imageUrl = slice.sliceMedia?.takeIf { it.isNotBlank() }
            ?: slice.rewards?.firstOrNull()?.sliceRewardMedia?.takeIf { it.isNotBlank() }

        if (!imageUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentSize(Alignment.Center)
                    .offset(
                        x = (imageRadiusPercent * radius * kotlin.math.cos(angleInRadians)).dp,
                        y = (imageRadiusPercent * radius * kotlin.math.sin(angleInRadians)).dp
                    )
                    .size(imageSize)
                    .rotate(contentRotation + imageRotation.toFloat()),
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = slice.prizeLabel,
                    modifier = Modifier
                        .padding(
                            top = (sliceMargin?.top ?: 0).dp,
                            bottom = (sliceMargin?.bottom ?: 0).dp,
                            start = (sliceMargin?.left ?: 0).dp,
                            end = (sliceMargin?.right ?: 0).dp
                        )
                        .fillMaxSize()
                        .clip(imageShape)
                        .background(Color.White.copy(alpha = 0.2f), imageShape),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
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
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
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
            }
        }

        // ── TEXT — placed OUTWARD (72% of radius from center, near rim) ─────────
        val displayLabel = slice.prizeLabel ?: if (slice.noPrize == true) "Better Luck" else null

        if (!displayLabel.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentSize(Alignment.Center)
                    .offset(
                        x = (textRadiusPercent * radius * kotlin.math.cos(angleInRadians)).dp,
                        y = (textRadiusPercent * radius * kotlin.math.sin(angleInRadians)).dp
                    )
                    .width(textWidth)
                    .rotate(contentRotation),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayLabel,
                    color = textColor,
                    fontSize = fontSize.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    lineHeight = (fontSize + 1).sp,
                    modifier = Modifier
                        .width(textWidth)
                        .padding(
                            top = (priceLabelMargin?.top ?: 0).dp,
                            bottom = (priceLabelMargin?.bottom ?: 0).dp,
                            start = (priceLabelMargin?.left ?: 0).dp,
                            end = (priceLabelMargin?.right ?: 0).dp
                        ),
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
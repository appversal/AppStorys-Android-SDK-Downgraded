package com.appversal.appstorys.ui.scratchcard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaPlayer
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt
import androidx.core.graphics.scale
import androidx.core.graphics.createBitmap

@Composable
fun ScratchableCard(
    cardWidth: Dp,
    cardHeight: Dp,
    points: List<Offset>,
    isRevealed: Boolean,
    overlayImageUrl: String,
    bannerImageUrl: String,
    offerTitle: String,
    offerSubtitle: String,
    couponCode: String,
    couponBgColor: String,
    couponBorderColor: String,
    couponTextColor: String,
    rewardBgColor: String,
    offerTitleColor: String,
    offerSubtitleColor: String,
    onlyImage: Boolean,
    soundFileUrl: String,
    onPointsChanged: (List<Offset>) -> Unit,
    onCellTouched: (Int) -> Unit,
    gridCols: Int,
    gridRows: Int,
    haptics: Boolean,
    customSoundEnabled: Boolean,
    titleFontSize: Int,
    subtitleFontSize: Int,
    // New styling parameters for title
    offerTitleFontFamily: String = "",
    offerTitleFontDecoration: List<String> = listOf(),
    offerTitleTextAlign: String = "center",
    offerTitleMarginTop: Dp = 0.dp,
    offerTitleMarginBottom: Dp = 0.dp,
    offerTitleMarginLeft: Dp = 0.dp,
    offerTitleMarginRight: Dp = 0.dp,
    // New styling parameters for subtitle
    offerSubtitleFontFamily: String = "",
    offerSubtitleFontDecoration: List<String> = listOf(),
    offerSubtitleTextAlign: String = "center",
    offerSubtitleMarginTop: Dp = 0.dp,
    offerSubtitleMarginBottom: Dp = 0.dp,
    offerSubtitleMarginLeft: Dp = 0.dp,
    offerSubtitleMarginRight: Dp = 0.dp,
    // Coupon styling parameters
    couponBorderWidth: Int = 1,
    couponAlignment: String = "center",
    couponCtaFullWidth: Boolean = false,
    couponCtaWidth: Dp = Dp.Unspecified,
    couponHeight: Dp = Dp.Unspecified,
    couponFontSize: Int = 14,
    couponFontFamily: String = "",
    couponFontDecoration: List<String> = listOf(),
    couponTopLeft: Dp = 8.dp,
    couponTopRight: Dp = 8.dp,
    couponBottomLeft: Dp = 8.dp,
    couponBottomRight: Dp = 8.dp,
    couponMarginTop: Dp = 0.dp,
    couponMarginBottom: Dp = 0.dp,
    couponMarginLeft: Dp = 0.dp,
    couponMarginRight: Dp = 0.dp,

    // Image styling
    imageWidth: Dp = Dp.Unspecified,
    imageHeight: Dp = Dp.Unspecified,
    imageTopLeft: Dp = 0.dp,
    imageTopRight: Dp = 0.dp,
    imageBottomLeft: Dp = 0.dp,
    imageBottomRight: Dp = 0.dp,
    imageMarginTop: Dp = 0.dp,
    imageMarginBottom: Dp = 0.dp,
    imageMarginLeft: Dp = 0.dp,
    imageMarginRight: Dp = 0.dp,
) {
    val context = LocalContext.current

    var measuredHeightPx by remember { mutableStateOf(0) }

    val cardWidthPx = with(LocalDensity.current) { cardWidth.toPx() }.toInt()
    val cardHeightPx = measuredHeightPx
    val coroutineScope = rememberCoroutineScope()

    // Media player for sound
    val mediaPlayer = remember {
        MediaPlayer().apply {
            setOnPreparedListener {
                // Ready to play
            }
            setOnErrorListener { _, what, extra ->
                Log.e("ScratchCard", "MediaPlayer error: what=$what, extra=$extra")
                true
            }
        }
    }

    // Vibrator for haptic feedback
    val vibrator = remember {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: SecurityException) {
            Log.e("ScratchCard", "Vibrator permission not granted: ${e.message}")
            null
        }
    }

    // Track if sound has been loaded and played
    var soundLoaded by remember { mutableStateOf(false) }
    var hasPlayedEffects by remember { mutableStateOf(false) }

    // Load sound file
    LaunchedEffect(soundFileUrl, customSoundEnabled) {
        if (customSoundEnabled && soundFileUrl.isNotEmpty() && !soundLoaded) {
            try {
                withContext(Dispatchers.IO) {
                    mediaPlayer.reset()
                    mediaPlayer.setDataSource(soundFileUrl)
                    mediaPlayer.prepareAsync()
                }
                soundLoaded = true
            } catch (e: Exception) {
                Log.e("ScratchCard", "Error loading sound: ${e.message}")
            }
        }
    }

    // Play sound and vibrate when scratching is complete
    LaunchedEffect(isRevealed) {
        if (isRevealed && !hasPlayedEffects) {
            hasPlayedEffects = true

            // Play sound
            if (customSoundEnabled) {
                coroutineScope.launch {
                    try {
                        if (soundLoaded && !mediaPlayer.isPlaying) {
                            mediaPlayer.start()
                        }
                    } catch (e: Exception) {
                        Log.e("ScratchCard", "Error playing sound: ${e.message}")
                    }
                }
            }

            // Vibrate once
            if (haptics) {
                try {
                    vibrator?.let {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            it.vibrate(
                                VibrationEffect.createOneShot(
                                    200,
                                    VibrationEffect.DEFAULT_AMPLITUDE
                                )
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            it.vibrate(200)
                        }
                    }
                } catch (e: SecurityException) {
                    Log.e("ScratchCard", "Vibration permission error: ${e.message}")
                } catch (e: Exception) {
                    Log.e("ScratchCard", "Error vibrating: ${e.message}")
                }
            }
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer.release()
            } catch (e: Exception) {
                Log.e("ScratchCard", "Error releasing media player: ${e.message}")
            }
        }
    }

    val scratchBitmap = remember(cardWidthPx, cardHeightPx) {
        if (cardHeightPx > 0) {
            createBitmap(cardWidthPx, cardHeightPx)
                .apply { eraseColor(Color.Gray.toArgb()) }
        } else null
    }


    val scratchCanvas = remember(scratchBitmap) {
        scratchBitmap?.let { android.graphics.Canvas(it) }
    }

    // Improved eraser paint with larger stroke for smoother scratching
    val eraserPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            isDither = true
            color = android.graphics.Color.TRANSPARENT
            xfermode = android.graphics.PorterDuffXfermode(
                android.graphics.PorterDuff.Mode.CLEAR
            )
            strokeWidth = 120f
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
            style = android.graphics.Paint.Style.STROKE
        }
    }

    // Circle paint for filling gaps
    val circlePaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            isDither = true
            color = android.graphics.Color.TRANSPARENT
            xfermode = android.graphics.PorterDuffXfermode(
                android.graphics.PorterDuff.Mode.CLEAR
            )
            style = android.graphics.Paint.Style.FILL
        }
    }

    // Overlay image
    var overlayBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(overlayImageUrl, cardWidthPx, cardHeightPx) {
        if (overlayImageUrl.isNotEmpty() && cardWidthPx > 0 && cardHeightPx > 0) {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(overlayImageUrl)
                // Decode straight to the card's pixel size. With no size and no view
                // target Coil decodes at full resolution, so a large cover allocates its
                // entire ARGB_8888 bitmap (6000x4000 = ~92 MB) before being scaled down.
                .size(cardWidthPx, cardHeightPx)
                .allowHardware(false)
                .build()

            val result = loader.execute(request)
            val bmp = (result.drawable as? BitmapDrawable)?.bitmap

            bmp?.let {
                overlayBitmap = it.scale(cardWidthPx, cardHeightPx)
                scratchCanvas?.drawBitmap(overlayBitmap!!, 0f, 0f, null)
            }
        }
    }

    Box(
        modifier = Modifier
            .width(cardWidth)
            .wrapContentHeight()
            .onSizeChanged {
                measuredHeightPx = it.height
            }
    )
    {

        // Bottom content
        if (onlyImage) {
            OnlyImageView(
                modifier = Modifier
                    .width(cardWidth)
                    .height(cardHeight),
                bannerImageUrl = bannerImageUrl,
                cardWidth = cardWidth,
                cardHeight = cardHeight
            )
        } else {
            CashBackInfoView(
                modifier = Modifier
                    .width(cardWidth)
                    .wrapContentHeight(),
                bannerImageUrl = bannerImageUrl,
                offerTitle = offerTitle,
                offerSubtitle = offerSubtitle,
                couponCode = couponCode,
                couponBgColor = couponBgColor,
                couponBorderColor = couponBorderColor,
                couponTextColor = couponTextColor,
                rewardBgColor = rewardBgColor,
                offerTitleColor = offerTitleColor,
                offerSubtitleColor = offerSubtitleColor,
                cardHeight = cardHeight,
                titleFontSize = titleFontSize,
                subtitleFontSize = subtitleFontSize,
                // Title styling
                offerTitleFontFamily = offerTitleFontFamily,
                offerTitleFontDecoration = offerTitleFontDecoration,
                offerTitleTextAlign = offerTitleTextAlign,
                offerTitleMarginTop = offerTitleMarginTop,
                offerTitleMarginBottom = offerTitleMarginBottom,
                offerTitleMarginLeft = offerTitleMarginLeft,
                offerTitleMarginRight = offerTitleMarginRight,
                // Subtitle styling
                offerSubtitleFontFamily = offerSubtitleFontFamily,
                offerSubtitleFontDecoration = offerSubtitleFontDecoration,
                offerSubtitleTextAlign = offerSubtitleTextAlign,
                offerSubtitleMarginTop = offerSubtitleMarginTop,
                offerSubtitleMarginBottom = offerSubtitleMarginBottom,
                offerSubtitleMarginLeft = offerSubtitleMarginLeft,
                offerSubtitleMarginRight = offerSubtitleMarginRight,
                // Coupon styling
                couponBorderWidth = couponBorderWidth,
                couponAlignment = couponAlignment,
                couponCtaFullWidth = couponCtaFullWidth,
                couponCtaWidth = couponCtaWidth,
                couponHeight = couponHeight,
                couponFontSize = couponFontSize,
                couponFontFamily = couponFontFamily,
                couponFontDecoration = couponFontDecoration,
                couponTopLeft = couponTopLeft,
                couponTopRight = couponTopRight,
                couponBottomLeft = couponBottomLeft,
                couponBottomRight = couponBottomRight,
                couponMarginTop = couponMarginTop,
                couponMarginBottom = couponMarginBottom,
                couponMarginLeft = couponMarginLeft,
                couponMarginRight = couponMarginRight,

                imageWidth = imageWidth,
                imageHeight = imageHeight,
                imageTopLeft = imageTopLeft,
                imageTopRight = imageTopRight,
                imageBottomLeft = imageBottomLeft,
                imageBottomRight = imageBottomRight,
                imageMarginTop = imageMarginTop,
                imageMarginBottom = imageMarginBottom,
                imageMarginLeft = imageMarginLeft,
                imageMarginRight = imageMarginRight,
            )
        }

        // SCRATCH LAYER
        if (!isRevealed) {
            var lastPoint by remember { mutableStateOf<Offset?>(null) }

            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(scratchCanvas) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                lastPoint = offset
                                onPointsChanged(points + offset)
                                onCellTouched(
                                    cellIndexFor(
                                        offset,
                                        cardWidthPx.toFloat(),
                                        cardHeightPx.toFloat(),
                                        gridCols,
                                        gridRows
                                    )
                                )
                            },
                            onDrag = { change, _ ->
                                change.consume()

                                val newPoint = change.position
                                onPointsChanged(points + newPoint)
                                onCellTouched(
                                    cellIndexFor(
                                        newPoint,
                                        cardWidthPx.toFloat(),
                                        cardHeightPx.toFloat(),
                                        gridCols,
                                        gridRows
                                    )
                                )

                                // Draw continuous stroke with interpolation
                                lastPoint?.let { last ->
                                    // Calculate distance between points
                                    val dx = newPoint.x - last.x
                                    val dy = newPoint.y - last.y
                                    val distance = sqrt(dx * dx + dy * dy)

                                    // If points are far apart, interpolate
                                    if (distance > 5f) {
                                        val steps = (distance / 5f).toInt()
                                        for (i in 0..steps) {
                                            val t = i.toFloat() / steps
                                            val interpolatedX = last.x + dx * t
                                            val interpolatedY = last.y + dy * t

                                            // Draw circle at each interpolated point
                                            scratchCanvas?.drawCircle(
                                                interpolatedX,
                                                interpolatedY,
                                                40f,
                                                circlePaint
                                            )
                                        }
                                    }
                                }

                                lastPoint = newPoint
                            },
                            onDragEnd = {
                                lastPoint = null
                            }
                        )
                    }
            ) {
                // Apply smooth erase to bitmap
                if (points.size >= 2) {
                    // Draw path
                    val path = android.graphics.Path()
                    path.moveTo(points.first().x, points.first().y)

                    for (i in 1 until points.size) {
                        val prev = points[i - 1]
                        val curr = points[i]

                        // Draw circles along the path to fill gaps
                        val dx = curr.x - prev.x
                        val dy = curr.y - prev.y
                        val distance = sqrt(dx * dx + dy * dy)

                        if (distance > 10f) {
                            val steps = (distance / 10f).toInt()
                            for (j in 0..steps) {
                                val t = j.toFloat() / steps
                                val x = prev.x + dx * t
                                val y = prev.y + dy * t
                                scratchCanvas?.drawCircle(x, y, 40f, circlePaint)
                            }
                        }

                        path.lineTo(curr.x, curr.y)
                    }

                    scratchCanvas?.drawPath(path, eraserPaint)
                }

                // Draw the updated scratch bitmap on screen
                scratchBitmap?.let {
                    drawImage(
                        image = it.asImageBitmap(),
                        dstSize = IntSize(size.width.toInt(), size.height.toInt())
                    )
                }
            }
        }
    }
}

private fun cellIndexFor(
    point: Offset,
    width: Float,
    height: Float,
    gridCols: Int,
    gridRows: Int
): Int {
    val x = point.x.coerceIn(0f, width)
    val y = point.y.coerceIn(0f, height)

    val col = ((x / width) * gridCols).toInt().coerceIn(0, gridCols - 1)
    val row = ((y / height) * gridRows).toInt().coerceIn(0, gridRows - 1)

    return row * gridCols + col
}

// Helper function to check if URL is a GIF

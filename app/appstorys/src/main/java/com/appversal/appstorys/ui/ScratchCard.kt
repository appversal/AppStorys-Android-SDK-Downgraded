package com.appversal.appstorys.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaPlayer
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.min
import kotlin.math.sqrt
import androidx.compose.ui.text.AnnotatedString
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.appversal.appstorys.api.TextStyling
import com.appversal.appstorys.ui.common_components.CommonText
import com.appversal.appstorys.ui.xml.toDp
import com.appversal.appstorys.utils.isLottieUrl
import kotlinx.serialization.json.booleanOrNull

@RequiresApi(Build.VERSION_CODES.M)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScratch(
    isPresented: Boolean,
    onDismiss: () -> Unit,
    onConfettiTrigger: () -> Unit,
    wasFullyScratched: Boolean,
    onWasFullyScratched: (Boolean) -> Unit,
    scratchCardDetails: com.appversal.appstorys.api.ScratchCardDetails,
    onCtaClick: () -> Unit = {}
) {
    val details = scratchCardDetails.content

    // -------- card_size --------
    val cardSizeData = details
        ?.get("card_size")
        ?.jsonObject

    val cardHeight = cardSizeData
        ?.get("width")
        ?.jsonPrimitive
        ?.intOrNull

    // -------- overlay_image (coverImage at root level) --------
    val overlayImage = scratchCardDetails.coverImage ?: ""

    // -------- interactions --------
    val interactions = details
        ?.get("interactions")
        ?.jsonObject

    val haptics = interactions
        ?.get("haptics")
        ?.jsonPrimitive
        ?.booleanOrNull

    // -------- reward_content --------
    val rewardContent = details
        ?.get("reward_content")
        ?.jsonObject

    // bannerImage at root level
    val bannerImage = scratchCardDetails.bannerImage ?: ""

    val offerTitle = rewardContent
        ?.get("offer_title")
        ?.jsonPrimitive
        ?.contentOrNull ?: ""

    val titleFontSize = rewardContent
        ?.get("titleFontSize")
        ?.jsonPrimitive
        ?.intOrNull ?: 18

    val offerSubtitle = rewardContent
        ?.get("offer_subtitle")
        ?.jsonPrimitive
        ?.contentOrNull ?: ""

    val subtitleFontSize = rewardContent
        ?.get("subtitleFontSize")
        ?.jsonPrimitive
        ?.intOrNull ?: 16

    val onlyImage = rewardContent
        ?.get("onlyImage")
        ?.jsonPrimitive
        ?.contentOrNull
        ?.toBoolean() ?: false

    val rewardBgColor = rewardContent
        ?.get("background_color")
        ?.jsonPrimitive
        ?.contentOrNull ?: "#141414"

    val offerTitleColor = rewardContent
        ?.get("offerTitleTextColor")
        ?.jsonPrimitive
        ?.contentOrNull ?: "#FFFFFF"

    val offerSubtitleColor = rewardContent
        ?.get("offerSubtitleTextColor")
        ?.jsonPrimitive
        ?.contentOrNull ?: "#B3B3B3"

    // -------- coupon --------
    val coupon = details
        ?.get("coupon")
        ?.jsonObject

    val couponCode = coupon
        ?.get("code")
        ?.jsonPrimitive
        ?.contentOrNull ?: ""

    val couponBgColor = coupon
        ?.get("background_color")
        ?.jsonPrimitive
        ?.contentOrNull ?: "#1F1F1F"

    val couponBorderColor = coupon
        ?.get("border_color")
        ?.jsonPrimitive
        ?.contentOrNull ?: "#0066FF"

    val couponTextColor = coupon
        ?.get("codeTextColor")
        ?.jsonPrimitive
        ?.contentOrNull ?: "#FFFFFF"

    // -------- cta --------
    val cta = details
        ?.get("cta")
        ?.jsonObject

    val ctaHeight = cta
        ?.get("height")
        ?.jsonPrimitive
        ?.intOrNull
        ?.dp ?: 48.dp

    val ctaBorderRadius = cta
        ?.get("border_radius")
        ?.jsonPrimitive
        ?.intOrNull
        ?.dp ?: 12.dp

    val ctaText = cta
        ?.get("button_text")
        ?.jsonPrimitive
        ?.contentOrNull ?: "Claim offer now"

    val ctaFontSize = cta
        ?.get("ctaFontSize")
        ?.jsonPrimitive
        ?.intOrNull ?: 16

    val ctaColor = cta
        ?.get("button_color")
        ?.jsonPrimitive
        ?.contentOrNull ?: "#0066FF"

    val ctaTextColor = cta
        ?.get("cta_text_color")
        ?.jsonPrimitive
        ?.contentOrNull ?: "#383838"

    val ctaFullWidth = cta
        ?.get("enable_full_width")
        ?.jsonPrimitive
        ?.contentOrNull
        ?.toBoolean() ?: false

    val ctaPadding = cta
        ?.get("padding")
        ?.jsonObject

    val ctaPaddingTop = ctaPadding
        ?.get("top")
        ?.jsonPrimitive
        ?.intOrNull
        ?.dp ?: 12.dp

    val ctaPaddingBottom = ctaPadding
        ?.get("bottom")
        ?.jsonPrimitive
        ?.intOrNull
        ?.dp ?: 12.dp

    val ctaPaddingLeft = ctaPadding
        ?.get("left")
        ?.jsonPrimitive
        ?.intOrNull
        ?.dp ?: 20.dp

    val ctaPaddingRight = ctaPadding
        ?.get("right")
        ?.jsonPrimitive
        ?.intOrNull
        ?.dp ?: 20.dp

    // -------- terms_and_conditions (HTML string) --------
    val termsAndConditionsHtml = details
        ?.get("terms_and_conditions")
        ?.jsonPrimitive
        ?.contentOrNull ?: ""


    var points by remember { mutableStateOf(listOf<Offset>()) }
    var touchedCells by remember { mutableStateOf(setOf<Int>()) }
    var isRevealed by remember { mutableStateOf(wasFullyScratched) }
    var showTerms by remember { mutableStateOf(false) }

    // Tuning parameters
    val gridCols = 20
    val gridRows = 20
    val revealThreshold = 0.1f

    // Card size (from campaign data or adaptive fallback)
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val configuredCardSize = cardSizeData
        ?.get("width")
        ?.jsonPrimitive
        ?.intOrNull
        ?.dp ?: min(screenWidth.value * 0.9f, 260f).dp
    val cardSize = if (configuredCardSize.value > screenWidth.value * 0.9f) {
        min(screenWidth.value * 0.9f, 260f).dp
    } else {
        configuredCardSize
    }
    val cornerRadius = cardSizeData
        ?.get("corner_radius")
        ?.jsonPrimitive
        ?.intOrNull
        ?.dp ?: 32.dp

    LaunchedEffect(wasFullyScratched) {
        if (wasFullyScratched) {
            isRevealed = true
        }
    }

    if (isPresented) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable(
                    enabled = true,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                }
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(1f))

                // Close button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    this@Column.AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut() + slideOutVertically()
                    ) {
                        IconButton(
                            onClick = { onDismiss() },
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    Color.White.copy(alpha = 0.4f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scratch card
                Box(
                    modifier = Modifier
                        .size(cardSize)
                        .clip(RoundedCornerShape(cornerRadius))
                ) {
                    ScratchableCard(
                        cardSize = cardSize,
                        points = points,
                        isRevealed = isRevealed,
                        overlayImageUrl = overlayImage,
                        bannerImageUrl = bannerImage,
                        offerTitle = offerTitle,
                        offerSubtitle = offerSubtitle,
                        couponCode = couponCode,
                        couponBgColor = couponBgColor,
                        couponBorderColor = couponBorderColor,
                        couponTextColor = couponTextColor,
                        rewardBgColor = rewardBgColor,
                        offerTitleColor = offerTitleColor,
                        offerSubtitleColor = offerSubtitleColor,
                        onlyImage = onlyImage,
                        soundFileUrl = scratchCardDetails.soundFile ?: "",
                        onPointsChanged = { newPoints ->
                            if (!isRevealed) {
                                points = newPoints
                            }
                        },
                        onCellTouched = { cellIndex ->
                            if (!isRevealed) {
                                touchedCells = touchedCells + cellIndex
                                val total = gridCols * gridRows
                                if (touchedCells.size.toFloat() / total >= revealThreshold) {
                                    isRevealed = true
                                    onWasFullyScratched(true)
                                    points = emptyList()
                                    onConfettiTrigger()
                                }
                            }
                        },
                        gridCols = gridCols,
                        gridRows = gridRows,
                        haptics = haptics ?: false,
                        cardHeight = cardHeight ?: 200,
                        titleFontSize = titleFontSize,
                        subtitleFontSize = subtitleFontSize
                    )
                }

                // Action buttons
                Box(
                    modifier = Modifier
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    this@Column.AnimatedVisibility(
                        visible = isRevealed,
                        enter = fadeIn() + slideInVertically { it },
                        exit = fadeOut() + slideOutVertically { it }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(
                                        top = ctaPaddingTop,
                                        bottom = ctaPaddingBottom,
                                        start = ctaPaddingLeft,
                                        end = ctaPaddingRight
                                    )
                            ) {
                                Button(
                                    onClick = { onCtaClick() },
                                    modifier = Modifier
                                        .then(
                                            if (ctaFullWidth) Modifier.fillMaxWidth()
                                            else Modifier.wrapContentWidth()
                                        )
                                        .height(ctaHeight),
                                    shape = RoundedCornerShape(ctaBorderRadius),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = parseColorSafe(ctaColor, Color(0xFF0066FF))
                                    )
                                ) {
                                    CommonText(
                                        text = ctaText,
                                        styling = TextStyling(
                                            color = ctaTextColor,
                                            fontSize = ctaFontSize,
                                            fontFamily = "",
                                            fontDecoration = listOf("semibold")
                                        )
                                    )
                                }
                            }

                            if (termsAndConditionsHtml.isNotEmpty()) {
                                CommonText(
                                    modifier = Modifier
                                        .clickable {
                                            showTerms = true
                                        },
                                    text = "Terms & Conditions*",
                                    styling = TextStyling(
                                        color = "#FFFFFF",
                                        fontSize = 12,
                                        fontFamily = "",
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
            }
        }

        // Terms and conditions bottom sheet
        if (showTerms) {
            ModalBottomSheet(
                modifier = Modifier.statusBarsPadding(),
                onDismissRequest = { showTerms = false },
                containerColor = Color.White,
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                dragHandle = null,
            ) {
                TermsAndConditionsView(
                    onDismiss = { showTerms = false },
                    termsHtml = termsAndConditionsHtml
                )
            }
        }
    }
}

@Composable
fun ScratchableCard(
    cardSize: Dp,
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
    cardHeight: Int,
    titleFontSize: Int,
    subtitleFontSize: Int
) {
    val context = LocalContext.current
    val cardSizePx = with(LocalDensity.current) { cardSize.toPx() }.toInt()
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
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
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
    LaunchedEffect(soundFileUrl) {
        if (soundFileUrl.isNotEmpty() && !soundLoaded) {
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
            coroutineScope.launch {
                try {
                    if (soundLoaded && !mediaPlayer.isPlaying) {
                        mediaPlayer.start()
                    }
                } catch (e: Exception) {
                    Log.e("ScratchCard", "Error playing sound: ${e.message}")
                }
            }

            // Vibrate once
            if(haptics){
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

    // Offscreen buffer (scratch surface)
    val scratchBitmap = remember {
        Bitmap.createBitmap(cardSizePx, cardSizePx, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(Color.Gray.toArgb()) }
    }

    val scratchCanvas = remember { android.graphics.Canvas(scratchBitmap) }

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

    LaunchedEffect(overlayImageUrl) {
        if (overlayImageUrl.isNotEmpty()) {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(overlayImageUrl)
                .allowHardware(false)
                .build()

            val result = loader.execute(request)
            val bmp = (result.drawable as? BitmapDrawable)?.bitmap

            bmp?.let {
                overlayBitmap = Bitmap.createScaledBitmap(
                    it,
                    cardSizePx,
                    cardSizePx,
                    true
                )
                scratchCanvas.drawBitmap(overlayBitmap!!, 0f, 0f, null)
            }
        }
    }

    Box(modifier = Modifier.size(cardSize)) {

        // Bottom content
        if (onlyImage) {
            OnlyImageView(
                modifier = Modifier.size(cardSize),
                bannerImageUrl = bannerImageUrl
            )
        } else {
            CashBackInfoView(
                modifier = Modifier.size(cardSize),
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
                subtitleFontSize = subtitleFontSize
            )
        }

        // SCRATCH LAYER
        if (!isRevealed) {
            var lastPoint by remember { mutableStateOf<Offset?>(null) }

            Canvas(
                modifier = Modifier
                    .size(cardSize)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                lastPoint = offset
                                onPointsChanged(points + offset)
                                onCellTouched(
                                    cellIndexFor(offset, cardSizePx.toFloat(), gridCols, gridRows)
                                )
                            },
                            onDrag = { change, _ ->
                                change.consume()

                                val newPoint = change.position
                                onPointsChanged(points + newPoint)
                                onCellTouched(
                                    cellIndexFor(newPoint, cardSizePx.toFloat(), gridCols, gridRows)
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
                                            scratchCanvas.drawCircle(
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
                                scratchCanvas.drawCircle(x, y, 40f, circlePaint)
                            }
                        }

                        path.lineTo(curr.x, curr.y)
                    }

                    scratchCanvas.drawPath(path, eraserPaint)
                }

                // Draw the updated scratch bitmap on screen
                drawImage(
                    image = scratchBitmap.asImageBitmap(),
                    dstSize = IntSize(size.width.toInt(), size.height.toInt())
                )
            }
        }
    }
}


@Composable
fun OnlyImageView(
    modifier: Modifier = Modifier,
    bannerImageUrl: String
) {
    val context = LocalContext.current

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (bannerImageUrl.isNotEmpty()) {
            if (isGifUrl(bannerImageUrl)) {
                val imageLoader = ImageLoader.Builder(context)
                    .components {
                        if (SDK_INT >= 28) {
                            add(ImageDecoderDecoder.Factory())
                        } else {
                            add(GifDecoder.Factory())
                        }
                    }
                    .build()

                val painter = rememberAsyncImagePainter(
                    ImageRequest.Builder(context)
                        .data(bannerImageUrl)
                        .memoryCacheKey(bannerImageUrl)
                        .diskCacheKey(bannerImageUrl)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .crossfade(true)
                        .apply { size(coil.size.Size.ORIGINAL) }
                        .build(),
                    imageLoader = imageLoader
                )

                Image(
                    painter = painter,
                    contentDescription = "Banner",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
            } else if(isLottieUrl(bannerImageUrl)){
                val composition by rememberLottieComposition(
                    spec = LottieCompositionSpec.Url(bannerImageUrl)
                )
                LottieAnimation(
                    composition = composition,
                    iterations = LottieConstants.IterateForever,
                    modifier = Modifier.fillMaxSize()
                )
            }
             else{
                SubcomposeAsyncImage(
                    model = bannerImageUrl,
                    contentDescription = "Banner",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// Helper function to safely parse color strings
private fun parseColorSafe(colorString: String, defaultColor: Color = Color.White): Color {
    return try {
        if (colorString.isNotEmpty()) {
            Color(android.graphics.Color.parseColor(colorString))
        } else {
            defaultColor
        }
    } catch (e: Exception) {
        defaultColor
    }
}

@Composable
fun CashBackInfoView(
    modifier: Modifier = Modifier,
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
    cardHeight: Int,
    titleFontSize: Int,
    subtitleFontSize: Int,
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .background(parseColorSafe(rewardBgColor, Color(0xFF141414)))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))
                if (bannerImageUrl.isNotEmpty()) {

                    val maxSize = (cardHeight * 0.3f).dp

                    if (isGifUrl(bannerImageUrl)) {
                        val imageLoader = ImageLoader.Builder(context)
                            .components {
                                if (SDK_INT >= 28) {
                                    add(ImageDecoderDecoder.Factory())
                                } else {
                                    add(GifDecoder.Factory())
                                }
                            }
                            .build()

                        val painter = rememberAsyncImagePainter(
                            ImageRequest.Builder(context)
                                .data(bannerImageUrl)
                                .memoryCacheKey(bannerImageUrl)
                                .diskCacheKey(bannerImageUrl)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .crossfade(true)
                                .apply { size(coil.size.Size.ORIGINAL) }
                                .build(),
                            imageLoader = imageLoader
                        )

                        Image(
                            painter = painter,
                            contentDescription = "Banner",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .sizeIn(
                                    maxWidth = maxSize,
                                    maxHeight = maxSize
                                )
                                .clip(CircleShape)
                        )
                    } else if(isLottieUrl(bannerImageUrl)){
                        val composition by rememberLottieComposition(
                            spec = LottieCompositionSpec.Url(bannerImageUrl)
                        )
                        LottieAnimation(
                            composition = composition,
                            iterations = LottieConstants.IterateForever,
                            modifier = Modifier
                                .sizeIn(
                                    maxWidth = maxSize,
                                    maxHeight = maxSize
                                )
                                .clip(CircleShape)
                        )
                    }
                    else{
                        SubcomposeAsyncImage(
                            model = bannerImageUrl,
                            contentDescription = "Banner",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .sizeIn(
                                    maxWidth = maxSize,
                                    maxHeight = maxSize
                                )
                                .clip(CircleShape)
                        )
                    }
                }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (offerTitle.isNotEmpty()) {
                    CommonText(
                        text = offerTitle,
                        styling = TextStyling(
                            color = offerTitleColor,
                            fontSize = titleFontSize,
                            fontFamily = "",
                            fontDecoration = listOf("bold")
                        )
                    )
                }

                Spacer(Modifier.height((cardHeight * 0.06).toDp()))

                if (offerSubtitle.isNotEmpty()) {
                    CommonText(
                        text = offerSubtitle,
                        letterSpacing = 0.1.toFloat(),
                        styling = TextStyling(
                            color = offerSubtitleColor,
                            fontSize = subtitleFontSize,
                            fontFamily = "",
                        )
                    )
                }

                Spacer(Modifier.height((cardHeight * 0.2).toDp()))

                // Coupon code display
                if (couponCode.isNotEmpty()) {
                    val clipboardManager = LocalClipboardManager.current
                    Box(
                        modifier = Modifier
                            .background(
                                color = parseColorSafe(couponBgColor, Color(0xFF1F1F1F)),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .then(
                                if (couponBorderColor.isNotEmpty()) {
                                    Modifier.drawWithContent {
                                        drawContent()
                                        drawRoundRect(
                                            color = parseColorSafe(couponBorderColor, Color(0xFF0066FF)),
                                            style = Stroke(width = 1.dp.toPx()),
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                                        )
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .clickable {
                                clipboardManager.setText(AnnotatedString(couponCode))   // ← FIXED
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                            .padding(start = 16.dp, top = (cardHeight * 0.05).toDp(), bottom = (cardHeight * 0.05).toDp(), end = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CommonText(
                                text = couponCode,
                                letterSpacing = 0.2.toFloat(),
                                styling = TextStyling(
                                    color = couponTextColor,
                                    fontFamily = "",
                                    fontSize = null
                                )
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Copy Icon
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Copy Coupon",
                                tint = parseColorSafe(couponTextColor, Color.White), // same color as text
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@RequiresApi(Build.VERSION_CODES.M)
@Composable
fun TermsAndConditionsView(
    onDismiss: () -> Unit,
    termsHtml: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // HTML content
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { context ->
                    android.widget.TextView(context).apply {
                        // Set text appearance
                        setTextAppearance(android.R.style.TextAppearance_Material_Body1)

                        // Parse HTML
                        text = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                            android.text.Html.fromHtml(termsHtml, android.text.Html.FROM_HTML_MODE_COMPACT)
                        } else {
                            @Suppress("DEPRECATION")
                            android.text.Html.fromHtml(termsHtml)
                        }

                        // Make links clickable
                        movementMethod = android.text.method.LinkMovementMethod.getInstance()

                        // Set text size
                        textSize = 14f

                        // Set padding
                        setPadding(0, 0, 0, 0)
                    }
                },
                update = { textView ->
                    textView.text = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        android.text.Html.fromHtml(termsHtml, android.text.Html.FROM_HTML_MODE_COMPACT)
                    } else {
                        @Suppress("DEPRECATION")
                        android.text.Html.fromHtml(termsHtml)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// Helper function to map point to grid cell index
private fun cellIndexFor(
    point: Offset,
    size: Float,
    gridCols: Int,
    gridRows: Int
): Int {
    val x = point.x.coerceIn(0f, size)
    val y = point.y.coerceIn(0f, size)

    val col = ((x / size) * gridCols).toInt().coerceIn(0, gridCols - 1)
    val row = ((y / size) * gridRows).toInt().coerceIn(0, gridRows - 1)

    return row * gridCols + col
}

// Helper function to check if URL is a GIF
private fun isGifUrl(url: String): Boolean {
    return url.lowercase().endsWith(".gif")
}

fun saveScratchedCampaigns(
    campaignIds: List<String>,
    sharedPreferences: SharedPreferences
) {
    val editor = sharedPreferences.edit()
    val idsString = campaignIds.joinToString(",")
    editor.putString("scratched_campaigns", idsString)
    editor.apply()
}

fun getScratchedCampaigns(sharedPreferences: SharedPreferences): List<String> {
    val idsString = sharedPreferences.getString("scratched_campaigns", "") ?: ""
    return if (idsString.isNotEmpty()) {
        idsString.split(",").filter { it.isNotEmpty() }
    } else {
        emptyList()
    }
}
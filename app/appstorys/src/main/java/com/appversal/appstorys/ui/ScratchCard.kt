package com.appversal.appstorys.ui

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build.VERSION.SDK_INT
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.min

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
    // Extract data from campaign details
    val details = scratchCardDetails.content

// -------- card_size --------
    val cardSizeData = details
        ?.get("card_size")
        ?.jsonObject

// -------- overlay_image --------
    val overlayImage = details
        ?.get("overlay_image")
        ?.jsonPrimitive
        ?.contentOrNull ?: ""

// -------- reward_content --------
    val rewardContent = details
        ?.get("reward_content")
        ?.jsonObject

    val bannerImage = rewardContent
        ?.get("banner_image")
        ?.jsonPrimitive
        ?.contentOrNull ?: ""

    val brandName = rewardContent
        ?.get("brand_name")
        ?.jsonPrimitive
        ?.contentOrNull ?: "AppStorys"

    val offerTitle = rewardContent
        ?.get("offer_title")
        ?.jsonPrimitive
        ?.contentOrNull ?: "Cashback on mobile and recharge"

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
        ?.contentOrNull ?: "#FFD700"

// -------- cta --------
    val cta = details
        ?.get("cta")
        ?.jsonObject

    val ctaHeight = cta
        ?.get("height")
        ?.jsonPrimitive
        ?.intOrNull
        ?.dp ?: 44.dp

    val ctaBorderRadius = cta
        ?.get("border_radius")
        ?.jsonPrimitive
        ?.intOrNull
        ?.dp ?: 16.dp

    val ctaText = cta
        ?.get("button_text")
        ?.jsonPrimitive
        ?.contentOrNull ?: "Claim offer now"

    val ctaColor = cta
        ?.get("button_color")
        ?.jsonPrimitive
        ?.contentOrNull ?: "#2196F3"

    val ctaTextColor = cta
        ?.get("cta_text_color")
        ?.jsonPrimitive
        ?.contentOrNull ?: "#FFFFFF"

// -------- interactions --------
    val interactions = details
        ?.get("interactions")
        ?.jsonObject

    val animation = interactions
        ?.get("animation")
        ?.jsonPrimitive
        ?.contentOrNull ?: ""

    val sound = interactions
        ?.get("sound")
        ?.jsonPrimitive
        ?.contentOrNull ?: ""

    val haptics = interactions
        ?.get("haptics")
        ?.jsonPrimitive
        ?.contentOrNull ?: false

// -------- terms_and_conditions --------
    val termsAndConditions = details
        ?.get("terms_and_conditions")
        ?.jsonArray
        ?.map { it.jsonObject } ?: emptyList()


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
                .background(Color.Black.copy(alpha = 0.7f))
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
                                    Color.White.copy(alpha = 0.2f),
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
                        gpayImageUrl = overlayImage,
                        bannerImageUrl = bannerImage,
                        brandName = brandName,
                        offerTitle = offerTitle,
                        couponCode = couponCode.toString(),
                        couponBgColor = couponBgColor,
                        couponBorderColor = couponBorderColor,
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
                        gridRows = gridRows
                    )
                }

                // Action buttons
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 96.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    this@Column.AnimatedVisibility(
                        visible = isRevealed,
                        enter = fadeIn() + slideInVertically { it },
                        exit = fadeOut() + slideOutVertically { it }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onCtaClick() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(ctaHeight),
                                shape = RoundedCornerShape(ctaBorderRadius),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(android.graphics.Color.parseColor(ctaColor))
                                )
                            ) {
                                Text(
                                    text = ctaText,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(android.graphics.Color.parseColor(ctaTextColor))
                                )
                            }

                            Text(
                                text = "Terms & Conditions*",
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clickable {
                                        showTerms = true
                                    }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
            }
        }

        // Terms and conditions bottom sheet
        if (showTerms) {
            ModalBottomSheet(
                onDismissRequest = { showTerms = false },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                dragHandle = null,
            ) {
                TermsAndConditionsView(
                    onDismiss = { showTerms = false },
                    termsAndConditions = termsAndConditions
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
    gpayImageUrl: String,
    bannerImageUrl: String,
    brandName: String,
    offerTitle: String,
    couponCode: String,
    couponBgColor: String,
    couponBorderColor: String,
    onPointsChanged: (List<Offset>) -> Unit,
    onCellTouched: (Int) -> Unit,
    gridCols: Int,
    gridRows: Int
) {
    val context = LocalContext.current
    val cardSizePx = with(LocalDensity.current) { cardSize.toPx() }.toInt()

    // Offscreen buffer (scratch surface)
    val scratchBitmap = remember {
        Bitmap.createBitmap(cardSizePx, cardSizePx, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(Color.Gray.toArgb()) } // fallback gray
    }

    val scratchCanvas = remember { android.graphics.Canvas(scratchBitmap) }
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
            style = android.graphics.Paint.Style.STROKE
        }
    }

    // Overlay image (to draw into scratch surface)
    var overlayBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(gpayImageUrl) {
        if (gpayImageUrl.isNotEmpty()) {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(gpayImageUrl)
                .allowHardware(false) // REQUIRED
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

                // Draw overlay into scratch surface
                scratchCanvas.drawBitmap(overlayBitmap!!, 0f, 0f, null)
            }
        }
    }

    Box(modifier = Modifier.size(cardSize)) {

        // Bottom content
        CashBackInfoView(
            modifier = Modifier.size(cardSize),
            bannerImageUrl = bannerImageUrl,
            brandName = brandName,
            offerTitle = offerTitle,
            couponCode = couponCode,
            couponBgColor = couponBgColor,
            couponBorderColor = couponBorderColor
        )

        // SCRATCH LAYER
        if (!isRevealed) {
            Canvas(
                modifier = Modifier
                    .size(cardSize)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                onPointsChanged(points + offset)
                                onCellTouched(
                                    cellIndexFor(offset, cardSizePx.toFloat(), gridCols, gridRows)
                                )
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                onPointsChanged(points + change.position)
                                onCellTouched(
                                    cellIndexFor(change.position, cardSizePx.toFloat(), gridCols, gridRows)
                                )
                            }
                        )
                    }
            ) {
                // Apply erase to bitmap (true erase)
                if (points.isNotEmpty()) {
                    val path = android.graphics.Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.forEach { lineTo(it.x, it.y) }
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
fun CashBackInfoView(
    modifier: Modifier = Modifier,
    bannerImageUrl: String,
    brandName: String,
    offerTitle: String,
    couponCode: String,
    couponBgColor: String,
    couponBorderColor: String
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .background(Color(0xFF141414))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Network image for banner - Using same pattern as PinnedBanner
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
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .padding(24.dp)
                    )
                } else {
                    SubcomposeAsyncImage(
                        model = bannerImageUrl,
                        contentDescription = "Banner",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(shape = CircleShape)
                            .padding(18.dp)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Offer from $brandName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = offerTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                // Coupon code display
                if (couponCode.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .background(
                                color = Color(android.graphics.Color.parseColor(couponBgColor)),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .then(
                                if (couponBorderColor.isNotEmpty()) {
                                    Modifier.drawWithContent {
                                        drawContent()
                                        drawRoundRect(
                                            color = Color(android.graphics.Color.parseColor(couponBorderColor)),
                                            style = Stroke(width = 2.dp.toPx()),
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                                        )
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = couponCode,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TermsAndConditionsView(
    onDismiss: () -> Unit,
    termsAndConditions: List<JsonObject>
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            termsAndConditions.forEach { termObj ->

                val title = termObj["title"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: ""

                val content = termObj["content"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: ""

                TermSection(
                    title = title,
                    content = content
                )
            }
        }

        // Close button
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = (-60).dp)
                .size(40.dp)
                .background(Color.White.copy(alpha = 0.2f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White
            )
        }
    }
}


@Composable
fun TermSection(title: String, content: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
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
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
import androidx.compose.ui.viewinterop.AndroidView
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

    // -------- overlay_image (coverImage at root level) --------
    val overlayImage = scratchCardDetails.coverImage ?: ""

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

    val offerSubtitle = rewardContent
        ?.get("offer_subtitle")
        ?.jsonPrimitive
        ?.contentOrNull ?: ""

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

    val ctaColor = cta
        ?.get("button_color")
        ?.jsonPrimitive
        ?.contentOrNull ?: "#0066FF"

    val ctaTextColor = cta
        ?.get("cta_text_color")
        ?.jsonPrimitive
        ?.contentOrNull ?: "#383838"

    val ctaUrl = cta
        ?.get("url")
        ?.jsonPrimitive
        ?.contentOrNull ?: ""

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
                                    .then(
                                        if (ctaFullWidth) Modifier.fillMaxWidth()
                                        else Modifier.wrapContentWidth()
                                    )
                                    .height(ctaHeight)
                                    .padding(
                                        top = ctaPaddingTop,
                                        bottom = ctaPaddingBottom,
                                        start = ctaPaddingLeft,
                                        end = ctaPaddingRight
                                    ),
                                shape = RoundedCornerShape(ctaBorderRadius),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = parseColorSafe(ctaColor, Color(0xFF0066FF))
                                )
                            ) {
                                Text(
                                    text = ctaText,
                                    fontWeight = FontWeight.Bold,
                                    color = parseColorSafe(ctaTextColor, Color(0xFF383838))
                                )
                            }

                            if (termsAndConditionsHtml.isNotEmpty()) {
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

    LaunchedEffect(overlayImageUrl) {
        if (overlayImageUrl.isNotEmpty()) {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(overlayImageUrl)
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
        if (onlyImage) {
            // Only show banner image when onlyImage is true
            OnlyImageView(
                modifier = Modifier.size(cardSize),
                bannerImageUrl = bannerImageUrl
            )
        } else {
            // Show full content
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
                offerSubtitleColor = offerSubtitleColor
            )
        }

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
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                SubcomposeAsyncImage(
                    model = bannerImageUrl,
                    contentDescription = "Banner",
                    contentScale = ContentScale.Fit,
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
    offerSubtitleColor: String
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .background(parseColorSafe(rewardBgColor, Color(0xFF141414)))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Network image for banner
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
                if (offerTitle.isNotEmpty()) {
                    Text(
                        text = offerTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = parseColorSafe(offerTitleColor, Color.White),
                        textAlign = TextAlign.Center
                    )
                }

                if (offerSubtitle.isNotEmpty()) {
                    Text(
                        text = offerSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = parseColorSafe(offerSubtitleColor, Color.White.copy(alpha = 0.7f)),
                        textAlign = TextAlign.Center
                    )
                }

                // Coupon code display
                if (couponCode.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
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
                            color = parseColorSafe(couponTextColor, Color.White),
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
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title
            Text(
                text = "Terms & Conditions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

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
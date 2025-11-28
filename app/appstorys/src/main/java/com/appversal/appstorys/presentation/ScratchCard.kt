package com.appversal.appstorys.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import coil.ImageLoader
import coil.request.ImageRequest
import com.appversal.appstorys.api.ScratchCardDetails
import com.appversal.appstorys.domain.State
import com.appversal.appstorys.domain.model.TypedCampaign
import com.appversal.appstorys.domain.rememberCampaign
import com.appversal.appstorys.domain.usecase.trackEvent
import com.appversal.appstorys.utils.toColor
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.min


@Composable
internal fun ScratchCard(
    modifier: Modifier = Modifier,
) {
    val campaign = rememberCampaign<ScratchCardDetails>("SCRT")
    if (campaign != null) {
        Content(
            campaign,
            modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Content(
    campaign: TypedCampaign<ScratchCardDetails>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    val details = campaign.details
    val content = details.content

    val cardSizeData = content?.get("card_size")?.jsonObject
    val overlayImage = content?.get("overlay_image")?.jsonPrimitive?.contentOrNull.orEmpty()
    val rewardContent = content?.get("reward_content")?.jsonObject
    val bannerImage = rewardContent?.get("banner_image")?.jsonPrimitive?.contentOrNull.orEmpty()
    val brandName = rewardContent?.get("brand_name")?.jsonPrimitive?.contentOrNull ?: "AppStorys"
    val offerTitle = rewardContent?.get("offer_title")?.jsonPrimitive?.contentOrNull
        ?: "Cashback on mobile and recharge"

    val coupon = content?.get("coupon")?.jsonObject
    val couponCode = coupon?.get("code")?.jsonPrimitive?.contentOrNull.orEmpty()
    val couponBgColor = coupon?.get("background_color")?.jsonPrimitive?.contentOrNull ?: "#1F1F1F"
    val couponBorderColor = coupon?.get("border_color")?.jsonPrimitive?.contentOrNull ?: "#FFD700"

    val cta = content?.get("cta")?.jsonObject
    val ctaHeight = cta?.get("height")?.jsonPrimitive?.intOrNull?.dp ?: 44.dp
    val ctaBorderRadius = cta?.get("border_radius")?.jsonPrimitive?.intOrNull?.dp ?: 16.dp
    val ctaText = cta?.get("button_text")?.jsonPrimitive?.contentOrNull ?: "Claim offer now"
    val ctaColor = cta?.get("button_color")?.jsonPrimitive?.contentOrNull ?: "#2196F3"
    val ctaTextColor = cta?.get("cta_text_color")?.jsonPrimitive?.contentOrNull ?: "#FFFFFF"

    val termsAndConditions =
        content?.get("terms_and_conditions")?.jsonArray?.map { it.jsonObject } ?: emptyList()

    // Card size (from campaign data or adaptive fallback)
    val configuredCardSize = cardSizeData?.get("width")?.jsonPrimitive?.intOrNull?.dp ?: min(
        screenWidth.value * 0.9f,
        260f
    ).dp
    val cardSize = if (configuredCardSize.value > screenWidth.value * 0.9f) {
        min(screenWidth.value * 0.9f, 260f).dp
    } else {
        configuredCardSize
    }
    val cardSizePx = with(LocalDensity.current) { cardSize.toPx() }.toInt()

    val cornerRadius = cardSizeData
        ?.get("corner_radius")
        ?.jsonPrimitive
        ?.intOrNull
        ?.dp ?: 32.dp

    // Tuning parameters
    val gridCols = 20
    val gridRows = 20
    val revealThreshold = 0.1f

    val scratchBitmap = remember(cardSizePx) {
        createBitmap(cardSizePx, cardSizePx).apply {
            eraseColor(Color.Gray.toArgb())
        }
    }
    val scratchCanvas = remember(cardSizePx) {
        android.graphics.Canvas(scratchBitmap)
    }
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

    val points = remember { mutableStateListOf<Offset>() }
    val touchedCells = remember { mutableStateListOf<Int>() }
    var isRevealed by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }

    val handleDismiss = remember {
        {
            State.addDisabledCampaign(campaign.id)
        }
    }
    val handlePointChange = remember(points, isRevealed, cardSizePx) {
        { point: Offset ->
            if (!isRevealed) {
                points += point

                val size = cardSizePx.toFloat()
                val x = point.x.coerceIn(0f, size)
                val y = point.y.coerceIn(0f, size)
                val col = ((x / size) * gridCols).toInt().coerceIn(0, gridCols - 1)
                val row = ((y / size) * gridRows).toInt().coerceIn(0, gridRows - 1)

                val index = row * gridCols + col
                if (!touchedCells.contains(index)) {
                    touchedCells += index
                }

                val total = gridCols * gridRows
                if (touchedCells.size.toFloat() / total >= revealThreshold) {
                    isRevealed = true
                    points.clear()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        trackEvent(context, "viewed", campaign.id)
    }

    LaunchedEffect(overlayImage) {
        if (overlayImage.isBlank()) {
            return@LaunchedEffect
        }

        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(overlayImage)
            .allowHardware(false) // REQUIRED
            .build()

        loader.execute(request).drawable?.toBitmap()?.let {
            scratchCanvas.drawBitmap(
                it.scale(cardSizePx, cardSizePx), 0f, 0f, null
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        content = {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = {
                    Spacer(modifier = Modifier.weight(1f))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        contentAlignment = Alignment.Center,
                        content = {
                            IconButton(
                                onClick = handleDismiss,
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        Color.White.copy(alpha = 0.2f),
                                        CircleShape
                                    ),
                                content = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = Color.White
                                    )
                                }
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .size(cardSize)
                            .clip(RoundedCornerShape(cornerRadius))
                            .size(cardSize),
                        content = {
                            Column(
                                modifier = Modifier
                                    .background(Color(0xFF141414))
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                content = {
                                    SdkImage(
                                        image = bannerImage,
                                        width = 120.dp,
                                        height = 120.dp,
                                        shape = CircleShape,
                                    )
                                    Text(
                                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
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

                                    if (couponCode.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 8.dp)
                                                .background(
                                                    color = couponBgColor.toColor(),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .then(
                                                    if (couponBorderColor.isNotEmpty()) {
                                                        Modifier.drawWithContent {
                                                            drawContent()
                                                            drawRoundRect(
                                                                color = couponBorderColor.toColor(),
                                                                style = Stroke(width = 2.dp.toPx()),
                                                                cornerRadius = CornerRadius(8.dp.toPx())
                                                            )
                                                        }
                                                    } else {
                                                        Modifier
                                                    }
                                                )
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            contentAlignment = Alignment.Center,
                                            content = {
                                                Text(
                                                    text = couponCode,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    letterSpacing = 2.sp
                                                )
                                            }
                                        )
                                    }
                                }
                            )

                            if (!isRevealed) {
                                Canvas(
                                    modifier = Modifier
                                        .size(cardSize)
                                        .pointerInput(Unit) {
                                            detectDragGestures(
                                                onDragStart = handlePointChange,
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    handlePointChange(change.position)
                                                }
                                            )
                                        },
                                    onDraw = {
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
                                            dstSize = IntSize(
                                                size.width.toInt(),
                                                size.height.toInt()
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    )

                    AnimatedVisibility(
                        visible = isRevealed,
                        enter = fadeIn() + slideInVertically { it },
                        exit = fadeOut() + slideOutVertically { it },
                        content = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 96.dp, vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                content = {
                                    Button(
                                        onClick = {
                                            // TODO: Handle CTA click
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(ctaHeight),
                                        shape = RoundedCornerShape(ctaBorderRadius),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ctaColor.toColor()
                                        ),
                                        content = {
                                            Text(
                                                text = ctaText,
                                                fontWeight = FontWeight.Bold,
                                                color = ctaTextColor.toColor()
                                            )
                                        }
                                    )
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
                            )
                        }
                    )

                    Spacer(modifier = Modifier.weight(1f))
                }
            )
        }
    )

    if (showTerms) {
        ModalBottomSheet(
            onDismissRequest = { showTerms = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
            dragHandle = null,
            content = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    content = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 20.dp),
                            content = {
                                termsAndConditions.forEachIndexed { index, data ->
                                    val title = data["title"]
                                        ?.jsonPrimitive
                                        ?.contentOrNull.orEmpty()

                                    val content = data["content"]
                                        ?.jsonPrimitive
                                        ?.contentOrNull.orEmpty()

                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Text(
                                        modifier = Modifier.padding(
                                            top = 8.dp,
                                            bottom = if (index == termsAndConditions.lastIndex) 0.dp else 20.dp
                                        ),
                                        text = content,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        )

                        IconButton(
                            onClick = { showTerms = false },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(y = (-60).dp)
                                .size(40.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape),
                            content = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White
                                )
                            }
                        )
                    }
                )
            }
        )
    }
}

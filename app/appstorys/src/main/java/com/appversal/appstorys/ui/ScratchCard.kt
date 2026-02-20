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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.snapping.SnapPosition
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
import androidx.compose.ui.layout.onSizeChanged
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.min
import kotlin.math.sqrt
import androidx.compose.ui.text.AnnotatedString
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.appversal.appstorys.api.CommonMargins
import com.appversal.appstorys.api.TextStyling
import com.appversal.appstorys.ui.common_components.CommonText
import com.appversal.appstorys.ui.common_components.CrossButton
import com.appversal.appstorys.ui.common_components.CrossButtonConfig
import com.appversal.appstorys.ui.xml.toDp
import com.appversal.appstorys.utils.isLottieUrl
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull

@RequiresApi(Build.VERSION_CODES.M)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScratch(
    isPresented: Boolean,
    crossButtonConfig: CrossButtonConfig = CrossButtonConfig(),
    crossButtonAlignment: String = "center",
    crossButtonMarginBottom: Dp = 0.dp,
    onDismiss: () -> Unit,
    onConfettiTrigger: () -> Unit,
    wasFullyScratched: Boolean,
    onWasFullyScratched: (Boolean) -> Unit,
    scratchCardDetails: com.appversal.appstorys.api.ScratchCardDetails,
    onCtaClick: () -> Unit = {},
) {
    val details = scratchCardDetails.content

    // -------- card_size --------
    val cardSizeData = details
        ?.get("card_size")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject

    val cardHeight = cardSizeData
        ?.get("height")
        ?.jsonPrimitive
        ?.intOrNull

    // -------- overlay_image (coverImage at root level) --------
    val overlayImage = scratchCardDetails.coverImage ?: ""

    // -------- interactions --------
    val interactions = details
        ?.get("interactions")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject

    val haptics = interactions
        ?.get("haptics")
        ?.jsonPrimitive
        ?.booleanOrNull

    // Alternative haptic fields
    val hapticFeedbackEnabled = details
        ?.get("haptic_feedback_enabled")
        ?.jsonPrimitive
        ?.booleanOrNull

    val hapticStyle = details
        ?.get("haptic_style")
        ?.jsonPrimitive
        ?.contentOrNull ?: ""

    // Use haptic_feedback_enabled if available, otherwise fall back to interactions.haptics
    val hapticsEnabled = hapticFeedbackEnabled ?: haptics ?: false

    // -------- reward_content --------
    val rewardContent = details
        ?.get("reward_content")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject

    // bannerImage at root level
    val bannerImage = scratchCardDetails.bannerImage ?: ""

    // NEW: offerTitle is now an object with text and textStyle
    val offerTitleObj = rewardContent
        ?.get("offerTitle")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject
    val offerTitle = offerTitleObj
        ?.get("text")
        ?.jsonPrimitive
        ?.contentOrNull ?: ""
    val offerTitleTextStyle = offerTitleObj
        ?.get("textStyle")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject
    val titleFontSize = offerTitleTextStyle
        ?.get("fontSize")
        ?.jsonPrimitive
        ?.intOrNull ?: 18
    val offerTitleColor = offerTitleTextStyle
        ?.get("color")
        ?.jsonPrimitive
        ?.contentOrNull ?: "#000000"
    val offerTitleFontFamily = offerTitleTextStyle
        ?.get("fontFamily")
        ?.jsonPrimitive
        ?.contentOrNull ?: ""
    val offerTitleFontDecoration = try {
        offerTitleTextStyle?.get("fontDecoration")?.jsonArray?.map {
            it.jsonPrimitive.content
        } ?: listOf()
    } catch (e: Exception) {
        listOf()
    }
    val offerTitleTextAlign = offerTitleTextStyle
        ?.get("textAlign")
        ?.jsonPrimitive
        ?.contentOrNull ?: "center"
    val offerTitleMargin = offerTitleTextStyle
        ?.get("margin")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject
    val offerTitleMarginTop = offerTitleMargin?.get("top")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp
    val offerTitleMarginBottom =
        offerTitleMargin?.get("bottom")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp
    val offerTitleMarginLeft = offerTitleMargin?.get("left")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp
    val offerTitleMarginRight = offerTitleMargin?.get("right")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp

    // NEW: offerSubtitle is now an object with text and textStyle
    val offerSubtitleObj = rewardContent
        ?.get("offerSubtitle")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject
    val offerSubtitle = offerSubtitleObj
        ?.get("text")
        ?.jsonPrimitive
        ?.contentOrNull ?: ""
    val offerSubtitleTextStyle = offerSubtitleObj
        ?.get("textStyle")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject
    val subtitleFontSize = offerSubtitleTextStyle
        ?.get("fontSize")
        ?.jsonPrimitive
        ?.intOrNull ?: 14
    val offerSubtitleColor = offerSubtitleTextStyle
        ?.get("color")
        ?.jsonPrimitive
        ?.contentOrNull ?: "#000000"
    val offerSubtitleFontFamily = offerSubtitleTextStyle
        ?.get("fontFamily")
        ?.jsonPrimitive
        ?.contentOrNull ?: ""
    val offerSubtitleFontDecoration = try {
        offerSubtitleTextStyle?.get("fontDecoration")?.jsonArray?.map {
            it.jsonPrimitive.content
        } ?: listOf()
    } catch (e: Exception) {
        listOf()
    }
    val offerSubtitleTextAlign = offerSubtitleTextStyle
        ?.get("textAlign")
        ?.jsonPrimitive
        ?.contentOrNull ?: "center"
    val offerSubtitleMargin = offerSubtitleTextStyle
        ?.get("margin")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject
    val offerSubtitleMarginTop =
        offerSubtitleMargin?.get("top")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp
    val offerSubtitleMarginBottom =
        offerSubtitleMargin?.get("bottom")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp
    val offerSubtitleMarginLeft =
        offerSubtitleMargin?.get("left")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp
    val offerSubtitleMarginRight =
        offerSubtitleMargin?.get("right")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp

    // NEW: onlyImage is now a boolean directly
    val onlyImage = rewardContent
        ?.get("onlyImage")
        ?.jsonPrimitive
        ?.booleanOrNull ?: false

    val rewardBgColor = rewardContent
        ?.get("background_color")
        ?.jsonPrimitive
        ?.contentOrNull
        ?.ifEmpty { "#FFFFFF" } ?: "#FFFFFF"

    // -------- coupon_code (now at root level of details) --------
    val couponCode = scratchCardDetails.coupon_code ?: ""

    // -------- couponCodeCta (replaces old coupon object, now inside reward_content) --------
    val couponCodeCta = rewardContent
        ?.get("couponCodeCta")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject
    val couponContainer = couponCodeCta
        ?.get("container")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject
    val couponTextObj = couponCodeCta
        ?.get("text")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject
    val couponCornerRadiusObj = couponCodeCta
        ?.get("cornerRadius")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject
    val couponMarginObj = couponCodeCta
        ?.get("margin")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject

    val couponBgColor = couponContainer
        ?.get("backgroundColor")
        ?.jsonPrimitive
        ?.contentOrNull ?: "#30d158"

    val couponBorderColor = couponContainer
        ?.get("borderColor")
        ?.jsonPrimitive
        ?.contentOrNull ?: "#fa6837"

    val couponBorderWidth = couponContainer
        ?.get("borderWidth")
        ?.jsonPrimitive
        ?.intOrNull ?: 1

    val couponAlignment = couponContainer
        ?.get("alignment")
        ?.jsonPrimitive
        ?.contentOrNull ?: "center"

    val couponCtaFullWidth = couponContainer
        ?.get("ctaFullWidth")
        ?.jsonPrimitive
        ?.booleanOrNull ?: false

    val couponCtaWidth = couponContainer
        ?.get("ctaWidth")
        ?.jsonPrimitive
        ?.intOrNull
        ?.dp ?: Dp.Unspecified

    val couponHeight = couponContainer
        ?.get("height")
        ?.jsonPrimitive
        ?.intOrNull
        ?.dp ?: Dp.Unspecified

    val couponTextColor = couponTextObj
        ?.get("color")
        ?.jsonPrimitive
        ?.contentOrNull ?: "#008932"

    val couponFontSize = couponTextObj
        ?.get("fontSize")
        ?.jsonPrimitive
        ?.intOrNull ?: 14

    val couponFontFamily = couponTextObj
        ?.get("fontFamily")
        ?.jsonPrimitive
        ?.contentOrNull ?: ""

    val couponFontDecoration = try {
        couponTextObj?.get("fontDecoration")?.jsonArray?.map {
            it.jsonPrimitive.content
        } ?: listOf()
    } catch (e: Exception) {
        listOf()
    }

    val couponTopLeft = couponCornerRadiusObj?.get("topLeft")?.jsonPrimitive?.intOrNull?.dp ?: 8.dp
    val couponTopRight =
        couponCornerRadiusObj?.get("topRight")?.jsonPrimitive?.intOrNull?.dp ?: 8.dp
    val couponBottomLeft =
        couponCornerRadiusObj?.get("bottomLeft")?.jsonPrimitive?.intOrNull?.dp ?: 8.dp
    val couponBottomRight =
        couponCornerRadiusObj?.get("bottomRight")?.jsonPrimitive?.intOrNull?.dp ?: 8.dp

    val couponMarginTop = couponMarginObj?.get("top")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp
    val couponMarginBottom = couponMarginObj?.get("bottom")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp
    val couponMarginLeft = couponMarginObj?.get("left")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp
    val couponMarginRight = couponMarginObj?.get("right")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp

    // -------- custom_sound_enabled --------
    val customSoundEnabled = details
        ?.get("custom_sound_enabled")
        ?.jsonPrimitive
        ?.booleanOrNull ?: true

    // -------- cta --------
    val cta = details
        ?.get("cta")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject

    val container = cta?.get("container")?.takeIf { it !is JsonNull }?.jsonObject
    val textObj = cta?.get("text")?.takeIf { it !is JsonNull }?.jsonObject
    val cornerRadiusObj = cta?.get("cornerRadius")?.takeIf { it !is JsonNull }?.jsonObject
    val marginObj = cta?.get("margin")?.takeIf { it !is JsonNull }?.jsonObject

    val ctaHeight = container
        ?.get("height")
        ?.jsonPrimitive
        ?.intOrNull
        ?.dp ?: 48.dp

    val ctaColor = container
        ?.get("backgroundColor")
        ?.jsonPrimitive
        ?.contentOrNull ?: "#0066FF"

    val ctaBorderColor = container
        ?.get("borderColor")
        ?.jsonPrimitive
        ?.contentOrNull ?: ""

    val ctaBorderWidth = container
        ?.get("borderWidth")
        ?.jsonPrimitive
        ?.intOrNull ?: 0

    val ctaAlignment = container
        ?.get("alignment")
        ?.jsonPrimitive
        ?.contentOrNull ?: "center"

    val ctaFullWidth = container
        ?.get("ctaFullWidth")
        ?.jsonPrimitive
        ?.booleanOrNull ?: false

    val ctaWidth = container
        ?.get("ctaWidth")
        ?.jsonPrimitive
        ?.intOrNull
        ?.dp ?: Dp.Unspecified


    val ctaText = scratchCardDetails.button_text ?: "Claim offer"


    val ctaTextColor = textObj
        ?.get("color")
        ?.jsonPrimitive
        ?.contentOrNull ?: "#FFFFFF"

    val ctaFontSize = textObj
        ?.get("fontSize")
        ?.jsonPrimitive
        ?.intOrNull ?: 16

    val ctaFontFamily = textObj
        ?.get("fontFamily")
        ?.jsonPrimitive
        ?.contentOrNull ?: ""

    val ctaFontDecoration = try {
        textObj?.get("fontDecoration")?.jsonArray?.map {
            it.jsonPrimitive.content
        } ?: listOf()
    } catch (e: Exception) {
        listOf()
    }


    val topLeft = cornerRadiusObj?.get("topLeft")?.jsonPrimitive?.intOrNull?.dp ?: 12.dp
    val topRight = cornerRadiusObj?.get("topRight")?.jsonPrimitive?.intOrNull?.dp ?: 12.dp
    val bottomLeft = cornerRadiusObj?.get("bottomLeft")?.jsonPrimitive?.intOrNull?.dp ?: 12.dp
    val bottomRight = cornerRadiusObj?.get("bottomRight")?.jsonPrimitive?.intOrNull?.dp ?: 12.dp


    val ctaPaddingTop = marginObj?.get("top")?.jsonPrimitive?.intOrNull?.dp ?: 4.dp
    val ctaPaddingBottom = marginObj?.get("bottom")?.jsonPrimitive?.intOrNull?.dp ?: 4.dp
    val ctaPaddingLeft = marginObj?.get("left")?.jsonPrimitive?.intOrNull?.dp ?: 4.dp
    val ctaPaddingRight = marginObj?.get("right")?.jsonPrimitive?.intOrNull?.dp ?: 4.dp


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
    val density = LocalDensity.current

    // Get configured width, respecting screen boundaries (industry standard: max 95% of screen width)
    val maxCardWidth = screenWidth * 0.90f
    val configuredCardWidth = cardSizeData
        ?.get("width")
        ?.jsonPrimitive
        ?.intOrNull
        ?.dp ?: min(screenWidth.value * 0.85f, 320f).dp

    // Clamp width to screen bounds - industry standard approach
    val cardWidth = minOf(configuredCardWidth, maxCardWidth)

    // Use cardHeight from card_size, fallback to cardWidth for square cards
    val cardHeightDp = cardHeight?.dp ?: cardWidth
    val cornerRadius = cardSizeData
        ?.get("corner_radius")
        ?.jsonPrimitive
        ?.intOrNull
        ?.dp ?: 32.dp

    // -------- imageCircle --------
    val imageCircleObj = rewardContent
        ?.get("imageCircle")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject

    val imageSizeObj = imageCircleObj
        ?.get("size")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject

    val imageWidth = imageSizeObj
        ?.get("width")
        ?.jsonPrimitive
        ?.intOrNull
        ?.dp ?: Dp.Unspecified

    val imageHeight = imageSizeObj
        ?.get("height")
        ?.jsonPrimitive
        ?.intOrNull
        ?.dp ?: Dp.Unspecified

    val imageCornerObj = imageCircleObj
        ?.get("cornerRadius")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject

    val imageTopLeft = imageCornerObj?.get("topLeft")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp
    val imageTopRight = imageCornerObj?.get("topRight")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp
    val imageBottomLeft = imageCornerObj?.get("bottomLeft")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp
    val imageBottomRight = imageCornerObj?.get("bottomRight")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp

    val imageMarginObj = imageCircleObj
        ?.get("margin")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject

    val imageMarginTop = imageMarginObj?.get("top")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp
    val imageMarginBottom = imageMarginObj?.get("bottom")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp
    val imageMarginLeft = imageMarginObj?.get("left")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp
    val imageMarginRight = imageMarginObj?.get("right")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp




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

                // Close button - constrained to card width for proper alignment
                Box(
                    modifier = Modifier
                        .width(cardWidth)
                        .padding(bottom = crossButtonMarginBottom),
                    contentAlignment = when (crossButtonAlignment.lowercase()) {
                        "left", "start" -> Alignment.CenterStart
                        "right", "end" -> Alignment.CenterEnd
                        else -> Alignment.Center
                    }
                ) {
                    this@Column.AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut() + slideOutVertically()
                    ) {
                        val isCrossButtonEnabled = scratchCardDetails.content
                            ?.get("crossButton")
                            ?.takeIf { it !is JsonNull }
                            ?.jsonObject
                            ?.get("enabled")
                            ?.jsonPrimitive
                            ?.booleanOrNull ?: true

                        if (isCrossButtonEnabled) {
                            CrossButton(
                                config = crossButtonConfig,
                                onClose = { onDismiss() }
                            )
                        }
                    }
                }

                //Spacer(modifier = Modifier.height(12.dp))

                // Scratch card
                Box(
                    modifier = Modifier
                        .width(cardWidth)
                        .wrapContentHeight()

                        .clip(RoundedCornerShape(cornerRadius))
                ) {
                    ScratchableCard(
                        cardWidth = cardWidth,
                        cardHeight = cardHeightDp,
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
                        haptics = hapticsEnabled,
                        customSoundEnabled = customSoundEnabled,
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

                // Action buttons
                Box(
                    modifier = Modifier
                        .fillMaxWidth(),
                    contentAlignment = when (ctaAlignment.lowercase()) {
                        "left", "start" -> Alignment.CenterStart
                        "right", "end" -> Alignment.CenterEnd
                        else -> Alignment.Center
                    }
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
                                            when {
                                                ctaFullWidth -> Modifier.fillMaxWidth()
                                                ctaWidth != Dp.Unspecified -> Modifier.width(
                                                    ctaWidth
                                                )

                                                else -> Modifier.wrapContentWidth()
                                            }
                                        )
                                        .height(ctaHeight)
                                        .then(
                                            if (ctaBorderWidth > 0 && ctaBorderColor.isNotEmpty()) {
                                                Modifier.border(
                                                    width = ctaBorderWidth.dp,
                                                    color = parseColorSafe(
                                                        ctaBorderColor,
                                                        Color.Transparent
                                                    ),
                                                    shape = RoundedCornerShape(
                                                        topLeft,
                                                        topRight,
                                                        bottomRight,
                                                        bottomLeft
                                                    )
                                                )
                                            } else {
                                                Modifier
                                            }
                                        ),
                                    shape = RoundedCornerShape(
                                        topLeft,
                                        topRight,
                                        bottomRight,
                                        bottomLeft
                                    ),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = parseColorSafe(ctaColor, Color(0xFF0066FF))
                                    )
                                ) {
                                    CommonText(
                                        text = ctaText,
                                        styling = TextStyling(
                                            color = ctaTextColor,
                                            fontSize = ctaFontSize,
                                            fontFamily = ctaFontFamily,
                                            fontDecoration = ctaFontDecoration.ifEmpty { listOf("semibold") }
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
            Bitmap.createBitmap(cardWidthPx, cardHeightPx, Bitmap.Config.ARGB_8888)
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
                .allowHardware(false)
                .build()

            val result = loader.execute(request)
            val bmp = (result.drawable as? BitmapDrawable)?.bitmap

            bmp?.let {
                overlayBitmap = Bitmap.createScaledBitmap(
                    it,
                    cardWidthPx,
                    cardHeightPx,
                    true
                )
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
                    .wrapContentHeight(),
                bannerImageUrl = bannerImageUrl
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
                    .width(cardWidth)
                    .height(with(LocalDensity.current){measuredHeightPx.toDp()})
                    .pointerInput(Unit) {
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
            } else if (isLottieUrl(bannerImageUrl)) {
                val composition by rememberLottieComposition(
                    spec = LottieCompositionSpec.Url(bannerImageUrl)
                )
                LottieAnimation(
                    composition = composition,
                    iterations = LottieConstants.IterateForever,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
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
    cardHeight: Dp,
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

    // Image styling (ADD THIS BLOCK)
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

    Box(
        modifier = modifier
            .background(parseColorSafe(rewardBgColor, Color(0xFF141414))),
        contentAlignment = Alignment.Center
    ) {
        Column(
//            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
//            verticalArrangement = Arrangement.Top
        ) {
            //Spacer(modifier = Modifier.weight(1f))
            if (bannerImageUrl.isNotEmpty()) {

                Box(
                    modifier = Modifier
                        .padding(
                            start = imageMarginLeft,
                            end = imageMarginRight,
                            top = imageMarginTop,
                            bottom = imageMarginBottom
                        )
                        .then(
                            if (imageWidth != Dp.Unspecified && imageHeight != Dp.Unspecified)
                                Modifier.size(imageWidth, imageHeight)
                            else
                                Modifier.sizeIn(maxWidth = cardHeight * 0.3f, maxHeight = cardHeight * 0.3f)
                        )
                        .clip(
                            RoundedCornerShape(
                                topStart = imageTopLeft,
                                topEnd = imageTopRight,
                                bottomStart = imageBottomLeft,
                                bottomEnd = imageBottomRight
                            )
                        )
                ) {
                    SubcomposeAsyncImage(
                        model = bannerImageUrl,
                        contentDescription = "Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }


            //Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (offerTitle.isNotEmpty()) {
                    CommonText(
                        modifier = Modifier.fillMaxWidth(),
                        text = offerTitle,
                        styling = TextStyling(
                            color = offerTitleColor,
                            fontSize = titleFontSize,
                            fontFamily = offerTitleFontFamily,
                            fontDecoration = offerTitleFontDecoration.ifEmpty { listOf("bold") },
                            textAlign = offerTitleTextAlign,
                            margin = CommonMargins(
                                top = offerTitleMarginTop.value.toInt(),
                                bottom = offerTitleMarginBottom.value.toInt(),
                                left = offerTitleMarginLeft.value.toInt(),
                                right = offerTitleMarginRight.value.toInt()
                            )
                        )
                    )
                }

                //Spacer(Modifier.height(cardHeight * 0.06f))

                if (offerSubtitle.isNotEmpty()) {
                    CommonText(
                        modifier = Modifier.fillMaxWidth(),
                        text = offerSubtitle,
                        letterSpacing = 0.1.toFloat(),
                        styling = TextStyling(
                            color = offerSubtitleColor,
                            fontSize = subtitleFontSize,
                            fontFamily = offerSubtitleFontFamily,
                            fontDecoration = offerSubtitleFontDecoration,
                            textAlign = offerSubtitleTextAlign,
                            margin = CommonMargins(
                                top = offerSubtitleMarginTop.value.toInt(),
                                bottom = offerSubtitleMarginBottom.value.toInt(),
                                left = offerSubtitleMarginLeft.value.toInt(),
                                right = offerSubtitleMarginRight.value.toInt()
                            )
                        )
                    )
                }

                //Spacer(Modifier.height(cardHeight * 0.2f))

                // Coupon code display
                if (couponCode.isNotEmpty()) {
                    val clipboardManager = LocalClipboardManager.current

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = couponMarginLeft,
                                end = couponMarginRight,
                                top = couponMarginTop,
                                bottom = couponMarginBottom
                            ),
                        horizontalArrangement = when (couponAlignment.lowercase()) {
                            "left", "start" -> Arrangement.Start
                            "right", "end" -> Arrangement.End
                            else -> Arrangement.Center
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .then(
                                    if (couponCtaFullWidth) Modifier.fillMaxWidth()
                                    else if (couponCtaWidth != Dp.Unspecified) Modifier.width(
                                        couponCtaWidth
                                    )
                                    else Modifier
                                )
                                .then(
                                    if (couponHeight != Dp.Unspecified) Modifier.height(couponHeight)
                                    else Modifier
                                )
                                .background(
                                    color = parseColorSafe(couponBgColor, Color(0xFF1F1F1F)),
                                    shape = RoundedCornerShape(
                                        topStart = couponTopLeft,
                                        topEnd = couponTopRight,
                                        bottomStart = couponBottomLeft,
                                        bottomEnd = couponBottomRight
                                    )
                                )
                                .then(
                                    if (couponBorderColor.isNotEmpty() && couponBorderWidth > 0) {
                                        Modifier.drawWithContent {
                                            drawContent()
                                            drawRoundRect(
                                                color = parseColorSafe(
                                                    couponBorderColor,
                                                    Color(0xFF0066FF)
                                                ),
                                                style = Stroke(width = couponBorderWidth.dp.toPx()),
                                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                                    couponTopLeft.toPx(),
                                                    couponTopRight.toPx()
                                                )
                                            )
                                        }
                                    } else Modifier
                                )
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(couponCode))
                                    Toast.makeText(
                                        context,
                                        "Copied to clipboard",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .padding(
                                    horizontal = 16.dp,
                                    vertical = if (couponHeight != Dp.Unspecified) 0.dp else cardHeight * 0.05f
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CommonText(
                                text = couponCode,
                                letterSpacing = 0.2.toFloat(),
                                styling = TextStyling(
                                    color = couponTextColor,
                                    fontFamily = couponFontFamily,
                                    fontSize = couponFontSize,
                                    fontDecoration = couponFontDecoration
                                )
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Copy Coupon",
                                tint = parseColorSafe(couponTextColor, Color.White),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            //Spacer(modifier = Modifier.weight(1f))
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
                        text =
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                android.text.Html.fromHtml(
                                    termsHtml,
                                    android.text.Html.FROM_HTML_MODE_COMPACT
                                )
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
                    textView.text =
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                            android.text.Html.fromHtml(
                                termsHtml,
                                android.text.Html.FROM_HTML_MODE_COMPACT
                            )
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

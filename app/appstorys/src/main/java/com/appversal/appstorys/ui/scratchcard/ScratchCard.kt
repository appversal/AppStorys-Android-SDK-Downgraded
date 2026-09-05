package com.appversal.appstorys.ui.scratchcard

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.min
import com.appversal.appstorys.api.TextStyling
import com.appversal.appstorys.ui.common_components.CommonText
import com.appversal.appstorys.ui.common_components.CrossButton
import com.appversal.appstorys.ui.common_components.CrossButtonConfig
import com.appversal.appstorys.ui.common_components.createCrossButtonConfig
import kotlinx.serialization.json.JsonNull
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
    onCtaClick: () -> Unit = {},
    /**
     * Fired when the card is actually composed — after the cover gate, so the user
     * really is looking at it. `viewed` used to fire on the decision to show the
     * card instead, which counted impressions nobody saw: a cover that 404s holds
     * the card for the full timeout, and backgrounding the app in that window still
     * recorded a view. That matters because dismissals are derived as
     * `viewed - scratched`, so every phantom view became a phantom dismissal.
     */
    onCardShown: () -> Unit = {},
) {
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
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    // Parsed once per campaign. CardScratch recomposes on every pointer event
    // while the card is scratched, and the payload cannot change under it.
    val cfg = remember(scratchCardDetails, screenWidth) {
        ScratchCardConfig.from(scratchCardDetails, screenWidth)
    }

    val crossButtonConfig = remember(cfg) {
        createCrossButtonConfig(
            fillColorString = cfg.crossFillColor,
            crossColorString = cfg.crossColor,
            strokeColorString = cfg.crossStrokeColor,
            marginTop = cfg.crossMarginTop,
            marginEnd = cfg.crossMarginEnd,
            size = cfg.crossSize,
            imageUrl = cfg.crossImageUrl
        )
    }

    // Decode the cover before the card is composed, then seed the scratch bitmap
    // with it (see ScratchableCard). Painting grey first and drawing the cover from
    // a LaunchedEffect afterwards cost a measured ~400ms of visible grey on every
    // launch, warm cache or not, because that effect cannot run before the first
    // frame. Skipped when already scratched — no cover is drawn then.
    val imageLoader = scratchCardImageLoader(LocalContext.current)
    // Decoded against the tallest card we could end up drawing, not the configured
    // height: the card's height is derived from this very bitmap, so a box of the
    // configured height would upscale a portrait cover. Coil fits within the box and
    // preserves aspect, so this only bounds it. prefetchScratchCardCovers must pass
    // the same numbers or it warms a cache key nothing reads.
    val coverPx = with(LocalDensity.current) {
        cfg.cardWidth.roundToPx() to (screenHeight * MAX_CARD_HEIGHT).roundToPx()
    }
    val coverState = rememberCover(cfg.overlayImage, imageLoader, coverPx.first, coverPx.second)
    if (coverState is CoverState.Loading && !wasFullyScratched) return
    val coverBitmap = (coverState as? CoverState.Ready)?.bitmap

    // An onlyImage card IS the cover, so the container takes the cover's aspect:
    // height = width x (srcH / srcW). The container then matches the artwork
    // exactly, which is what makes fit/fill/crop equivalent for it.
    //
    // Capped, because a tall upload would otherwise size the card past the screen —
    // 300dp wide from a 1000x5000 image is a 1500dp card, which is how this
    // component originally went wrong. When the cap bites the aspects no longer
    // match, and the centre-crop in ScratchableCard absorbs the difference.
    val coverAspectHeight = if (coverBitmap != null && coverBitmap.width > 0) {
        (cfg.cardWidth * (coverBitmap.height.toFloat() / coverBitmap.width))
            .coerceAtMost(screenHeight * MAX_CARD_HEIGHT)
    } else {
        null
    }

    // onlyImage: the card IS the cover, so it keeps the cover's aspect in both
    // states and the reward is cropped into it.
    //
    // onlyImage off: the two states are sized independently. While covered the box
    // takes the cover's aspect so the artwork is not distorted; once revealed the
    // styled content decides, because that is what the backend styling describes.
    // cardHeight stays the configured value either way — with the toggle off it is
    // not a height at all, it is the proportion basis the reward view scales its
    // banner and coupon padding from, and that must not move between states.
    val cardHeight = if (cfg.onlyImage) coverAspectHeight ?: cfg.cardHeightDp else cfg.cardHeightDp

    // ponytail: snaps between the two heights on reveal. Animate when the jump
    // reads badly — animateDpAsState on coveredHeight is the whole change.
    val coveredHeight = if (!cfg.onlyImage && !isRevealed) coverAspectHeight else null

    with(cfg) {
        LaunchedEffect(wasFullyScratched) {
            if (wasFullyScratched) {
                isRevealed = true
            }
        }

        if (isPresented) {
            LaunchedEffect(Unit) { onCardShown() }
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
                            .padding(bottom = crossMarginBottom),
                        contentAlignment = when (crossAlignment.lowercase()) {
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
                            if (crossEnabled) {
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
                            cardHeight = cardHeight,
                            coveredHeight = coveredHeight,
                            points = points,
                            isRevealed = isRevealed,
                            coverBitmap = coverBitmap,
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
}

/** Ceiling for a cover-derived card height, as a fraction of screen height. */
private const val MAX_CARD_HEIGHT = 0.75f

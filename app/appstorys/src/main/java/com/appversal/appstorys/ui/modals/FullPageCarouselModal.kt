package com.appversal.appstorys.ui.modals

import android.os.Build.VERSION.SDK_INT
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.appversal.appstorys.api.ModalContent
import com.appversal.appstorys.api.ModalDetails
import com.appversal.appstorys.ui.AutoSlidingCarousel
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import com.appversal.appstorys.ui.components.CrossButton
import com.appversal.appstorys.ui.components.createCrossButtonConfig
import com.appversal.appstorys.ui.components.parseColorString
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment.Companion.TopEnd
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.appversal.appstorys.utils.VideoCache

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FullPageCarouselModal(
    onCloseClick: () -> Unit,
    modalDetails: ModalDetails,
    onModalClick: () -> Unit,
    onPrimaryCta: ((link: String?) -> Unit)? = null,
    onSecondaryCta: ((link: String?) -> Unit)? = null,
) {
    val modal = modalDetails.modals?.getOrNull(0) ?: return
    // Determine slides: prefer content.set if present, else fallback to single content as a list
    val slides: List<ModalContent> = modal.content?.set?.takeIf { it.isNotEmpty() } ?: listOfNotNull(modal.content)

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    // appearance and styling - prefer slide-level appearance if present else modal-level
    // We'll compute effective appearance per modal using first slide's styling if provided
    val firstSlideAppearance = slides.firstOrNull()?.styling?.appearance
    val effectiveAppearance = firstSlideAppearance ?: modal.styling?.appearance

    // prefer dimentons if provided by backend (some send misspelled field)
    val effectiveDimension = effectiveAppearance?.dimension ?: effectiveAppearance?.dimentons

    // backdrop color/opacity may be provided in multiple forms; prefer structured `backdrop` then fallback to flat fields
    val backdropColorString = effectiveAppearance?.backdrop?.color ?: effectiveAppearance?.backdropColor
    val backdropOpacityString = effectiveAppearance?.backdrop?.opacity ?: effectiveAppearance?.backdropOpacity

    val rawBackdropOpacityFinal = backdropOpacityString?.toFloatOrNull() ?: 30f
    val backdropAlphaFinal = if (effectiveAppearance?.enableBackdrop == false) 0f else (rawBackdropOpacityFinal / 100f).coerceIn(0f,1f)
    val backdropColorFinal = parseColorString(backdropColorString) ?: Color.Black

    // compute container corner radius from effective appearance if provided
    val containerCornerShape = RoundedCornerShape(
        topStart = (effectiveAppearance?.cornerRadius?.topLeft?.toFloatOrNull() ?: 12f).dp,
        topEnd = (effectiveAppearance?.cornerRadius?.topRight?.toFloatOrNull() ?: 12f).dp,
        bottomStart = (effectiveAppearance?.cornerRadius?.bottomLeft?.toFloatOrNull() ?: 12f).dp,
        bottomEnd = (effectiveAppearance?.cornerRadius?.bottomRight?.toFloatOrNull() ?: 12f).dp,
    )

    // compute container height: prefer effectiveDimension.height, else fallback to 90% screen
    val containerHeight = effectiveDimension?.height?.toFloatOrNull()?.dp ?: (screenHeight * 1f)

    // container padding from effective appearance (fallback to 8.dp)
    val padStart = (effectiveAppearance?.padding?.left ?: 8).dp
    val padTop = (effectiveAppearance?.padding?.top ?: 8).dp
    val padEnd = (effectiveAppearance?.padding?.right ?: 8).dp
    val padBottom = (effectiveAppearance?.padding?.bottom ?: 8).dp

    Dialog(
        onDismissRequest = onCloseClick,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backdropColorFinal.copy(alpha = backdropAlphaFinal))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onCloseClick() },
             contentAlignment = Alignment.Center
        ) {
            // Carousel container: use a large container (90% height)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(containerHeight)
                    .clip(containerCornerShape)
                    .background(Color.White)
                    .padding(start = padStart, top = padTop, end = padEnd, bottom = padBottom)
            ) {
                val pagerState = rememberPagerState(initialPage = 0, pageCount = { slides.size })

                AutoSlidingCarousel(
                    widgetDetails = com.appversal.appstorys.api.WidgetDetails(id = null, type = null, width = null, height = null, widgetImages = null, campaign = null, screen = null, styling = null),
                    autoScrollEnabled = false,
                    pagerState = pagerState,
                    itemsCount = slides.size,
                    itemContent = { index ->
                        val slide = slides[index]
                        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onModalClick() }
                            ) {
                                // render media
                                val imageUrl = slide.chooseMediaType?.url ?: ""
                                val mediaType = when {
                                    imageUrl.endsWith(".gif", ignoreCase = true) -> "gif"
                                    imageUrl.endsWith(".json", ignoreCase = true) -> "lottie"
                                    imageUrl.endsWith(".mp4", ignoreCase = true) || imageUrl.endsWith(".m3u8", ignoreCase = true) -> "video"
                                    else -> "image"
                                }

                                ModalMediaRenderer(mediaUrl = imageUrl, modifier = Modifier.fillMaxWidth().fillMaxHeight(), contentDescription = slide.titleText ?: "", muted = false)
                             }

                            // Resolve alignment from slide styling if present, else fallback to modal styling.
                            val titleAlignmentStr = slide.styling?.title?.alignment ?: modal.styling?.title?.alignment
                            val titleTextAlign = when (titleAlignmentStr?.trim()?.lowercase()) {
                                "left" -> TextAlign.Start
                                "right" -> TextAlign.End
                                "center" -> TextAlign.Center
                                else -> TextAlign.Center
                            }

                            val subtitleAlignmentStr = slide.styling?.subTitle?.alignment ?: modal.styling?.subTitle?.alignment
                            val subtitleTextAlign = when (subtitleAlignmentStr?.trim()?.lowercase()) {
                                "left" -> TextAlign.Start
                                "right" -> TextAlign.End
                                "center" -> TextAlign.Center
                                else -> TextAlign.Center
                            }

                            slide.titleText?.let { title ->
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = title,
                                    color = parseColorString(slide.styling?.title?.color) ?: parseColorString(modal.styling?.title?.color) ?: Color.Black,
                                    fontSize = (slide.styling?.title?.size ?: modal.styling?.title?.size ?: 18).sp,
                                    textAlign = titleTextAlign,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            slide.subtitleText?.let { subtitle ->
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = subtitle,
                                    color = parseColorString(slide.styling?.subTitle?.color) ?: parseColorString(modal.styling?.subTitle?.color) ?: Color.Gray,
                                    fontSize = (slide.styling?.subTitle?.size ?: modal.styling?.subTitle?.size ?: 14).sp,
                                    textAlign = subtitleTextAlign,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // CTAs row
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                // Resolve CTA text with fallbacks (slide -> modal)
                                val primaryText = slide.primaryCtaText
                                    ?: slide.primaryCta
                                    ?: modal.content?.primaryCtaText
                                    ?: modal.content?.primaryCta

                                val secondaryText = slide.secondaryCtaText
                                    ?: slide.secondayCta
                                    ?: slide.secondaryCtaAlt
                                    ?: modal.content?.secondaryCtaText
                                    ?: modal.content?.secondaryCtaAlt

                                val primaryStyling = slide.styling?.primaryCta ?: modal.styling?.primaryCta
                                val secondaryStyling = slide.styling?.secondaryCta ?: modal.styling?.secondaryCta

                                val primaryOccupy = primaryStyling?.occupyFullWidth?.trim()?.equals("true", true) == true
                                val secondaryOccupy = secondaryStyling?.occupyFullWidth?.trim()?.equals("true", true) == true

                                // Margins
                                val primaryMarginLeftDp = (primaryStyling?.spacing?.margin?.left ?: 0).dp
                                val primaryMarginRightDp = (primaryStyling?.spacing?.margin?.right ?: 0).dp
                                val primaryMarginTopDp = (primaryStyling?.spacing?.margin?.top ?: 0).dp
                                val primaryMarginBottomDp = (primaryStyling?.spacing?.margin?.bottom ?: 0).dp

                                val secondaryMarginLeftDp = (secondaryStyling?.spacing?.margin?.left ?: 0).dp
                                val secondaryMarginRightDp = (secondaryStyling?.spacing?.margin?.right ?: 0).dp
                                val secondaryMarginTopDp = (secondaryStyling?.spacing?.margin?.top ?: 0).dp
                                val secondaryMarginBottomDp = (secondaryStyling?.spacing?.margin?.bottom ?: 0).dp

                                val minHorizontalInset = 2.dp
                                val effectivePrimaryMarginLeftDp = if (primaryOccupy && primaryMarginLeftDp == 0.dp) minHorizontalInset else primaryMarginLeftDp
                                val effectivePrimaryMarginRightDp = if (primaryOccupy && primaryMarginRightDp == 0.dp) minHorizontalInset else primaryMarginRightDp
                                val effectiveSecondaryMarginLeftDp = if (secondaryOccupy && secondaryMarginLeftDp == 0.dp) minHorizontalInset else secondaryMarginLeftDp
                                val effectiveSecondaryMarginRightDp = if (secondaryOccupy && secondaryMarginRightDp == 0.dp) minHorizontalInset else secondaryMarginRightDp

                                val ctaBetweenSpacing = 8.dp

                                // Primary button
                                if (!primaryText.isNullOrEmpty()) {
                                    val primaryBg = parseColorString(primaryStyling?.backgroundColor) ?: Color.Black
                                    val primaryTextColor = parseColorString(primaryStyling?.textColor) ?: Color.White
                                    val primaryHeight = (primaryStyling?.containerStyle?.height ?: 48).dp
                                    val primaryBorderWidth = (primaryStyling?.containerStyle?.borderWidth ?: 0).dp
                                    val primaryBorderColor = parseColorString(primaryStyling?.borderColor) ?: Color.Transparent
                                    val primaryWidth = primaryStyling?.containerStyle?.ctaWidth?.let { it.dp }
                                    val primaryTextSize = primaryStyling?.textStyle?.size ?: 14
                                    val primaryShape = RoundedCornerShape(
                                        topStart = (primaryStyling?.cornerRadius?.topLeft ?: 12).dp,
                                        topEnd = (primaryStyling?.cornerRadius?.topRight ?: 12).dp,
                                        bottomStart = (primaryStyling?.cornerRadius?.bottomLeft ?: 12).dp,
                                        bottomEnd = (primaryStyling?.cornerRadius?.bottomRight ?: 12).dp,
                                    )

                                    val primaryBoxModifier = (if (primaryOccupy) Modifier.weight(1f) else Modifier)
                                        .padding(start = effectivePrimaryMarginLeftDp, end = effectivePrimaryMarginRightDp, top = primaryMarginTopDp, bottom = primaryMarginBottomDp)

                                    Box(modifier = primaryBoxModifier) {
                                        BackendCta(
                                            text = primaryText,
                                            height = primaryHeight,
                                            width = primaryWidth,
                                            occupyFullWidth = primaryOccupy,
                                            backgroundColor = primaryBg,
                                            textColor = primaryTextColor,
                                            textSizeSp = primaryTextSize,
                                            borderColor = primaryBorderColor,
                                            borderWidth = primaryBorderWidth,
                                            cornerRadius = primaryShape,
                                            modifier = Modifier
                                        ) {
                                            val link = slide.primaryCtaRedirection?.url ?: slide.primaryCtaRedirection?.value
                                            onPrimaryCta?.invoke(link)
                                        }
                                    }
                                }

                                // Secondary button
                                if (!secondaryText.isNullOrEmpty()) {
                                    val secondaryBg = parseColorString(secondaryStyling?.backgroundColor) ?: Color.DarkGray
                                    val secondaryTextColor = parseColorString(secondaryStyling?.textColor) ?: Color.White
                                    val secondaryHeight = (secondaryStyling?.containerStyle?.height ?: 48).dp
                                    val secondaryBorderWidth = (secondaryStyling?.containerStyle?.borderWidth ?: 0).dp
                                    val secondaryBorderColor = parseColorString(secondaryStyling?.borderColor) ?: Color.Transparent
                                    val secondaryWidth = secondaryStyling?.containerStyle?.ctaWidth?.let { it.dp }
                                    val secondaryTextSize = secondaryStyling?.textStyle?.size ?: 14
                                    val secondaryShape = RoundedCornerShape(
                                        topStart = (secondaryStyling?.cornerRadius?.topLeft ?: 12).dp,
                                        topEnd = (secondaryStyling?.cornerRadius?.topRight ?: 12).dp,
                                        bottomStart = (secondaryStyling?.cornerRadius?.bottomLeft ?: 12).dp,
                                        bottomEnd = (secondaryStyling?.cornerRadius?.bottomRight ?: 12).dp,
                                    )

                                    val secondaryBoxModifier = (if (secondaryOccupy) Modifier.weight(1f) else Modifier)
                                        .padding(start = effectiveSecondaryMarginLeftDp, end = effectiveSecondaryMarginRightDp, top = secondaryMarginTopDp, bottom = secondaryMarginBottomDp)

                                    Box(modifier = secondaryBoxModifier) {
                                        BackendCta(
                                            text = secondaryText,
                                            height = secondaryHeight,
                                            width = secondaryWidth,
                                            occupyFullWidth = secondaryOccupy,
                                            backgroundColor = secondaryBg,
                                            textColor = secondaryTextColor,
                                            textSizeSp = secondaryTextSize,
                                            borderColor = secondaryBorderColor,
                                            borderWidth = secondaryBorderWidth,
                                            cornerRadius = secondaryShape,
                                            modifier = Modifier
                                        ) {
                                            val link = slide.secondaryCtaRedirection?.url ?: slide.secondaryCtaRedirection?.value
                                            onSecondaryCta?.invoke(link)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                )

                // Cross button: prefer slide-level crossButton config (first slide) then modal-level
                val slideCrossButton = slides.firstOrNull()?.styling?.crossButton
                val effectiveCrossButton = slideCrossButton ?: modal.styling?.crossButton

                val crossButtonImageUrl = effectiveCrossButton?.uploadImage?.url ?: effectiveCrossButton?.default?.crossButtonImage
                val crossConfig = createCrossButtonConfig(
                    fillColorString = effectiveCrossButton?.default?.color?.fill,
                    crossColorString = effectiveCrossButton?.default?.color?.cross,
                    strokeColorString = effectiveCrossButton?.default?.color?.stroke,
                    marginTop = effectiveCrossButton?.default?.spacing?.margin?.top,
                    marginEnd = effectiveCrossButton?.default?.spacing?.margin?.right,
                    imageUrl = crossButtonImageUrl
                )

                // show cross button only if enabled (defaults to true)
                // check per-slide enable first, then modal.content
                val contentEnable = slides.firstOrNull()?.enableCrossButton?.trim()?.equals("true", true)
                    ?: modal.content?.enableCrossButton?.trim()?.equals("true", true)

                val crossEnableFlag = effectiveCrossButton?.enableCrossButton
                val hasCrossResources = (effectiveCrossButton?.default != null) || (!effectiveCrossButton?.uploadImage?.url.isNullOrEmpty())
                val showCross = (contentEnable ?: (crossEnableFlag != false)) && hasCrossResources

                if (showCross) {
                    Box(modifier = Modifier.align(TopEnd)) {
                        CrossButton(modifier = Modifier.size(36.dp), config = crossConfig, onClose = onCloseClick, boundaryPadding = 3.dp)
                    }
                }

             }
         }
     }
 }

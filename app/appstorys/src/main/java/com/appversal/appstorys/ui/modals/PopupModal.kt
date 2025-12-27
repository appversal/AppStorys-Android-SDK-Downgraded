package com.appversal.appstorys.ui.modals

import android.os.Build.VERSION.SDK_INT
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.appversal.appstorys.api.ModalDetails
import androidx.compose.foundation.layout.Arrangement
import com.appversal.appstorys.ui.components.CrossButton
import com.appversal.appstorys.ui.components.createCrossButtonConfig
import com.appversal.appstorys.ui.components.parseColorString
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
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

@Composable
internal fun PopupModal(
    onCloseClick: () -> Unit,
    modalDetails: ModalDetails,
    onModalClick: () -> Unit,
    onPrimaryCta: ((link: String?) -> Unit)? = null,
    onSecondaryCta: ((link: String?) -> Unit)? = null,
) {
    val modal = modalDetails.modals?.getOrNull(0)

    // If backend explicitly sent a carousel modal type, delegate to the FullPageCarousel implementation
    if (modal?.modalType?.trim()?.equals("modal-fullpage-carousel", true) == true) {
        FullPageCarouselModal(onCloseClick = onCloseClick, modalDetails = modalDetails, onModalClick = onModalClick, onPrimaryCta = onPrimaryCta, onSecondaryCta = onSecondaryCta)
        return
    }

    val imageUrl = modal?.content?.chooseMediaType?.url
    val context = LocalContext.current
    // appearance (styling) - may be null if backend omits styling
    val appearance = modal?.styling?.appearance

    val mediaType = when {
        imageUrl?.endsWith(".gif", ignoreCase = true) == true -> "gif"
        imageUrl?.endsWith(".json", ignoreCase = true) == true -> "lottie"
        else -> "image"
    }

    // appearance values (safe parsing) - wired from ModalAppearance
    // Use industry-standard fallbacks when backend omits values
    val appearanceHeightDp = appearance?.dimension?.height?.toFloatOrNull()?.dp ?: 180.dp // default image height
    val appearanceBorderWidth = appearance?.dimension?.borderWidth?.toFloatOrNull()?.dp ?: 0.dp
    val containerShape = RoundedCornerShape(
        topStart = appearance?.cornerRadius?.topLeft?.toFloatOrNull()?.dp ?: 12.dp,
        topEnd = appearance?.cornerRadius?.topRight?.toFloatOrNull()?.dp ?: 12.dp,
        bottomStart = appearance?.cornerRadius?.bottomLeft?.toFloatOrNull()?.dp ?: 12.dp,
        bottomEnd = appearance?.cornerRadius?.bottomRight?.toFloatOrNull()?.dp ?: 12.dp
    )

    // backdrop opacity (appearance.backdrop.opacity is percentage). Respect enableBackdrop (if false => 0f)
    // backdrop opacity percentage (fallback to 30% if missing)
    val rawBackdropOpacityStr = appearance?.backdrop?.opacity ?: appearance?.backdropOpacity ?: "30"
    val rawBackdropOpacity = rawBackdropOpacityStr.toFloatOrNull() ?: 30f
    val backdropAlpha = if (appearance?.enableBackdrop == false) 0f else (rawBackdropOpacity / 100f).coerceIn(0f, 1f)

    // CTA styling (integers from model)
    val primaryBg = parseColorString(modal?.styling?.primaryCta?.backgroundColor) ?: Color.Black
    val primaryTextColor = parseColorString(modal?.styling?.primaryCta?.textColor) ?: Color.White

    // CTA default height: 48.dp is a common, accessible size
    val primaryHeight = (modal?.styling?.primaryCta?.containerStyle?.height ?: 48).dp
    val primaryBorderWidth = (modal?.styling?.primaryCta?.containerStyle?.borderWidth ?: 0).dp
    val primaryBorderColor = parseColorString(modal?.styling?.primaryCta?.borderColor) ?: Color.Transparent
    val primaryWidth = modal?.styling?.primaryCta?.containerStyle?.ctaWidth?.dp

    val secondaryBg = parseColorString(modal?.styling?.secondaryCta?.backgroundColor) ?: Color.DarkGray
    val secondaryTextColor = parseColorString(modal?.styling?.secondaryCta?.textColor) ?: Color.White
    val secondaryHeight = (modal?.styling?.secondaryCta?.containerStyle?.height ?: 48).dp
    val secondaryBorderWidth = (modal?.styling?.secondaryCta?.containerStyle?.borderWidth ?: 0).dp
    val secondaryBorderColor = parseColorString(modal?.styling?.secondaryCta?.borderColor) ?: Color.Transparent
    val secondaryWidth = modal?.styling?.secondaryCta?.containerStyle?.ctaWidth?.dp

    // Title/subtitle styling
    val titleColor = parseColorString(modal?.styling?.title?.color) ?: Color(0xFF3700FF)
    val titleSizeSp = modal?.styling?.title?.size?.sp ?: 16.sp
    val subtitleColor = parseColorString(modal?.styling?.subTitle?.color) ?: Color.Gray
    val subtitleSizeSp = modal?.styling?.subTitle?.size?.sp ?: 12.sp

    // Prefer uploaded image URL if provided by backend, fallback to default crossButtonImage
    val crossButtonImageUrl = modal?.styling?.crossButton?.uploadImage?.url
        ?: modal?.styling?.crossButton?.default?.crossButtonImage

    val crossConfig = createCrossButtonConfig(
        fillColorString = modal?.styling?.crossButton?.default?.color?.fill,
        crossColorString = modal?.styling?.crossButton?.default?.color?.cross,
        strokeColorString = modal?.styling?.crossButton?.default?.color?.stroke,
        marginTop = modal?.styling?.crossButton?.default?.spacing?.margin?.top,
        marginEnd = modal?.styling?.crossButton?.default?.spacing?.margin?.right,
        imageUrl = crossButtonImageUrl
    )


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
                .background(Color.Black.copy(alpha = backdropAlpha))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    onCloseClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.wrapContentSize()) {
                // White rounded container
                // Enforce a minimum bottom padding so CTAs don't sit flush when backend sends 0
                // Give at least 12.dp bottom inset so CTA borders/rounded corners don't visually overflow
                val minBottomPadding = 0.dp
                val containerPaddingStart = appearance?.padding?.left?.dp ?: 0.dp
                val containerPaddingTop = appearance?.padding?.top?.dp ?: 0.dp
                val containerPaddingEnd = appearance?.padding?.right?.dp ?: 0.dp
                val rawBottom = appearance?.padding?.bottom?.dp ?: 0.dp
                val containerPaddingBottom = if (rawBottom < minBottomPadding) minBottomPadding else rawBottom

                Box(
                    modifier = Modifier
                        .widthIn(max = appearanceHeightDp * 1.2f)
                        .clip(containerShape)
                        .background(Color.White)
                        .then(if (appearanceBorderWidth > 0.dp) Modifier.border(appearanceBorderWidth, Color.LightGray, containerShape) else Modifier)
                        .padding(
                            start = containerPaddingStart,
                            end = containerPaddingEnd,
                            top = containerPaddingTop,
                            bottom = containerPaddingBottom
                        )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // media
                        Box(
                            modifier = Modifier
                                .wrapContentSize()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { onModalClick() }
                        ) {
                            when (mediaType) {
                                "gif" -> {
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
                                            .data(imageUrl)
                                            .diskCachePolicy(CachePolicy.ENABLED)
                                            .memoryCachePolicy(CachePolicy.ENABLED)
                                            .build(),
                                        imageLoader = imageLoader
                                    )

                                    Image(
                                        painter = painter,
                                        contentDescription = "GIF Image",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(appearanceHeightDp)
                                            .clip(containerShape)
                                    )
                                }

                                "lottie" -> {
                                    // Support both URL-backed Lottie and inline Lottie JSON strings
                                    val lottieSrc = imageUrl ?: ""
                                    val compositionSpec = if (lottieSrc.trimStart().startsWith("{") || lottieSrc.trimStart().startsWith("[")) {
                                        LottieCompositionSpec.JsonString(lottieSrc)
                                    } else {
                                        LottieCompositionSpec.Url(lottieSrc)
                                    }
                                    val composition by rememberLottieComposition(compositionSpec)
                                    LottieAnimation(
                                        composition = composition,
                                        iterations = LottieConstants.IterateForever,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(appearanceHeightDp)
                                            .clip(containerShape)
                                    )
                                }

                                // Video handling: detect common video extensions and render ExoPlayer
                                "video" -> {
                                    ModalMediaRenderer(mediaUrl = imageUrl ?: "", modifier = Modifier
                                        .fillMaxWidth()
                                        .height(appearanceHeightDp)
                                        .clip(containerShape), contentDescription = "Video", muted = false)
                                }

                                else -> {
                                    AsyncImage(
                                        model = imageUrl,
                                        contentDescription = "Popup Image",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(appearanceHeightDp)
                                            .clip(containerShape),
                                        contentScale = ContentScale.Crop
                                    )
                                }

                            }
                        }

                        // Title and subtitle
                        // Determine title/subtitle alignment from backend styling (defaults to Center)
                        val titleTextAlign = when (modal?.styling?.title?.alignment?.trim()?.lowercase()) {
                            "left" -> TextAlign.Start
                            "right" -> TextAlign.End
                            "center" -> TextAlign.Center
                            else -> TextAlign.Center
                        }

                        val subtitleTextAlign = when (modal?.styling?.subTitle?.alignment?.trim()?.lowercase()) {
                            "left" -> TextAlign.Start
                            "right" -> TextAlign.End
                            "center" -> TextAlign.Center
                            else -> TextAlign.Center
                        }

                        modal?.content?.titleText?.let { title ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = title,
                                color = titleColor,
                                fontSize = titleSizeSp,
                                textAlign = titleTextAlign,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        modal?.content?.subtitleText?.let { subtitle ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = subtitle,
                                color = subtitleColor,
                                fontSize = subtitleSizeSp,
                                textAlign = subtitleTextAlign,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // CTA buttons
                        // If neither CTA is occupying full width, center the row contents
                        val primaryOccupy = modal?.styling?.primaryCta?.occupyFullWidth?.trim()?.equals("true", true) == true
                        val secondaryOccupy = modal?.styling?.secondaryCta?.occupyFullWidth?.trim()?.equals("true", true) == true

                        // Read CTA margins from backend (dashboard) - default to 0.dp to honor explicit zero from dashboard
                        val primaryMarginLeft = (modal?.styling?.primaryCta?.spacing?.margin?.left ?: 0)
                        val primaryMarginRight = (modal?.styling?.primaryCta?.spacing?.margin?.right ?: 0)
                        val primaryMarginTop = (modal?.styling?.primaryCta?.spacing?.margin?.top ?: 0)
                        val primaryMarginBottom = (modal?.styling?.primaryCta?.spacing?.margin?.bottom ?: 0)

                        val secondaryMarginLeft = (modal?.styling?.secondaryCta?.spacing?.margin?.left ?: 0)
                        val secondaryMarginRight = (modal?.styling?.secondaryCta?.spacing?.margin?.right ?: 0)
                        val secondaryMarginTop = (modal?.styling?.secondaryCta?.spacing?.margin?.top ?: 0)
                        val secondaryMarginBottom = (modal?.styling?.secondaryCta?.spacing?.margin?.bottom ?: 0)

                        // Convert to Dp
                        val primaryMarginLeftDp = primaryMarginLeft.dp
                        val primaryMarginRightDp = primaryMarginRight.dp
                        val primaryMarginTopDp = primaryMarginTop.dp
                        val primaryMarginBottomDp = primaryMarginBottom.dp

                        val secondaryMarginLeftDp = secondaryMarginLeft.dp
                        val secondaryMarginRightDp = secondaryMarginRight.dp
                        val secondaryMarginTopDp = secondaryMarginTop.dp
                        val secondaryMarginBottomDp = secondaryMarginBottom.dp

                        // Provide a small horizontal inset when occupyFullWidth is true and backend margin is zero
                        val minHorizontalInset = 2.dp
                        val effectivePrimaryMarginLeftDp = if (primaryOccupy && primaryMarginLeftDp == 0.dp) minHorizontalInset else primaryMarginLeftDp
                        val effectivePrimaryMarginRightDp = if (primaryOccupy && primaryMarginRightDp == 0.dp) minHorizontalInset else primaryMarginRightDp

                        val effectiveSecondaryMarginLeftDp = if (secondaryOccupy && secondaryMarginLeftDp == 0.dp) minHorizontalInset else secondaryMarginLeftDp
                        val effectiveSecondaryMarginRightDp = if (secondaryOccupy && secondaryMarginRightDp == 0.dp) minHorizontalInset else secondaryMarginRightDp

                        // If a full-width CTA is present, add a small row-level horizontal inset to keep CTA inside container rounded corners
                        val rowStartInset = when {
                            primaryOccupy && primaryMarginLeftDp == 0.dp -> minHorizontalInset
                            !primaryOccupy && secondaryOccupy && secondaryMarginLeftDp == 0.dp -> minHorizontalInset
                            else -> 0.dp
                        }

                        val rowEndInset = when {
                            secondaryOccupy && secondaryMarginRightDp == 0.dp -> minHorizontalInset
                            !secondaryOccupy && primaryOccupy && primaryMarginRightDp == 0.dp -> minHorizontalInset
                            else -> 0.dp
                        }

                        // Between-CTA spacing: prefer primary.right then secondary.left, fallback to 0.dp
                        val ctaBetweenSpacing = ((modal?.styling?.primaryCta?.spacing?.margin?.right
                             ?: modal?.styling?.secondaryCta?.spacing?.margin?.left ?: 0)).dp

                         // Row top/bottom padding should respect max of both CTAs' top/bottom margins
                         val rowTopPadding = maxOf(primaryMarginTopDp, secondaryMarginTopDp)
                         val rowBottomPadding = maxOf(primaryMarginBottomDp, secondaryMarginBottomDp)

                         val rowArrangement = if (primaryOccupy || secondaryOccupy) {
                             Arrangement.spacedBy(ctaBetweenSpacing)
                         } else {
                             Arrangement.spacedBy(ctaBetweenSpacing, Alignment.CenterHorizontally)
                         }

                        Row(
                            modifier = Modifier
                                .padding(start = rowStartInset, end = rowEndInset, top = rowTopPadding, bottom = rowBottomPadding)
                                .fillMaxWidth(),
                             horizontalArrangement = rowArrangement,
                             verticalAlignment = Alignment.CenterVertically
                         ) {

                            // Create shapes using full corner radius values
                            val primaryShape = RoundedCornerShape(
                                topStart = (modal?.styling?.primaryCta?.cornerRadius?.topLeft ?: 12).dp,
                                topEnd = (modal?.styling?.primaryCta?.cornerRadius?.topRight ?: 12).dp,
                                bottomStart = (modal?.styling?.primaryCta?.cornerRadius?.bottomLeft ?: 12).dp,
                                bottomEnd = (modal?.styling?.primaryCta?.cornerRadius?.bottomRight ?: 12).dp,
                            )
                            val secondaryShape = RoundedCornerShape(
                                topStart = (modal?.styling?.secondaryCta?.cornerRadius?.topLeft ?: 12).dp,
                                topEnd = (modal?.styling?.secondaryCta?.cornerRadius?.topRight ?: 12).dp,
                                bottomStart = (modal?.styling?.secondaryCta?.cornerRadius?.bottomLeft ?: 12).dp,
                                bottomEnd = (modal?.styling?.secondaryCta?.cornerRadius?.bottomRight ?: 12).dp,
                            )

                            val primaryTextSize = modal?.styling?.primaryCta?.textStyle?.size?.sp ?: 14.sp
                            val secondaryTextSize = modal?.styling?.secondaryCta?.textStyle?.size?.sp ?: 14.sp

                            modal?.content?.primaryCtaText?.let { primaryText ->
                                val primaryTextAlign = when (modal?.styling?.primaryCta?.containerStyle?.alignment) {
                                    "left" -> TextAlign.Start
                                    "right" -> TextAlign.End
                                    else -> TextAlign.Center
                                }

                                // Wrap CTA so backend-provided margins are applied
                                val primaryBoxModifier = (if (primaryOccupy) Modifier.weight(1f) else Modifier)
                                    .padding(
                                        start = effectivePrimaryMarginLeftDp,
                                        end = effectivePrimaryMarginRightDp,
                                        top = primaryMarginTopDp,
                                        bottom = primaryMarginBottomDp
                                    )

                                Box(modifier = primaryBoxModifier) {
                                    BackendCta(
                                        text = primaryText,
                                        height = primaryHeight,
                                        width = primaryWidth,
                                        occupyFullWidth = primaryOccupy,
                                        backgroundColor = primaryBg,
                                        textColor = primaryTextColor,
                                        textSizeSp = primaryTextSize.value.toInt(),
                                        borderColor = primaryBorderColor,
                                        borderWidth = primaryBorderWidth,
                                        cornerRadius = primaryShape,
                                        textAlign = primaryTextAlign,
                                        modifier = Modifier
                                    ) {
                                        val link =
                                            modal.content?.primaryCtaRedirection?.url
                                                ?: modal.content?.primaryCtaRedirection?.value
                                        onPrimaryCta?.invoke(link)
                                    }
                                }
                            }

                            modal?.content?.secondaryCtaText?.let { secondaryText ->
                                val secondaryTextAlign = when (modal?.styling?.secondaryCta?.containerStyle?.alignment) {
                                    "left" -> TextAlign.Start
                                    "right" -> TextAlign.End
                                    else -> TextAlign.Center
                                }

                                val secondaryBoxModifier = (if (secondaryOccupy) Modifier.weight(1f) else Modifier)
                                    .padding(
                                        start = effectiveSecondaryMarginLeftDp,
                                        end = effectiveSecondaryMarginRightDp,
                                        top = secondaryMarginTopDp,
                                        bottom = secondaryMarginBottomDp
                                    )

                                Box(modifier = secondaryBoxModifier) {
                                    BackendCta(
                                        text = secondaryText,
                                        height = secondaryHeight,
                                        width = secondaryWidth,
                                        occupyFullWidth = secondaryOccupy,
                                        backgroundColor = secondaryBg,
                                        textColor = secondaryTextColor,
                                        textSizeSp = secondaryTextSize.value.toInt(),
                                        borderColor = secondaryBorderColor,
                                        borderWidth = secondaryBorderWidth,
                                        cornerRadius = secondaryShape,
                                        textAlign = secondaryTextAlign,
                                        modifier = Modifier
                                    ) {
                                        val link =
                                            modal.content?.secondaryCtaRedirection?.url
                                                ?: modal.content?.secondaryCtaRedirection?.value
                                        onSecondaryCta?.invoke(link)
                                    }
                                }
                            }


                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // Cross button in top-right using non-padded parent Box so it visually overlaps the modal
                        Log.d("PopupModal", "Rendering cross button with config=$crossConfig")
                        Box(modifier = Modifier.align(Alignment.TopEnd)) {
                            CrossButton(modifier = Modifier.size(36.dp), config = crossConfig, onClose = onCloseClick, boundaryPadding = 3.dp)

                        }
                    }
                }
            }
        }
    }

    // local VideoPlayerInline removed in favor of ModalComponents.VideoPlayerInline

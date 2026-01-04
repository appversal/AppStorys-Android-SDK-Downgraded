package com.appversal.appstorys.ui.modals

import android.os.Build.VERSION.SDK_INT
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
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
import com.appversal.appstorys.ui.components.CrossButton
import com.appversal.appstorys.ui.components.createCrossButtonConfig
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border

@Composable
internal fun PopupModal(
    onCloseClick: () -> Unit,
    modalDetails: ModalDetails,
    onModalClick: () -> Unit,
    onPrimaryCta: ((link: String?) -> Unit)? = null,
    onSecondaryCta: ((link: String?) -> Unit)? = null,
) {
    // Early-return when no modal is provided; simplifies downstream nullability handling
    val modal = modalDetails.modals?.getOrNull(0) ?: return

    // Robust extraction of media across different backend shapes:
    // 1) modal.content.chooseMediaType
    // 2) modal.chooseMediaType
    // 3) modal.content.set -> first element -> chooseMediaType
    // 4) top-level modal.url
    val chooseMedia = modal.content?.chooseMediaType
        ?: modal.chooseMediaType
        ?: modal.content?.set?.firstOrNull()?.chooseMediaType

    // Use the resolved chooseMedia url if present. The `Modal` data class does not have a `url` field
    // so referencing `modal.url` causes "Unresolved reference: url" at compile time.
    val imageUrl = chooseMedia?.url?.takeIf { it.isNotBlank() }


    // If backend explicitly sent a carousel modal type, delegate to the FullPageCarousel implementation
    if (modal.modalType?.trim()?.equals("modal-fullpage-carousel", true) == true) {
        FullPageCarouselModal(onCloseClick = onCloseClick, modalDetails = modalDetails, onModalClick = onModalClick, onPrimaryCta = onPrimaryCta, onSecondaryCta = onSecondaryCta)
        return
    }


    val context = LocalContext.current
    // appearance (styling) - may be null if backend omits styling
    val appearance = modal.styling?.appearance

    val mediaType = when {
        imageUrl?.endsWith(".gif", true) == true -> "gif"
        imageUrl?.endsWith(".json", true) == true -> "lottie"
        imageUrl?.endsWith(".mp4", true) == true || imageUrl?.endsWith(".m3u8", true) == true -> "video"
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

    // Prefer uploaded image URL if provided by backend, fallback to default crossButtonImage
    val crossButtonImageUrl = modal.styling?.crossButton?.uploadImage?.url
        ?: modal.styling?.crossButton?.default?.crossButtonImage

    // Extract spacing to avoid naming conflicts with Modifier.padding
    val crossButtonSpacing = modal.styling?.crossButton?.default?.spacing

    val crossConfig = createCrossButtonConfig(
        fillColorString = modal.styling?.crossButton?.default?.color?.fill,
        crossColorString = modal.styling?.crossButton?.default?.color?.cross,
        strokeColorString = modal.styling?.crossButton?.default?.color?.stroke,
        marginTop = crossButtonSpacing?.margin?.top,
        marginEnd = crossButtonSpacing?.margin?.right,
        paddingTop = crossButtonSpacing?.padding?.top,
        paddingEnd = crossButtonSpacing?.padding?.right,
        paddingBottom = crossButtonSpacing?.padding?.bottom,
        paddingStart = crossButtonSpacing?.padding?.left,
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
                            // Use the shared ModalMediaRenderer for all media types for consistent behavior
                            ModalMediaRenderer(
                                mediaUrl = imageUrl,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(appearanceHeightDp)
                                    .clip(containerShape),
                                contentDescription = "Popup Image",
                                muted = false
                            )
                        }

                        // Replace inline title/subtitle/cta with ModalWithCta for better separation
                        ModalWithCta(
                            modal = modal,
                            onPrimaryCta = onPrimaryCta,
                            onSecondaryCta = onSecondaryCta
                        )

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

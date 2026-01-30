package com.appversal.appstorys.ui.modals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.appversal.appstorys.api.Modal
import com.appversal.appstorys.ui.common_components.CrossButton
import com.appversal.appstorys.ui.common_components.createCrossButtonConfig
import com.appversal.appstorys.ui.common_components.parseColorString

@Composable
fun MediaOnlyModal(
    onCloseClick: () -> Unit,
    modal: Modal,
    onModalClick: () -> Unit,
    onPrimaryCta: ((link: String?) -> Unit)? = null,
    onSecondaryCta: ((link: String?) -> Unit)? = null,
) {
    val appearance = modal.styling?.appearance
    val dimension = appearance?.dimension
    // Corner radius - ModalCornerRadius uses Int? fields
    // When cornerRadius values are provided (even 0), use them. Only fallback if null.
    val cornerRadius = appearance?.cornerRadius
    val flatBorderRadius = modal.borderRadius ?: 16
    val cornerShape = RoundedCornerShape(
        topStart = (cornerRadius?.topLeft ?: flatBorderRadius).dp,
        topEnd = (cornerRadius?.topRight ?: flatBorderRadius).dp,
        bottomStart = (cornerRadius?.bottomLeft ?: flatBorderRadius).dp,
        bottomEnd = (cornerRadius?.bottomRight ?: flatBorderRadius).dp
    )

    // Modal width from size parameter
    val modalWidth = modal.size?.toFloatOrNull()?.dp ?: 300.dp

    // For media-only modal, use transparent background so only media shows
    val backgroundColor = Color.Transparent

    // Backdrop - extract color and opacity from styling
    val backdrop = appearance?.backdrop
    val backdropColorString = backdrop?.color ?: appearance?.backdropColor
    val backdropColor = parseColorString(backdropColorString) ?: Color.Black
    val backdropOpacity = (backdrop?.opacity ?: appearance?.backdropOpacity
    ?: modal.backgroundOpacity ?: "50").toString().toFloatOrNull() ?: 50f
    val backdropEnabled = (appearance?.enableBackdrop ?: modal.enableBackdrop) != false
    val backdropAlpha = if (backdropEnabled) (backdropOpacity / 100f).coerceIn(0f, 1f) else 0f

    // Cross button
    val crossButton = modal.styling?.crossButton
    // The enabled flag comes from styling.crossButton.enabled
    val crossEnableFlag = crossButton?.enabled ?: crossButton?.enableCrossButton ?: modal.enableCrossButton
    val crossEnabled = crossEnableFlag != false
    val crossImageUrl = crossButton?.uploadImage?.url
        ?: crossButton?.default?.crossButtonImage
        ?: crossButton?.image
        ?: modal.crossButtonImage
    // Support both "color" and "colors" fields, as well as default.color
    val crossColors = crossButton?.default?.color ?: crossButton?.color ?: crossButton?.colors
    val crossMargin = crossButton?.default?.spacing?.margin ?: crossButton?.margin

    // Normalize size field — some payloads put it under default.size, some under size directly
    val crossSize = crossButton?.default?.size ?: crossButton?.size

    val crossConfig = createCrossButtonConfig(
        fillColorString = crossColors?.fill,
        crossColorString = crossColors?.cross,
        strokeColorString = crossColors?.stroke,
        marginTop = crossMargin?.top,
        marginEnd = crossMargin?.right,
        size = crossSize,
        imageUrl = crossImageUrl
    )

    // Resolve media URL using unified utility (see ModalMediaUtils.kt)
    val resolvedMediaUrl = modal.resolveMediaUrl() ?: modal.url

    // Check if media is loaded before showing the modal
    val mediaLoadState = rememberMediaLoadState(resolvedMediaUrl)
    val isMediaLoaded = mediaLoadState is MediaLoadState.Success

    // Track if media has actually been rendered (not just loaded)
    // This prevents the cross button "jump" glitch
    var isMediaRendered by remember { mutableStateOf(false) }

    // Use alpha to control visibility - prevents animation glitches
    val contentAlpha = if (isMediaLoaded) 1f else 0f

    Dialog(
        onDismissRequest = onCloseClick,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        // Always render the structure, but control visibility with alpha
        Box(
            modifier = Modifier
                .fillMaxSize()
                //.graphicsLayer { alpha = contentAlpha }
                .background(backdropColor.copy(alpha = backdropAlpha))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    enabled = isMediaLoaded)
                { onCloseClick() },
            contentAlignment = Alignment.Center
        ) {
                Box(
                    modifier = Modifier
                        .width(modalWidth)
                        .wrapContentHeight()
                        .background(backgroundColor)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            enabled = isMediaLoaded
                        ) {
                            val redirectUrl = modal.redirection?.url ?: modal.link
                            if (!redirectUrl.isNullOrBlank()) {
                                onPrimaryCta?.invoke(redirectUrl)
                            }
                        }
                ) {
                    val mediaType = determineMediaType(resolvedMediaUrl)

                    // For videos, use wrapContentHeight to respect natural aspect ratio
                    val mediaModifier = Modifier
                        .width(modalWidth)
                        .wrapContentHeight()
                        .clip(cornerShape)

                    ModalMediaRendererWithCallback(
                        mediaUrl = resolvedMediaUrl,
                        modifier = mediaModifier,
                        contentDescription = "Media Only Modal",
                        contentScale = ContentScale.FillWidth,
                        muted = false,
                        cornerShape = cornerShape,
                        onMediaRendered = { isMediaRendered = true }
                    )

                    // Only show cross button after media has actually rendered
                    // This prevents the "jump" glitch where button appears before media
                    if (crossEnabled && isMediaRendered) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                            //.statusBarsPadding()
                            //.padding(16.dp)
                        ) {
                            CrossButton(config = crossConfig, onClose = onCloseClick)
                        }
                    }
                }

        }
    }
}
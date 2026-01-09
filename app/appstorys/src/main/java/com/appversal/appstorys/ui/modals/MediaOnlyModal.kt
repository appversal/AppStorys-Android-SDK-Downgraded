package com.appversal.appstorys.ui.modals

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.appversal.appstorys.api.Modal
import com.appversal.appstorys.api.resolvedMedia
import com.appversal.appstorys.ui.components.CrossButton
import com.appversal.appstorys.ui.components.createCrossButtonConfig

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
    // Corner radius
    val cornerRadius = appearance?.cornerRadius
    val flatBorderRadius = modal.borderRadius
    val cornerShape = RoundedCornerShape(
        topStart = cornerRadius?.topLeft?.toIntOrNull()?.dp ?: flatBorderRadius?.dp ?: 16.dp,
        topEnd = cornerRadius?.topRight?.toIntOrNull()?.dp ?: flatBorderRadius?.dp ?: 16.dp,
        bottomStart = cornerRadius?.bottomLeft?.toIntOrNull()?.dp ?: flatBorderRadius?.dp ?: 16.dp,
        bottomEnd = cornerRadius?.bottomRight?.toIntOrNull()?.dp ?: flatBorderRadius?.dp ?: 16.dp
    )

    // Modal width from size parameter
    val modalWidth = modal.size?.toFloatOrNull()?.dp ?: 300.dp
    val borderWidth = dimension?.borderWidth?.toFloatOrNull()?.dp ?: 0.dp


    // Backdrop
    val backdrop = appearance?.backdrop
    val backdropOpacity = (backdrop?.opacity ?: appearance?.backdropOpacity
    ?: modal.backgroundOpacity ?: "50").toString().toFloatOrNull() ?: 50f
    val backdropEnabled = (appearance?.enableBackdrop ?: modal.enableBackdrop) != false
    val backdropAlpha = if (backdropEnabled) (backdropOpacity / 100f).coerceIn(0f, 1f) else 0f

    // Cross button
    val crossButton = modal.styling?.crossButton
    val crossEnabled = (crossButton?.enableCrossButton ?: crossButton?.enabled ?: modal.enableCrossButton) != false
    val crossImageUrl = crossButton?.uploadImage?.url
        ?: crossButton?.default?.crossButtonImage
        ?: modal.crossButtonImage
    val crossColors = crossButton?.default?.color ?: crossButton?.colors
    val crossMargin = crossButton?.default?.spacing?.margin ?: crossButton?.margin
    val crossSize = crossButton?.default?.crossButtonSize ?: crossButton?.crossButtonSize

    val crossConfig = createCrossButtonConfig(
        fillColorString = crossColors?.fill,
        crossColorString = crossColors?.cross,
        strokeColorString = crossColors?.stroke,
        marginTop = crossMargin?.top,
        marginEnd = crossMargin?.right,
        paddingTop = crossButton?.default?.spacing?.padding?.top,
        paddingEnd = crossButton?.default?.spacing?.padding?.right,
        paddingBottom = crossButton?.default?.spacing?.padding?.bottom,
        paddingStart = crossButton?.default?.spacing?.padding?.left,
        size = crossSize,
        imageUrl = crossImageUrl
    )
    val resolvedMediaUrl = modal.resolvedMedia()?.url ?: modal.url


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
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onCloseClick() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(modalWidth)
                    .wrapContentHeight()
            ) {
                Box(
                    modifier = Modifier
                        .width(modalWidth)
                        .wrapContentHeight()
                        .clip(cornerShape)
                        .background(Color.White)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            onModalClick()
                            val redirectUrl = modal.redirection?.url ?: modal.link
                            if (!redirectUrl.isNullOrBlank()) {
                                onPrimaryCta?.invoke(redirectUrl)
                            }
                        }
                ) {
                    ModalMediaRenderer(
                        mediaUrl = resolvedMediaUrl,
                        modifier = Modifier
                            .width(modalWidth)
                            .wrapContentHeight()
                            .clip(cornerShape),
                        contentDescription = "Media Only Modal",
                        contentScale = ContentScale.FillWidth,
                        muted = false
                    )
                }

                if (crossEnabled) {
                    Log.d("MediaOnlyModal", "Rendering cross button")
                    Box(modifier = Modifier.align(Alignment.TopEnd)) {
                        CrossButton(config = crossConfig, onClose = onCloseClick)
                    }
                }
            }
        }
    }
}
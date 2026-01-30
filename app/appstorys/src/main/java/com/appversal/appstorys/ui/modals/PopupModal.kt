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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.appversal.appstorys.api.Modal
import com.appversal.appstorys.api.ModalDetails
import com.appversal.appstorys.api.TextStyling
import com.appversal.appstorys.ui.common_components.CommonText
import com.appversal.appstorys.ui.common_components.CrossButton
import com.appversal.appstorys.ui.common_components.createCrossButtonConfig
import com.appversal.appstorys.ui.common_components.parseColorString
import com.appversal.appstorys.utils.noRippleClickable

@Composable
internal fun PopupModal(
    onCloseClick: () -> Unit,
    modalDetails: ModalDetails,
    onModalClick: () -> Unit,
    onPrimaryCta: ((link: String?) -> Unit)? = null,
    onSecondaryCta: ((link: String?) -> Unit)? = null,
) {
    val modal = modalDetails.modals?.getOrNull(0) ?: return

    when {
        modal.isCarousel() || modal.modalType?.trim()?.equals("modal-fullpage-carousel", true) == true -> {
            FullPageCarouselModal(onCloseClick, modalDetails, onModalClick, onPrimaryCta, onSecondaryCta)
        }
        modal.isMediaOnly() -> {
            MediaOnlyModal(onCloseClick, modal, onModalClick, onPrimaryCta, onSecondaryCta)
        }
        else -> {
            ModalWithCTA(onCloseClick, modal, onPrimaryCta, onSecondaryCta)
        }
    }
}

@Composable
private fun ModalWithCTA(
    onCloseClick: () -> Unit,
    modal: Modal,
    onPrimaryCta: ((String?) -> Unit)?,
    onSecondaryCta: ((String?) -> Unit)?
) {

    // Extract styling
    val appearance = modal.styling?.appearance
    val dimension = appearance?.dimension

    // Corner radius - ModalCornerRadius uses Int? fields
    val cornerRadius = appearance?.cornerRadius
    val cornerShape = RoundedCornerShape(
        topStart = cornerRadius?.topLeft?.dp ?: 0.dp,
        topEnd = cornerRadius?.topRight?.dp ?: 0.dp,
        bottomStart = cornerRadius?.bottomLeft?.dp ?: 0.dp,
        bottomEnd = cornerRadius?.bottomRight?.dp ?: 0.dp
    )

    val modalWidth = (dimension?.height?.toFloatOrNull()?.dp ?: 300.dp)

    // Background color for modal content area (from styling or default white for CTA modals)
    val backgroundColor = parseColorString(appearance?.backgroundColor) ?: Color.White

    // Backdrop - extract color and opacity from styling
    val backdrop = appearance?.backdrop
    val backdropColorString = backdrop?.color ?: appearance?.backdropColor
    val backdropColor = parseColorString(backdropColorString) ?: Color.Black
    val backdropOpacity = (backdrop?.opacity ?: appearance?.backdropOpacity ?: "50").toString().toFloatOrNull() ?: 50f
    val backdropEnabled = appearance?.enableBackdrop ?: true
    val backdropAlpha = if (backdropEnabled) (backdropOpacity / 100f).coerceIn(0f, 1f) else 0f
    // Note: some payloads put size under `default.crossButtonSize` or `crossButtonSize` (legacy),
    // while others use `size`. Normalize all possibilities below after we read `crossButton`.

    // Cross button
    val crossButton = modal.styling?.crossButton
    val crossSize = crossButton?.default?.size ?: crossButton?.size ?: crossButton?.size

    // Content padding
    val padding = appearance?.padding
    val contentPaddingStart = padding?.left?.dp ?: 16.dp
    val contentPaddingEnd = padding?.right?.dp ?: 16.dp
    val contentPaddingTop = padding?.top?.dp ?: 16.dp
    val contentPaddingBottom = padding?.bottom?.dp ?: 16.dp

    // Cross button
    // The enabled flag comes from styling.crossButton.enabled
    val crossEnableFlag = crossButton?.enabled ?: crossButton?.enableCrossButton
    val crossEnabled = crossEnableFlag != false
    val crossImageUrl = crossButton?.uploadImage?.url
        ?: crossButton?.default?.crossButtonImage
        ?: crossButton?.image
    // Support both "color" and "colors" fields, as well as default.color
    val crossColors = crossButton?.default?.color ?: crossButton?.color ?: crossButton?.colors
    val crossMargin = crossButton?.default?.spacing?.margin ?: crossButton?.margin

    val crossConfig = createCrossButtonConfig(
        fillColorString = crossColors?.fill,
        crossColorString = crossColors?.cross,
        strokeColorString = crossColors?.stroke,
        marginTop = crossMargin?.top,
        marginEnd = crossMargin?.right,
        size = crossSize,
        imageUrl = crossImageUrl
    )

    // Check if media is loaded before showing the modal
    // Uses resolveMediaUrl() from ModalMediaUtils.kt
    val mediaUrl = modal.resolveMediaUrl()
    val mediaLoadState = rememberMediaLoadState(mediaUrl)
    val isMediaLoaded = mediaLoadState is MediaLoadState.Success || mediaUrl.isNullOrEmpty()

    // Track if media has actually been rendered (not just loaded)
    // This prevents the cross button "jump" glitch
    var isMediaRendered by remember { mutableStateOf(mediaUrl.isNullOrEmpty()) }

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
                    enabled = isMediaLoaded
                ) { onCloseClick() },
            contentAlignment = Alignment.Center
        ) {
            // Wrapper Box to position cross button outside the modal
            Box(
                modifier = Modifier
                    .wrapContentSize()

            ) {
                // Modal container - clip first, then background (order matters!)
                Box(
                    modifier = Modifier
                        .width(modalWidth)
                        .wrapContentHeight()
                        .clip(cornerShape)
                        .background(backgroundColor)
                ) {
                    Column(
                        modifier = Modifier
                            .width(modalWidth)
                            .wrapContentHeight(),
                        verticalArrangement = Arrangement.Top
                    ) {
                        // Media section
                        if (!mediaUrl.isNullOrEmpty()) {
                            val mediaType = determineMediaType(mediaUrl)

                            // For videos, use wrapContentHeight to respect natural aspect ratio
                            // For images/gifs/lottie, use fillWidth with aspect fit
                            val mediaModifier = if (mediaType == "video") {
                                Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .noRippleClickable(onClick = {})
                            ) {
                                ModalMediaRendererWithCallback(
                                    mediaUrl = mediaUrl,
                                    modifier = mediaModifier,
                                    contentDescription = "Modal Media",
                                    contentScale = ContentScale.FillWidth,
                                    muted = false,
                                    onMediaRendered = { isMediaRendered = true }
                                )
                            }
                        }

                        // Content section (title, subtitle, CTAs)
                        val hasContent = !modal.content?.titleText.isNullOrBlank() ||
                                !modal.content?.subtitleText.isNullOrBlank() ||
                                !modal.content?.primaryCtaText.isNullOrBlank() ||
                                !modal.content?.secondaryCtaText.isNullOrBlank()

                        if (hasContent) {
                            ModalContentSection(
                                modal = modal,
                                paddingStart = contentPaddingStart,
                                paddingEnd = contentPaddingEnd,
                                paddingTop = contentPaddingTop,
                                onPrimaryCta = onPrimaryCta,
                                onSecondaryCta = onSecondaryCta
                            )
                        }
                    }
                }

                // Cross button overlay - positioned outside the modal at top-right
                if (crossEnabled && isMediaRendered) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            //.offset(x = 12.dp, y = (-12).dp)
                    ) {
                        CrossButton(config = crossConfig, onClose = onCloseClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModalContentSection(
    modal: Modal,
    paddingStart: androidx.compose.ui.unit.Dp,
    paddingEnd: androidx.compose.ui.unit.Dp,
    paddingTop: androidx.compose.ui.unit.Dp,
    onPrimaryCta: ((String?) -> Unit)?,
    onSecondaryCta: ((String?) -> Unit)?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title and Subtitle inside padded area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = paddingStart,
                    end = paddingEnd,
                    top = paddingTop,
                    bottom = 0.dp  // No bottom padding - CTAs handle their own spacing
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            modal.content?.titleText?.let { title ->
                val titleStyling = modal.styling?.title
                Spacer(modifier = Modifier.height(12.dp))
                CommonText(
                    modifier = Modifier.fillMaxWidth(),
                    text = title,
                    styling = TextStyling(
                        color = titleStyling?.color,
                        fontSize = titleStyling?.fontSize ?: titleStyling?.size ?: 16,
                        fontFamily = titleStyling?.fontFamily ?: titleStyling?.font ?: "",
                        textAlign = titleStyling?.textAlign ?: titleStyling?.alignment?.trim()?.lowercase(),
                        fontDecoration = titleStyling?.fontDecoration
                    )
                )
            }

            // Subtitle
            modal.content?.subtitleText?.let { subtitle ->
                val subtitleStyling = modal.styling?.subTitle
                Spacer(modifier = Modifier.height(6.dp))
                CommonText(
                    modifier = Modifier.fillMaxWidth(),
                    text = subtitle,
                    styling = TextStyling(
                        color = subtitleStyling?.color,
                        fontSize = subtitleStyling?.fontSize ?: subtitleStyling?.size ?: 14,
                        fontFamily = subtitleStyling?.fontFamily ?: subtitleStyling?.font ?: "",
                        textAlign = subtitleStyling?.textAlign ?: subtitleStyling?.alignment?.trim()?.lowercase(),
                        fontDecoration = subtitleStyling?.fontDecoration
                    )
                )
            }
        }

        // CTAs - OUTSIDE the content padding so they can extend to modal edges
        // Their own margins control spacing from edges
        ModalCtaRow(
            modal = modal,
            onPrimaryCta = onPrimaryCta,
            onSecondaryCta = onSecondaryCta
        )
    }
}

@Composable
private fun ModalCtaRow(
    modal: Modal,
    onPrimaryCta: ((String?) -> Unit)?,
    onSecondaryCta: ((String?) -> Unit)?
) {
    val primaryText = modal.content?.primaryCtaText
    val secondaryText = modal.content?.secondaryCtaText

    val hasPrimary = !primaryText.isNullOrBlank()
    val hasSecondary = !secondaryText.isNullOrBlank()

    if (!hasPrimary && !hasSecondary) return

    // Create configs using the common helper
    val primaryConfig = if (hasPrimary) {
        val link = modal.content?.primaryCtaRedirection?.url
            ?: modal.content?.primaryCtaRedirection?.value
        createModalCTAButtonConfig(
            text = primaryText!!,
            styling = modal.styling?.primaryCta,
            redirectionUrl = link,
            defaultHeight = 40.dp,
            defaultWidth = 120.dp,
            defaultBackgroundColor = Color.Black
        )
    } else null

    val secondaryConfig = if (hasSecondary) {
        val link = modal.content?.secondaryCtaRedirection?.url
            ?: modal.content?.secondaryCtaRedirection?.value
        createModalCTAButtonConfig(
            text = secondaryText!!,
            styling = modal.styling?.secondaryCta,
            redirectionUrl = link,
            defaultHeight = 40.dp,
            defaultWidth = 120.dp,
            defaultBackgroundColor = Color.DarkGray
        )
    } else null

    // Use the common CTA row
    ModalCTARow(
        primaryConfig = primaryConfig,
        secondaryConfig = secondaryConfig,
        onPrimaryCta = onPrimaryCta,
        onSecondaryCta = onSecondaryCta
    )
}

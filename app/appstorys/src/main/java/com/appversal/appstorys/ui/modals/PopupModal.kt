package com.appversal.appstorys.ui.modals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.appversal.appstorys.api.Modal
import com.appversal.appstorys.api.ModalDetails
import com.appversal.appstorys.api.isCarousel
import com.appversal.appstorys.api.isMediaOnly
import com.appversal.appstorys.api.resolvedMedia
import com.appversal.appstorys.ui.components.CrossButton
import com.appversal.appstorys.ui.components.createCrossButtonConfig
import com.appversal.appstorys.ui.components.parseColorString

@Composable
internal fun PopupModal(
    onCloseClick: () -> Unit,
    modalDetails: ModalDetails,
    onModalClick: () -> Unit,
    onPrimaryCta: ((link: String?) -> Unit)? = null,
    onSecondaryCta: ((link: String?) -> Unit)? = null,
) {
    val modal = modalDetails.modals?.getOrNull(0) ?: return

    // Delegate to specialized modals
    if (modal.isCarousel() || modal.modalType?.trim()?.equals("modal-fullpage-carousel", true) == true) {
        FullPageCarouselModal(onCloseClick, modalDetails, onModalClick, onPrimaryCta, onSecondaryCta)
        return
    }

    if (modal.isMediaOnly()) {
        MediaOnlyModal(onCloseClick, modal, onModalClick, onPrimaryCta, onSecondaryCta)
        return
    }

    // Extract styling
    val appearance = modal.styling?.appearance
    val dimension = appearance?.dimension

    // Corner radius
    val cornerRadius = appearance?.cornerRadius
    val cornerShape = RoundedCornerShape(
        topStart = cornerRadius?.topLeft?.toIntOrNull()?.dp ?: 0.dp,
        topEnd = cornerRadius?.topRight?.toIntOrNull()?.dp ?: 0.dp,
        bottomStart = cornerRadius?.bottomLeft?.toIntOrNull()?.dp ?: 0.dp,
        bottomEnd = cornerRadius?.bottomRight?.toIntOrNull()?.dp ?: 0.dp
    )

    val modalWidth = (dimension?.height?.toFloatOrNull()?.dp ?: 300.dp)

    // Backdrop
    val backdrop = appearance?.backdrop
    val backdropOpacity = (backdrop?.opacity ?: appearance?.backdropOpacity ?: "50").toString().toFloatOrNull() ?: 50f
    val backdropEnabled = appearance?.enableBackdrop ?: true
    val backdropAlpha = if (backdropEnabled) (backdropOpacity / 100f).coerceIn(0f, 1f) else 0f

    // Content padding
    val padding = appearance?.padding
    val contentPaddingStart = padding?.left?.dp ?: 16.dp
    val contentPaddingEnd = padding?.right?.dp ?: 16.dp
    val contentPaddingTop = padding?.top?.dp ?: 16.dp
    val contentPaddingBottom = padding?.bottom?.dp ?: 16.dp

    // Cross button
    val crossButton = modal.styling?.crossButton
    val crossEnabled = crossButton?.enableCrossButton ?: true
    val crossImageUrl = crossButton?.uploadImage?.url ?: crossButton?.default?.crossButtonImage
    val crossColors = crossButton?.default?.color
    val crossMargin = crossButton?.default?.spacing?.margin

    val crossConfig = createCrossButtonConfig(
        fillColorString = crossColors?.fill,
        crossColorString = crossColors?.cross,
        strokeColorString = crossColors?.stroke,
        marginTop = crossMargin?.top,
        marginEnd = crossMargin?.right,
        paddingTop = null,
        paddingEnd = null,
        paddingBottom = null,
        paddingStart = null,
        size = null,
        imageUrl = crossImageUrl
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
                Column(
                    modifier = Modifier
                        .width(modalWidth)
                        .wrapContentHeight()
                        .clip(cornerShape)
                        .background(Color.White),
                    verticalArrangement = Arrangement.Top
                ) {
                    // Media section
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .clickable(
                                indication = null, // Removes the ripple effect
                                interactionSource = remember { MutableInteractionSource() }
                            ) {}
                    ) {
                        ModalMediaRenderer(
                            mediaUrl = modal.resolvedMedia()?.url,
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(),
                            contentDescription = "Modal Media",
                            contentScale = ContentScale.FillWidth,
                            muted = false
                        )
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
                            paddingBottom = contentPaddingBottom,
                            onPrimaryCta = onPrimaryCta,
                            onSecondaryCta = onSecondaryCta
                        )
                    }
                }

                // Cross button overlay
                if (crossEnabled) {
                    Box(modifier = Modifier.align(Alignment.TopEnd)) {
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
    paddingBottom: androidx.compose.ui.unit.Dp,
    onPrimaryCta: ((String?) -> Unit)?,
    onSecondaryCta: ((String?) -> Unit)?
) {
    // Styling
    val titleColor = parseColorString(modal.styling?.title?.color) ?: Color(0xFF3700FF)
    val titleSize = modal.styling?.title?.size?.sp ?: 16.sp
    val titleAlign = when (modal.styling?.title?.alignment?.trim()?.lowercase()) {
        "left" -> TextAlign.Start
        "right" -> TextAlign.End
        else -> TextAlign.Center
    }

    val subtitleColor = parseColorString(modal.styling?.subTitle?.color) ?: Color.Gray
    val subtitleSize = modal.styling?.subTitle?.size?.sp ?: 12.sp
    val subtitleAlign = when (modal.styling?.subTitle?.alignment?.trim()?.lowercase()) {
        "left" -> TextAlign.Start
        "right" -> TextAlign.End
        else -> TextAlign.Center
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = paddingStart,
                end = paddingEnd,
                top = paddingTop,
                bottom = paddingBottom
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title
        modal.content?.titleText?.let { title ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                color = titleColor,
                fontSize = titleSize,
                textAlign = titleAlign,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Subtitle
        modal.content?.subtitleText?.let { subtitle ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                color = subtitleColor,
                fontSize = subtitleSize,
                textAlign = subtitleAlign,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // CTAs
        ModalCtaRow(
            modal = modal,
            onPrimaryCta = onPrimaryCta,
            onSecondaryCta = onSecondaryCta
        )
        Spacer(modifier = Modifier.height(2.dp))
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

    val primaryStyling = modal.styling?.primaryCta
    val secondaryStyling = modal.styling?.secondaryCta

    val primaryOccupy = hasPrimary && primaryStyling?.occupyFullWidth?.trim()?.equals("true", true) == true
    val secondaryOccupy = hasSecondary && secondaryStyling?.occupyFullWidth?.trim()?.equals("true", true) == true

    val alignmentFromBtn = when {
        hasPrimary && !hasSecondary -> primaryStyling?.containerStyle?.alignment
        hasSecondary && !hasPrimary -> secondaryStyling?.containerStyle?.alignment
        else -> primaryStyling?.containerStyle?.alignment // Default if both exist
    }?.trim()?.lowercase()

    val primaryMargin = primaryStyling?.spacing?.margin
    val secondaryMargin = secondaryStyling?.spacing?.margin

    // Compute horizontal spacing between CTAs (use primary right or secondary left margin)
    val ctaBetweenSpacing = if (hasPrimary && hasSecondary) {
        val right = primaryMargin?.right ?: 0
        val left = secondaryMargin?.left ?: 0
        maxOf(right, left).dp
    } else {
        0.dp
    }
    val rowTopPadding = maxOf(primaryMargin?.top?.dp ?: 0.dp, secondaryMargin?.top?.dp ?: 0.dp)
    val rowBottomPadding = maxOf(primaryMargin?.bottom?.dp ?: 0.dp, secondaryMargin?.bottom?.dp ?: 0.dp)



    val arrangement = when {
        primaryOccupy || secondaryOccupy -> Arrangement.spacedBy(0.dp)
        alignmentFromBtn == "right" -> Arrangement.spacedBy(ctaBetweenSpacing, Alignment.End)
        alignmentFromBtn == "left" -> Arrangement.spacedBy(ctaBetweenSpacing, Alignment.Start)
        else -> Arrangement.spacedBy(ctaBetweenSpacing, Alignment.CenterHorizontally)
    }
    Row(
        modifier = Modifier
            .padding(top = rowTopPadding, bottom = rowBottomPadding)
            .fillMaxWidth(),
        horizontalArrangement = arrangement,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Primary CTA - Only shows if text is not null/blank
        if (hasPrimary) {
            ModalCtaButton(
                text = primaryText!!, // Safe because of hasPrimary check
                styling = primaryStyling,
                occupy = primaryOccupy,
                onClick = {
                    val link = modal.content?.primaryCtaRedirection?.url
                        ?: modal.content?.primaryCtaRedirection?.value
                    onPrimaryCta?.invoke(link)
                },
                modifier = if (primaryOccupy) Modifier.weight(1f) else Modifier
            )
        }

        // Secondary CTA - Only shows if text is not null/blank
        if (hasSecondary) {
            ModalCtaButton(
                text = secondaryText!!, // Safe because of hasSecondary check
                styling = secondaryStyling,
                occupy = secondaryOccupy,
                onClick = {
                    val link = modal.content?.secondaryCtaRedirection?.url
                        ?: modal.content?.secondaryCtaRedirection?.value
                    onSecondaryCta?.invoke(link)
                },
                modifier = if (secondaryOccupy) Modifier.weight(1f) else Modifier
            )
        }
    }
}

@Composable
private fun ModalCtaButton(
    text: String,
    styling: com.appversal.appstorys.api.ModalCta?,
    occupy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = parseColorString(styling?.backgroundColor) ?: Color.Black
    val textColor = parseColorString(styling?.textColor) ?: Color.White
    val borderColor = parseColorString(styling?.borderColor) ?: Color.Transparent
    val minWidth = styling?.containerStyle?.ctaWidth?.dp ?: 120.dp

    val shape = RoundedCornerShape(
        topStart = (styling?.cornerRadius?.topLeft ?: 12).dp,
        topEnd = (styling?.cornerRadius?.topRight ?: 12).dp,
        bottomStart = (styling?.cornerRadius?.bottomLeft ?: 12).dp,
        bottomEnd = (styling?.cornerRadius?.bottomRight ?: 12).dp
    )

    BackendCta(
        text = text,
        height = (styling?.containerStyle?.height ?: 40).dp,
        // If not occupying full width, allow it to wrap content so text isn't cut
        width = if (occupy) null else minWidth,
        occupyFullWidth = occupy,
        backgroundColor = bg,
        textColor = textColor,
        textSizeSp = styling?.textStyle?.size ?: 14,
        borderColor = borderColor,
        borderWidth = (styling?.containerStyle?.borderWidth ?: 0).dp,
        cornerRadius = shape,
        textAlign = TextAlign.Center,
        modifier = modifier.then(
            if (!occupy) Modifier.widthIn(min = minWidth) else Modifier
        ),
        onClick = onClick
    )
}
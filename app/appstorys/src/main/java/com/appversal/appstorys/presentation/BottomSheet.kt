package com.appversal.appstorys.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.appversal.appstorys.api.BottomSheetDetails
import com.appversal.appstorys.api.BottomSheetElement
import com.appversal.appstorys.domain.State
import com.appversal.appstorys.domain.model.TypedCampaign
import com.appversal.appstorys.domain.rememberCampaign
import com.appversal.appstorys.domain.usecase.ClickEvent
import com.appversal.appstorys.domain.usecase.trackEvent
import com.appversal.appstorys.utils.googleFontProvider
import com.appversal.appstorys.utils.toColor


@Composable
internal fun BottomSheet(
    modifier: Modifier = Modifier,
) {
    val campaign = rememberCampaign<BottomSheetDetails>("BTS")

    if (campaign != null) {
        Content(campaign, modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Content(
    campaign: TypedCampaign<BottomSheetDetails>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val details = campaign.details

    val elements = details.elements?.sortedBy { it.order } ?: emptyList()
    val imageElement = elements.firstOrNull { it.type == "image" }
    val bodyElements = elements.filter { it.type == "body" }
    val ctaElements = elements.filter { it.type == "cta" }
    val cornerRadius = details.cornerRadius?.toFloatOrNull()?.dp ?: 16.dp

    var imageActive by remember(imageElement) { mutableStateOf(imageElement != null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val handleImageState = remember {
        { state: AsyncImagePainter.State ->
            imageActive = state is AsyncImagePainter.State.Success
        }
    }
    val handleClick = remember(campaign.id) {
        { link: String? ->
            ClickEvent(context, link = link, campaignId = campaign.id)
            Unit
        }
    }
    val handleDismiss = remember(campaign.id) {
        {
            State.addDisabledCampaign(campaign.id)
        }
    }

    LaunchedEffect(Unit) {
        trackEvent(context, "viewed", campaign.id)
    }

    LaunchedEffect(imageActive) {
        if (imageActive) {
            sheetState.show()
        } else {
            sheetState.hide()
        }
    }

    ModalBottomSheet(
        modifier = modifier,
        onDismissRequest = handleDismiss,
        shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius),
        containerColor = Color.Transparent,
        dragHandle = null,
        sheetState = sheetState,
        content = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                content = {
                    val hasOverlayButton = imageElement?.overlayButton == true

                    if (imageElement != null && hasOverlayButton) {
                        ImageElement(
                            imageElement,
                            onClick = handleClick,
                            onState = handleImageState
                        )
                    }

                    Column(
                        modifier = Modifier
                            .then(
                                when (hasOverlayButton) {
                                    true -> Modifier.align(Alignment.BottomCenter)
                                    else -> Modifier
                                }
                            )
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        content = {
                            if (!hasOverlayButton && imageElement != null) {
                                ImageElement(
                                    imageElement,
                                    onClick = handleClick,
                                    onState = handleImageState
                                )
                            }

                            bodyElements.forEach { BodyElement(it) }

                            val leftCTA = ctaElements.firstOrNull { it.position == "left" }
                            val rightCTA = ctaElements.firstOrNull { it.position == "right" }
                            val centerCTAs = ctaElements.filter {
                                it.position == "center" || it.position.isNullOrEmpty()
                            }

                            if (leftCTA != null || rightCTA != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    content = {
                                        Box(
                                            modifier = Modifier.weight(1f),
                                            content = {
                                                if (leftCTA != null) {
                                                    CtaElement(leftCTA) {
                                                        handleClick(leftCTA.ctaLink)
                                                    }
                                                }
                                            }
                                        )
                                        Box(
                                            modifier = Modifier.weight(1f),
                                            content = {
                                                if (rightCTA != null) {
                                                    CtaElement(rightCTA) {
                                                        handleClick(rightCTA.ctaLink)
                                                    }
                                                }
                                            }
                                        )
                                    }
                                )
                            }

                            centerCTAs.forEach { cta ->
                                CtaElement(cta) { handleClick(cta.ctaLink) }
                            }
                        }
                    )

                    if (details.enableCrossButton == "true") {
                        IconButton(
                            onClick = handleDismiss,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                            content = {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color(0x4D000000), shape = CircleShape)
                                        .padding(4.dp)
                                        .clip(CircleShape)
                                )
                            }
                        )
                    }
                }
            )
        }
    )
}


@Composable
private fun ImageElement(
    element: BottomSheetElement,
    modifier: Modifier = Modifier,
    onClick: (String?) -> Unit = { _ -> },
    onState: (AsyncImagePainter.State) -> Unit = { _ -> }
) {
    val paddingLeft = element.paddingLeft?.dp ?: 0.dp
    val paddingRight = element.paddingRight?.dp ?: 0.dp
    val paddingTop = element.paddingTop?.dp ?: 0.dp
    val paddingBottom = element.paddingBottom?.dp ?: 0.dp

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = paddingLeft,
                end = paddingRight,
                top = paddingTop,
                bottom = paddingBottom
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick(element.imageLink) }
            ),
        contentAlignment = when (element.alignment) {
            "left" -> Alignment.CenterStart
            "right" -> Alignment.CenterEnd
            else -> Alignment.Center
        },
        content = {
            Image(
                painter = rememberAsyncImagePainter(element.url, onState = onState),
                contentDescription = element.ctaText ?: "Image",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
        }
    )
}

@Composable
private fun BodyElement(
    element: BottomSheetElement,
    modifier: Modifier = Modifier,
) {
    val paddingLeft = element.paddingLeft?.dp ?: 0.dp
    val paddingRight = element.paddingRight?.dp ?: 0.dp
    val paddingTop = element.paddingTop?.dp ?: 0.dp
    val paddingBottom = element.paddingBottom?.dp ?: 0.dp

    val alignment = when (element.alignment) {
        "left" -> Alignment.Start
        "right" -> Alignment.End
        else -> Alignment.CenterHorizontally
    }

    val textAlign = when (element.alignment) {
        "left" -> TextAlign.Start
        "right" -> TextAlign.End
        else -> TextAlign.Center
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = Color(
                    (element.bodyBackgroundColor ?: "#FFFFFF").toColorInt()
                )
            )
            .padding(
                start = paddingLeft,
                end = paddingRight,
                top = paddingTop,
                bottom = paddingBottom
            ),
        horizontalAlignment = alignment,
        content = {
            if (!element.titleText.isNullOrBlank()) {
                val (fontWeight, fontStyle, textDecoration) = element.titleFontStyle?.decoration.decoration()
                Text(
                    text = element.titleText,
                    color = element.titleFontStyle?.colour.toColor(),
                    fontSize = (element.titleFontSize ?: 16).sp,
                    fontWeight = fontWeight,
                    fontStyle = fontStyle,
                    textDecoration = textDecoration,
                    textAlign = textAlign,
                    lineHeight = ((element.titleLineHeight ?: 1f) * (element.titleFontSize
                        ?: 16)).sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (!element.descriptionText.isNullOrBlank()) {
                Spacer(
                    modifier = Modifier.height(
                        (element.spacingBetweenTitleDesc?.toInt() ?: 0).dp
                    )
                )

                val (fontWeight, fontStyle, textDecoration) = element.descriptionFontStyle?.decoration.decoration()
                Text(
                    text = element.descriptionText,
                    color = element.descriptionFontStyle?.colour.toColor(),
                    fontSize = (element.descriptionFontSize ?: 14).sp,
                    fontWeight = fontWeight,
                    fontStyle = fontStyle,
                    textDecoration = textDecoration,
                    textAlign = textAlign,
                    lineHeight = ((element.descriptionLineHeight
                        ?: 1f) * (element.descriptionFontSize
                        ?: 14)).sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

@Composable
private fun CtaElement(
    element: BottomSheetElement,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val paddingLeft = element.paddingLeft?.dp ?: 0.dp
    val paddingRight = element.paddingRight?.dp ?: 0.dp
    val paddingTop = element.paddingTop?.dp ?: 0.dp
    val paddingBottom = element.paddingBottom?.dp ?: 0.dp

    val borderRadius = element.ctaBorderRadius?.dp ?: 5.dp
    val buttonHeight = element.ctaHeight?.dp ?: 50.dp
    val buttonWidth = element.ctaWidth?.dp ?: 100.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = Color(
                    (element.ctaBackgroundColor ?: "#FFFFFF").toColorInt()
                )
            )
            .padding(
                start = paddingLeft,
                end = paddingRight,
                top = paddingTop,
                bottom = paddingBottom
            ),
        contentAlignment = when (element.alignment) {
            "left" -> Alignment.CenterStart
            "right" -> Alignment.CenterEnd
            else -> Alignment.Center
        },
        content = {
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(borderRadius),
                colors = ButtonDefaults.buttonColors(containerColor = element.ctaBoxColor.toColor()),
                modifier = Modifier
                    .height(buttonHeight)
                    .then(
                        if (element.ctaFullWidth == true) Modifier.fillMaxWidth() else Modifier.width(
                            buttonWidth
                        )
                    ),
                content = {
                    val (fontWeight, fontStyle, textDecoration) = element.ctaFontDecoration.decoration()

                    Text(
                        text = element.ctaText ?: "Click",
                        color = element.ctaTextColour.toColor(Color.White),
                        fontFamily = try {
                            FontFamily(
                                Font(
                                    googleFont = GoogleFont(element.ctaFontFamily ?: "Poppins"),
                                    fontProvider = googleFontProvider,
                                    weight = FontWeight.Normal,
                                    style = FontStyle.Normal
                                )
                            )
                        } catch (_: Exception) {
                            FontFamily.Default
                        },
                        fontSize = (element.ctaFontSize?.toFloatOrNull() ?: 14f).sp,
                        fontWeight = fontWeight,
                        fontStyle = fontStyle,
                        textDecoration = textDecoration
                    )
                }
            )
        }
    )
}

private fun List<String>?.decoration() = run {
    val fontWeight = if (this?.contains("bold") == true) FontWeight.Bold else FontWeight.Normal
    val fontStyle = if (this?.contains("italic") == true) FontStyle.Italic else FontStyle.Normal
    val textDecoration = if (this?.contains("underline") == true) TextDecoration.Underline else null
    Triple(fontWeight, fontStyle, textDecoration)
}
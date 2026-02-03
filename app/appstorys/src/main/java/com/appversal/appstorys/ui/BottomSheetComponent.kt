package com.appversal.appstorys.ui

import android.util.Log
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import coil.ImageLoader
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.appversal.appstorys.R
import com.appversal.appstorys.api.BottomSheetDetails
import com.appversal.appstorys.api.BottomSheetElement
import com.appversal.appstorys.api.TextStyling
import com.appversal.appstorys.ui.common_components.CommonText
import com.appversal.appstorys.ui.common_components.CrossButton
import com.appversal.appstorys.ui.common_components.createCrossButtonConfig
import com.appversal.appstorys.utils.asInt
import com.appversal.appstorys.utils.isGifUrl
import com.appversal.appstorys.utils.isLottieUrl
import android.os.Build.VERSION.SDK_INT

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BottomSheetComponent(
    onClick: (String?) -> Unit = { _ -> },
    onDismissRequest: () -> Unit,
    bottomSheetDetails: BottomSheetDetails,
) {
    val elements = bottomSheetDetails.elements?.sortedBy { it.order } ?: emptyList()
    val imageElement = elements.firstOrNull { it.type == "image" }

    var imageActive by remember(imageElement) { mutableStateOf(imageElement != null) }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    LaunchedEffect(imageActive) {
        if (imageActive) {
            sheetState.show()
        } else {
            sheetState.hide()
        }
    }

    // Parse corner radius - now it's a CornerRadius object
    val topLeftRadius = (bottomSheetDetails.cornerRadius?.topLeft ?: 16).dp
    val topRightRadius = (bottomSheetDetails.cornerRadius?.topRight ?: 16).dp

    val onImageState = remember {
        { state: AsyncImagePainter.State ->
            imageActive = state is AsyncImagePainter.State.Success
        }
    }

    // Parse backdrop color and opacity from backend
    val backdropColor = try {
        Color(bottomSheetDetails.backdropColor?.toColorInt() ?: 0x808080)
    } catch (e: Exception) {
        Color(0x808080) // Default gray
    }

    val backdropOpacity = (bottomSheetDetails.backdropOpacity?.asInt(50)?.toFloat() ?: 50f) / 100f

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(topStart = topLeftRadius, topEnd = topRightRadius),
        containerColor = Color.Transparent, // Use backend background color (transparent for imageOnly)
        scrimColor = backdropColor.copy(alpha = backdropOpacity), // Apply backdrop with opacity
        dragHandle = null,
        sheetState = sheetState,
        content = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                content = {

                    val hasOverlayButton = imageElement?.overlayButton == true

                    if (imageElement != null && hasOverlayButton) {
                        ImageElement(imageElement, onClick = onClick, onState = onImageState)
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
                            // Render elements in order, excluding overlay images
                            elements.forEach { element ->
                                when (element.type) {
                                    "image" -> {
                                        // Only render image here if it's not an overlay
                                        if (!hasOverlayButton) {
                                            ImageElement(element, onClick, onImageState)
                                        }
                                    }
                                    "body" -> {
                                        BodyElement(element)
                                    }
                                    "cta" -> {
                                        // Check if this is part of a left/right pair
                                        val allCTAs = elements.filter { it.type == "cta" }
                                        val leftCTA = allCTAs.firstOrNull { it.position == "left" }
                                        val rightCTA = allCTAs.firstOrNull { it.position == "right" }

                                        // If this is the first left or right CTA and both exist, render them together
                                        if ((element == leftCTA || element == rightCTA) && leftCTA != null && rightCTA != null) {
                                            if (element == leftCTA) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    content = {
                                                        Box(
                                                            modifier = Modifier.weight(1f),
                                                            content = {
                                                                CTAElement(leftCTA) { onClick(leftCTA.ctaLink) }
                                                            }
                                                        )
                                                        Box(
                                                            modifier = Modifier.weight(1f),
                                                            content = {
                                                                CTAElement(rightCTA) { onClick(rightCTA.ctaLink) }
                                                            }
                                                        )
                                                    }
                                                )
                                            }
                                            // Skip rendering the second CTA of the pair (rightCTA when we already rendered both)
                                        } else if (element.position != "left" && element.position != "right") {
                                            // Render center or no-position CTAs normally
                                            CTAElement(element) { onClick(element.ctaLink) }
                                        } else if ((element.position == "left" && rightCTA == null) ||
                                                   (element.position == "right" && leftCTA == null)) {
                                            // Render single left or right CTA
                                            CTAElement(element) { onClick(element.ctaLink) }
                                        }
                                    }
                                }
                            }
                        }
                    )

                    // Check the common enabled field first, then fall back to legacy enableCrossButton string
                    // Prioritize root-level crossButton (new backend format) over styling.crossButton
                    val crossButton = bottomSheetDetails.crossButton ?: bottomSheetDetails.styling?.crossButton
                    val crossEnabled = crossButton?.enabled
                        ?: (bottomSheetDetails.enableCrossButton?.trim()?.equals("true", true) ?: true)

                    if (crossEnabled) {
                        // Get cross button config with support for new backend format
                        val crossColors = crossButton?.color ?: crossButton?.colors
                        val crossImageUrl = crossButton?.image

                        CrossButton(
                            modifier = Modifier.align(Alignment.TopEnd),
                            config = createCrossButtonConfig(
                                fillColorString = crossColors?.fill,
                                crossColorString = crossColors?.cross,
                                strokeColorString = crossColors?.stroke,
                                marginTop = crossButton?.margin?.top,
                                marginEnd = crossButton?.margin?.right,
                                size = crossButton?.size,
                                imageUrl = crossImageUrl
                            ),
                            onClose = onDismissRequest
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
    onClick: (String?) -> Unit = { _ -> },
    onState: (AsyncImagePainter.State) -> Unit = { _ -> }
) {
    val paddingLeft = element.paddingLeft?.dp ?: 0.dp
    val paddingRight = element.paddingRight?.dp ?: 0.dp
    val paddingTop = element.paddingTop?.dp ?: 0.dp
    val paddingBottom = element.paddingBottom?.dp ?: 0.dp

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
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
            if(isLottieUrl(element.url.orEmpty())){
                val composition by rememberLottieComposition(
                    spec = LottieCompositionSpec.Url(element.url.orEmpty())
                )
                LottieAnimation(
                    composition = composition,
                    iterations = LottieConstants.IterateForever,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(
                            topStart = (element.cornerRadius?.topLeft ?: 0).dp,
                            topEnd = (element.cornerRadius?.topRight ?: 0).dp,
                            bottomEnd = (element.cornerRadius?.bottomRight ?: 0).dp,
                            bottomStart = (element.cornerRadius?.bottomLeft ?: 0).dp
                        )),
                    contentScale = ContentScale.FillWidth
                )
            } else if(isGifUrl(element.url.orEmpty())){

                val imageUrl = element.url

                val imageLoader = ImageLoader.Builder(LocalContext.current)
                    .components {
                        if (SDK_INT >= 28) {
                            add(ImageDecoderDecoder.Factory())
                        } else {
                            add(GifDecoder.Factory())
                        }
                    }
                    .build()

                val painter = rememberAsyncImagePainter(
                    ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .memoryCacheKey(imageUrl)
                        .diskCacheKey(imageUrl)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .crossfade(true)
                        .apply { size(coil.size.Size.ORIGINAL) }
                        .build(),
                    imageLoader = imageLoader
                )

                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(
                            topStart = (element.cornerRadius?.topLeft ?: 0).dp,
                            topEnd = (element.cornerRadius?.topRight ?: 0).dp,
                            bottomEnd = (element.cornerRadius?.bottomRight ?: 0).dp,
                            bottomStart = (element.cornerRadius?.bottomLeft ?: 0).dp
                        )),
                    contentScale = ContentScale.FillWidth
                )
            } else {
                Image(
                    painter = rememberAsyncImagePainter(element.url, onState = onState),
                    contentDescription = "Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(
                            topStart = (element.cornerRadius?.topLeft ?: 0).dp,
                            topEnd = (element.cornerRadius?.topRight ?: 0).dp,
                            bottomEnd = (element.cornerRadius?.bottomRight ?: 0).dp,
                            bottomStart = (element.cornerRadius?.bottomLeft ?: 0).dp
                        )),
                    contentScale = ContentScale.FillWidth
                )
            }
        }
    )
}

@Composable
private fun BodyElement(element: BottomSheetElement) {
    val paddingLeft = element.marginLeft?.dp ?: 0.dp
    val paddingRight = element.marginRight?.dp ?: 0.dp
    val paddingTop = element.marginTop?.dp ?: 0.dp
    val paddingBottom = element.marginBottom?.dp ?: 0.dp

    // Add default padding if all margins are 0 to make text visible
    val hasZeroMargins = paddingLeft == 0.dp && paddingRight == 0.dp && paddingTop == 0.dp && paddingBottom == 0.dp
    val defaultPadding = if (hasZeroMargins) 12.dp else 0.dp

    val alignment = when (element.alignment) {
        "left" -> Alignment.Start
        "right" -> Alignment.End
        else -> Alignment.CenterHorizontally
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = try {
                    val bgColor = element.bodyBackgroundColor?.takeIf { it.isNotBlank() }
                    if (bgColor != null) {
                        Color(bgColor.toColorInt())
                    } else {
                        Color.Transparent
                    }
                } catch (_: Exception) {
                    Color.Transparent
                }
            )
            .padding(
                start = paddingLeft + defaultPadding,
                end = paddingRight + defaultPadding,
                top = paddingTop + defaultPadding,
                bottom = paddingBottom + defaultPadding
            ),
        horizontalAlignment = alignment,
        content = {
            if (!element.titleText.isNullOrBlank()) {
                // Parse fontSize from JsonElement (can be String or Int)
                val titleFontSizeValue = element.titleFontStyle?.fontSize?.asInt(element.titleFontSize ?: 16) ?: element.titleFontSize ?: 16

                CommonText(
                    modifier = Modifier.fillMaxWidth(),
                    text = element.titleText,
                    lineHeight = ((element.titleLineHeight ?: 1f) * titleFontSizeValue),
                    styling = TextStyling(
                        color = element.titleFontStyle?.colour,
                        fontSize = titleFontSizeValue,
                        fontFamily = "",
                        textAlign = element.titleFontStyle?.alignment ?: element.alignment, // Use fontStyle alignment first
                        fontDecoration = element.titleFontStyle?.decoration
                    )
                )
            }

            if (!element.descriptionText.isNullOrBlank()) {
                Spacer(
                    modifier = Modifier.height(
                        (element.spacingBetweenTitleDesc?.toInt() ?: 0).dp
                    )
                )

                // Parse fontSize from JsonElement (can be String or Int)
                val descFontSizeValue = element.descriptionFontStyle?.fontSize?.asInt(element.descriptionFontSize ?: 14) ?: element.descriptionFontSize ?: 14

                CommonText(
                    modifier = Modifier.fillMaxWidth(),
                    text = element.descriptionText,
                    lineHeight = ((element.descriptionLineHeight ?: 1f) * descFontSizeValue),
                    styling = TextStyling(
                        color = element.descriptionFontStyle?.colour,
                        fontSize = descFontSizeValue,
                        fontFamily = "",
                        textAlign = element.descriptionFontStyle?.alignment ?: element.alignment, // Use fontStyle alignment first
                        fontDecoration = element.descriptionFontStyle?.decoration
                    )
                )
            }
        }
    )
}

@Composable
private fun CTAElement(element: BottomSheetElement, onClick: () -> Unit = {}) {
    // Support both nested cta.margin and flat margin fields
    val marginLeft = element.cta?.margin?.left ?: element.marginLeft
    val marginRight = element.cta?.margin?.right ?: element.marginRight
    val marginTop = element.cta?.margin?.top ?: element.marginTop
    val marginBottom = element.cta?.margin?.bottom ?: element.marginBottom

    val paddingLeft = marginLeft?.dp ?: 0.dp
    val paddingRight = marginRight?.dp ?: 0.dp
    val paddingTop = marginTop?.dp ?: 0.dp
    val paddingBottom = marginBottom?.dp ?: 0.dp

    // Support both nested cta.container.ctaBoxColor and flat ctaBoxColor
    val buttonColor = try {
        val colorString = element.cta?.container?.ctaBoxColor ?: element.ctaBoxColor ?: "#000000"
        Color(colorString.toColorInt())
    } catch (_: Exception) {
        Color.Black
    }

    // Support both nested cta.text.color and flat ctaTextColour
    val textColor = try {
        val colorString = element.cta?.text?.color ?: element.ctaTextColour ?: "#FFFFFF"
        Color(colorString.toColorInt())
    } catch (_: Exception) {
        Color.White
    }

    // Support both nested and flat height/width
    val buttonHeight = (element.cta?.container?.height ?: element.ctaHeight?.asInt(50))?.dp ?: 50.dp
    val buttonWidth = (element.cta?.container?.ctaWidth?.asInt(100) ?: element.ctaWidth?.asInt(100))?.dp ?: 100.dp

    // Support both nested cta.container.backgroundColor and flat ctaBackgroundColor
    val ctaBackgroundColor = try {
        val colorString = element.cta?.container?.backgroundColor?.takeIf { it.isNotBlank() }
            ?: element.ctaBackgroundColor?.takeIf { it.isNotBlank() }
        if (colorString != null) {
            Color(colorString.toColorInt())
        } else {
            Color.Transparent
        }
    } catch (_: Exception) {
        Color.Transparent
    }

    // Support both nested cta.container.ctaFullWidth and flat ctaFullWidth
    val isFullWidth = element.cta?.container?.ctaFullWidth ?: element.ctaFullWidth ?: false

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = ctaBackgroundColor
            )
            .padding(
                start = paddingLeft,
                end = paddingRight,
                top = paddingTop,
                bottom = paddingBottom
            ),
        contentAlignment = when (element.cta?.container?.alignment ?: element.alignment) {
            "left" -> Alignment.CenterStart
            "right" -> Alignment.CenterEnd
            else -> Alignment.Center
        },
        content = {
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(
                    topStart = (element.cta?.cornerRadius?.topLeft ?: element.ctaBorderRadius?.topLeft ?: 0).dp,
                    topEnd = (element.cta?.cornerRadius?.topRight ?: element.ctaBorderRadius?.topRight ?: 0).dp,
                    bottomEnd = (element.cta?.cornerRadius?.bottomRight ?: element.ctaBorderRadius?.bottomRight ?: 0).dp,
                    bottomStart = (element.cta?.cornerRadius?.bottomLeft ?: element.ctaBorderRadius?.bottomLeft ?: 0).dp
                ),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                modifier = Modifier
                    .height(buttonHeight)
                    .then(
                        if (isFullWidth) Modifier.fillMaxWidth()
                        else Modifier.width(buttonWidth)
                    ),
                content = {
                    // Support both nested cta.text.fontDecoration and flat ctaFontDecoration
                    val decoration = element.cta?.text?.fontDecoration ?: element.ctaFontDecoration ?: emptyList()

                    val ctaFontWeight =
                        if (decoration.contains("bold")) FontWeight.Bold else FontWeight.Normal
                    val ctaFontStyle =
                        if (decoration.contains("italic")) FontStyle.Italic else FontStyle.Normal
                    val ctaTextDecoration =
                        if (decoration.contains("underline")) TextDecoration.Underline else null

                    // Support both nested cta.text.fontFamily and flat ctaFontFamily
                    val fontName = element.cta?.text?.fontFamily ?: element.ctaFontFamily ?: "Poppins"

                    val fontFamily = try {
                        val provider = GoogleFont.Provider(
                            providerAuthority = "com.google.android.gms.fonts",
                            providerPackage = "com.google.android.gms",
                            certificates = R.array.com_google_android_gms_fonts_certs
                        )
                        val googleFont = GoogleFont(fontName)
                        FontFamily(
                            Font(
                                googleFont = googleFont,
                                fontProvider = provider,
                                weight = FontWeight.Normal,
                                style = FontStyle.Normal
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("fontFamily", "Failed to load font: $fontName", e)
                        FontFamily.Default
                    }

                    CommonText(
                        text = element.ctaText ?: "Click",
                        styling = TextStyling(
                            color = element.cta?.text?.color ?: element.ctaTextColour,
                            fontSize = element.cta?.text?.fontSize ?: element.ctaFontSize?.toIntOrNull(),
                            fontFamily = "",
                            fontDecoration = decoration
                        )
                    )
                }
            )
        }
    )
}
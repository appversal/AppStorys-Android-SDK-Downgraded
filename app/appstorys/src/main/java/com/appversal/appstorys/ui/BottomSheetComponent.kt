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
import com.appversal.appstorys.R
import com.appversal.appstorys.api.BottomSheetDetails
import com.appversal.appstorys.api.BottomSheetElement


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BottomSheetComponent(
    onClick: (String?) -> Unit = { _ -> },
    onDismissRequest: () -> Unit,
    bottomSheetDetails: BottomSheetDetails,
) {
    val elements = bottomSheetDetails.elements?.sortedBy { it.order } ?: emptyList()
    val imageElement = elements.firstOrNull { it.type == "image" }
    val bodyElements = elements.filter { it.type == "body" }
    val ctaElements = elements.filter { it.type == "cta" }

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

    val cornerRadius = bottomSheetDetails.cornerRadius?.toFloatOrNull()?.dp ?: 16.dp

    val onImageState = remember {
        { state: AsyncImagePainter.State ->
            imageActive = state is AsyncImagePainter.State.Success
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
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
                            if (!hasOverlayButton && imageElement != null) {
                                ImageElement(imageElement, onClick, onImageState)
                            }

                            bodyElements.forEach { BodyElement(it) }

                            val leftCTA = ctaElements.firstOrNull { it.position == "left" }
                            val rightCTA = ctaElements.firstOrNull { it.position == "right" }
                            val centerCTAs =
                                ctaElements.filter { it.position == "center" || it.position.isNullOrEmpty() }

                            if (leftCTA != null || rightCTA != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    content = {
                                        Box(
                                            modifier = Modifier.weight(1f),
                                            content = {
                                                if (leftCTA != null) {
                                                    CTAElement(leftCTA) { onClick(leftCTA.ctaLink) }
                                                }
                                            }
                                        )
                                        Box(
                                            modifier = Modifier.weight(1f),
                                            content = {
                                                if (rightCTA != null) {
                                                    CTAElement(rightCTA) { onClick(rightCTA.ctaLink) }
                                                }
                                            }
                                        )
                                    }
                                )
                            }

                            centerCTAs.forEach { cta ->
                                CTAElement(cta) { onClick(cta.ctaLink) }
                            }
                        }
                    )

                    if (bottomSheetDetails.enableCrossButton == "true") {
                        IconButton(
                            onClick = onDismissRequest,
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
            Image(
                painter = rememberAsyncImagePainter(element.url, onState = onState),
                contentDescription = "Image",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
        }
    )
}

@Composable
private fun BodyElement(element: BottomSheetElement) {
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
        modifier = Modifier
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
                val titleColor = try {
                    Color(
                        (element.titleFontStyle?.colour ?: "#000000").toColorInt()
                    )
                } catch (_: Exception) {
                    Color.Black
                }

                val decoration = element.titleFontStyle?.decoration.orEmpty()

                val titleFontWeight =
                    if (decoration.contains("bold")) FontWeight.Bold else FontWeight.Normal
                val titleFontStyle =
                    if (decoration.contains("italic")) FontStyle.Italic else FontStyle.Normal
                val titleTextDecoration =
                    if (decoration.contains("underline")) TextDecoration.Underline else null

                Text(
                    text = element.titleText,
                    color = titleColor,
                    fontSize = (element.titleFontSize ?: 16).sp,
                    fontWeight = titleFontWeight,
                    fontStyle = titleFontStyle,
                    textDecoration = titleTextDecoration,
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
                val descriptionColor = try {
                    Color(
                        (element.descriptionFontStyle?.colour ?: "#000000").toColorInt()
                    )
                } catch (_: Exception) {
                    Color.Black
                }

                val decoration = element.descriptionFontStyle?.decoration.orEmpty()
                val descriptionFontWeight =
                    if (decoration.contains("bold")) FontWeight.Bold else FontWeight.Normal
                val descriptionFontStyle =
                    if (decoration.contains("italic")) FontStyle.Italic else FontStyle.Normal
                val descriptionTextDecoration =
                    if (decoration.contains("underline")) TextDecoration.Underline else null

                Text(
                    text = element.descriptionText,
                    color = descriptionColor,
                    fontSize = (element.descriptionFontSize ?: 14).sp,
                    fontWeight = descriptionFontWeight,
                    fontStyle = descriptionFontStyle,
                    textDecoration = descriptionTextDecoration,
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
private fun CTAElement(element: BottomSheetElement, onClick: () -> Unit = {}) {
    val paddingLeft = element.paddingLeft?.dp ?: 0.dp
    val paddingRight = element.paddingRight?.dp ?: 0.dp
    val paddingTop = element.paddingTop?.dp ?: 0.dp
    val paddingBottom = element.paddingBottom?.dp ?: 0.dp

    val buttonColor = try {
        Color((element.ctaBoxColor ?: "#000000").toColorInt())
    } catch (_: Exception) {
        Color.Black
    }

    val textColor = try {
        Color((element.ctaTextColour ?: "#FFFFFF").toColorInt())
    } catch (_: Exception) {
        Color.White
    }

    val borderRadius = element.ctaBorderRadius?.dp ?: 5.dp
    val buttonHeight = element.ctaHeight?.dp ?: 50.dp
    val buttonWidth = element.ctaWidth?.dp ?: 100.dp

    Box(
        modifier = Modifier
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
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                modifier = Modifier
                    .height(buttonHeight)
                    .then(
                        if (element.ctaFullWidth == true) Modifier.fillMaxWidth()
                        else Modifier.width(buttonWidth)
                    ),
                content = {
                    val decoration = element.ctaFontDecoration.orEmpty()

                    val ctaFontWeight =
                        if (decoration.contains("bold")) FontWeight.Bold else FontWeight.Normal
                    val ctaFontStyle =
                        if (decoration.contains("italic")) FontStyle.Italic else FontStyle.Normal
                    val ctaTextDecoration =
                        if (decoration.contains("underline")) TextDecoration.Underline else null

                    val fontName = element.ctaFontFamily ?: "Poppins"

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

                    Text(
                        text = element.ctaText ?: "Click",
                        color = textColor,
                        fontFamily = fontFamily,
                        fontSize = (element.ctaFontSize?.toFloatOrNull() ?: 14f).sp,
                        fontWeight = ctaFontWeight,
                        fontStyle = ctaFontStyle,
                        textDecoration = ctaTextDecoration
                    )
                }
            )
        }
    )
}
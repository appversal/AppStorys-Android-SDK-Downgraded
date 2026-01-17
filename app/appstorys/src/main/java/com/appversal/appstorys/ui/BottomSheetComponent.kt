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
import com.appversal.appstorys.ui.components.CommonText
import com.appversal.appstorys.ui.components.CrossButton
import com.appversal.appstorys.ui.components.createCrossButtonConfig
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
                        CrossButton(
                            modifier = Modifier.align(Alignment.TopEnd),
                            config = createCrossButtonConfig(
                                fillColorString = bottomSheetDetails.styling?.crossButton?.colors?.fill,
                                crossColorString = bottomSheetDetails.styling?.crossButton?.colors?.cross,
                                strokeColorString = bottomSheetDetails.styling?.crossButton?.colors?.stroke,
                                marginTop = bottomSheetDetails.styling?.crossButton?.margin?.top,
                                marginEnd = bottomSheetDetails.styling?.crossButton?.margin?.right,
                                size = bottomSheetDetails.styling?.crossButton?.size,
//                                imageUrl = bannerDetails.crossButtonImage?.takeIf { it.isNotBlank() }?.let { raw ->
//                                    val trimmed = raw.trim()
//                                    if (trimmed.startsWith("http", true)) {
//                                        trimmed
//                                    } else {
//                                        val base = "https://appstorysmediabucketdev.s3.ap-south-1.amazonaws.com/"
//                                        if (trimmed.startsWith("/")) base + trimmed.removePrefix("/") else base + trimmed
//                                    }
//                                }
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

    val alignment = when (element.alignment) {
        "left" -> Alignment.Start
        "right" -> Alignment.End
        else -> Alignment.CenterHorizontally
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

                CommonText(
                    modifier = Modifier.fillMaxWidth(),
                    text = element.titleText,
                    lineHeight = ((element.titleLineHeight ?: 1f) * (element.titleFontSize ?: 16)),
                    styling = TextStyling(
                        color = element.titleFontStyle?.colour,
                        fontSize = element.titleFontSize,
                        fontFamily = "",
                        textAlign = element.alignment,
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

                CommonText(
                    modifier = Modifier.fillMaxWidth(),
                    text = element.descriptionText,
                    lineHeight = ((element.descriptionLineHeight ?: 1f) * (element.descriptionFontSize ?: 14)),
                    styling = TextStyling(
                        color = element.descriptionFontStyle?.colour,
                        fontSize = element.descriptionFontSize,
                        fontFamily = "",
                        textAlign = element.alignment,
                        fontDecoration = element.descriptionFontStyle?.decoration
                    )
                )
            }
        }
    )
}

@Composable
private fun CTAElement(element: BottomSheetElement, onClick: () -> Unit = {}) {
    val paddingLeft = element.marginLeft?.dp ?: 0.dp
    val paddingRight = element.marginRight?.dp ?: 0.dp
    val paddingTop = element.marginTop?.dp ?: 0.dp
    val paddingBottom = element.marginBottom?.dp ?: 0.dp

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

    val buttonHeight = element.ctaHeight?.asInt(50)?.dp ?: 50.dp
    val buttonWidth = element.ctaWidth?.asInt(100)?.dp ?: 100.dp

    val ctaBackgroundColor = try {
        Color(
            (element.ctaBackgroundColor ?: "#FFFFFF").toColorInt()
        )
    } catch (_: Exception) {
        Color.White
    }

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
        contentAlignment = when (element.alignment) {
            "left" -> Alignment.CenterStart
            "right" -> Alignment.CenterEnd
            else -> Alignment.Center
        },
        content = {
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(
                    topStart = (element.ctaBorderRadius?.topLeft ?: 0).dp,
                    topEnd = (element.ctaBorderRadius?.topRight ?: 0).dp,
                    bottomEnd = (element.ctaBorderRadius?.bottomRight ?: 0).dp,
                    bottomStart = (element.ctaBorderRadius?.bottomLeft ?: 0).dp
                ),
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

                    CommonText(
                        text = element.ctaText ?: "Click",
                        styling = TextStyling(
                            color = element.ctaTextColour,
                            fontSize = element.ctaFontSize?.toIntOrNull(),
                            fontFamily = "",
                            fontDecoration = element.ctaFontDecoration
                        )
                    )
                }
            )
        }
    )
}
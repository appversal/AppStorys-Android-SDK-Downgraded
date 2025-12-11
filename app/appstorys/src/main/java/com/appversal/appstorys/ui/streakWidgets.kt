package com.appversal.appstorys.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.appversal.appstorys.api.StreaksDetails
import com.appversal.appstorys.api.StreaksImage

/**
 * StreaksWidget - A composable that displays images based on event triggers
 *
 * Features:
 * - Shows only one image at a time
 * - Switches images when matching events are triggered
 * - Supports default image when no event is triggered
 * - Smooth transitions between images
 * - Supports both regular images and Lottie animations
 *
 * @param modifier Modifier for the widget
 * @param streaksDetails Streaks configuration from backend
 * @param triggeredEvents Set of events that have been triggered
 * @param placeholder Optional placeholder drawable
 * @param onImageClick Callback when image is clicked
 */
@Composable
fun StreaksWidget(
    modifier: Modifier = Modifier,
    streaksDetails: StreaksDetails,
    triggeredEvents: Set<String>,
    placeholder: Drawable? = null,
    onImageClick: ((StreaksImage) -> Unit)? = null
) {
    val context = LocalContext.current

    // Find the current image to display based on triggered events
    val currentImage = remember(triggeredEvents, streaksDetails.streaksImages) {
        findImageForEvent(streaksDetails.streaksImages, triggeredEvents)
    }

    val height = streaksDetails.height?.dp
    val width = streaksDetails.width?.dp

    // Animated transition when image changes
    AnimatedContent(
        targetState = currentImage,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "streaks_image_transition",
        modifier = modifier
    ) { targetImage ->
        if (targetImage != null) {
            StreaksImageCard(
                streaksImage = targetImage,
                streaksDetails = streaksDetails,
                height = height,
                width = width,
                placeholder = placeholder,
                context = context,
                onImageClick = onImageClick
            )
        }
    }
}

/**
 * Find the appropriate image to display based on triggered events
 * Priority: Triggered event match > Default image (order = 0 or lowest order)
 */
private fun findImageForEvent(
    streaksImages: List<StreaksImage>?,
    triggeredEvents: Set<String>
): StreaksImage? {
    if (streaksImages.isNullOrEmpty()) return null

    // First, try to find an image with a matching triggered event
    val matchedImage = streaksImages.firstOrNull { image ->
        val eventTrigger = image.eventTrigger
        !eventTrigger.isNullOrEmpty() && triggeredEvents.contains(eventTrigger)
    }

    if (matchedImage != null) {
        return matchedImage
    }

    // If no match, return the default image (lowest order number or image with null eventTrigger)
    return streaksImages.minByOrNull { it.order ?: Int.MAX_VALUE }
}

/**
 * Display a single image card with proper styling
 */
@Composable
private fun StreaksImageCard(
    streaksImage: StreaksImage,
    streaksDetails: StreaksDetails,
    height: Dp?,
    width: Dp?,
    placeholder: Drawable?,
    context: Context,
    onImageClick: ((StreaksImage) -> Unit)?
) {
    Card(
        shape = RoundedCornerShape(
            topStart = (streaksDetails.styling?.topLeftRadius?.toFloatOrNull() ?: 0f).dp,
            topEnd = (streaksDetails.styling?.topRightRadius?.toFloatOrNull() ?: 0f).dp,
            bottomStart = (streaksDetails.styling?.bottomLeftRadius?.toFloatOrNull() ?: 0f).dp,
            bottomEnd = (streaksDetails.styling?.bottomRightRadius?.toFloatOrNull() ?: 0f).dp,
        ),
        modifier = Modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .then(if (height != null) Modifier.height(height) else Modifier)
            .clickable(enabled = onImageClick != null) {
                onImageClick?.invoke(streaksImage)
            }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Check if this is a Lottie animation
            if (!streaksImage.lottie_data.isNullOrEmpty()) {
                LottieImageContent(
                    lottieUrl = streaksImage.lottie_data,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Regular image
                RegularImageContent(
                    imageUrl = streaksImage.image ?: "",
                    placeholder = placeholder,
                    context = context,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Display Lottie animation content
 */
@Composable
private fun LottieImageContent(
    lottieUrl: String,
    modifier: Modifier = Modifier
) {
    val composition = rememberLottieComposition(
        LottieCompositionSpec.Url(lottieUrl)
    )

    LottieAnimation(
        composition = composition.value,
        iterations = LottieConstants.IterateForever,
        modifier = modifier
    )
}

/**
 * Display regular image content (supports GIFs)
 */
@Composable
private fun RegularImageContent(
    imageUrl: String,
    placeholder: Drawable?,
    context: Context,
    modifier: Modifier = Modifier
) {
    if (isGifUrl(imageUrl)) {
        // GIF image
        val imageLoader = ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()

        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .build(),
            imageLoader = imageLoader
        )

        androidx.compose.foundation.Image(
            painter = painter,
            contentDescription = "Streaks Image",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        // Regular image
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .placeholder(placeholder)
                .crossfade(true)
                .build(),
            contentDescription = "Streaks Image",
            contentScale = ContentScale.Crop,
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Gray.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            },
            modifier = modifier
        )
    }
}

/**
 * Helper function to check if URL is a GIF
 */
private fun isGifUrl(url: String): Boolean {
    return url.lowercase().endsWith(".gif")
}
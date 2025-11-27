package com.appversal.appstorys.ui

import android.os.Build.VERSION.SDK_INT
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImage
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

@Composable
internal fun PopupModal(
    onCloseClick: () -> Unit,
    modalDetails: ModalDetails,
    onModalClick: () -> Unit,
) {
    val modal = modalDetails.modals?.getOrNull(0)
    val imageUrl = modal?.url
    val context = LocalContext.current

    val mediaType = when {
        imageUrl?.endsWith(".gif", ignoreCase = true) == true -> "gif"
        imageUrl?.endsWith(".json", ignoreCase = true) == true -> "lottie"
        else -> "image"
    }

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
                .background(Color.Black.copy(alpha = modal?.backgroundOpacity?.toFloat() ?: 0.3f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    onCloseClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.wrapContentSize()) {
                Box(
                    modifier = Modifier
                        .wrapContentSize()
                        .padding(8.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onModalClick() }
                ) {
                    when (mediaType) {
                        "gif" -> {
                            val imageLoader = ImageLoader.Builder(context)
                                .components {
                                    if (SDK_INT >= 28) {
                                        add(ImageDecoderDecoder.Factory())
                                    } else {
                                        add(GifDecoder.Factory())
                                    }
                                }
                                .build()

                            val painter = rememberAsyncImagePainter(
                                ImageRequest.Builder(context)
                                    .data(imageUrl)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .build(),
                                imageLoader = imageLoader
                            )

                            androidx.compose.foundation.Image(
                                painter = painter,
                                contentDescription = "GIF Image",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .width(modal?.size?.toFloatOrNull()?.dp ?: 100.dp)
                                    .clip(RoundedCornerShape(modal?.borderRadius?.toFloatOrNull()?.dp ?: 12.dp))
                            )
                        }

                        "lottie" -> {
                            val composition by rememberLottieComposition(LottieCompositionSpec.Url(imageUrl ?: ""))
                            LottieAnimation(
                                composition = composition,
                                iterations = LottieConstants.IterateForever,
                                modifier = Modifier
                                    .width(modal?.size?.toFloatOrNull()?.dp ?: 100.dp)
                                    .clip(RoundedCornerShape(modal?.borderRadius?.toFloatOrNull()?.dp ?: 12.dp))
                            )
                        }

                        else -> {
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = "Popup Image",
                                modifier = Modifier
                                    .width(modal?.size?.toFloatOrNull()?.dp ?: 100.dp)
                                    .clip(RoundedCornerShape(modal?.borderRadius?.toFloatOrNull()?.dp ?: 12.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .clickable { onCloseClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

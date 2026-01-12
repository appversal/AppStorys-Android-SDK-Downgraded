package com.appversal.appstorys.ui.modals

import android.os.Build.VERSION.SDK_INT
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.appversal.appstorys.utils.VideoCache
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
import androidx.compose.ui.layout.ContentScale as UiContentScale

/**
 * Sealed class representing the loading state of modal media
 */
sealed class MediaLoadState {
    object Loading : MediaLoadState()
    object Success : MediaLoadState()
    object Error : MediaLoadState()
}

/**
 * Preloads media and returns the current loading state.
 * Uses Coil's execute to actually load and cache the image.
 */
@Composable
fun rememberMediaLoadState(mediaUrl: String?): MediaLoadState {
    val context = LocalContext.current
    val mediaType = determineMediaType(mediaUrl)

    var loadState by remember(mediaUrl) { mutableStateOf<MediaLoadState>(MediaLoadState.Loading) }

    when (mediaType) {
        "video" -> {
            // For video, we consider it loaded once the player is prepared
            // We'll mark it as success immediately since ExoPlayer handles buffering
            LaunchedEffect(mediaUrl) {
                loadState = MediaLoadState.Success
            }
        }
        "lottie" -> {
            val composition by rememberLottieComposition(
                if (mediaUrl?.trimStart()?.startsWith("{") == true || mediaUrl?.trimStart()?.startsWith("[") == true) {
                    LottieCompositionSpec.JsonString(mediaUrl)
                } else {
                    LottieCompositionSpec.Url(mediaUrl ?: "")
                }
            )
            LaunchedEffect(composition) {
                loadState = if (composition != null) MediaLoadState.Success else MediaLoadState.Loading
            }
        }
        "gif", "image" -> {
            // Use LaunchedEffect to actually execute the image request
            LaunchedEffect(mediaUrl) {
                if (mediaUrl.isNullOrEmpty()) {
                    loadState = MediaLoadState.Error
                    return@LaunchedEffect
                }

                try {
                    val imageLoader = if (mediaType == "gif") {
                        ImageLoader.Builder(context)
                            .components {
                                if (SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
                            }
                            .build()
                    } else {
                        ImageLoader.Builder(context).build()
                    }

                    val request = ImageRequest.Builder(context)
                        .data(mediaUrl)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .build()

                    // Execute the request to actually load the image
                    val result = imageLoader.execute(request)
                    loadState = if (result.drawable != null) {
                        MediaLoadState.Success
                    } else {
                        MediaLoadState.Error
                    }
                } catch (e: Exception) {
                    loadState = MediaLoadState.Error
                }
            }
        }
        else -> {
            LaunchedEffect(mediaUrl) {
                loadState = if (mediaUrl.isNullOrEmpty()) MediaLoadState.Error else MediaLoadState.Success
            }
        }
    }

    return loadState
}

// Determine media type from URL/contents
internal fun determineMediaType(url: String?): String {
    val u = url ?: ""
    return when {
        u.endsWith(".gif", ignoreCase = true) -> "gif"
        u.endsWith(".json", ignoreCase = true) -> "lottie"
        u.endsWith(".mp4", ignoreCase = true) || u.endsWith(".m3u8", ignoreCase = true) -> "video"
        else -> "image"
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerInline(videoUrl: String, modifier: Modifier = Modifier, muted: Boolean = false) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val exo = remember(videoUrl) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(VideoCache.getFactory(context)))
            .build().apply {
                setMediaItem(MediaItem.fromUri(videoUrl))
                repeatMode = Player.REPEAT_MODE_ONE
                volume = if (muted) 0f else 1f
                prepare()
                play()
            }
    }

    // The above placeholder for repeatMode will be replaced below with proper Player constants in callers' context if needed.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> exo.play()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE, androidx.lifecycle.Lifecycle.Event.ON_STOP -> exo.pause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exo.release()
        }
    }

    AndroidView(factory = { ctx ->
        PlayerView(ctx).apply {
            player = exo
            useController = false
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }, modifier = modifier)
}

@Composable
fun ModalMediaRenderer(
    mediaUrl: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: UiContentScale = UiContentScale.Fit,
    muted: Boolean = false
) {
    val context = LocalContext.current
    val mediaType = determineMediaType(mediaUrl)

    when (mediaType) {
        "gif" -> {
            val imageLoader = ImageLoader.Builder(context)
                .components {
                    if (SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
                }
                .build()

            val painter = rememberAsyncImagePainter(
                ImageRequest.Builder(context).data(mediaUrl).diskCachePolicy(CachePolicy.ENABLED).memoryCachePolicy(CachePolicy.ENABLED).build(),
                imageLoader = imageLoader
            )

            Image(
                painter = painter,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = modifier
            )
        }

        "lottie" -> {
            val lottieSrc = mediaUrl ?: ""
            val compositionSpec = if (lottieSrc.trimStart().startsWith("{") || lottieSrc.trimStart().startsWith("[")) {
                LottieCompositionSpec.JsonString(lottieSrc)
            } else {
                LottieCompositionSpec.Url(lottieSrc)
            }
            val composition by rememberLottieComposition(compositionSpec)
            LottieAnimation(
                composition = composition,
                iterations = LottieConstants.IterateForever,
                modifier = modifier
            )
        }

        "video" -> {
            VideoPlayerInline(
                videoUrl = mediaUrl ?: "",
                modifier = modifier.then(
                    if (contentScale == UiContentScale.FillWidth) {
                        Modifier.aspectRatio(16f / 9f) // 16:9 landscape
                    } else {
                        Modifier
                    }
                ),
                muted = muted
            )
        }

        else -> {
            AsyncImage(
                model = ImageRequest.Builder(context).data(mediaUrl).diskCachePolicy(CachePolicy.ENABLED).memoryCachePolicy(CachePolicy.ENABLED).build(),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale
            )
        }
    }
}

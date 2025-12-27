package com.appversal.appstorys.ui.modals

import android.os.Build.VERSION.SDK_INT
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale as UiContentScale

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
//@OptIn(UnstableApi::class)
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
    contentScale: ContentScale = UiContentScale.Crop,
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
                contentScale = UiContentScale.Crop,
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
            VideoPlayerInline(videoUrl = mediaUrl ?: "", modifier = modifier, muted = muted)
        }

        else -> {
            AsyncImage(
                model = ImageRequest.Builder(context).data(mediaUrl).diskCachePolicy(CachePolicy.ENABLED).memoryCachePolicy(CachePolicy.ENABLED).build(),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = UiContentScale.Crop
            )
        }
    }
}

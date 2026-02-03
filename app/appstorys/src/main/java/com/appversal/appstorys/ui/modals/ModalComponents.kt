package com.appversal.appstorys.ui.modals

import android.os.Build.VERSION.SDK_INT
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    data class Success(
        val intrinsicWidth: Int? = null,
        val intrinsicHeight: Int? = null
    ) : MediaLoadState()
    object Error : MediaLoadState()
}

/**
 * Computes aspect ratio from MediaLoadState if dimensions are available
 */
fun MediaLoadState.getAspectRatio(): Float? {
    return when (this) {
        is MediaLoadState.Success -> {
            if (intrinsicWidth != null && intrinsicHeight != null && intrinsicHeight > 0) {
                intrinsicWidth.toFloat() / intrinsicHeight.toFloat()
            } else null
        }
        else -> null
    }
}

/**
 * Preloads media and returns the current loading state WITH dimensions.
 * This ensures we know the media's intrinsic size BEFORE rendering,
 * preventing layout shifts where the modal structure appears before content.
 *
 * For images/gifs: Loads the drawable and extracts intrinsic dimensions
 * For videos: Uses MediaMetadataRetriever to get video dimensions before playing
 * For Lottie: Uses composition bounds for dimensions
 */
@Composable
fun rememberMediaLoadState(mediaUrl: String?): MediaLoadState {
    val context = LocalContext.current
    val mediaType = determineMediaType(mediaUrl)

    var loadState by remember(mediaUrl) { mutableStateOf<MediaLoadState>(MediaLoadState.Loading) }

    when (mediaType) {
        "video" -> {
            // For video, we need to get the actual dimensions BEFORE showing the modal
            // Use MediaMetadataRetriever to extract video dimensions
            LaunchedEffect(mediaUrl) {
                if (mediaUrl.isNullOrEmpty()) {
                    loadState = MediaLoadState.Error
                    return@LaunchedEffect
                }

                try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val retriever = android.media.MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(mediaUrl, HashMap())
                            val width = retriever.extractMetadata(
                                android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                            )?.toIntOrNull()
                            val height = retriever.extractMetadata(
                                android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                            )?.toIntOrNull()

                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                loadState = if (width != null && height != null && width > 0 && height > 0) {
                                    MediaLoadState.Success(intrinsicWidth = width, intrinsicHeight = height)
                                } else {
                                    // Fallback: allow rendering without dimensions
                                    MediaLoadState.Success()
                                }
                            }
                        } finally {
                            retriever.release()
                        }
                    }
                } catch (e: Exception) {
                    // Fallback on error - still show video, just without pre-known dimensions
                    loadState = MediaLoadState.Success()
                }
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
                loadState = if (composition != null) {
                    val bounds = composition!!.bounds
                    MediaLoadState.Success(
                        intrinsicWidth = bounds.width(),
                        intrinsicHeight = bounds.height()
                    )
                } else {
                    MediaLoadState.Loading
                }
            }
        }
        "gif", "image" -> {
            // Use LaunchedEffect to actually execute the image request and get dimensions
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
                    val drawable = result.drawable
                    loadState = if (drawable != null) {
                        MediaLoadState.Success(
                            intrinsicWidth = drawable.intrinsicWidth,
                            intrinsicHeight = drawable.intrinsicHeight
                        )
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
                loadState = if (mediaUrl.isNullOrEmpty()) MediaLoadState.Error else MediaLoadState.Success()
            }
        }
    }

    return loadState
}

/**
 * A Composable function for playing a video inline using ExoPlayer.
 *
 * This player is designed to handle video playback within a Compose UI, including features
 * like lifecycle management (pausing/resuming automatically), muting, video caching,
 * and applying custom corner rounding that works with video surfaces.
 *
 * It uses a `TextureView` wrapped in an `AndroidView` to ensure that UI transformations
 * like clipping to a shape are correctly applied, which is not always possible with the
 * more performant but window-based `SurfaceView`. The video's aspect ratio is detected
 * dynamically and applied to the layout to prevent letterboxing or stretching.
 *
 * @param videoUrl The URL of the video to be played.
 * @param modifier The modifier to be applied to the video player container. This modifier
 *                 is used as a base, and the video's own aspect ratio will be applied on top of it.
 * @param muted A boolean flag to control whether the video should be played with sound.
 *              Defaults to `false`.
 * @param cornerShape An optional `Shape` to apply rounded corners to the video player.
 *                    It's specifically designed to work with `RoundedCornerShape` by translating
 *                    Compose corner radii into native Android view clipping. If null, the video will be rectangular.
 * @param preloadedAspectRatio If provided, this aspect ratio will be used immediately to size
 *                             the video container, preventing layout shifts while ExoPlayer loads.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerInline(
    videoUrl: String,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    cornerShape: androidx.compose.ui.graphics.Shape? = null,
    preloadedAspectRatio: Float? = null
) {
    VideoPlayerInlineWithCallback(
        videoUrl = videoUrl,
        modifier = modifier,
        muted = muted,
        cornerShape = cornerShape,
        preloadedAspectRatio = preloadedAspectRatio,
        onVideoRendered = null
    )
}

/**
 * Video player with callback that fires when the first video frame is actually rendered.
 * This prevents the cross button from appearing before the video is visible.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerInlineWithCallback(
    videoUrl: String,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    cornerShape: androidx.compose.ui.graphics.Shape? = null,
    preloadedAspectRatio: Float? = null,
    onVideoRendered: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Track the video's aspect ratio dynamically
    var videoAspectRatio by remember { mutableStateOf<Float?>(null) }

    // Extract corner radius values from the shape if it's a RoundedCornerShape
    // We need this to apply native Android clipping
    val density = androidx.compose.ui.platform.LocalDensity.current

    // Data class to hold all four corner radii in pixels
    data class CornerRadii(
        val topLeft: Float,
        val topRight: Float,
        val bottomLeft: Float,
        val bottomRight: Float
    )

    val cornerRadii = remember(cornerShape, density) {
        when (cornerShape) {
            is androidx.compose.foundation.shape.RoundedCornerShape -> {
                val size = androidx.compose.ui.geometry.Size(1000f, 1000f) // Arbitrary size for calculation
                CornerRadii(
                    topLeft = cornerShape.topStart.toPx(shapeSize = size, density = density),
                    topRight = cornerShape.topEnd.toPx(shapeSize = size, density = density),
                    bottomLeft = cornerShape.bottomStart.toPx(shapeSize = size, density = density),
                    bottomRight = cornerShape.bottomEnd.toPx(shapeSize = size, density = density)
                )
            }
            else -> CornerRadii(0f, 0f, 0f, 0f)
        }
    }

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

    // Listen for video size changes to get the actual aspect ratio
    // Also listen for first frame rendered to trigger the callback
    DisposableEffect(exo, onVideoRendered) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspectRatio = videoSize.width.toFloat() / videoSize.height.toFloat()
                }
            }

            override fun onRenderedFirstFrame() {
                // Video has actually rendered its first frame - now safe to show UI
                onVideoRendered?.invoke()
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
        }
    }

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

    // Apply the detected aspect ratio to the modifier
    // Prioritize preloaded aspect ratio (from MediaMetadataRetriever) to prevent layout shifts,
    // then fall back to dynamically detected ratio from ExoPlayer
    val effectiveAspectRatio = preloadedAspectRatio ?: videoAspectRatio
    val aspectModifier = effectiveAspectRatio?.let { ratio ->
        modifier.aspectRatio(ratio)
    } ?: modifier

    Box(modifier = aspectModifier) {
        AndroidView(
            factory = { ctx ->
                // Create a custom clipping container with TextureView for video
                // TextureView (unlike SurfaceView) participates in the normal view hierarchy
                // and respects canvas clipping operations
                object : android.widget.FrameLayout(ctx) {
                    private val path = android.graphics.Path()
                    private val radiiArray = floatArrayOf(
                        cornerRadii.topLeft, cornerRadii.topLeft,
                        cornerRadii.topRight, cornerRadii.topRight,
                        cornerRadii.bottomRight, cornerRadii.bottomRight,
                        cornerRadii.bottomLeft, cornerRadii.bottomLeft
                    )
                    private var textureView: android.view.TextureView? = null

                    init {
                        // Create TextureView for video rendering
                        textureView = android.view.TextureView(ctx).apply {
                            layoutParams = android.widget.FrameLayout.LayoutParams(
                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                            )
                        }
                        addView(textureView)

                        // Set the TextureView as the video output for ExoPlayer
                        exo.setVideoTextureView(textureView)

                        // Set outline provider for clipping
                        if (cornerRadii.topLeft > 0 || cornerRadii.topRight > 0 ||
                            cornerRadii.bottomLeft > 0 || cornerRadii.bottomRight > 0) {
                            outlineProvider = object : android.view.ViewOutlineProvider() {
                                override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                                    path.reset()
                                    path.addRoundRect(
                                        0f, 0f, view.width.toFloat(), view.height.toFloat(),
                                        radiiArray,
                                        android.graphics.Path.Direction.CW
                                    )
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                        outline.setPath(path)
                                    } else {
                                        // For older API, use setRoundRect with average radius
                                        val avgRadius = (cornerRadii.topLeft + cornerRadii.topRight +
                                                        cornerRadii.bottomLeft + cornerRadii.bottomRight) / 4f
                                        outline.setRoundRect(0, 0, view.width, view.height, avgRadius)
                                    }
                                }
                            }
                            clipToOutline = true
                        }
                    }

                    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                        super.onSizeChanged(w, h, oldw, oldh)
                        // Update path when size changes
                        path.reset()
                        if (cornerRadii.topLeft > 0 || cornerRadii.topRight > 0 ||
                            cornerRadii.bottomLeft > 0 || cornerRadii.bottomRight > 0) {
                            path.addRoundRect(
                                0f, 0f, w.toFloat(), h.toFloat(),
                                radiiArray,
                                android.graphics.Path.Direction.CW
                            )
                        }
                        // Invalidate outline when size changes
                        invalidateOutline()
                    }
                }.apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Media renderer with callback that fires when media has actually been rendered.
 * This ensures the cross button only appears after media is in place, preventing
 * the "jump" visual glitch.
 *
 * @param preloadedAspectRatio If provided from rememberMediaLoadState, this aspect ratio
 *        will be used to pre-size the container, preventing layout shifts.
 */
@Composable
fun ModalMediaRendererWithCallback(
    mediaUrl: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: UiContentScale = UiContentScale.Fit,
    muted: Boolean = false,
    cornerShape: androidx.compose.ui.graphics.Shape? = null,
    preloadedAspectRatio: Float? = null,
    onMediaRendered: () -> Unit
) {
    val context = LocalContext.current
    val mediaType = determineMediaType(mediaUrl)

    // If we have a preloaded aspect ratio, apply it to the modifier to prevent layout shifts
    // This ensures the container is correctly sized BEFORE the media renders
    val aspectModifier = if (preloadedAspectRatio != null && preloadedAspectRatio > 0) {
        modifier.aspectRatio(preloadedAspectRatio)
    } else {
        modifier
    }

    when (mediaType) {
        "gif" -> {
            val imageLoader = ImageLoader.Builder(context)
                .components {
                    if (SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
                }
                .build()

            val painter = rememberAsyncImagePainter(
                ImageRequest.Builder(context).data(mediaUrl).diskCachePolicy(CachePolicy.ENABLED).memoryCachePolicy(CachePolicy.ENABLED).build(),
                imageLoader = imageLoader,
                onState = { state ->
                    if (state is coil.compose.AsyncImagePainter.State.Success) {
                        onMediaRendered()
                    }
                }
            )

            Image(
                painter = painter,
                contentDescription = contentDescription,
                contentScale = if (preloadedAspectRatio != null) UiContentScale.Crop else contentScale,
                modifier = aspectModifier
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

            // Trigger callback when composition is loaded
            LaunchedEffect(composition) {
                if (composition != null) {
                    onMediaRendered()
                }
            }

            // Get screen configuration for dynamic height constraints
            val configuration = LocalConfiguration.current
            val screenHeightDp = configuration.screenHeightDp

            // Maximum height: 50% of screen height to keep modal proportionate
            val maxHeightDp = (screenHeightDp * 0.5f).dp

            // Use preloaded aspect ratio if available, otherwise calculate from composition
            val lottieAspectRatio = preloadedAspectRatio ?: composition?.bounds?.let { bounds ->
                if (bounds.height() > 0) {
                    bounds.width().toFloat() / bounds.height().toFloat()
                } else null
            }

            // Apply dynamic sizing:
            // - If Lottie is tall (aspect ratio < 1), constrain by max height and use aspectRatio
            // - If Lottie is wide or square (aspect ratio >= 1), let it fill width naturally
            val lottieModifier = when {
                lottieAspectRatio != null && lottieAspectRatio < 1f -> {
                    // Tall Lottie: constrain height, maintain aspect ratio
                    modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeightDp)
                        .aspectRatio(lottieAspectRatio, matchHeightConstraintsFirst = true)
                }
                lottieAspectRatio != null -> {
                    // Wide or square Lottie: fill width, aspect ratio determines height
                    modifier
                        .fillMaxWidth()
                        .aspectRatio(lottieAspectRatio)
                }
                else -> {
                    // Composition not loaded yet, use modifier with max height constraint
                    modifier.heightIn(max = maxHeightDp)
                }
            }

            LottieAnimation(
                composition = composition,
                iterations = LottieConstants.IterateForever,
                modifier = lottieModifier
            )
        }

        "video" -> {
            // Use preloaded aspect ratio to size the video container correctly from the start
            VideoPlayerInlineWithCallback(
                videoUrl = mediaUrl ?: "",
                modifier = aspectModifier,
                muted = muted,
                cornerShape = cornerShape,
                preloadedAspectRatio = preloadedAspectRatio,
                onVideoRendered = onMediaRendered
            )
        }

        else -> {
            // For regular images (PNG, JPG, WebP), use AsyncImage with onSuccess
            AsyncImage(
                model = ImageRequest.Builder(context).data(mediaUrl).diskCachePolicy(CachePolicy.ENABLED).memoryCachePolicy(CachePolicy.ENABLED).build(),
                contentDescription = contentDescription,
                modifier = aspectModifier,
                contentScale = if (preloadedAspectRatio != null) UiContentScale.Crop else contentScale,
                onSuccess = { onMediaRendered() }
            )
        }
    }
}

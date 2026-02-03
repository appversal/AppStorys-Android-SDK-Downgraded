package com.appversal.appstorys.ui.stories

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build.VERSION.SDK_INT
import android.util.Log
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.appversal.appstorys.AppStorys
import com.appversal.appstorys.AppStorys.trackEvents
import com.appversal.appstorys.api.StoriesDetails
import com.appversal.appstorys.api.StoryGroup
import com.appversal.appstorys.api.StoryGroupStyling
import com.appversal.appstorys.api.StorySlide
import com.appversal.appstorys.api.TextStyling
import com.appversal.appstorys.ui.common_components.CommonText
import com.appversal.appstorys.ui.common_components.ShareButton
import com.appversal.appstorys.ui.common_components.createShareButtonConfig
import com.appversal.appstorys.ui.common_components.SoundToggleButton
import com.appversal.appstorys.ui.common_components.createSoundToggleButtonConfig
import com.appversal.appstorys.ui.common_components.CrossButton
import com.appversal.appstorys.ui.common_components.createCrossButtonConfig
import com.appversal.appstorys.ui.common_components.CTAButton
import com.appversal.appstorys.ui.common_components.createCTAButtonConfig
import com.appversal.appstorys.utils.VideoCache
import com.appversal.appstorys.utils.isGifUrl
import com.appversal.appstorys.utils.isLottieUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray

private const val PROGRESS_UPDATE_INTERVAL_MS = 16L // ~60fps
private const val TAP_DURATION_THRESHOLD_MS = 200L
private const val VERTICAL_SWIPE_THRESHOLD = 30
private const val TOP_TAP_EXCLUSION_ZONE = 100

// ExoPlayer buffer configuration for smooth playback
private const val MIN_BUFFER_MS = 2000
private const val MAX_BUFFER_MS = 8000
private const val BUFFER_FOR_PLAYBACK_MS = 1000
private const val BUFFER_FOR_REBUFFER_MS = 2000

@Composable
internal fun StoryCircles(
    storyGroups: List<StoryGroup>,
    onStoryClick: (StoryGroup) -> Unit,
    viewedStories: List<String>
) {
    // Sort groups: unviewed first, then by order
    val sortedStoryGroups = remember(storyGroups, viewedStories) {
        storyGroups.sortedWith(
            compareByDescending<StoryGroup> { it.id !in viewedStories }
                .thenBy { it.order }
        )
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = sortedStoryGroups,
            key = { storyGroup -> storyGroup.id ?: storyGroup.hashCode() }
        ) { storyGroup ->
            if (storyGroup.thumbnail != null) {
                StoryItem(
                    isStoryGroupViewed = viewedStories.contains(storyGroup.id),
                    imageUrl = storyGroup.thumbnail,
                    username = storyGroup.name ?: "",
                    ringColor = remember(storyGroup.ringColor) {
                        try {
                            Color(android.graphics.Color.parseColor(storyGroup.ringColor))
                        } catch (_: Exception) {
                            Color.Gray
                        }
                    },
                    nameColor = storyGroup.nameColor ?: "#000000",
                    onClick = { onStoryClick(storyGroup) },
                    groupStyling = storyGroup.styling
                )
            }
        }
    }
}

@Composable
internal fun StoryItem(
    isStoryGroupViewed: Boolean,
    imageUrl: String,
    username: String,
    ringColor: Color,
    nameColor: String,
    onClick: () -> Unit,
    groupStyling: StoryGroupStyling?
) {
    // Get styling values with fallbacks
    val ringAndImageSpace = groupStyling?.ringAndImageSpace ?: 8
    val size = (groupStyling?.size ?: 70).dp
    val ringWidth = (groupStyling?.ringWidth ?: 2).dp

    // Get state-specific styling
    val currentState = if (isStoryGroupViewed) {
        groupStyling?.storyGroupViewed
    } else {
        groupStyling?.storyGroupNotViewed
    }

    // Determine final colors - use hardcoded greys when viewed (matching React Native)
    val finalRingColor = if (isStoryGroupViewed) {
        Color(0xFFCCCCCC) // #CCCCCC
    } else {
        try {
            currentState?.ringColor?.let { Color(android.graphics.Color.parseColor(it)) }
                ?: ringColor
        } catch (_: Exception) {
            ringColor
        }
    }

    // Font decoration
    val fontDecoration = currentState?.fontDecoration ?: emptyList()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(4.dp)
            .clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ),
        content = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(size + (ringWidth * 2) + 4.dp),
                content = {
                    // Ring/Border with corner radius
                    Box(
                        modifier = Modifier
                            .size(size + (ringWidth * 2))
                            .clip(
                                RoundedCornerShape(
                                    topStart = (groupStyling?.cornerRadius?.topLeft
                                        ?: 0).dp + (ringWidth * 2),
                                    topEnd = (groupStyling?.cornerRadius?.topRight
                                        ?: 0).dp + (ringWidth * 2),
                                    bottomStart = (groupStyling?.cornerRadius?.bottomLeft
                                        ?: 0).dp + (ringWidth * 2),
                                    bottomEnd = (groupStyling?.cornerRadius?.bottomRight
                                        ?: 0).dp + (ringWidth * 2)
                                )
                            )
                            .border(
                                width = ringWidth,
                                color = finalRingColor,
                                shape = RoundedCornerShape(
                                    topStart = (groupStyling?.cornerRadius?.topLeft
                                        ?: 0).dp + (ringWidth * 2),
                                    topEnd = (groupStyling?.cornerRadius?.topRight
                                        ?: 0).dp + (ringWidth * 2),
                                    bottomStart = (groupStyling?.cornerRadius?.bottomLeft
                                        ?: 0).dp + (ringWidth * 2),
                                    bottomEnd = (groupStyling?.cornerRadius?.bottomRight
                                        ?: 0).dp + (ringWidth * 2)
                                )
                            )
                    )

                    // Thumbnail with support for Lottie, GIF, and regular images (JPEG, PNG)
                    val thumbnailModifier = Modifier
                        .size(size - ringAndImageSpace.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = (groupStyling?.cornerRadius?.topLeft ?: 0).dp,
                                topEnd = (groupStyling?.cornerRadius?.topRight ?: 0).dp,
                                bottomStart = (groupStyling?.cornerRadius?.bottomLeft ?: 0).dp,
                                bottomEnd = (groupStyling?.cornerRadius?.bottomRight ?: 0).dp
                            )
                        )
                        .background(Color.Transparent)

                    val context = LocalContext.current

                    when {
                        // Lottie animation (.json or .lottie files)
                        isLottieUrl(imageUrl) -> {
                            val composition by rememberLottieComposition(
                                spec = LottieCompositionSpec.Url(imageUrl)
                            )
                            Box(
                                modifier = thumbnailModifier,
                                contentAlignment = Alignment.Center
                            ) {
                                LottieAnimation(
                                    composition = composition,
                                    iterations = LottieConstants.IterateForever,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Apply grey overlay for viewed stories
                                if (isStoryGroupViewed) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.White.copy(alpha = 0.4f))
                                    )
                                }
                            }
                        }

                        // GIF images
                        isGifUrl(imageUrl) -> {
                            val imageLoader = remember(context) {
                                ImageLoader.Builder(context)
                                    .components {
                                        if (SDK_INT >= 28) {
                                            add(ImageDecoderDecoder.Factory())
                                        } else {
                                            add(GifDecoder.Factory())
                                        }
                                    }
                                    .build()
                            }

                            val painter = rememberAsyncImagePainter(
                                ImageRequest.Builder(context)
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
                                modifier = thumbnailModifier,
                                contentScale = ContentScale.Crop,
                                alpha = if (isStoryGroupViewed) 0.6f else 1f
                            )
                        }

                        // Regular images (JPEG, PNG, etc.)
                        else -> {
                            Image(
                                painter = rememberAsyncImagePainter(imageUrl),
                                contentDescription = null,
                                modifier = thumbnailModifier,
                                contentScale = ContentScale.Crop,
                                alpha = if (isStoryGroupViewed) 0.6f else 1f
                            )
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(4.dp))

            CommonText(
                modifier = Modifier
                    .width(60.dp)
                    .align(Alignment.CenterHorizontally),
                text = username,
                maxLines = 2,
                lineHeight = (((currentState?.fontSize ?: groupStyling?.name?.size)
                    ?: 12) * 1.2).toFloat(),
                styling = TextStyling(
                    color = if (isStoryGroupViewed) {
                        "#CCCCCC"
                    } else {
                        currentState?.fontColor ?: nameColor
                    },
                    fontSize = currentState?.fontSize ?: groupStyling?.name?.size,
                    fontFamily = "",
                    fontDecoration = fontDecoration
                )
            )
        }
    )
}

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StoryScreenContent(
    storyGroup: StoryGroup,
    slides: List<StorySlide>,
    sheetState: androidx.compose.material3.SheetState,
    onDismiss: () -> Unit,
    onStoryGroupEnd: () -> Unit,
    onStoryGroupBack: () -> Unit,
    sendEvent: (Pair<StorySlide, String>) -> Unit,
    sendClickEvent: (Pair<StorySlide, String>) -> Unit,
    campaignId: String
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isHolding by remember { mutableStateOf(false) }
    var isMuted by rememberSaveable { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }

    // Transition state to prevent flicker when switching slides (especially video -> image)
    var isTransitioning by remember { mutableStateOf(false) }

    // Reset slide index when story group changes - use remember with storyGroup.id as key
    var currentSlideIndex by remember(storyGroup.id) { mutableIntStateOf(0) }
    val currentSlide = remember(currentSlideIndex, slides) {
        slides.getOrNull(currentSlideIndex) ?: slides.first()
    }

    // Reset progress when story group changes
    var progress by remember(storyGroup.id) { mutableFloatStateOf(0f) }

    // Track video ready state to prevent progress bar issues
    // Don't key by storyGroup.id - we reset these in LaunchedEffect when needed
    var isVideoReady by remember { mutableStateOf(false) }
    var videoDuration by remember { mutableStateOf(0L) }
    var isBuffering by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Use a set for O(1) lookup instead of list - reset on story group change
    val completedSlides = remember(storyGroup.id) { mutableSetOf<Int>() }

    val isImage = currentSlide.image != null
    // Use slideShowTime from styling if available, otherwise default to 5 seconds
    val storyDuration = if (isImage) (storyGroup.styling?.slideShowTime ?: 5) * 1000 else 0

    // Optimized ExoPlayer with LoadControl for smooth playback
    val loadControl = remember {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_REBUFFER_MS
            )
            .setTargetBufferBytes(C.LENGTH_UNSET)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    // Create a single player instance that persists across story group changes
    // We'll manually reset it when story group changes instead of recreating
    val player = remember(context) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(VideoCache.getFactory(context)))
            .setLoadControl(loadControl)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            }
    }

    // Properly stop and reset player when story group changes
    DisposableEffect(storyGroup.id) {
        onDispose {
            // Stop and clear when switching story groups
            player.stop()
            player.clearMediaItems()
        }
    }

    // Preload next slide images for smoother transitions
    LaunchedEffect(currentSlideIndex, slides) {
        val nextIndex = currentSlideIndex + 1
        if (nextIndex < slides.size) {
            val nextSlide = slides[nextIndex]
            nextSlide.image?.let { imageUrl ->
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .memoryCacheKey(imageUrl)
                    .diskCacheKey(imageUrl)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .build()
                coil.Coil.imageLoader(context).enqueue(request)
            }
        }
    }

    // Listen for player state changes
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        isVideoReady = true
                        isBuffering = false
                        videoDuration = player.duration.coerceAtLeast(1L)
                    }

                    Player.STATE_ENDED -> {
                        // Video ended naturally
                        isBuffering = false
                    }

                    Player.STATE_BUFFERING -> {
                        // Only show buffering if we haven't received first frame yet
                        // Once video is ready, don't show buffering indicator for rebuffering
                        if (!isVideoReady) {
                            isBuffering = true
                        }
                    }

                    Player.STATE_IDLE -> {
                        isVideoReady = false
                        isBuffering = false
                    }
                }
            }
        }
        player.addListener(listener)

        // Check current state immediately in case player is already ready
        when (player.playbackState) {
            Player.STATE_READY -> {
                isVideoReady = true
                isBuffering = false
                videoDuration = player.duration.coerceAtLeast(1L)
            }

            Player.STATE_BUFFERING -> {
                if (!isVideoReady) {
                    isBuffering = true
                }
            }
        }

        onDispose {
            player.removeListener(listener)
            // Release player only when the entire StoryScreenContent is disposed
            player.release()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> player.play()

                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> player.pause()

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Reset and initialize media when slide or story group changes
    LaunchedEffect(storyGroup.id, currentSlideIndex) {
        progress = 0f
        isVideoReady = false
        isBuffering = false
        videoDuration = 0L
        sendEvent(Pair(currentSlide, "IMP"))

        player.stop()
        player.clearMediaItems()

        if (!isImage && currentSlide.video != null) {
            isBuffering = true  // Set buffering true before prepare
            player.setMediaItem(MediaItem.fromUri(currentSlide.video.toUri()))
            player.prepare()
        }

        // Brief delay to allow content to settle, then hide transition overlay
        delay(50)
        isTransitioning = false
    }

    // Optimized progress tracking with better video duration handling
    LaunchedEffect(storyGroup.id, currentSlideIndex, isHolding, isDismissing, isVideoReady) {
        if (isHolding || isDismissing) {
            return@LaunchedEffect
        }

        when {
            isImage -> {
                // Calculate the effective start time based on current progress
                val elapsedDuration = (storyDuration * progress).toLong()
                val startTime = System.currentTimeMillis() - elapsedDuration

                while (progress < 1f) {
                    val elapsedTime = System.currentTimeMillis() - startTime
                    progress = (elapsedTime.toFloat() / storyDuration).coerceIn(0f, 1f)
                    delay(PROGRESS_UPDATE_INTERVAL_MS)
                }
            }

            currentSlide.video != null -> {
                // Wait for video to be ready before tracking progress
                if (!isVideoReady || videoDuration <= 0L) {
                    return@LaunchedEffect
                }

                while (progress < 1f && isVideoReady) {
                    val currentPos = player.currentPosition.coerceAtLeast(0L)
                    val duration = videoDuration.coerceAtLeast(1L)
                    progress = (currentPos.toFloat() / duration).coerceIn(0f, 1f)
                    delay(PROGRESS_UPDATE_INTERVAL_MS)
                }
            }
        }

        if (!completedSlides.contains(currentSlideIndex)) {
            completedSlides.add(currentSlideIndex)
        }

        // Stop player and show transition overlay before changing slide to prevent flicker
        player.stop()
        isTransitioning = true

        currentSlideIndex = when {
            currentSlideIndex < slides.lastIndex -> currentSlideIndex + 1
            else -> {
                onStoryGroupEnd()
                completedSlides.clear()
                0
            }
        }
    }

    LaunchedEffect(sheetState.targetValue) {
        isDismissing = sheetState.targetValue == SheetValue.Hidden
    }

    LaunchedEffect(isHolding, isDismissing) {
        when {
            isDismissing || isHolding -> player.pause()
            else -> player.play()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .pointerInput(storyGroup.id, slides.size, currentSlideIndex) {
                var startPosition: Offset? = null
                var startTime = 0L
                var hasMovedVertically = false
                var isCurrentlyHolding = false

                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull() ?: continue

                        when {
                            change.changedToDown() -> {
                                startPosition = change.position
                                startTime = System.currentTimeMillis()
                                hasMovedVertically = false
                                isCurrentlyHolding = true
                                isHolding = true
                                change.consume()
                            }

                            change.pressed && startPosition != null -> {
                                val currentPosition = change.position
                                val deltaY = kotlin.math.abs(currentPosition.y - startPosition!!.y)
                                val deltaX = kotlin.math.abs(currentPosition.x - startPosition!!.x)

                                // If there's significant vertical movement, it's likely a dismiss gesture
                                if (deltaY > VERTICAL_SWIPE_THRESHOLD && deltaY > deltaX) {
                                    hasMovedVertically = true
                                }
                            }

                            change.changedToUp() && isCurrentlyHolding -> {
                                isCurrentlyHolding = false
                                isHolding = false

                                val duration = System.currentTimeMillis() - startTime
                                val tapPosition = startPosition ?: change.position

                                // Only navigate if:
                                // 1. Quick tap (< TAP_DURATION_THRESHOLD_MS)
                                // 2. No vertical movement (not a swipe down)
                                // 3. Not in top area (progress bar region)
                                if (duration < TAP_DURATION_THRESHOLD_MS &&
                                    !hasMovedVertically &&
                                    tapPosition.y > TOP_TAP_EXCLUSION_ZONE
                                ) {

                                    val screenWidth = size.width
                                    val isLeftTap = tapPosition.x < screenWidth / 2
                                    val isRightTap = !isLeftTap
                                    val lastSlideIndex = slides.lastIndex

                                    when {
                                        // ⬅️ Left tap → previous slide
                                        isLeftTap && currentSlideIndex > 0 -> {
                                            completedSlides.remove(currentSlideIndex)
                                            // Stop player and show transition overlay immediately to prevent flicker
                                            player.stop()
                                            isTransitioning = true
                                            progress =
                                                0f  // Reset progress immediately before changing slide
                                            currentSlideIndex--
                                        }

                                        // ⬅️ Left tap on first slide → previous story group
                                        isLeftTap && currentSlideIndex == 0 -> {
                                            onStoryGroupBack()
                                        }

                                        // ➡️ Right tap → next slide
                                        isRightTap && currentSlideIndex < lastSlideIndex -> {
                                            completedSlides.add(currentSlideIndex)
                                            // Stop player and show transition overlay immediately to prevent flicker
                                            player.stop()
                                            isTransitioning = true
                                            progress =
                                                0f  // Reset progress immediately before changing slide
                                            currentSlideIndex++
                                        }

                                        // ➡️ Right tap on last slide → next story group
                                        isRightTap && currentSlideIndex == lastSlideIndex -> {
                                            onStoryGroupEnd()
                                        }
                                    }
                                }

                                startPosition = null
                                change.consume()
                            }
                        }
                    }
                }
            },
        content = {
            // Story Content with optimized image loading
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Image content with support for Lottie, GIF, and regular images
                if (currentSlide.image != null) {
                    val imageUrl = currentSlide.image

                    when {
                        // Lottie animation (.json or .lottie files)
                        isLottieUrl(imageUrl) -> {
                            val composition by rememberLottieComposition(
                                spec = LottieCompositionSpec.Url(imageUrl)
                            )
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                LottieAnimation(
                                    composition = composition,
                                    iterations = LottieConstants.IterateForever,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }

                        // GIF images
                        isGifUrl(imageUrl) -> {
                            val imageLoader = remember(context) {
                                ImageLoader.Builder(context)
                                    .components {
                                        if (SDK_INT >= 28) {
                                            add(ImageDecoderDecoder.Factory())
                                        } else {
                                            add(GifDecoder.Factory())
                                        }
                                    }
                                    .build()
                            }

                            val painter = rememberAsyncImagePainter(
                                ImageRequest.Builder(context)
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
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }

                        // Regular images (JPEG, PNG, etc.)
                        else -> {
                            val imageRequest = remember(imageUrl) {
                                ImageRequest.Builder(context)
                                    .data(imageUrl)
                                    .memoryCacheKey(imageUrl)
                                    .diskCacheKey(imageUrl)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .crossfade(true)
                                    .build()
                            }
                            Image(
                                painter = rememberAsyncImagePainter(imageRequest),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }

                // Video content
                if (currentSlide.video != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = player
                                layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                                useController = false
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Show loading indicator only while initially buffering
                    if (isBuffering) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }

                // CTA Button
                if (currentSlide.link?.isNotEmpty() == true && currentSlide.buttonText?.isNotEmpty() == true) {
                    val styling = currentSlide.styling
                    val ctaConfig = styling?.cta
                    val container = ctaConfig?.container
                    val cornerRadius = ctaConfig?.cornerRadius
                    val ctaMargin = ctaConfig?.margin ?: styling?.ctaMargins
                    val ctaText = ctaConfig?.text

                    val alignmentStr = container?.alignment ?: styling?.ctaAlignment
                    val alignment = when (alignmentStr?.lowercase()) {
                        "left" -> Alignment.BottomStart
                        "right" -> Alignment.BottomEnd
                        else -> Alignment.BottomCenter
                    }

                    val ctaButtonConfig = createCTAButtonConfig(
                        textColor = ctaText?.color ?: styling?.ctaText?.fontColor ?: "#FFFFFF",
                        textSize = ctaText?.fontSize ?: styling?.ctaText?.fontSize ?: 12,
                        fontFamily = ctaText?.fontFamily,
                        fontDecoration = ctaText?.fontDecoration,
                        marginTop = ctaMargin?.top ?: 12,
                        marginEnd = ctaMargin?.right ?: 12,
                        marginBottom = ctaMargin?.bottom ?: 12,
                        marginStart = ctaMargin?.left ?: 12,
                        height = container?.height ?: styling?.ctaHeight ?: 32,
                        width = container?.ctaWidth,
                        borderColorString = container?.borderColor
                            ?: styling?.ctaBackground?.borderColor,
                        borderWidth = container?.borderWidth ?: styling?.borderWidth ?: 2,
                        fullWidth = container?.ctaFullWidth ?: styling?.fullWidthCta ?: false,
                        backgroundColorString = container?.backgroundColor
                            ?: styling?.ctaBackground?.backgroundColor ?: "#FFFFFF",
                        alignment = alignmentStr ?: "center",
                        borderRadiusTopLeft = cornerRadius?.topLeft ?: 12,
                        borderRadiusTopRight = cornerRadius?.topRight ?: 12,
                        borderRadiusBottomLeft = cornerRadius?.bottomLeft ?: 12,
                        borderRadiusBottomRight = cornerRadius?.bottomRight ?: 12
                    )

                    Box(
                        modifier = Modifier
                            .align(alignment)
                            .navigationBarsPadding()
                    ) {
                        CTAButton(
                            text = currentSlide.buttonText ?: "",
                            config = ctaButtonConfig,
                            onClick = {
                                try {
                                    uriHandler.openUri(currentSlide.link)
                                } catch (e: Exception) {
                                    Log.e("StoryScreen", "Failed to open link: ${e.message}")
                                }
                                sendEvent(Pair(currentSlide, "CLK"))
                                sendClickEvent(Pair(currentSlide, "clicked"))
                            }
                        )
                    }
                }

                // Transition overlay to mask content swap and prevent flicker
                if (isTransitioning) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    )
                }
            }

            // Header overlay - Fixed at top with Column for proper layout
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
            ) {
                // Progress indicator row - Instagram-style with rounded corners
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        //.height(4.dp)
                        .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    content = {
                        slides.forEachIndexed { index, _ ->
                            LinearProgressIndicator(
                                progress = {
                                    when {
                                        index == currentSlideIndex -> progress
                                        index < currentSlideIndex || completedSlides.contains(index) -> 1f
                                        else -> 0f
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.3f),
                            )
                        }
                    }
                )

                // Header row with user info and action buttons
                // Use Alignment.Top so button margins don't push the entire row down
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left side: Thumbnail + Name - centered vertically within itself
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(
                            top = 18.dp,
                            start = 18.dp
                        ), // Small padding to align with default button position
                        content = {
                            // Thumbnail with support for Lottie, GIF, and regular images
                            val thumbnailUrl = storyGroup.thumbnail
                            val thumbnailModifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.LightGray)

                            when {
                                // Lottie animation (.json or .lottie files)
                                thumbnailUrl != null && isLottieUrl(thumbnailUrl) -> {
                                    val composition by rememberLottieComposition(
                                        spec = LottieCompositionSpec.Url(thumbnailUrl)
                                    )
                                    Box(
                                        modifier = thumbnailModifier,
                                        contentAlignment = Alignment.Center
                                    ) {
                                        LottieAnimation(
                                            composition = composition,
                                            iterations = LottieConstants.IterateForever,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                // GIF images
                                thumbnailUrl != null && isGifUrl(thumbnailUrl) -> {
                                    val imageLoader = remember(context) {
                                        ImageLoader.Builder(context)
                                            .components {
                                                if (SDK_INT >= 28) {
                                                    add(ImageDecoderDecoder.Factory())
                                                } else {
                                                    add(GifDecoder.Factory())
                                                }
                                            }
                                            .build()
                                    }

                                    val painter = rememberAsyncImagePainter(
                                        ImageRequest.Builder(context)
                                            .data(thumbnailUrl)
                                            .memoryCacheKey(thumbnailUrl)
                                            .diskCacheKey(thumbnailUrl)
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
                                        modifier = thumbnailModifier,
                                        contentScale = ContentScale.Crop
                                    )
                                }

                                // Regular images (JPEG, PNG, etc.)
                                else -> {
                                    Image(
                                        painter = rememberAsyncImagePainter(thumbnailUrl),
                                        contentDescription = null,
                                        modifier = thumbnailModifier,
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            storyGroup.name?.let {
                                CommonText(
                                    text = it,
                                    styling = TextStyling(
                                        color = "#FFFFFF",
                                        fontSize = 15,
                                        fontDecoration = listOf("medium"),
                                        fontFamily = "",
                                    )
                                )
                            }
                        }
                    )

                    // Right side: Action buttons (mute, share, close)
                    // Align to top so each button's marginTop works independently
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(0.dp, Alignment.End),
                        verticalAlignment = Alignment.Top,
                        content = {
                            if (!isImage) {
                                // NEW IMPLEMENTATION: Using common SoundToggleButton component
                                val soundToggle = storyGroup.styling?.soundToggle
                                val isSoundToggleEnabled = soundToggle?.enabled ?: true

                                if (isSoundToggleEnabled) {
                                    val muteSettings = soundToggle?.mute
                                    val unmuteSettings = soundToggle?.unmute

                                    // Support both "color" and "colors" fields
                                    val muteColors = muteSettings?.color ?: muteSettings?.colors
                                    val unmuteColors =
                                        unmuteSettings?.color ?: unmuteSettings?.colors

                                    val muteButtonConfig = createSoundToggleButtonConfig(
                                        fillColorString = muteColors?.fill,
                                        iconColorString = muteColors?.cross,
                                        strokeColorString = muteColors?.stroke,
                                        marginTop = muteSettings?.margin?.top,
                                        marginEnd = muteSettings?.margin?.right,
                                        size = muteSettings?.size ?: 32,
                                        imageUrl = muteSettings?.image
                                    )

                                    val unmuteButtonConfig = createSoundToggleButtonConfig(
                                        fillColorString = unmuteColors?.fill,
                                        iconColorString = unmuteColors?.cross,
                                        strokeColorString = unmuteColors?.stroke,
                                        marginTop = unmuteSettings?.margin?.top,
                                        marginEnd = unmuteSettings?.margin?.right,
                                        size = unmuteSettings?.size ?: 32,
                                        imageUrl = unmuteSettings?.image
                                    )

                                    SoundToggleButton(
                                        muteConfig = muteButtonConfig,
                                        unmuteConfig = unmuteButtonConfig,
                                        isMuted = isMuted,
                                        onToggle = {
                                            isMuted = !isMuted
                                            if (isMuted) {
                                                player.volume = 0f
                                            } else {
                                                player.volume = 1f
                                            }
                                        }
                                    )
                                }
                            }

                            if (currentSlide.link?.isNotEmpty() == true && currentSlide.buttonText?.isNotEmpty() == true) {
                                // NEW IMPLEMENTATION: Using common ShareButton component
                                // Get share button styling from storyGroup
                                val shareConfig = storyGroup.styling?.share
                                val isShareEnabled = shareConfig?.enabled ?: true

                                if (isShareEnabled) {
                                    // Support both "color" and "colors" fields
                                    val shareColors = shareConfig?.color ?: shareConfig?.colors

                                    val shareButtonConfig = createShareButtonConfig(
                                        fillColorString = shareColors?.fill,
                                        iconColorString = shareColors?.cross,
                                        strokeColorString = shareColors?.stroke,
                                        marginTop = shareConfig?.margin?.top,
                                        marginEnd = shareConfig?.margin?.right,
                                        size = shareConfig?.size ?: 32,
                                        imageUrl = shareConfig?.image
                                    )

                                    ShareButton(
                                        config = shareButtonConfig,
                                        onShare = {
                                            context.startActivity(
                                                Intent.createChooser(
                                                    Intent().apply {
                                                        action = Intent.ACTION_SEND
                                                        putExtra(
                                                            Intent.EXTRA_TEXT,
                                                            "Check out this story: ${currentSlide.link}"
                                                        )
                                                        type = "text/plain"
                                                    },
                                                    "Share via"
                                                )
                                            )
                                            trackEvents(campaignId, "shared", mapOf("story_slide" to (currentSlide.id ?: "")))
                                        }
                                    )
                                }
                            }

                            // NEW IMPLEMENTATION: Using common CrossButton component
                            val closeConfig = storyGroup.styling?.crossButton
                            val isCrossEnabled = closeConfig?.enabled ?: true

                            if (isCrossEnabled) {
                                // Support both "color" and "colors" fields
                                val closeColors = closeConfig?.color ?: closeConfig?.colors

                                val crossButtonConfig = createCrossButtonConfig(
                                    fillColorString = closeColors?.fill,
                                    crossColorString = closeColors?.cross,
                                    strokeColorString = closeColors?.stroke,
                                    marginTop = closeConfig?.margin?.top,
                                    marginEnd = closeConfig?.margin?.right,
                                    size = closeConfig?.size ?: 32,
                                    imageUrl = closeConfig?.image
                                )

                                CrossButton(
                                    config = crossButtonConfig,
                                    onClose = {
                                        scope.launch {
                                            sheetState.hide()
                                            onDismiss()
                                        }
                                    }
                                )
                            }
                        }
                    )
                }
            }
        }
    )
}

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StoryScreenWrapper(
    storyGroups: List<StoryGroup>,
    initialStoryGroup: StoryGroup,
    onDismiss: () -> Unit,
    onStoryGroupChange: (StoryGroup?) -> Unit,
    sendEvent: (Pair<StorySlide, String>) -> Unit,
    sendClickEvent: (Pair<StorySlide, String>) -> Unit,
    campaignId: String
) {
    // Track current story group internally for smooth transitions
    var currentStoryGroup by remember { mutableStateOf(initialStoryGroup) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // Update internal state when external changes (e.g., from StoriesApp)
    LaunchedEffect(initialStoryGroup.id) {
        currentStoryGroup = initialStoryGroup
    }

    DisposableEffect(Unit) {
        AppStorys.isVisible = false
        onDispose {
            AppStorys.isVisible = true
        }
    }

    ModalBottomSheet(
        modifier = Modifier.fillMaxSize(),
        shape = RectangleShape,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Black,
        contentColor = Color.White,
        dragHandle = null,
        content = {
            // Use AnimatedContent for smooth transitions between story groups
            AnimatedContent(
                targetState = currentStoryGroup,
                transitionSpec = {
                    fadeIn(animationSpec = tween(150)) togetherWith
                            fadeOut(animationSpec = tween(150))
                },
                label = "StoryGroupTransition"
            ) { storyGroup ->
                if (!storyGroup.slides.isNullOrEmpty()) {
                    StoryScreenContent(
                        storyGroup = storyGroup,
                        slides = storyGroup.slides,
                        sheetState = sheetState,
                        onDismiss = {
                            scope.launch {
                                sheetState.hide()
                                onDismiss()
                            }
                        },
                        onStoryGroupEnd = {
                            // Use currentStoryGroup instead of storyGroup from AnimatedContent
                            // to avoid stale reference issues during animation
                            val currentIndex =
                                storyGroups.indexOfFirst { it.id == currentStoryGroup.id }
                            Log.d(
                                "StoryScreenWrapper",
                                "onStoryGroupEnd called - currentIndex: $currentIndex, storyGroups.size: ${storyGroups.size}, storyGroups.lastIndex: ${storyGroups.lastIndex}, currentStoryGroup.id: ${currentStoryGroup.id}"
                            )
                            Log.d(
                                "StoryScreenWrapper",
                                "storyGroups IDs: ${storyGroups.map { it.id }}"
                            )
                            if (currentIndex >= 0 && currentIndex < storyGroups.lastIndex) {
                                val nextGroup = storyGroups[currentIndex + 1]
                                Log.d(
                                    "StoryScreenWrapper",
                                    "Navigating to next group: ${nextGroup.id}"
                                )
                                currentStoryGroup = nextGroup
                                onStoryGroupChange(nextGroup)
                            } else {
                                Log.d(
                                    "StoryScreenWrapper",
                                    "No more groups, dismissing. currentIndex: $currentIndex, lastIndex: ${storyGroups.lastIndex}"
                                )
                                scope.launch {
                                    sheetState.hide()
                                    onDismiss()
                                }
                            }
                        },
                        onStoryGroupBack = {
                            // Use currentStoryGroup instead of storyGroup from AnimatedContent
                            val currentIndex =
                                storyGroups.indexOfFirst { it.id == currentStoryGroup.id }
                            if (currentIndex > 0) {
                                val prevGroup = storyGroups[currentIndex - 1]
                                currentStoryGroup = prevGroup
                                onStoryGroupChange(prevGroup)
                            }
                            // If at first story group, do nothing (stay on current story)
                        },
                        sendEvent = sendEvent,
                        sendClickEvent = sendClickEvent,
                        campaignId = campaignId
                    )
                }
            }
        }
    )
}

@UnstableApi
@Composable
internal fun StoriesApp(
    storiesDetails: StoriesDetails,
    sendEvent: (Pair<StorySlide, String>) -> Unit,
    viewedStories: List<String>,
    storyViewed: (String) -> Unit,
    sendClickEvent: (Pair<StorySlide, String>) -> Unit,
    campaignId: String
) {
    var selectedStoryGroup by remember { mutableStateOf<StoryGroup?>(null) }
    val storyGroups = storiesDetails.groups ?: emptyList()

    // Capture the story groups list when opening a story viewer
    // This prevents re-sorting from affecting navigation while viewing
    var activeStoryGroups by remember { mutableStateOf<List<StoryGroup>>(emptyList()) }

    // Track the initial story group that was clicked
    var initialClickedGroup by remember { mutableStateOf<StoryGroup?>(null) }

    Box(
        modifier = Modifier.fillMaxSize(),
        content = {
            StoryCircles(
                viewedStories = viewedStories,
                storyGroups = storyGroups,
                onStoryClick = { storyGroup ->
                    // Capture current order of groups when opening
                    activeStoryGroups = storyGroups.toList()
                    initialClickedGroup = storyGroup
                    selectedStoryGroup = storyGroup
                }
            )

            val storyGroup = selectedStoryGroup
            val capturedGroups = activeStoryGroups
            if (storyGroup != null && !storyGroup.slides.isNullOrEmpty() && capturedGroups.isNotEmpty()) {
                // Use key to ensure StoryScreenWrapper gets the correct list
                key(initialClickedGroup?.id) {
                    StoryScreenWrapper(
                        // Use the captured list for stable navigation
                        storyGroups = capturedGroups,
                        initialStoryGroup = storyGroup,
                        onDismiss = {
                            selectedStoryGroup = null
                            activeStoryGroups = emptyList()
                            initialClickedGroup = null
                        },
                        onStoryGroupChange = { newGroup ->
                            selectedStoryGroup = newGroup
                            // Mark the new story group as viewed
                            newGroup?.id?.let { storyViewed(it) }
                        },
                        sendEvent = sendEvent,
                        sendClickEvent = sendClickEvent,
                        campaignId = campaignId
                    )
                }
            }
        }
    )
}

@UnstableApi
@Composable
internal fun StoryAppMain(
    apiStoriesDetails: StoriesDetails,
    sendEvent: (Pair<StorySlide, String>) -> Unit,
    sendClickEvent: (Pair<StorySlide, String>) -> Unit,
    campaignId: String
) {
    val context = LocalContext.current
    val storyGroups = apiStoriesDetails.groups ?: emptyList()

    // Load viewed slides synchronously to prevent flicker on app reopen
    // Using remember with context ensures it loads immediately on first composition
    var viewedSlides by remember {
        mutableStateOf(
            getViewedSlides(
                context.getSharedPreferences(
                    "AppStory",
                    Context.MODE_PRIVATE
                )
            )
        )
    }


    // Determine which groups are fully viewed (all slides viewed)
    val viewedGroups = remember(viewedSlides, storyGroups) {
        storyGroups.filter { group ->
            val slideIds = group.slides?.mapNotNull { it.id } ?: emptyList()
            slideIds.isNotEmpty() && slideIds.all { it in viewedSlides }
        }.mapNotNull { it.id }
    }

    val sortedGroups = remember(storyGroups, viewedGroups) {
        storyGroups.sortedWith(
            compareByDescending<StoryGroup> { it.id !in viewedGroups }
                .thenBy { it.order }
        )
    }


    // Create a new StoriesDetails with sorted groups
    val sortedStoriesDetails = StoriesDetails(
        groups = sortedGroups
    )

    StoriesApp(
        storiesDetails = sortedStoriesDetails,
        sendEvent = { pair ->
            sendEvent(pair)
            // Mark slide as viewed
            val slideId = pair.first.id
            if (slideId != null && !viewedSlides.contains(slideId)) {
                val updatedSlides = ArrayList(viewedSlides)
                updatedSlides.add(slideId)
                viewedSlides = updatedSlides
                saveViewedSlides(
                    slideIds = updatedSlides,
                    sharedPreferences = context.getSharedPreferences(
                        "AppStory",
                        Context.MODE_PRIVATE
                    )
                )
            }
        },
        viewedStories = viewedGroups,
        storyViewed = { /* No-op, tracking at slide level now */ },
        sendClickEvent = sendClickEvent,
        campaignId = campaignId
    )
}

internal fun saveViewedStories(idList: List<String>, sharedPreferences: SharedPreferences) {
    val jsonArray = JSONArray(idList)
    sharedPreferences.edit { putString("VIEWED_STORIES", jsonArray.toString()) }
}

internal fun getViewedStories(sharedPreferences: SharedPreferences): List<String> {
    val jsonString = sharedPreferences.getString("VIEWED_STORIES", "[]") ?: "[]"
    val jsonArray = JSONArray(jsonString)
    return List(jsonArray.length()) { jsonArray.getString(it) }
}

// New functions for slide-level tracking
internal fun saveViewedSlides(slideIds: List<String>, sharedPreferences: SharedPreferences) {
    val jsonArray = JSONArray(slideIds)
    sharedPreferences.edit { putString("VIEWED_STORY_SLIDES", jsonArray.toString()) }
}

internal fun getViewedSlides(sharedPreferences: SharedPreferences): List<String> {
    val jsonString = sharedPreferences.getString("VIEWED_STORY_SLIDES", "[]") ?: "[]"
    val jsonArray = JSONArray(jsonString)
    return List(jsonArray.length()) { jsonArray.getString(it) }
}
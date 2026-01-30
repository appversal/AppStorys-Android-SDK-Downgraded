package com.appversal.appstorys.ui.stories

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build.VERSION.SDK_INT
import android.util.Log
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
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
import androidx.compose.runtime.key

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
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
import kotlin.math.abs

@Composable
internal fun StoryCircles(
    storyGroups: List<StoryGroup>,
    onStoryClick: (StoryGroup) -> Unit,
    viewedStories: List<String>
) {

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
        items(sortedStoryGroups.size) { index ->
            val storyGroup = sortedStoryGroups[index]
            if (storyGroup.thumbnail != null) {
                StoryItem(
                    isStoryGroupViewed = viewedStories.contains(storyGroup.id),
                    imageUrl = storyGroup.thumbnail,
                    username = storyGroup.name ?: "",
                    ringColor = Color(android.graphics.Color.parseColor(storyGroup.ringColor)),
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
            currentState?.ringColor?.let { Color(android.graphics.Color.parseColor(it)) } ?: ringColor
        } catch (e: Exception) {
            ringColor
        }
    }

    val fontSize = (currentState?.fontSize ?: groupStyling?.name?.size ?: 12).sp

    // Font decoration
    val fontDecoration = currentState?.fontDecoration ?: emptyList()
    val fontWeight = if (fontDecoration.contains("bold")) FontWeight.Bold else FontWeight.Normal
    val fontStyle = if (fontDecoration.contains("italic")) FontStyle.Italic else FontStyle.Normal

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
                            .clip(RoundedCornerShape(
                                topStart = (groupStyling?.cornerRadius?.topLeft ?: 0).dp + (ringWidth * 2),
                                topEnd = (groupStyling?.cornerRadius?.topRight ?: 0).dp + (ringWidth * 2),
                                bottomStart = (groupStyling?.cornerRadius?.bottomLeft ?: 0).dp + (ringWidth * 2),
                                bottomEnd = (groupStyling?.cornerRadius?.bottomRight ?: 0).dp + (ringWidth * 2)
                            ))
                            .border(
                                width = ringWidth,
                                color = finalRingColor,
                                shape = RoundedCornerShape(
                                    topStart = (groupStyling?.cornerRadius?.topLeft ?: 0).dp + (ringWidth * 2),
                                    topEnd = (groupStyling?.cornerRadius?.topRight ?: 0).dp + (ringWidth * 2),
                                    bottomStart = (groupStyling?.cornerRadius?.bottomLeft ?: 0).dp + (ringWidth * 2),
                                    bottomEnd = (groupStyling?.cornerRadius?.bottomRight ?: 0).dp + (ringWidth * 2)
                                )
                            )
                    )

                    // Thumbnail with support for Lottie, GIF, and regular images (JPEG, PNG)
                    val thumbnailModifier = Modifier
                        .size(size - ringAndImageSpace.dp)
                        .clip(RoundedCornerShape(
                            topStart = (groupStyling?.cornerRadius?.topLeft ?: 0).dp,
                            topEnd = (groupStyling?.cornerRadius?.topRight ?: 0).dp,
                            bottomStart = (groupStyling?.cornerRadius?.bottomLeft ?: 0).dp,
                            bottomEnd = (groupStyling?.cornerRadius?.bottomRight ?: 0).dp
                        ))
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
                lineHeight = (((currentState?.fontSize ?: groupStyling?.name?.size) ?: 12) * 1.2).toFloat(),
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
internal fun StoryScreen(
    storyGroup: StoryGroup,
    onDismiss: () -> Unit,
    slides: List<StorySlide>,
    onStoryGroupEnd: () -> Unit,
    onStoryGroupBack: () -> Unit, // 🔴 NAVIGATION PATCH
    sendEvent: (Pair<StorySlide, String>) -> Unit,
    sendClickEvent: (Pair<StorySlide, String>) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current


    var isHolding by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }
    var currentSlideIndex by remember(storyGroup, slides) { mutableIntStateOf(0) }
    val currentSlide = slides[currentSlideIndex]
    var progress by remember(currentSlideIndex) { mutableFloatStateOf(0f) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val completedSlides = remember { mutableStateListOf<Int>() }

    val isImage = currentSlide.image != null
    // Use slideShowTime from styling if available, otherwise default to 5 seconds
    val storyDuration = if (isImage) (storyGroup.styling?.slideShowTime ?: 5) * 1000 else 0

    val player = remember(context) {
        ExoPlayer
            .Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(VideoCache.getFactory(context)))
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true
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
            player.release()
        }
    }

    LaunchedEffect(currentSlideIndex) {
        progress = 0f
        sendEvent(Pair(currentSlide, "IMP"))

        player.stop()
        player.clearMediaItems()

        if (!isImage && currentSlide.video != null) {
            player.setMediaItem(MediaItem.fromUri(currentSlide.video.toUri()))
            player.prepare()
        }
    }

    LaunchedEffect(currentSlideIndex, isHolding, isDismissing) {
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
                    delay(16)
                }
            }

            currentSlide.video != null -> {
                while (progress < 1) {
                    progress = (player.currentPosition.toFloat() / player.duration).coerceIn(0f, 1f)
                    delay(16)
                }
            }
        }

        if (!completedSlides.contains(currentSlideIndex)) {
            completedSlides.add(currentSlideIndex)
        }

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

    DisposableEffect(Unit) {
        AppStorys.isVisible = false

        onDispose {
            AppStorys.isVisible = true
        }
    }

    ModalBottomSheet(
        modifier = Modifier
            .fillMaxSize(),
        shape = RectangleShape,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Black,
        contentColor = Color.White,
        dragHandle = null,
        content = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .pointerInput(storyGroup.id, slides, currentSlideIndex) {
                        var startPosition: Offset? = null
                        var startTime = 0L
                        var hasMovedVertically = false
                        var isCurrentlyHolding = false

                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.first()

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
                                        val deltaY =
                                            kotlin.math.abs(currentPosition.y - startPosition!!.y)
                                        val deltaX =
                                            kotlin.math.abs(currentPosition.x - startPosition!!.x)

                                        // If there's significant vertical movement, it's likely a dismiss gesture
                                        if (deltaY > 30 && deltaY > deltaX) {
                                            hasMovedVertically = true
                                        }
                                    }

                                    change.changedToUp() && isCurrentlyHolding -> {
                                        isCurrentlyHolding = false
                                        isHolding = false

                                        val duration = System.currentTimeMillis() - startTime
                                        val tapPosition = startPosition ?: change.position

                                        // Only navigate if:
                                        // 1. Quick tap (< 200ms)
                                        // 2. No vertical movement (not a swipe down)
                                        // 3. Not in top area
                                        if (duration < 200 && !hasMovedVertically && tapPosition.y > 100) {
                                            val screenWidth = size.width
                                            val isLeftTap = tapPosition.x < screenWidth / 2
                                            val isRightTap = tapPosition.x >= screenWidth / 2
                                            val lastSlideIndex = slides.lastIndex

                                            // 🔴 NAVIGATION PATCH (ported 1:1)
                                            when {
                                                // ⬅️ Left tap → previous slide
                                                isLeftTap && currentSlideIndex > 0 -> {
                                                    completedSlides.remove(currentSlideIndex)
                                                    currentSlideIndex--
                                                }

                                                // ⬅️ Left tap on first slide → previous story group
                                                isLeftTap && currentSlideIndex == 0 -> {
                                                    onStoryGroupBack()
                                                }

                                                // ➡️ Right tap → next slide
                                                isRightTap && currentSlideIndex < lastSlideIndex -> {
                                                    completedSlides.add(currentSlideIndex)
                                                    currentSlideIndex++
                                                }

                                                // ➡️ Right tap on last slide → next story group
                                                isRightTap && currentSlideIndex == lastSlideIndex -> {
                                                    onStoryGroupEnd()
                                                }

                                                else -> currentSlideIndex
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
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                        content = {
                            if (currentSlide.image != null) {
                                Image(
                                    painter = rememberAsyncImagePainter(currentSlide.image),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }

                            if (currentSlide.video != null) {
                                AndroidView(
                                    factory = { ctx ->
                                        PlayerView(ctx).apply {
                                            this.player = player
                                            layoutParams =
                                                FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                                            useController = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            if (currentSlide.link?.isNotEmpty() == true && currentSlide.buttonText?.isNotEmpty() == true) {
                                val styling = currentSlide.styling

                                // NEW IMPLEMENTATION: Using common CTAButton component
                                // Support both new nested cta structure and legacy fields
                                val ctaConfig = styling?.cta
                                val container = ctaConfig?.container
                                val cornerRadius = ctaConfig?.cornerRadius
                                val ctaMargin = ctaConfig?.margin ?: styling?.ctaMargins
                                val ctaText = ctaConfig?.text

                                // Determine alignment for Box positioning
                                // Check new structure first, then legacy
                                val alignmentStr = container?.alignment ?: styling?.ctaAlignment
                                val alignment = when (alignmentStr?.lowercase()) {
                                    "left" -> Alignment.BottomStart
                                    "right" -> Alignment.BottomEnd
                                    else -> Alignment.BottomCenter
                                }

                                val ctaButtonConfig = createCTAButtonConfig(
                                    // Text styling - new structure first, then legacy
                                    textColor = ctaText?.color ?: styling?.ctaText?.fontColor ?: "#FFFFFF",
                                    textSize = ctaText?.fontSize ?: styling?.ctaText?.fontSize ?: 12,
                                    fontFamily = ctaText?.fontFamily,
                                    fontDecoration = ctaText?.fontDecoration,

                                    // Margins - new structure first, then legacy
                                    marginTop = ctaMargin?.top ?: 12,
                                    marginEnd = ctaMargin?.right ?: 12,
                                    marginBottom = ctaMargin?.bottom ?: 12,
                                    marginStart = ctaMargin?.left ?: 12,

                                    // Container - new structure first, then legacy
                                    height = container?.height ?: styling?.ctaHeight ?: 32,
                                    width = container?.ctaWidth,
                                    borderColorString = container?.borderColor ?: styling?.ctaBackground?.borderColor,
                                    borderWidth = container?.borderWidth ?: styling?.borderWidth ?: 2,
                                    fullWidth = container?.ctaFullWidth ?: styling?.fullWidthCta ?: false,
                                    backgroundColorString = container?.backgroundColor ?: styling?.ctaBackground?.backgroundColor ?: "#FFFFFF",
                                    alignment = alignmentStr ?: "center",

                                    // Corner radius - new structure
                                    borderRadiusTopLeft = cornerRadius?.topLeft ?: 12,
                                    borderRadiusTopRight = cornerRadius?.topRight ?: 12,
                                    borderRadiusBottomLeft = cornerRadius?.bottomLeft ?: 12,
                                    borderRadiusBottomRight = cornerRadius?.bottomRight ?: 12
                                )

                                Box(
                                    modifier = Modifier.align(alignment)
                                ) {
                                    CTAButton(
                                        text = currentSlide.buttonText ?: "",
                                        config = ctaButtonConfig,
                                        onClick = {
                                            try {
                                                uriHandler.openUri(currentSlide.link)
                                            } catch (e: Exception) {
                                                Log.i("Click", "Link has $e")
                                            }
                                            sendEvent(Pair(currentSlide, "CLK"))
                                            sendClickEvent(Pair(currentSlide, "clicked"))
                                        }
                                    )
                                }

                                /* OLD IMPLEMENTATION: Inline Button (commented out)
                                // Parse colors from styling or use defaults
                                val backgroundColor = try {
                                    Color(android.graphics.Color.parseColor(styling?.ctaBackground?.backgroundColor ?: "#FFFFFF"))
                                } catch (e: Exception) {
                                    Color.White
                                }

                                val borderColor = try {
                                    Color(android.graphics.Color.parseColor(styling?.ctaBackground?.borderColor ?: "#FFFFFF"))
                                } catch (e: Exception) {
                                    Color.White
                                }

                                // Get dimensions and margins
                                val ctaHeight = (styling?.ctaHeight ?: 32).dp
                                val borderWidth = (styling?.borderWidth ?: 2).dp
                                val fullWidth = styling?.fullWidthCta ?: false

                                val marginLeft = (styling?.ctaMargins?.left ?: 12).dp
                                val marginRight = (styling?.ctaMargins?.right ?: 12).dp
                                val marginTop = (styling?.ctaMargins?.top ?: 12).dp
                                val marginBottom = (styling?.ctaMargins?.bottom ?: 12).dp

                                // Determine alignment
                                val alignment = when (styling?.ctaAlignment?.lowercase()) {
                                    "left" -> Alignment.BottomStart
                                    "right" -> Alignment.BottomEnd
                                    else -> Alignment.BottomCenter
                                }

                                Button(
                                    onClick = {
                                        try {
                                            uriHandler.openUri(currentSlide.link)
                                        } catch (e: Exception) {
                                            Log.i("Click", "Link has $e")
                                        }
                                        sendEvent(Pair(currentSlide, "CLK"))
                                        sendClickEvent(Pair(currentSlide, "clicked"))
                                    },
                                    modifier = Modifier
                                        .align(alignment)
                                        .padding(
                                            start = marginLeft,
                                            end = marginRight,
                                            top = marginTop,
                                            bottom = marginBottom
                                        )
                                        .height(ctaHeight)
                                        .then(
                                            if (fullWidth) Modifier.fillMaxWidth() else Modifier
                                        ),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = backgroundColor
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = borderWidth,
                                        color = borderColor
                                    ),
                                    content = {
                                        CommonText(
                                            text = currentSlide.buttonText,
                                            styling = TextStyling(
                                                color = styling?.ctaText?.fontColor,
                                                fontSize = styling?.ctaText?.fontSize ?: 12,
                                                fontFamily = "",
                                            )
                                        )
                                    }
                                )
                                */
                            }
                        }
                    )

                    // Progress indicator row
                    Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            content = {
                                slides.forEachIndexed { index, _ ->
                                    LinearProgressIndicator(
                                        progress = {
                                            when {
                                                index == currentSlideIndex -> progress
                                                index < currentSlideIndex || completedSlides.contains(
                                                    index
                                                ) -> 1f

                                                else -> 0f
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(4.dp),
                                        color = Color.White,
                                        trackColor = Color.Gray.copy(alpha = 0.5f),
                                    )
                                }
                            }
                        )

                        // Header row with user info and action buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, end = 8.dp, top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left side: Thumbnail + Name
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                content = {
                                    Image(
                                        painter = rememberAsyncImagePainter(storyGroup.thumbnail),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color.LightGray),
                                        contentScale = ContentScale.Crop
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    storyGroup.name?.let {
                                        CommonText(
                                            text = it,
                                            styling = TextStyling(
                                                color = "#FFFFFF",
                                                fontSize = 14,
                                                fontFamily = "",
                                            )
                                        )
                                    }
                                }
                            )

                            // Right side: Action buttons (mute, share, close)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
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
                                            val unmuteColors = unmuteSettings?.color ?: unmuteSettings?.colors

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

                                        /* OLD IMPLEMENTATION: Inline sound toggle (commented out)
                                        val muteConfig = if (isMuted) {
                                            storyGroup.styling?.soundToggle?.mute
                                        } else {
                                            storyGroup.styling?.soundToggle?.unmute
                                        }

                                        val fillColor = try {
                                            muteConfig?.colors?.fill?.let { Color(android.graphics.Color.parseColor(it)) }
                                        } catch (e: Exception) {
                                            null
                                        } ?: Color.Black.copy(alpha = 0.2f)

                                        val iconColor = try {
                                            muteConfig?.colors?.cross?.let { Color(android.graphics.Color.parseColor(it)) }
                                        } catch (e: Exception) {
                                            null
                                        } ?: Color.White

                                        val strokeColor = try {
                                            muteConfig?.colors?.stroke?.let { Color(android.graphics.Color.parseColor(it)) }
                                        } catch (e: Exception) {
                                            null
                                        }

                                        val marginTop = (muteConfig?.margin?.top ?: 0).dp
                                        val marginRight = (muteConfig?.margin?.right ?: 0).dp

                                        Box(
                                            modifier = Modifier
                                                .padding(top = marginTop, end = marginRight)
                                                .size(32.dp)
                                                .background(
                                                    color = fillColor,
                                                    shape = CircleShape
                                                )
                                                .then(
                                                    strokeColor?.let {
                                                        Modifier.border(1.dp, it, CircleShape)
                                                    } ?: Modifier
                                                )
                                                .clickable {
                                                    isMuted = !isMuted
                                                    if (isMuted) {
                                                        player.volume = 0f
                                                    } else {
                                                        player.volume = 1f
                                                    }
                                                },
                                            contentAlignment = Alignment.Center,
                                            content = {
                                                Icon(
                                                    painter = if (isMuted) painterResource(R.drawable.mute) else painterResource(
                                                        R.drawable.volume
                                                    ),
                                                    contentDescription = if (isMuted) "Unmute" else "Mute",
                                                    tint = iconColor
                                                )
                                            }
                                        )
                                        */
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
                                                    sendEvent(Pair(currentSlide, "SHR"))
                                                }
                                            )
                                        }

                                        /* OLD IMPLEMENTATION: Inline share button (commented out)
                                        val fillColor = try {
                                            shareColors?.fill?.let { Color(android.graphics.Color.parseColor(it)) }
                                        } catch (e: Exception) {
                                            null
                                        } ?: Color.Black.copy(alpha = 0.2f)

                                        val iconColor = try {
                                            shareColors?.cross?.let { Color(android.graphics.Color.parseColor(it)) }
                                        } catch (e: Exception) {
                                            null
                                        } ?: Color.White

                                        val strokeColor = try {
                                            shareColors?.stroke?.let { Color(android.graphics.Color.parseColor(it)) }
                                        } catch (e: Exception) {
                                            null
                                        }

                                        val marginTop = (shareConfig?.margin?.top ?: 0).dp
                                        val marginRight = (shareConfig?.margin?.right ?: 0).dp

                                        Box(
                                            modifier = Modifier
                                                .padding(top = marginTop, end = marginRight)
                                                .size(32.dp)
                                                .background(
                                                    color = fillColor,
                                                    shape = CircleShape
                                                )
                                                .then(
                                                    strokeColor?.let {
                                                        Modifier.border(1.dp, it, CircleShape)
                                                    } ?: Modifier
                                                )
                                                .clickable(onClick = {
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
                                                }),
                                            contentAlignment = Alignment.Center,
                                            content = {
                                                Icon(
                                                    painter = painterResource(R.drawable.share),
                                                    contentDescription = "Share",
                                                    tint = iconColor,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        )
                                        */
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

                                    /* OLD IMPLEMENTATION: Inline cross button (commented out)
                                    val fillColor = try {
                                        closeColors?.fill?.let { Color(android.graphics.Color.parseColor(it)) }
                                    } catch (e: Exception) {
                                        null
                                    } ?: Color.Black.copy(alpha = 0.2f)

                                    val iconColor = try {
                                        closeColors?.cross?.let { Color(android.graphics.Color.parseColor(it)) }
                                    } catch (e: Exception) {
                                        null
                                    } ?: Color.White

                                    val strokeColor = try {
                                        closeColors?.stroke?.let { Color(android.graphics.Color.parseColor(it)) }
                                    } catch (e: Exception) {
                                        null
                                    }

                                    val marginTop = (closeConfig?.margin?.top ?: 0).dp
                                    val marginRight = (closeConfig?.margin?.right ?: 0).dp

                                    Box(
                                        modifier = Modifier
                                            .padding(top = marginTop, end = marginRight)
                                            .size(32.dp)
                                            .background(
                                                color = fillColor,
                                                shape = CircleShape
                                            )
                                            .then(
                                                strokeColor?.let {
                                                    Modifier.border(1.dp, it, CircleShape)
                                                } ?: Modifier
                                            )
                                            .clickable {
                                                scope.launch {
                                                    sheetState.hide()
                                                    onDismiss()
                                                }
                                            },
                                        contentAlignment = Alignment.Center,
                                        content = {
                                            Icon(
                                                painter = painterResource(R.drawable.cross),
                                                contentDescription = "Close",
                                                tint = iconColor,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    )
                                    */
                                }
                            )
                        }
                }
            )
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
    sendClickEvent: (Pair<StorySlide, String>) -> Unit
) {
    var selectedStoryGroup by remember { mutableStateOf<StoryGroup?>(null) }
    val storyGroups = storiesDetails.groups ?: emptyList()

    Box(
        modifier = Modifier.fillMaxSize(),
        content = {
            StoryCircles(
                viewedStories = viewedStories,
                storyGroups = storyGroups,
                onStoryClick = { storyGroup ->
                    selectedStoryGroup = storyGroup
                }
            )

            val storyGroup = selectedStoryGroup
            if (storyGroup != null && !storyGroup.slides.isNullOrEmpty()) {
                StoryScreen(
                    storyGroup = storyGroup,
                    slides = storyGroup.slides,
                    onDismiss = { selectedStoryGroup = null },
                    onStoryGroupEnd = {
                        val currentIndex = storyGroups.indexOf(storyGroup)
                        if (currentIndex < storyGroups.lastIndex) {
                            selectedStoryGroup = storyGroups[currentIndex + 1]
                            // Mark the new story group as viewed
                            selectedStoryGroup?.id?.let { storyViewed(it) }
                        } else {
                            selectedStoryGroup = null
                        }
                    },
                    onStoryGroupBack = {
                        val currentIndex = storyGroups.indexOf(storyGroup)
                        if (currentIndex > 0) {
                            selectedStoryGroup = storyGroups[currentIndex - 1]
                            // Mark the new story group as viewed when going back
                            selectedStoryGroup?.id?.let { storyViewed(it) }
                        }
                        // If at first story group, do nothing (stay on current story)
                    },
                    sendEvent = sendEvent,
                    sendClickEvent = sendClickEvent
                )
            }
        }
    )
}

@UnstableApi
@Composable
internal fun StoryAppMain(
    apiStoriesDetails: StoriesDetails,
    sendEvent: (Pair<StorySlide, String>) -> Unit,
    sendClickEvent: (Pair<StorySlide, String>) -> Unit
) {
    val context = LocalContext.current
    val storyGroups = apiStoriesDetails.groups ?: emptyList()

    // Track viewed slides instead of just groups
//    var viewedSlides by remember {
//        mutableStateOf(
//            getViewedSlides(
//                context.getSharedPreferences(
//                    "AppStory",
//                    Context.MODE_PRIVATE
//                )
//            )
//        )
//    }

    var viewedSlides by remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(Unit) {
        viewedSlides = getViewedSlides(
            context.getSharedPreferences(
                "AppStory",
                Context.MODE_PRIVATE
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

//    var sortedGroups by remember {
//        mutableStateOf(
//            storyGroups.sortedWith(
//                compareByDescending<StoryGroup> { it.id !in viewedGroups }
//                    .thenBy { it.order })
//        )
//    }
//
//    LaunchedEffect(viewedGroups) {
//        sortedGroups = storyGroups.sortedWith(
//            compareByDescending<StoryGroup> { it.id !in viewedGroups }
//                .thenBy { it.order }
//        )
//    }

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
        sendClickEvent = sendClickEvent
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
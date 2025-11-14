package com.appversal.appstorys.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
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
import coil.compose.rememberAsyncImagePainter
import com.appversal.appstorys.AppStorys
import com.appversal.appstorys.R
import com.appversal.appstorys.api.StoryGroup
import com.appversal.appstorys.api.StorySlide
import com.appversal.appstorys.utils.VideoCache
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray

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
                    nameColor = Color(android.graphics.Color.parseColor(storyGroup.nameColor)),
                    onClick = { onStoryClick(storyGroup) }
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
    nameColor: Color,
    onClick: () -> Unit
) {
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
                modifier = Modifier.size(70.dp),
                content = {
                    Canvas(
                        modifier = Modifier.size(80.dp),
                        onDraw = {
                            drawCircle(
                                color = if (isStoryGroupViewed) Color.Gray else ringColor,
                                style = Stroke(width = 5f),
                                radius = size.minDimension / 2
                            )
                        }
                    )
                    Image(
                        painter = rememberAsyncImagePainter(imageUrl),
                        contentDescription = null,
                        modifier = Modifier
                            .size(65.dp)
                            .clip(CircleShape)
                            .background(Color.LightGray),
                        contentScale = ContentScale.Crop
                    )
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                modifier = Modifier
                    .width(60.dp)
                    .align(Alignment.CenterHorizontally),
                text = username,
                maxLines = 2,
                fontSize = 12.sp,
                color = nameColor,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp
            )
        }
    )
}

@UnstableApi
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StoryScreen(
    storyGroup: StoryGroup,
    onDismiss: () -> Unit,
    slides: List<StorySlide>,
    onStoryGroupEnd: () -> Unit,
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
    val storyDuration = if (isImage) 5000 else 0

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
            .fillMaxSize()
            .statusBarsPadding(),
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
                    .pointerInput("story_gestures") {
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
                                            currentSlideIndex = when {
                                                tapPosition.x < screenWidth / 2 && currentSlideIndex > 0 -> {
                                                    if (completedSlides.contains(currentSlideIndex)) {
                                                        completedSlides.remove(currentSlideIndex)
                                                    }
                                                    currentSlideIndex - 1
                                                }

                                                tapPosition.x > screenWidth / 2 && currentSlideIndex < slides.lastIndex -> {
                                                    if (!completedSlides.contains(currentSlideIndex)) {
                                                        completedSlides.add(currentSlideIndex)
                                                    }
                                                    currentSlideIndex + 1
                                                }

                                                else -> {
                                                    onStoryGroupEnd()
                                                    completedSlides.clear()
                                                    0
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
                                Button(
                                    onClick = {
                                        uriHandler.openUri(currentSlide.link)
                                        sendEvent(Pair(currentSlide, "CLK"))
                                        sendClickEvent(Pair(currentSlide, "clicked"))
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 32.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White
                                    ),
                                    content = {
                                        Text(text = currentSlide.buttonText, color = Color.Black)
                                    }
                                )
                            }
                        }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.TopCenter)
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopStart)
                            .padding(18.dp),
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
                                Text(
                                    text = it,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopEnd)
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                        content = {
                            if (!isImage) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(
                                            color = Color.Black.copy(alpha = 0.2f),
                                            shape = CircleShape
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
                                            tint = Color.White
                                        )
                                    }
                                )
                            }
                            if (currentSlide.link?.isNotEmpty() == true && currentSlide.buttonText?.isNotEmpty() == true) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(
                                            color = Color.Black.copy(alpha = 0.2f),
                                            shape = CircleShape
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
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        color = Color.Black.copy(alpha = 0.2f),
                                        shape = CircleShape
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
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            )
                        }
                    )
                }
            )
        }
    )
}

@UnstableApi
@Composable
internal fun StoriesApp(
    storyGroups: List<StoryGroup>,
    sendEvent: (Pair<StorySlide, String>) -> Unit,
    viewedStories: List<String>,
    storyViewed: (String) -> Unit,
    sendClickEvent: (Pair<StorySlide, String>) -> Unit
) {
    var selectedStoryGroup by remember { mutableStateOf<StoryGroup?>(null) }

    Box(
        modifier = Modifier.fillMaxSize(),
        content = {
            StoryCircles(
                viewedStories = viewedStories,
                storyGroups = storyGroups,
                onStoryClick = { storyGroup ->
                    selectedStoryGroup = storyGroup
                    selectedStoryGroup?.id?.let {
                        storyViewed(it)
                    }
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
                            selectedStoryGroup?.id?.let {
                                storyViewed(it)
                            }
                        } else {
                            selectedStoryGroup = null
                        }
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
    apiStoryGroups: List<StoryGroup>,
    sendEvent: (Pair<StorySlide, String>) -> Unit,
    sendClickEvent: (Pair<StorySlide, String>) -> Unit
) {

    val context = LocalContext.current
    var viewedStories by remember {
        mutableStateOf(
            getViewedStories(
                context.getSharedPreferences(
                    "AppStory",
                    Context.MODE_PRIVATE
                )
            )
        )
    }
    var storyGroups by remember {
        mutableStateOf(
            apiStoryGroups.sortedWith(
                compareByDescending<StoryGroup> { it.id !in viewedStories }
                    .thenBy { it.order })
        )
    }

    LaunchedEffect(viewedStories) {
        storyGroups = storyGroups.sortedWith(
            compareByDescending<StoryGroup> { it.id !in viewedStories }
                .thenBy { it.order }
        )
    }

    StoriesApp(
        storyGroups = storyGroups,
        sendEvent = sendEvent,
        viewedStories = viewedStories,
        storyViewed = {
            if (!viewedStories.contains(it)) {
                val list = ArrayList(viewedStories)
                list.add(it)
                viewedStories = list
                saveViewedStories(
                    idList = list,
                    sharedPreferences = context.getSharedPreferences(
                        "AppStory",
                        Context.MODE_PRIVATE
                    )
                )
            }
        },
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
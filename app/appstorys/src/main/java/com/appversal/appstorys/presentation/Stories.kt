package com.appversal.appstorys.presentation

import android.content.Intent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.annotation.OptIn
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
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil.compose.rememberAsyncImagePainter
import com.appversal.appstorys.R
import com.appversal.appstorys.api.StoriesDetails
import com.appversal.appstorys.api.StoryGroup
import com.appversal.appstorys.api.StorySlide
import com.appversal.appstorys.domain.State
import com.appversal.appstorys.domain.rememberCampaign
import com.appversal.appstorys.domain.usecase.ActionType
import com.appversal.appstorys.domain.usecase.trackEvent
import com.appversal.appstorys.domain.usecase.trackUserAction
import com.appversal.appstorys.utils.rememberPlayer
import com.appversal.appstorys.utils.rememberSharedPreferences
import com.appversal.appstorys.utils.toColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
internal fun Stories(
    modifier: Modifier = Modifier,
) {
    val campaign = rememberCampaign<StoriesDetails>("STR")
    val stories = campaign?.details?.groups

    if (!stories.isNullOrEmpty()) {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        Content(
            modifier = modifier,
            groups = stories,
            sendEvent = {
                scope.launch {
                    ActionType.entries.find { type -> type.name == it.second }?.let { type ->
                        trackUserAction(campaign.id, type, it.first.id)
                    }
                    trackEvent(
                        context,
                        "viewed",
                        campaign.id,
                        mapOf("story_slide" to it.first.id!!)
                    )
                }
            },
            sendClickEvent = {
                scope.launch {
                    trackEvent(
                        context,
                        it.second,
                        campaign.id,
                        mapOf("story_slide" to it.first.id!!)
                    )
                }
            }
        )
    }
}

@UnstableApi
@Composable
private fun Content(
    groups: List<StoryGroup>,
    modifier: Modifier = Modifier,
    sendEvent: (Pair<StorySlide, String>) -> Unit,
    sendClickEvent: (Pair<StorySlide, String>) -> Unit
) {
    val prefs = rememberSharedPreferences()

    val viewed = remember(prefs) {
        val data = prefs.getStringSet("viewed_stories", emptySet())?.toList() ?: emptyList()
        mutableStateListOf(*data.toTypedArray())
    }
    val groups = remember(groups, viewed) {
        groups
            .filter { !it.thumbnail.isNullOrBlank() }
            .sortedWith(compareByDescending<StoryGroup> { it.id !in viewed }.thenBy { it.order })
    }
    var selectedGroup by remember { mutableStateOf<StoryGroup?>(null) }

    val handleStoryViewed = remember(prefs, viewed) {
        { group: StoryGroup ->
            selectedGroup = group
            if (!viewed.contains(group.id) && group.id != null) {
                viewed += group.id
                prefs.edit { putStringSet("viewed_stories", viewed.toSet()) }
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        content = {
            Circles(
                viewed = viewed,
                groups = groups,
                onStoryClick = handleStoryViewed
            )

            val group = selectedGroup
            if (group != null && !group.slides.isNullOrEmpty()) {
                Screen(
                    storyGroup = group,
                    slides = group.slides,
                    onDismiss = { selectedGroup = null },
                    onStoryGroupEnd = {
                        val currentIndex = groups.indexOf(group)
                        if (currentIndex < groups.lastIndex) {
                            handleStoryViewed(groups[currentIndex + 1])
                        } else {
                            selectedGroup = null
                        }
                    },
                    sendEvent = sendEvent,
                    sendClickEvent = sendClickEvent
                )
            }
        }
    )
}

@Composable
private fun Circles(
    groups: List<StoryGroup>,
    viewed: List<String>,
    modifier: Modifier = Modifier,
    onStoryClick: (StoryGroup) -> Unit,
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = {
            items(
                groups.size,
                key = { index -> groups[index].id ?: index.toString() },
                itemContent = { index ->
                    val group = groups[index]
                    Item(
                        group = group,
                        isViewed = viewed.contains(group.id),
                        onClick = { onStoryClick(group) }
                    )
                }
            )
        }
    )
}

@Composable
private fun Item(
    group: StoryGroup,
    isViewed: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .padding(4.dp)
            .clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(70.dp),
                content = {
                    Canvas(
                        modifier = Modifier.size(80.dp),
                        onDraw = {
                            drawCircle(
                                color = if (isViewed) Color.Gray else group.ringColor.toColor(),
                                style = Stroke(width = 5f),
                                radius = size.minDimension / 2
                            )
                        }
                    )
                    Image(
                        painter = rememberAsyncImagePainter(group.thumbnail),
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
                text = group.name.orEmpty(),
                maxLines = 2,
                fontSize = 12.sp,
                color = group.nameColor.toColor(),
                textAlign = TextAlign.Center,
                lineHeight = 15.sp
            )
        }
    )
}


@UnstableApi
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Screen(
    storyGroup: StoryGroup,
    onDismiss: () -> Unit,
    slides: List<StorySlide>,
    onStoryGroupEnd: () -> Unit,
    sendEvent: (Pair<StorySlide, String>) -> Unit,
    sendClickEvent: (Pair<StorySlide, String>) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

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

    val player = rememberPlayer(
        videoUri = currentSlide.video,
        muted = isMuted,
    )

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
        State.isVisible = false

        onDispose {
            State.isVisible = true
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

                            if (!currentSlide.video.isNullOrBlank()) {
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

                            if (currentSlide.link?.isNotBlank() == true && currentSlide.buttonText?.isNotBlank() == true) {
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
package com.appversal.appstorys.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
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
import com.appversal.appstorys.AppStorys
import com.appversal.appstorys.R
import com.appversal.appstorys.api.PipStyling
import com.appversal.appstorys.utils.VideoCache
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
internal fun PipVideo(
    height: Dp,
    width: Dp,
    videoUri: String,
    fullScreenVideoUri: String?,
    button_text: String,
    position: String?,
    link: String,
    bottomPadding: Dp = 0.dp,
    topPadding: Dp = 0.dp,
    isMovable: Boolean = true,
    pipStyling: PipStyling?,
    onClose: () -> Unit,
    onButtonClick: () -> Unit,
    onExpandClick: () -> Unit = {}
) {
    var isFullScreen by remember { mutableStateOf(false) }

    when {
        isFullScreen && !fullScreenVideoUri.isNullOrEmpty() -> FullScreenVideoDialog(
            videoUri = fullScreenVideoUri,
            onDismiss = {
                isFullScreen = false
            },
            onClose = onClose,
            button_text = button_text,
            link = link,
            pipStyling = pipStyling,
            onButtonClick = onButtonClick
        )

        AppStorys.isVisible -> {
            val density = LocalDensity.current.density
            val configuration = LocalConfiguration.current

            val screenWidth = configuration.screenWidthDp * density
            val screenHeight = configuration.screenHeightDp * density

            val boundaryPadding = 12.dp
            val boundaryPaddingPx = with(LocalDensity.current) { boundaryPadding.toPx() }

            var isMuted by remember { mutableStateOf(true) }
            var pipSize by remember { mutableStateOf(IntSize(0, 0)) }
            var offsetX by remember { mutableFloatStateOf(0f) }
            var offsetY by remember { mutableFloatStateOf(0f) }
            var isInitialized by remember { mutableStateOf(false) }

            val pipPlayer = player(videoUri, isMuted)

            val bottomPaddingPx = with(LocalDensity.current) {
                (pipStyling?.pipBottomPadding?.toFloatOrNull()?.dp ?: bottomPadding).toPx()
            }
            val topPaddingPx = with(LocalDensity.current) {
                (pipStyling?.pipTopPadding?.toFloatOrNull()?.dp ?: topPadding).toPx()
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                content = {
                    when (isInitialized) {
                        true -> Card(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        offsetX.roundToInt(),
                                        offsetY.roundToInt()
                                    )
                                }
                                .size(width = width, height = height)
                                .onGloballyPositioned { coordinates ->
                                    pipSize = coordinates.size
                                }
                                .clickable {
                                    if (!fullScreenVideoUri.isNullOrEmpty()) {
                                        onExpandClick()
                                        isFullScreen = true
                                        pipPlayer.pause()
                                    }
                                }
                                .then(
                                    if (isMovable) {
                                        Modifier.pointerInput("drag_gesture") {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                offsetX = (offsetX + dragAmount.x).coerceIn(
                                                    boundaryPaddingPx,
                                                    screenWidth - pipSize.width - boundaryPaddingPx
                                                )
                                                offsetY = (offsetY + dragAmount.y).coerceIn(
                                                    boundaryPaddingPx + topPaddingPx,
                                                    screenHeight - pipSize.height - boundaryPaddingPx - bottomPaddingPx
                                                )
                                            }
                                        }
                                    } else {
                                        Modifier
                                    }
                                ),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            content = {
                                Box {
                                    PipPlayerView(
                                        exoPlayer = pipPlayer,
                                        pipStyling = pipStyling,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(23.dp)
                                            .background(
                                                Color.Black.copy(alpha = 0.5f),
                                                CircleShape
                                            )
                                            .clickable { onClose() },
                                        contentAlignment = Alignment.Center,
                                        content = {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Close",
                                                tint = Color.White,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(4.dp)
                                            .size(24.dp)
                                            .background(
                                                Color.Black.copy(alpha = 0.5f),
                                                CircleShape
                                            )
                                            .clickable { isMuted = !isMuted },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = if (isMuted) painterResource(R.drawable.mute) else painterResource(
                                                R.drawable.volume
                                            ),
                                            contentDescription = "Mute/Unmute",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    if (!fullScreenVideoUri.isNullOrEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(4.dp)
                                                .size(24.dp)
                                                .background(
                                                    Color.Black.copy(alpha = 0.5f),
                                                    CircleShape
                                                )
                                                .clickable {
                                                    if (fullScreenVideoUri.isNotEmpty()) {
                                                        onExpandClick()
                                                        isFullScreen = true
                                                        pipPlayer.pause()
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.expand),
                                                contentDescription = "Maximize",
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        )

                        else -> Box(
                            modifier = Modifier
                                .size(width = width, height = height)
                                .onGloballyPositioned { coordinates ->
                                    pipSize = coordinates.size

                                    offsetX = if (position == "left") {
                                        boundaryPaddingPx
                                    } else {
                                        screenWidth - pipSize.width - boundaryPaddingPx
                                    }
                                    offsetY =
                                        screenHeight - pipSize.height - boundaryPaddingPx - bottomPaddingPx
                                    isInitialized = true
                                }
                                .alpha(0f)
                        )
                    }
                }
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PipPlayerView(
    exoPlayer: ExoPlayer,
    pipStyling: PipStyling?,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                useArtwork = false
                setKeepContentOnPlayerReset(true)
            }
        },
        modifier = modifier
    )
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenVideoDialog(
    videoUri: String,
    onDismiss: () -> Unit,
    button_text: String?,
    link: String?,
    pipStyling: PipStyling?,
    onClose: () -> Unit,
    onButtonClick: () -> Unit
) {
    var isMuted by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val player = player(videoUri, isMuted)
    val onHide = remember {
        { action: () -> Unit ->
            scope.launch {
                sheetState.hide()
                action()
            }
        }
    }

    DisposableEffect(Unit) {
        AppStorys.isVisible = false

        onDispose {
            AppStorys.isVisible = true
        }
    }

    LaunchedEffect(sheetState.targetValue) {
        when (sheetState.targetValue) {
            SheetValue.Hidden -> player.pause()
            else -> player.play()
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
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
                content = {
                    PipPlayerView(
                        exoPlayer = player,
                        pipStyling = pipStyling,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    IconButton(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                        onClick = {
                            onHide(onDismiss)
                        },
                        content = {
                            Icon(
                                painter = painterResource(R.drawable.minimize),
                                contentDescription = "Minimize",
                                tint = Color.White,
                                modifier = Modifier.size(23.dp)
                            )
                        }
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = {
                            IconButton(
                                onClick = { isMuted = !isMuted },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                                content = {
                                    Icon(
                                        painter = if (isMuted) painterResource(R.drawable.mute) else painterResource(
                                            R.drawable.volume
                                        ),
                                        contentDescription = "Mute/Unmute",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            )

                            Spacer(Modifier.width(12.dp))

                            IconButton(
                                onClick = {
                                    onHide(onClose)
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                                content = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            )
                        }
                    )

                    if (!button_text.isNullOrEmpty() && !link.isNullOrEmpty()) {

                        fun String?.toDp(): Dp = this?.toIntOrNull()?.dp ?: 0.dp

                        val paddingLeft = pipStyling?.marginLeft?.toDp()
                        val paddingRight = pipStyling?.marginRight?.toDp()
                        val paddingTop = pipStyling?.marginTop?.toDp()
                        val paddingBottom = pipStyling?.marginBottom?.toDp()

                        val buttonColor = try {
                            Color((pipStyling?.ctaButtonBackgroundColor ?: "#000000").toColorInt())
                        } catch (e: Exception) {
                            Color.Black
                        }

                        val textColor = try {
                            Color((pipStyling?.ctaButtonTextColor ?: "#FFFFFF").toColorInt())
                        } catch (e: Exception) {
                            Color.White
                        }

                        Button(
                            onClick = {
                                uriHandler.openUri(link);
                                onButtonClick()
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(
//                                top = paddingTop ?: 0.dp,
                                    bottom = paddingBottom ?: 0.dp,
                                    start = paddingLeft ?: 0.dp,
                                    end = paddingRight ?: 0.dp
                                )
                                .then(
                                    if (pipStyling?.ctaFullWidth == true) {
                                        Modifier.fillMaxWidth()
                                    } else {
                                        Modifier.width(pipStyling?.ctaWidth?.toDp() ?: 0.dp)
                                    }
                                )
                                .height(pipStyling?.ctaHeight?.toDp() ?: 0.dp),
                            shape = RoundedCornerShape(pipStyling?.cornerRadius?.toDp() ?: 0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                            content = {
                                Text(
                                    fontFamily = FontFamily(
                                        Font(
                                            googleFont = GoogleFont("Poppins"),
                                            fontProvider = GoogleFont.Provider(
                                                providerAuthority = "com.google.android.gms.fonts",
                                                providerPackage = "com.google.android.gms",
                                                certificates = R.array.com_google_android_gms_fonts_certs
                                            ),
                                            FontWeight.Normal,
                                            FontStyle.Normal
                                        )
                                    ),
                                    text = button_text,
                                    color = textColor,
                                    textAlign = TextAlign.Center
                                )
                            }
                        )
                    }
                }
            )
        }
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun player(videoUri: String, muted: Boolean): ExoPlayer {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val pipPlayer = remember {
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(5000)
            .setLoadControl(DefaultLoadControl())
            .setSeekForwardIncrementMs(5000)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(VideoCache.getFactory(context))
            )
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(videoUri.toUri()))
                repeatMode = Player.REPEAT_MODE_ALL
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                volume = if (muted) 0f else 1.0f
                prepare()
                play()
            }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> pipPlayer.play()

                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> pipPlayer.pause()

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            pipPlayer.release()
        }
    }

    LaunchedEffect(muted) {
        pipPlayer.volume = if (muted) 0f else 1.0f
    }

    return pipPlayer
}
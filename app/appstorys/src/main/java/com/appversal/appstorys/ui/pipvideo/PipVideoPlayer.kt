package com.appversal.appstorys.ui.pipvideo

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
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
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.appversal.appstorys.AppStorys
import com.appversal.appstorys.api.PipStyling
import com.appversal.appstorys.ui.common_components.CrossButton
import com.appversal.appstorys.ui.common_components.CrossButtonConfig
import com.appversal.appstorys.ui.common_components.CTAButton
import com.appversal.appstorys.ui.common_components.createCTAButtonConfig
import com.appversal.appstorys.ui.common_components.ExpandButton
import com.appversal.appstorys.ui.common_components.ExpandButtonConfig
import com.appversal.appstorys.ui.common_components.SoundToggleButton
import com.appversal.appstorys.ui.common_components.SoundToggleButtonConfig
import com.appversal.appstorys.utils.VideoCache
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
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

    crossButtonConfig: CrossButtonConfig = CrossButtonConfig(),

    maximiseButtonConfig: ExpandButtonConfig? = null,
    minimiseButtonConfig: ExpandButtonConfig? = null,

    muteButtonConfig: SoundToggleButtonConfig = SoundToggleButtonConfig(),
    unmuteButtonConfig: SoundToggleButtonConfig = SoundToggleButtonConfig(),

    //Not in use
    muteButtonImageUrl: String? = null,
    unmuteButtonImageUrl: String? = null,
    maximiseImageUrl: String? = null,
    minimiseImageUrl: String? = null,

    onClose: () -> Unit,
    onButtonClick: () -> Unit,
    onExpandClick: () -> Unit = {}
) {
    var isFullScreen by remember { mutableStateOf(false) }

    when {
        isFullScreen && !fullScreenVideoUri.isNullOrEmpty() ->
            FullScreenVideoDialog(
                videoUri = fullScreenVideoUri,
                onDismiss = {
                    isFullScreen = false
                },
                onClose = onClose,
                button_text = button_text,
                link = link,
                pipStyling = pipStyling,
                crossButtonConfig = crossButtonConfig,
                maximiseButtonConfig = maximiseButtonConfig,
                minimiseButtonConfig = minimiseButtonConfig,
                muteButtonConfig = muteButtonConfig,
                unmuteButtonConfig = unmuteButtonConfig,
                muteButtonImageUrl = muteButtonImageUrl,
                unmuteButtonImageUrl = unmuteButtonImageUrl,
                maximiseImageUrl = maximiseImageUrl,
                minimiseImageUrl = minimiseImageUrl,
                onButtonClick = onButtonClick
            )

        AppStorys.isVisible -> {
            val density = LocalDensity.current.density
            val configuration = LocalConfiguration.current

            val screenWidth = configuration.screenWidthDp * density
            val screenHeight = configuration.screenHeightDp * density

            val boundaryPadding = 12.dp
            val boundaryPaddingPx = with(LocalDensity.current) { boundaryPadding.toPx() }

            // Determine initial muted state from backend configuration
            // defaultSound = "yes" means unmuted, "no" or null means muted
            val initialMutedState = when (pipStyling?.appearance?.defaultSound ?: pipStyling?.soundToggle?.defaultSound) {
                "yes" -> false  // unmuted
                "no" -> true    // muted
                else -> false    // default to muted if not specified
            }
            var isMuted by remember { mutableStateOf(false) }
            var pipSize by remember { mutableStateOf(IntSize(0, 0)) }
            var offsetX by remember { mutableFloatStateOf(0f) }
            var offsetY by remember { mutableFloatStateOf(0f) }
            var isInitialized by remember { mutableStateOf(false) }

            val pipPlayer = player(videoUri, !isMuted)

            // Helper function to parse JsonElement to Float
            fun parseJsonPadding(jsonElement: kotlinx.serialization.json.JsonElement?): Float? {
                return when {
                    jsonElement == null -> null
                    jsonElement is JsonPrimitive && jsonElement.floatOrNull != null -> jsonElement.floatOrNull
                    jsonElement is JsonPrimitive && jsonElement.intOrNull != null -> jsonElement.intOrNull?.toFloat()
                    jsonElement is JsonPrimitive -> jsonElement.content.toFloatOrNull()
                    else -> null
                }
            }

            val bottomPaddingPx = with(LocalDensity.current) {
                (parseJsonPadding(pipStyling?.pipBottomPadding)?.dp?.plus(bottomPadding) ?: bottomPadding).toPx()
            }
            val topPaddingPx = with(LocalDensity.current) {
                (parseJsonPadding(pipStyling?.pipTopPadding)?.dp?.plus(topPadding) ?: topPadding).toPx()
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
                                Box(modifier = Modifier.background(Color.Black)) {
                                    PipPlayerView(
                                        exoPlayer = pipPlayer,
                                        pipStyling = pipStyling,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    // Only show cross button if enabled (default to true if not specified)
                                    val isCrossButtonEnabled = pipStyling?.crossButton?.enabled ?: true
                                    if (isCrossButtonEnabled) {
                                        CrossButton(
                                            modifier = Modifier.align(Alignment.TopEnd),
                                            config = crossButtonConfig,
                                            onClose = onClose
                                        )
                                    }

                                    // Only show mute/unmute button if enabled (default to true if not specified)
                                    val isSoundToggleEnabled = pipStyling?.soundToggle?.enabled ?: true
                                    if (isSoundToggleEnabled) {
                                        SoundToggleButton(
                                            modifier = Modifier.align(Alignment.TopStart),
                                            muteConfig = muteButtonConfig,
                                            unmuteConfig = unmuteButtonConfig,
                                            isMuted = !isMuted,
                                            onToggle = { isMuted = !isMuted }
                                        )
                                    }

                                    // Only show expand button if enabled (default to true if not specified)
                                    val isExpandControlsEnabled = pipStyling?.expandControls?.enabled ?: true
                                    if (!fullScreenVideoUri.isNullOrEmpty() && isExpandControlsEnabled) {

                                        ExpandButton(
                                            modifier = Modifier.align(Alignment.BottomEnd),
                                            maximiseConfig = maximiseButtonConfig
                                                ?: ExpandButtonConfig(),
                                            minimiseConfig = minimiseButtonConfig
                                                ?: ExpandButtonConfig(),
                                            isExpanded = false,
                                            onToggle = {
                                                onExpandClick()
                                                isFullScreen = true
                                                pipPlayer.pause()
                                            }
                                        )



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
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
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
    crossButtonConfig: CrossButtonConfig = CrossButtonConfig(),
    muteButtonImageUrl: String? = null,
    unmuteButtonImageUrl: String? = null,
    maximiseImageUrl: String? = null,
    minimiseImageUrl: String? = null,

    maximiseButtonConfig: ExpandButtonConfig? = null,
    minimiseButtonConfig: ExpandButtonConfig? = null,

    muteButtonConfig: SoundToggleButtonConfig = SoundToggleButtonConfig(),
    unmuteButtonConfig: SoundToggleButtonConfig = SoundToggleButtonConfig(),

    onClose: () -> Unit,
    onButtonClick: () -> Unit
) {
    // Determine initial muted state from backend configuration
    // defaultSound = "yes" means unmuted, "no" or null means muted
    val initialMutedState = when (pipStyling?.appearance?.defaultSound ?: pipStyling?.soundToggle?.defaultSound) {
        "yes" -> false  // unmuted
        "no" -> true    // muted
        else -> false   // default to unmuted for fullscreen (better UX)
    }
    var isMuted by remember { mutableStateOf(false) }

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
            .fillMaxSize(),
        shape = RectangleShape,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Black,
        contentColor = Color.White,
        dragHandle = null,
        content = {
            // Check enabled flags for each control
            val isExpandControlsEnabled = pipStyling?.expandControls?.enabled ?: true
            val isSoundToggleEnabled = pipStyling?.soundToggle?.enabled ?: true
            val isCrossButtonEnabled = pipStyling?.crossButton?.enabled ?: true

            Box(
                modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                contentAlignment = Alignment.Center,
                content = {
                    PipPlayerView(
                        exoPlayer = player,
                        pipStyling = pipStyling,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Minimize button (top-left) - using ExpandButton component
                    // Only show if expand controls are enabled
                    if (isExpandControlsEnabled) {
                        // Override size for fullscreen mode (46.dp) and remove margins
                        val fullscreenMaximiseConfig = maximiseButtonConfig?.copy(
                            size = 46.dp,
                            marginTop = 0.dp,
                            marginEnd = 0.dp,
                            marginBottom = 0.dp,
                            marginStart = 0.dp
                        ) ?: ExpandButtonConfig(size = 46.dp)

                        val fullscreenMinimiseConfig = minimiseButtonConfig?.copy(
                            size = 46.dp,
                            marginTop = 0.dp,
                            marginEnd = 0.dp,
                            marginBottom = 0.dp,
                            marginStart = 0.dp
                        ) ?: ExpandButtonConfig(size = 46.dp)

                        ExpandButton(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp),
                            maximiseConfig = fullscreenMaximiseConfig,
                            minimiseConfig = fullscreenMinimiseConfig,
                            isExpanded = true,
                            onToggle = {
                                onHide(onDismiss)
                            }
                        )
                    }



                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = {
                            // Mute/Unmute button - using SoundToggleButton component
                            // Only show if sound toggle is enabled
                            if (isSoundToggleEnabled) {
                                SoundToggleButton(
                                    modifier = Modifier,
                                    muteConfig = muteButtonConfig.copy(
                                        size = 46.dp,
                                        marginTop = 0.dp,
                                        marginEnd = 0.dp,
                                        marginBottom = 0.dp,
                                        marginStart = 0.dp
                                    ),
                                    unmuteConfig = unmuteButtonConfig.copy(
                                        size = 46.dp,
                                        marginTop = 0.dp,
                                        marginEnd = 0.dp,
                                        marginBottom = 0.dp,
                                        marginStart = 0.dp
                                    ),
                                    isMuted = isMuted,
                                    onToggle = { isMuted = !isMuted }
                                )

                                Spacer(Modifier.width(12.dp))
                            }

                            // Cross button - using CrossButton component
                            // Only show if cross button is enabled
                            if (isCrossButtonEnabled) {
                                CrossButton(
                                    modifier = Modifier,
                                    config = crossButtonConfig.copy(
                                        size = 46.dp,
                                        marginTop = 0.dp,
                                        marginEnd = 0.dp
                                    ),
                                    onClose = {
                                        onHide(onClose)
                                    }
                                )
                            }
                        }
                    )

                    if (!button_text.isNullOrEmpty() && !link.isNullOrEmpty()) {
                        // Extract CTA config from pipStyling
                        val ctaConfig = pipStyling?.cta
                        val container = ctaConfig?.container
                        val cornerRadius = ctaConfig?.cornerRadius
                        val ctaMargin = ctaConfig?.margin
                        val ctaText = ctaConfig?.text

                        // Parse height from container (can be JsonElement)
                        val heightValue = container?.height?.let { h ->
                            when {
                                h is JsonPrimitive && h.intOrNull != null -> h.intOrNull
                                h is JsonPrimitive && h.content.isNotBlank() -> h.content.toIntOrNull()
                                else -> null
                            }
                        } ?: pipStyling?.ctaHeight?.toIntOrNull() ?: 48

                        // Parse borderWidth from container (can be JsonElement)
                        val borderWidthValue = container?.borderWidth?.let { w ->
                            when {
                                w is JsonPrimitive && w.intOrNull != null -> w.intOrNull
                                w is JsonPrimitive && w.content.isNotBlank() -> w.content.toIntOrNull()
                                else -> null
                            }
                        } ?: 0

                        val ctaButtonConfig = createCTAButtonConfig(
                            // Text styling
                            textColor = ctaText?.color ?: pipStyling?.ctaButtonTextColor ?: "#FFFFFF",
                            textSize = ctaText?.fontSize ?: pipStyling?.fontSize?.toIntOrNull() ?: 12,
                            fontFamily = ctaText?.fontFamily ?: pipStyling?.fontFamily,
                            fontDecoration = ctaText?.fontDecoration ?: ctaText?.textDecoration ?: pipStyling?.fontDecoration,

                            // Margins
                            marginTop = ctaMargin?.top ?: pipStyling?.marginTop?.toIntOrNull() ?: 0,
                            marginEnd = ctaMargin?.right ?: pipStyling?.marginRight?.toIntOrNull() ?: 0,
                            marginBottom = ctaMargin?.bottom ?: pipStyling?.marginBottom?.toIntOrNull() ?: 0,
                            marginStart = ctaMargin?.left ?: pipStyling?.marginLeft?.toIntOrNull() ?: 0,

                            // Container
                            height = heightValue,
                            width = container?.ctaWidth ?: pipStyling?.ctaWidth?.toIntOrNull(),
                            borderColorString = container?.borderColor ?: "#FE6B35",
                            borderWidth = borderWidthValue,
                            fullWidth = container?.ctaFullWidth ?: pipStyling?.ctaFullWidth ?: true,
                            backgroundColorString = container?.backgroundColor ?: pipStyling?.ctaButtonBackgroundColor ?: "#F7921C",
                            alignment = container?.alignment ?: "center",

                            // Corner radius
                            borderRadiusTopLeft = cornerRadius?.topLeft ?: pipStyling?.cornerRadius?.toIntOrNull() ?: 0,
                            borderRadiusTopRight = cornerRadius?.topRight ?: pipStyling?.cornerRadius?.toIntOrNull() ?: 0,
                            borderRadiusBottomLeft = cornerRadius?.bottomLeft ?: pipStyling?.cornerRadius?.toIntOrNull() ?: 0,
                            borderRadiusBottomRight = cornerRadius?.bottomRight ?: pipStyling?.cornerRadius?.toIntOrNull() ?: 0
                        )

                        // Use common CTAButton component
                        // CTAButton handles horizontal alignment internally based on config.alignment
                        CTAButton(
                            text = button_text,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .zIndex(10f),
                            config = ctaButtonConfig,
                            onClick = {
                                if (AppStorys.isValidUrl(link)) {
                                    AppStorys.openUrl(link)
                                } else {
                                    AppStorys.navigateToScreen(link)
                                }
                                onButtonClick()
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
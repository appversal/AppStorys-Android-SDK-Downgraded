package com.appversal.appstorys.presentation

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
import androidx.compose.ui.platform.LocalDensity
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
import com.appversal.appstorys.AppStorys.trackEvent
import com.appversal.appstorys.R
import com.appversal.appstorys.api.PipDetails
import com.appversal.appstorys.domain.State
import com.appversal.appstorys.domain.model.TypedCampaign
import com.appversal.appstorys.domain.rememberCampaign
import com.appversal.appstorys.domain.usecase.ClickEvent
import com.appversal.appstorys.utils.View
import com.appversal.appstorys.utils.googleFontProvider
import com.appversal.appstorys.utils.rememberPlayer
import com.appversal.appstorys.utils.toColor
import com.appversal.appstorys.utils.toDp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt


@Composable
internal fun Pip(
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    topPadding: Dp = 0.dp,
) {
    val campaign = rememberCampaign<PipDetails>("PIP")
    if (!campaign?.details?.smallVideo.isNullOrBlank()) {
        Content(
            campaign = campaign,
            modifier = modifier,
            bottomPadding = bottomPadding,
            topPadding = topPadding,
        )
    }
}

@Composable
private fun Content(
    campaign: TypedCampaign<PipDetails>,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    topPadding: Dp = 0.dp,
) {
    var showPip by remember { mutableStateOf(true) }
    var isFullScreen by remember { mutableStateOf(false) }

    val onDismiss: () -> Unit = remember(campaign) {
        {
            showPip = false
            campaign.triggerEvent?.let {
                State.removeTrackedEvent(it)
            }
        }
    }

    LaunchedEffect(campaign.id) {
        trackEvent(campaign.id, "viewed")
    }

    when {
        showPip && isFullScreen && !campaign.details.largeVideo.isNullOrEmpty() -> FullPlayer(
            details = campaign.details,
            onDismiss = {
                isFullScreen = false
            },
            onClose = onDismiss,
        )

        showPip && State.isVisible -> SmallPlayer(
            modifier = modifier,
            details = campaign.details,
            bottomPadding = bottomPadding,
            topPadding = topPadding,
            onDismiss = onDismiss,
            onExpand = {
                isFullScreen = true
                trackEvent(campaign.id, "clicked")
            }
        )
    }
}

@Composable
private fun SmallPlayer(
    details: PipDetails,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    topPadding: Dp = 0.dp,
    onDismiss: () -> Unit,
    onExpand: () -> Unit,
) {
    val styling = details.styling
    val density = LocalDensity.current.density
    val configuration = LocalConfiguration.current

    val height = details.height?.dp ?: 180.dp
    val width = details.width?.dp ?: 120.dp
    val screenWidth = configuration.screenWidthDp * density
    val screenHeight = configuration.screenHeightDp * density

    val hasLargeVideo = !details.largeVideo.isNullOrEmpty()

    val boundaryPadding = 12.dp
    val boundaryPaddingPx = with(LocalDensity.current) { boundaryPadding.toPx() }

    var isMuted by remember { mutableStateOf(true) }
    var pipSize by remember { mutableStateOf(IntSize(0, 0)) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isInitialized by remember { mutableStateOf(false) }

    val pipPlayer = rememberPlayer(details.smallVideo, isMuted, true)

    val bottomPaddingPx = with(LocalDensity.current) {
        (styling?.pipBottomPadding?.toFloatOrNull()?.dp ?: bottomPadding).toPx()
    }
    val topPaddingPx = with(LocalDensity.current) {
        (styling?.pipTopPadding?.toFloatOrNull()?.dp ?: topPadding).toPx()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        content = {
            when {
                isInitialized -> Card(
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
                        .then(
                            when {
                                styling?.isMovable == true -> Modifier.pointerInput("drag_gesture") {
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

                                else -> Modifier
                            }
                        ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    onClick = {
                        if (hasLargeVideo) {
                            onExpand()
                            pipPlayer.pause()
                        }
                    },
                    content = {
                        Box {
                            pipPlayer.View(modifier = Modifier.fillMaxSize())
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(23.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .clickable(onClick = onDismiss),
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
                                contentAlignment = Alignment.Center,
                                content = {
                                    Icon(
                                        painter = painterResource(if (isMuted) R.drawable.mute else R.drawable.volume),
                                        contentDescription = "Mute/Unmute",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                            if (hasLargeVideo) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .size(24.dp)
                                        .background(
                                            Color.Black.copy(alpha = 0.5f),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center,
                                    content = {
                                        Icon(
                                            painter = painterResource(R.drawable.expand),
                                            contentDescription = "Maximize",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
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

                            offsetX = if (details.position == "left") {
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


@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayer(
    details: PipDetails,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val styling = details.styling

    var isMuted by remember { mutableStateOf(false) }

    val player = rememberPlayer(details.largeVideo, isMuted, true)
    val onHide = remember {
        { action: () -> Unit ->
            scope.launch {
                sheetState.hide()
                action()
            }
        }
    }

    DisposableEffect(Unit) {
        State.isVisible = false

        onDispose {
            State.isVisible = true
        }
    }

    LaunchedEffect(sheetState.targetValue) {
        when (sheetState.targetValue) {
            SheetValue.Hidden -> player.pause()
            else -> player.play()
        }
    }

    ModalBottomSheet(
        modifier = modifier
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
                    player.View(modifier = Modifier.fillMaxWidth())
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
                                        painter = painterResource(if (isMuted) R.drawable.mute else R.drawable.volume),
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

                    if (!details.buttonText.isNullOrBlank() && !details.link.isNullOrBlank()) {
                        Button(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(
//                                top = styling?.marginTop.toDp(),
                                    bottom = styling?.marginBottom.toDp(),
                                    start = styling?.marginLeft.toDp(),
                                    end = styling?.marginRight.toDp()
                                )
                                .then(
                                    when {
                                        styling?.ctaFullWidth == true -> Modifier.fillMaxWidth()
                                        else -> Modifier.width(styling?.ctaWidth.toDp())
                                    }
                                )
                                .height(styling?.ctaHeight.toDp()),
                            shape = RoundedCornerShape(styling?.cornerRadius.toDp()),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = styling?.ctaButtonBackgroundColor.toColor()
                            ),
                            onClick = {
                                ClickEvent(details.link)
                            },
                            content = {
                                Text(
                                    fontFamily = FontFamily(
                                        Font(
                                            googleFont = GoogleFont("Poppins"),
                                            fontProvider = googleFontProvider,
                                            FontWeight.Normal,
                                            FontStyle.Normal
                                        )
                                    ),
                                    text = details.buttonText,
                                    color = styling?.ctaButtonTextColor.toColor(
                                        Color.White
                                    ),
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
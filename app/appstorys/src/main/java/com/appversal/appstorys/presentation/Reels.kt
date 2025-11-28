package com.appversal.appstorys.presentation

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import coil.compose.rememberAsyncImagePainter
import com.appversal.appstorys.api.ApiService
import com.appversal.appstorys.api.ReelStatusRequest
import com.appversal.appstorys.api.ReelsDetails
import com.appversal.appstorys.domain.State
import com.appversal.appstorys.domain.model.TypedCampaign
import com.appversal.appstorys.domain.rememberCampaign
import com.appversal.appstorys.domain.usecase.ActionType
import com.appversal.appstorys.domain.usecase.ClickEvent
import com.appversal.appstorys.domain.usecase.launchTask
import com.appversal.appstorys.domain.usecase.trackEvent
import com.appversal.appstorys.domain.usecase.trackUserAction
import com.appversal.appstorys.utils.View
import com.appversal.appstorys.utils.rememberPlayer
import com.appversal.appstorys.utils.rememberSharedPreferences
import com.appversal.appstorys.utils.toColor
import com.appversal.appstorys.utils.toDp


@Composable
internal fun Reels(
    modifier: Modifier = Modifier
) {
    val campaign = rememberCampaign<ReelsDetails>("REL")

    if (campaign?.details?.reels?.isNotEmpty() == true) {
        Content(
            modifier = modifier,
            campaign = campaign,
        )
    }
}

@Composable
private fun Content(
    campaign: TypedCampaign<ReelsDetails>,
    modifier: Modifier = Modifier,
) {
    val details = campaign.details
    val reels = details.reels ?: emptyList()
    val styling = details.styling

    var selectedReel by remember { mutableStateOf<Int?>(null) }

    Box(
        modifier = Modifier.fillMaxSize(),
        content = {
            LazyRow(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                content = {
                    items(reels.size) { index ->
                        Box(
                            modifier = Modifier
                                .width(styling?.thumbnailWidth.toDp(120.dp))
                                .height(styling?.thumbnailHeight.toDp(180.dp))
                                .padding(end = 10.dp)
                                .clip(RoundedCornerShape(styling?.cornerRadius.toDp(12.dp)))
                                .clickable { selectedReel = index },
                            content = {
                                Image(
                                    painter = rememberAsyncImagePainter(reels[index].thumbnail),
                                    contentDescription = "Thumbnail",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        )
                    }
                }
            )

            val index = selectedReel
            if (index != null) {
                Screen(
                    campaignId = campaign.id,
                    details = details,
                    selectedIndex = index,
                    onDismiss = { selectedReel = null }
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Screen(
    campaignId: String,
    details: ReelsDetails,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val reels = remember(details.reels) {
        details.reels?.filter { !it.id.isNullOrBlank() } ?: emptyList()
    }
    val styling = details.styling

    val prefs = rememberSharedPreferences()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pagerState = rememberPagerState(initialPage = selectedIndex, pageCount = { reels.size })

    val likes = remember(prefs, reels) {
        val map = mutableStateMapOf<String, Int>()
        reels.forEach { reel ->
            if (reel.likes != null && reel.likes > 0) {
                map[reel.id.orEmpty()] = reel.likes
            }
        }
        prefs.getStringSet("liked_reels", emptySet())?.forEach { id ->
            if (map.containsKey(id)) {
                map[id] = map[id]?.plus(1) ?: 1
            }
        }
        map
    }

    val toggleLike = remember(prefs, likes) {
        { id: String ->
            val isLike = !likes.contains(id)
            likes[id] = when {
                isLike -> 1
                else -> likes[id]?.minus(1) ?: 0
            }

            prefs.edit {
                putStringSet("liked_reels", likes.filter { it.value > 0 }.keys)
            }

            launchTask {
                try {
                    ApiService.getInstance().sendReelLikeStatus(
                        ReelStatusRequest(
                            action = when {
                                isLike -> "like"
                                else -> "unlike"
                            },
                            reel = id
                        )
                    )
                } catch (_: Exception) {

                }
            }
        }
    }

    val shareLink = remember(context) {
        { link: String ->
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, link)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(
                sendIntent,
                null
            )
            context.startActivity(shareIntent)
        }
    }

    val clickLink = remember(context) {
        { link: String, id: String? ->
            ClickEvent(context, link)
            launchTask {
                trackUserAction(campaignId, ActionType.CLK, reelId = id)
            }
        }
    }

    BackHandler(onBack = onDismiss)

    DisposableEffect(Unit) {
        State.isVisible = false

        onDispose {
            State.isVisible = true
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
            LaunchedEffect(pagerState.currentPage) {
                val id = reels[pagerState.currentPage].id ?: return@LaunchedEffect
                trackUserAction(
                    campaignId,
                    ActionType.IMP,
                    reelId = id
                )
                trackEvent(
                    context,
                    "viewed",
                    campaignId,
                    mapOf("reel_id" to id)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize(),
                content = {
                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 20,
                        pageContent = { page ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black),
                                content = {
                                    val reel = reels[page]

                                    val video = reel.video
                                    if (pagerState.currentPage == page && video != null) {
                                        val player = rememberPlayer(
                                            video,
                                            muted = false,
                                            extraSetup = true
                                        )

                                        player.View(Modifier.fillMaxSize())

                                        LaunchedEffect(sheetState.targetValue) {
                                            when (sheetState.targetValue) {
                                                SheetValue.Hidden -> player.pause()
                                                else -> player.play()
                                            }
                                        }
                                    }

                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        content = {
                                            Spacer(modifier = Modifier.weight(1f))

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                content = {
                                                    Spacer(modifier = Modifier.weight(20f))

                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        content = {
                                                            Column(
                                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                                modifier = Modifier.clickable {
                                                                    toggleLike(reel.id.orEmpty())
                                                                },
                                                                content = {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Favorite,
                                                                        contentDescription = "Like",
                                                                        tint = when {
                                                                            likes.contains(reel.id) -> styling?.likeButtonColor.toColor()
                                                                            else -> Color.White
                                                                        },
                                                                        modifier = Modifier.size(32.dp)
                                                                    )
                                                                    Text(
                                                                        text = likes[reel.id].toString(),
                                                                        color = Color.White,
                                                                        style = MaterialTheme.typography.labelMedium
                                                                    )
                                                                }
                                                            )

                                                            if (!reel.link.isNullOrBlank()) {
                                                                Column(
                                                                    modifier = Modifier
                                                                        .padding(top = 8.dp)
                                                                        .clickable {
                                                                            shareLink(reel.link)
                                                                        },
                                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                                    content = {
                                                                        Icon(
                                                                            imageVector = Icons.Default.Share,
                                                                            contentDescription = "Share",
                                                                            tint = Color.White,
                                                                            modifier = Modifier.size(
                                                                                32.dp
                                                                            )
                                                                        )
                                                                        Text(
                                                                            text = "Share",
                                                                            color = Color.White,
                                                                            style = MaterialTheme.typography.labelMedium
                                                                        )
                                                                    }
                                                                )
                                                            }

                                                            Spacer(modifier = Modifier.height(12.dp))
                                                        }
                                                    )

                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            )


                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0f, 0f, 0f, 0.3f))
                                                    .padding(horizontal = 24.dp),
                                                content = {
                                                    if (!reel.descriptionText.isNullOrEmpty()) {
                                                        Text(
                                                            text = reel.descriptionText,
                                                            color = styling?.descriptionTextColor.toColor(),
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.padding(top = 20.dp)
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.height(16.dp))

                                                    if (!reel.link.isNullOrEmpty() && !reel.buttonText.isNullOrEmpty()) {
                                                        Button(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(bottom = 16.dp),
                                                            onClick = {
                                                                clickLink(reel.link, reel.id)
                                                            },
                                                            shape = RoundedCornerShape(12.dp),
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = styling?.ctaBoxColor.toColor()
                                                            ),
                                                            content = {
                                                                Text(
                                                                    text = reel.buttonText,
                                                                    color = styling?.ctaTextColor.toColor(
                                                                        Color.White
                                                                    ),
                                                                    style = MaterialTheme.typography.labelLarge.copy(
                                                                        fontWeight = FontWeight.Bold
                                                                    )
                                                                )
                                                            }
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.fillMaxHeight(0.02f))
                                                }
                                            )
                                        }
                                    )
                                }
                            )
                        }
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp),
                        content = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
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
package com.appversal.appstorys.ui.reels

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.rememberAsyncImagePainter
import com.appversal.appstorys.api.Reel
import com.appversal.appstorys.api.ReelsDetails
import com.appversal.appstorys.ui.common_components.LikeButton
import com.appversal.appstorys.ui.common_components.createLikeButtonConfig
import com.appversal.appstorys.ui.common_components.ShareButton
import com.appversal.appstorys.ui.common_components.createShareButtonConfig
import com.appversal.appstorys.ui.common_components.ArrowButton
import com.appversal.appstorys.ui.common_components.createArrowButtonConfig
import org.json.JSONArray
import kotlin.collections.get

@Composable
internal fun ReelsRow(
    modifier: Modifier,
    reels: List<Reel>,
    onReelClick: (Int) -> Unit,
    height: Dp,
    width: Dp,
    cornerRadius: Dp,
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        items(reels.size) { index ->
            Box(
                modifier = Modifier
                    .width(width)
                    .height(height)
                    .padding(end = 10.dp)
                    .clip(RoundedCornerShape(cornerRadius))
                    .clickable { onReelClick(index) }
            ) {
                Image(
                    painter = rememberAsyncImagePainter(reels[index].thumbnail),
                    contentDescription = "Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

}

@Composable
internal fun FullScreenVideoScreen(
    reelsDetails: ReelsDetails,
    reels: List<Reel>,
    likedReels: List<String>,
    startIndex: Int,
    onBack: () -> Unit,
    sendLikesStatus: (Pair<Reel, String>) -> Unit,
    sendEvents: (Pair<Reel, String>) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { reels.size })
    val context = LocalContext.current

    val likesState = remember {
        mutableStateMapOf<String, Int>().apply {
            reels.forEach { reel ->
                reel.id?.let { this[it] = reel.likes ?: 0 }
            }
        }
    }

    LaunchedEffect(Unit) {
        likedReels.forEach {
            likesState[it] = likesState[it]?.plus(1) ?: 1
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        sendEvents(Pair(reels[pagerState.currentPage], "IMP"))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize(),
            beyondViewportPageCount = 20
        ) { page ->

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {

                if (pagerState.currentPage == page) {
                    reels[page].video?.let {
                        VideoPlayer(
                            url = it,
                            isPlaying = pagerState.currentPage == page
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Spacer(modifier = Modifier.weight(1f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Spacer(modifier = Modifier.weight(20f))

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // NEW IMPLEMENTATION: Using common LikeButton component
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val isLiked = likedReels.contains(reels[page].id)
                                val likeButtonConfig = createLikeButtonConfig(
                                    iconColorString = if (isLiked) reelsDetails.styling?.likeButtonColor else "#FFFFFF",
                                    size = 32
                                )

                                LikeButton(
                                    likedConfig = likeButtonConfig,
                                    unlikedConfig = createLikeButtonConfig(
                                        iconColorString = "#FFFFFF",
                                        size = 32
                                    ),
                                    isLiked = isLiked,
                                    onToggle = {
                                        if (!likedReels.contains(reels[page].id)) {
                                            reels[page].id?.let{
                                                likesState[it] = likesState[reels[page].id]?.plus(1) ?: 1
                                            }
                                        } else {
                                            reels[page].id?.let{
                                                likesState[it] = likesState[reels[page].id]?.minus(1) ?: 1
                                            }
                                        }

                                        sendLikesStatus(
                                            Pair(
                                                reels[page],
                                                if (!likedReels.contains(reels[page].id)) "like" else "unlock"
                                            )
                                        )
                                    }
                                )

                                /* OLD IMPLEMENTATION: Inline like button (commented out)
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Favorite,
                                    contentDescription = "Like",
                                    tint = if (likedReels.contains(reels[page].id)) Color(android.graphics.Color.parseColor(reelsDetails.styling?.likeButtonColor)) else Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                                */

                                Text(
                                    text = likesState[reels[page].id].toString(),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // NEW IMPLEMENTATION: Using common ShareButton component
                            if (!reels[page].link.isNullOrEmpty()) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val shareButtonConfig = createShareButtonConfig(
                                        iconColorString = "#FFFFFF",
                                        size = 32
                                    )

                                    ShareButton(
                                        config = shareButtonConfig,
                                        onShare = {
                                            val sendIntent = Intent().apply {
                                                action = Intent.ACTION_SEND
                                                putExtra(
                                                    Intent.EXTRA_TEXT,
                                                    reels[page].link
                                                )
                                                type = "text/plain"
                                            }
                                            val shareIntent =
                                                Intent.createChooser(sendIntent, null)
                                            context.startActivity(shareIntent)
                                        }
                                    )

                                    /* OLD IMPLEMENTATION: Inline share button (commented out)
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.Share,
                                        contentDescription = "Share",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    */

                                    Text(
                                        text = "Share",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0f, 0f, 0f, 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                        ) {
                            if (!reels[page].descriptionText.isNullOrEmpty()) {
                                Text(
                                    text = reels[page].descriptionText ?: "",
                                    color = Color(android.graphics.Color.parseColor(reelsDetails.styling?.descriptionTextColor)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (!reels[page].link.isNullOrEmpty() && !reels[page].buttonText.isNullOrEmpty()) {
                                Button(
                                    onClick = {
                                        sendEvents(Pair(reels[page], "CLK"))
                                        try {
                                            val uri = Uri.parse(reels[page].link)
                                            val intent = Intent(
                                                Intent.ACTION_VIEW,
                                                uri
                                            )
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                context,
                                                "Could not open link",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(android.graphics.Color.parseColor(reelsDetails.styling?.ctaBoxColor))
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp)
                                ) {
                                    Text(
                                        text = reels[page].buttonText ?: "",
                                        color = Color(android.graphics.Color.parseColor(reelsDetails.styling?.ctaTextColor)),
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.fillMaxHeight(0.02f))
                        }
                    }
                }
            }
        }

        // NEW IMPLEMENTATION: Using common ArrowButton component
        val arrowButtonConfig = createArrowButtonConfig(
            iconColorString = "#FFFFFF",
            fillColorString = null, // Transparent background
            size = 28
        )

        ArrowButton(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            config = arrowButtonConfig,
            onBack = onBack
        )

        /* OLD IMPLEMENTATION: Inline arrow button (commented out)
        IconButton(
            onClick = { onBack() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        */
    }
}

@Composable
fun VideoPlayer(
    modifier: Modifier = Modifier,
    url: String,
    isPlaying: Boolean
) {
    val context = LocalContext.current

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
        }
    }

    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(isPlaying) {
        if (!initialized) {
            val mediaItem = MediaItem.fromUri(url.toUri())
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            initialized = true
        }
        exoPlayer.playWhenReady = isPlaying
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }


    if (isPlaying) {

        AndroidView(
            modifier = modifier.fillMaxSize(),
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            }
        )
    }
}

internal fun saveLikedReels(idList: List<String>, sharedPreferences: SharedPreferences) {
    val jsonArray = JSONArray(idList)
    sharedPreferences.edit().putString("LIKED_REELS", jsonArray.toString()).apply()
}

internal fun getLikedReels(sharedPreferences: SharedPreferences): List<String> {
    val jsonString = sharedPreferences.getString("LIKED_REELS", "[]") ?: "[]"
    val jsonArray = JSONArray(jsonString)
    return List(jsonArray.length()) { jsonArray.getString(it) }
}

@Composable
fun getScreenHeight(): Int {
    val configuration = LocalConfiguration.current
    return configuration.screenHeightDp
}

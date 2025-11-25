package com.appversal.appstorys.utils

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import java.io.File


@OptIn(UnstableApi::class)
@Composable
internal fun rememberPlayer(
    videoUri: String? = null,
    muted: Boolean = false,
    extraSetup: Boolean = false
): ExoPlayer {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val player = remember {
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(5000)
            .setLoadControl(DefaultLoadControl())
            .setSeekForwardIncrementMs(5000)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(VideoCache.getFactory(context))
            )
            .build()
            .apply {
                if (!videoUri.isNullOrBlank()) {
                    setMediaItem(MediaItem.fromUri(videoUri.toUri()))
                }
                repeatMode = when {
                    extraSetup -> Player.REPEAT_MODE_ALL
                    else -> Player.REPEAT_MODE_OFF
                }
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                volume = if (muted) 0f else 1.0f
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

    LaunchedEffect(muted) {
        player.volume = if (muted) 0f else 1.0f
    }

    return player
}

@OptIn(UnstableApi::class)
@Composable
internal fun ExoPlayer.View(
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = this@View
                useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                @Suppress("DEPRECATION")
                useArtwork = false
                setKeepContentOnPlayerReset(true)
            }
        },
    )
}

@UnstableApi
private object VideoCache {
    private var simpleCache: SimpleCache? = null
    private var cacheDataSourceFactory: CacheDataSource.Factory? = null

    // Cache size in bytes (1 GB)
    private const val CACHE_SIZE = 1024L * 1024 * 1024

    fun getCache(context: Context): SimpleCache {
        if (simpleCache == null) {
            simpleCache = SimpleCache(
                File(context.cacheDir, "video_cache"),
                LeastRecentlyUsedCacheEvictor(CACHE_SIZE),
                StandaloneDatabaseProvider(context)
            )
        }
        return simpleCache!!
    }

    fun getFactory(context: Context): CacheDataSource.Factory {
        if (cacheDataSourceFactory == null) {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)

            val defaultDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

            cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(getCache(context))
                .setUpstreamDataSourceFactory(defaultDataSourceFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }
        return cacheDataSourceFactory!!
    }

    fun releaseCache() {
        try {
            simpleCache?.release()
        } catch (e: Exception) {
            // Handle release exception
        } finally {
            simpleCache = null
            cacheDataSourceFactory = null
        }
    }
}
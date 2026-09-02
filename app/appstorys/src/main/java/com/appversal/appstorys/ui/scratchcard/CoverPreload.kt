package com.appversal.appstorys.ui.scratchcard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One [ImageLoader] for the whole scratch card, for the life of the process.
 *
 * The cover used to be fetched by an `ImageLoader(context)` built inside a
 * `LaunchedEffect`, so every load started with an empty memory cache and Coil
 * ended up with several loaders over one disk-cache directory, which it does not
 * support.
 *
 * Only ever touched from composition (main thread), so no locking.
 */
private var sharedImageLoader: ImageLoader? = null

internal fun scratchCardImageLoader(context: Context): ImageLoader =
    sharedImageLoader ?: ImageLoader(context.applicationContext).also { sharedImageLoader = it }

/**
 * Builds the cover request. Used by both the prefetch and the composable read, so
 * their memory-cache keys match — Coil keys on size and options as well as the URL,
 * so a prefetch that differs in any of these warms an entry nobody ever reads.
 */
private fun coverRequest(context: Context, url: String, widthPx: Int, heightPx: Int) =
    ImageRequest.Builder(context)
        .data(url)
        // Same size the card draws at, so this is the only decode of what may be
        // a multi-megabyte source image.
        .size(widthPx, heightPx)
        // Drawn into a software Canvas, so it cannot be a hardware bitmap.
        .allowHardware(false)
        .diskCachePolicy(CachePolicy.ENABLED)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .build()

/**
 * Warms the cover into Coil's cache as soon as the campaign is known, which is
 * typically seconds before the user reaches the screen. Without it the fetch only
 * starts once the card is about to be shown, and the card is held back for the
 * whole download.
 *
 * Fire and forget: if it has not finished by the time the card composes,
 * [rememberCover] simply waits on the same request.
 */
internal fun prefetchScratchCardCover(context: Context, url: String, widthPx: Int, heightPx: Int) {
    if (url.isEmpty() || widthPx <= 0 || heightPx <= 0) return
    runCatching {
        scratchCardImageLoader(context).enqueue(coverRequest(context, url, widthPx, heightPx))
    }.onFailure { Log.w("ScratchCard", "Cover prefetch failed to start: ${it.message}") }
}

/** How long to wait for the cover before showing the card without it. */
private const val COVER_TIMEOUT_MS = 5_000L

internal sealed class CoverState {
    object Loading : CoverState()
    /** [bitmap] is null when the cover failed or timed out — the card falls back to grey. */
    data class Ready(val bitmap: Bitmap?) : CoverState()
}

/**
 * Decodes the cover at card size and hands back the [Bitmap] itself.
 *
 * Returning the bitmap rather than a "ready" flag is the whole point. The scratch
 * bitmap is erased to grey at creation and the card paints immediately; anything
 * that draws the cover afterwards — however warm the cache — lands a frame or more
 * later, which is the ~400ms grey flash. [ScratchableCard] instead seeds its
 * scratch bitmap with this, so the first painted frame already has the artwork and
 * there is no grey frame to see.
 *
 * Decoded once, at [widthPx] x [heightPx], software-backed because it gets drawn
 * into a software [android.graphics.Canvas].
 *
 * Resolves to [CoverState.Ready] on failure and timeout as well as success: a cover
 * that 404s or a dead connection must not hide the campaign forever.
 */
@Composable
internal fun rememberCover(
    url: String,
    imageLoader: ImageLoader,
    widthPx: Int,
    heightPx: Int
): CoverState {
    val context = LocalContext.current
    var state by remember(url, widthPx, heightPx) {
        mutableStateOf<CoverState>(
            if (url.isEmpty()) CoverState.Ready(null) else CoverState.Loading
        )
    }

    LaunchedEffect(url, widthPx, heightPx) {
        if (url.isEmpty() || widthPx <= 0 || heightPx <= 0) {
            state = CoverState.Ready(null)
            return@LaunchedEffect
        }
        val request = coverRequest(context, url, widthPx, heightPx)

        val bitmap = withTimeoutOrNull(COVER_TIMEOUT_MS) {
            runCatching { (imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap }
                .getOrNull()
        }
        if (bitmap == null) {
            Log.w("ScratchCard", "Cover unavailable within ${COVER_TIMEOUT_MS}ms, card falls back to grey: $url")
        }
        state = CoverState.Ready(bitmap)
    }

    return state
}

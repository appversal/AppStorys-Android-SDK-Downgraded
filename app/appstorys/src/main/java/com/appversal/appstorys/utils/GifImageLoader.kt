package com.appversal.appstorys.utils

import android.content.Context
import android.os.Build.VERSION.SDK_INT
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder

/**
 * One GIF-capable ImageLoader for the whole SDK.
 *
 * Each component used to build its own loader — several on every recomposition — and every
 * new loader starts with an empty memory cache and its own OkHttpClient. The disk cache was
 * already shared (Coil's singleton `image_cache`), and still is.
 */
object GifImageLoader {
    @Volatile
    private var loader: ImageLoader? = null

    fun get(context: Context): ImageLoader = loader ?: synchronized(this) {
        loader ?: ImageLoader.Builder(context.applicationContext)
            .components {
                if (SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .build()
            .also { loader = it }
    }
}

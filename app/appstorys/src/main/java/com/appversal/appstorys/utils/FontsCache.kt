package com.appversal.appstorys.utils

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest

/**
 * FontCache manages downloading, caching, and loading custom fonts from URLs.
 * Features:
 * - Downloads fonts only once and caches them locally
 * - Thread-safe font loading
 * - Automatic fallback to default fonts on failure
 * - Memory cache for loaded FontFamily objects
 */
object FontCache {
    private const val TAG = "FontCache"
    private const val FONT_CACHE_DIR = "custom_fonts"

    // Memory cache for loaded FontFamily objects
    private val fontFamilyCache = mutableMapOf<String, FontFamily>()

    // Track downloading fonts to prevent duplicate downloads
    private val downloadingFonts = mutableSetOf<String>()

    /**
     * Load a custom font from URL with caching support
     *
     * @param context Application context
     * @param fontUrl URL of the font file (e.g., .ttf, .otf)
     * @param weight FontWeight to apply (default: Normal)
     * @param style FontStyle to apply (default: Normal)
     * @return FontFamily object or null if loading fails
     */
    suspend fun loadFont(
        context: Context,
        fontUrl: String?,
        weight: FontWeight = FontWeight.Normal,
        style: FontStyle = FontStyle.Normal
    ): FontFamily? {
        if (fontUrl.isNullOrBlank()) return null

        return try {
            // Check memory cache first
            fontFamilyCache[fontUrl]?.let {
                Log.d(TAG, "Font loaded from memory cache: $fontUrl")
                return it
            }

            // Check if font is already downloaded
            val cacheDir = getFontCacheDir(context)
            val fontFile = getFontFile(cacheDir, fontUrl)

            if (!fontFile.exists()) {
                // Download font if not cached
                downloadFont(fontUrl, fontFile)
            }

            // Load the font
            val fontFamily = loadFontFromFile(fontFile, weight, style)

            // Cache in memory
            if (fontFamily != null) {
                fontFamilyCache[fontUrl] = fontFamily
                Log.d(TAG, "Font loaded successfully: $fontUrl")
            }

            fontFamily
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load font from $fontUrl", e)
            null
        }
    }

    /**
     * Get or create font cache directory
     */
    private fun getFontCacheDir(context: Context): File {
        val cacheDir = File(context.cacheDir, FONT_CACHE_DIR)
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        return cacheDir
    }

    /**
     * Generate a unique filename for the font URL
     */
    private fun getFontFile(cacheDir: File, fontUrl: String): File {
        // Extract extension from URL
        val extension = fontUrl.substringAfterLast('.', "ttf")
            .substringBefore('?') // Remove query params if any

        // Create hash of URL for filename
        val hash = fontUrl.hashCode().toString()

        return File(cacheDir, "font_$hash.$extension")
    }

    /**
     * Download font from URL
     */
    private suspend fun downloadFont(fontUrl: String, destFile: File) = withContext(Dispatchers.IO) {
        // Prevent duplicate downloads
        synchronized(downloadingFonts) {
            if (downloadingFonts.contains(fontUrl)) {
                Log.d(TAG, "Font already being downloaded: $fontUrl")
                // Wait for download to complete
                while (downloadingFonts.contains(fontUrl)) {
                    Thread.sleep(100)
                }
                return@withContext
            }
            downloadingFonts.add(fontUrl)
        }

        try {
            Log.d(TAG, "Downloading font: $fontUrl")

            val url = URL(fontUrl)
            val connection = url.openConnection()
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            connection.getInputStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            Log.d(TAG, "Font downloaded successfully: $fontUrl")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download font: $fontUrl", e)
            // Clean up partial download
            if (destFile.exists()) {
                destFile.delete()
            }
            throw e
        } finally {
            synchronized(downloadingFonts) {
                downloadingFonts.remove(fontUrl)
            }
        }
    }

    /**
     * Load font from cached file
     */
    private fun loadFontFromFile(
        fontFile: File,
        weight: FontWeight,
        style: FontStyle
    ): FontFamily? {
        return try {
            val typeface = Typeface.createFromFile(fontFile)
            if (typeface != null) {
                // Create FontFamily from file
                FontFamily(
                    Font(
                        file = fontFile,
                        weight = weight,
                        style = style
                    )
                )
            } else {
                Log.e(TAG, "Failed to create typeface from file: ${fontFile.path}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading font from file: ${fontFile.path}", e)
            null
        }
    }

    /**
     * Clear all cached fonts from disk
     */
    fun clearCache(context: Context) {
        try {
            val cacheDir = getFontCacheDir(context)
            cacheDir.listFiles()?.forEach { it.delete() }
            fontFamilyCache.clear()
            Log.d(TAG, "Font cache cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing font cache", e)
        }
    }

    /**
     * Get cache size in bytes
     */
    fun getCacheSize(context: Context): Long {
        return try {
            val cacheDir = getFontCacheDir(context)
            cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating cache size", e)
            0L
        }
    }

    /**
     * Check if font is cached
     */
    fun isFontCached(context: Context, fontUrl: String): Boolean {
        val cacheDir = getFontCacheDir(context)
        val fontFile = getFontFile(cacheDir, fontUrl)
        return fontFile.exists()
    }
}
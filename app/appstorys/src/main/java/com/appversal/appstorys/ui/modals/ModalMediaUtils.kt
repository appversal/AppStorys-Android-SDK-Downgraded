package com.appversal.appstorys.ui.modals

import com.appversal.appstorys.api.Modal
import com.appversal.appstorys.api.ModalContent
import com.appversal.appstorys.api.ModalMedia

/**
 * =============================================================================
 * MODAL MEDIA UTILITIES
 * =============================================================================
 *
 * This file contains all utility functions for working with media in modals.
 *
 * There are TWO main concerns when working with modal media:
 *
 * 1. FINDING MEDIA (WHERE is the media URL?)
 *    - Modal.resolveMediaUrl() - Finds media URL from a Modal object
 *    - ModalContent.resolveMediaUrl() - Finds media URL from a carousel slide
 *
 * 2. DETERMINING RENDER TYPE (HOW should we render this media?)
 *    - determineMediaType() - Analyzes URL to decide: image, gif, lottie, or video
 *
 * =============================================================================
 *
 * USAGE EXAMPLES:
 *
 * // For MediaOnlyModal or PopupModal:
 * val mediaUrl = modal.resolveMediaUrl()
 * val mediaType = determineMediaType(mediaUrl)
 *
 * // For FullPageCarouselModal slides:
 * val slideMediaUrl = slide.resolveMediaUrl()
 * val slideMediaType = determineMediaType(slideMediaUrl)
 *
 * // The mediaType will be one of: "image", "gif", "lottie", "video"
 * // Use this to decide which renderer to use (AsyncImage, LottieAnimation, VideoPlayer, etc.)
 *
 * =============================================================================
 */

// =============================================================================
// SECTION 1: FINDING MEDIA (WHERE is the media?)
// =============================================================================

/**
 * Resolves and returns the media URL from a Modal object.
 *
 * This function searches through multiple possible locations in the Modal
 * where the media URL might be stored (due to different backend payload formats).
 *
 * Search order:
 * 1. content.chooseMediaType.url - Media defined in content
 * 2. content.set[0].chooseMediaType.url - First slide of carousel
 * 3. chooseMediaType.url - Top-level media field
 * 4. url - Direct URL field
 * 5. redirection.url - Redirect URL (if it's a media file)
 * 6. link - Link field (if it's a media file)
 *
 * @return The media URL string, or null if no media found
 *
 * Example:
 * ```kotlin
 * val modal: Modal = ...
 * val mediaUrl = modal.resolveMediaUrl()
 * if (mediaUrl != null) {
 *     // Load and display the media
 * }
 * ```
 */
fun Modal.resolveMediaUrl(): String? {
    // Priority 1: Content-level media
    content?.chooseMediaType?.url?.takeIf { it.isNotBlank() }?.let { return it }

    // Priority 2: First slide of carousel
    content?.set?.firstOrNull()?.chooseMediaType?.url?.takeIf { it.isNotBlank() }?.let { return it }

    // Priority 3: Top-level chooseMediaType
    chooseMediaType?.url?.takeIf { it.isNotBlank() }?.let { return it }

    // Priority 4: Direct URL field (if it looks like a media file)
    url?.takeIf { it.isMediaUrl() }?.let { return it }

    // Priority 5: Redirection URL (if it looks like a media file)
    redirection?.url?.takeIf { it.isMediaUrl() }?.let { return it }

    // Priority 6: Link field (if it looks like a media file)
    link?.takeIf { it.isMediaUrl() }?.let { return it }

    return null
}

/**
 * Resolves the full ModalMedia object from a Modal.
 * Use this when you need both the URL and the type hint.
 *
 * @return ModalMedia object with url and type, or null if no media found
 */
fun Modal.resolveMedia(): ModalMedia? {
    // Priority 1: Content-level media
    content?.chooseMediaType?.takeIf { !it.url.isNullOrBlank() }?.let { return it }

    // Priority 2: First slide of carousel
    content?.set?.firstOrNull()?.chooseMediaType?.takeIf { !it.url.isNullOrBlank() }?.let { return it }

    // Priority 3: Top-level chooseMediaType
    chooseMediaType?.takeIf { !it.url.isNullOrBlank() }?.let { return it }

    // Priority 4-6: Fallback URLs (create ModalMedia with type="auto")
    val fallbackUrl = url?.takeIf { it.isMediaUrl() }
        ?: redirection?.url?.takeIf { it.isMediaUrl() }
        ?: link?.takeIf { it.isMediaUrl() }

    return fallbackUrl?.let { ModalMedia(type = "auto", url = it) }
}

/**
 * Resolves the media URL from a ModalContent (carousel slide).
 *
 * @return The media URL string, or null if no media found
 *
 * Example:
 * ```kotlin
 * val slide: ModalContent = carousel.slides[0]
 * val mediaUrl = slide.resolveMediaUrl()
 * ```
 */
fun ModalContent.resolveMediaUrl(): String? {
    return chooseMediaType?.url?.takeIf { it.isNotBlank() }
}

/**
 * Resolves the full ModalMedia object from a ModalContent (carousel slide).
 *
 * @return ModalMedia object with url and type, or null if no media found
 */
fun ModalContent.resolveMedia(): ModalMedia? {
    return chooseMediaType?.takeIf { !it.url.isNullOrBlank() }
}

// =============================================================================
// SECTION 2: DETERMINING RENDER TYPE (HOW to render?)
// =============================================================================

/**
 * Supported media types for rendering.
 */
object MediaType {
    const val IMAGE = "image"   // PNG, JPG, JPEG, WebP - rendered with AsyncImage
    const val GIF = "gif"       // Animated GIF - rendered with Coil GIF decoder
    const val LOTTIE = "lottie" // Lottie JSON animation - rendered with LottieAnimation
    const val VIDEO = "video"   // MP4, M3U8 - rendered with ExoPlayer
}

/**
 * Determines HOW to render the media based on URL extension and optional type hint.
 *
 * This is the main function to decide which renderer to use:
 * - "image" → Use AsyncImage (Coil)
 * - "gif" → Use AsyncImage with GIF decoder
 * - "lottie" → Use LottieAnimation
 * - "video" → Use ExoPlayer/VideoPlayerInline
 *
 * @param url The media URL to analyze
 * @param typeHint Optional type hint from backend (e.g., ModalMedia.type)
 * @return One of: MediaType.IMAGE, MediaType.GIF, MediaType.LOTTIE, MediaType.VIDEO
 *
 * Example:
 * ```kotlin
 * val mediaUrl = modal.resolveMediaUrl()
 * val mediaType = determineMediaType(mediaUrl)
 *
 * when (mediaType) {
 *     MediaType.IMAGE -> // Render with AsyncImage
 *     MediaType.GIF -> // Render with AsyncImage + GIF decoder
 *     MediaType.LOTTIE -> // Render with LottieAnimation
 *     MediaType.VIDEO -> // Render with ExoPlayer
 * }
 * ```
 */
fun determineMediaType(url: String?, typeHint: String? = null): String {
    // First, check if we have a valid type hint from the backend
    val normalizedHint = typeHint?.trim()?.lowercase()
    if (!normalizedHint.isNullOrBlank()) {
        when (normalizedHint) {
            "gif" -> return MediaType.GIF
            "lottie", "json" -> return MediaType.LOTTIE
            "video", "mp4", "m3u8" -> return MediaType.VIDEO
            "image", "png", "jpg", "jpeg", "webp" -> return MediaType.IMAGE
            // "auto" or unknown - fall through to URL detection
        }
    }

    // Fallback: detect from URL extension
    val lowercaseUrl = url?.lowercase() ?: ""
    return when {
        lowercaseUrl.endsWith(".gif") -> MediaType.GIF
        lowercaseUrl.endsWith(".json") -> MediaType.LOTTIE
        lowercaseUrl.endsWith(".mp4") || lowercaseUrl.endsWith(".m3u8") -> MediaType.VIDEO
        else -> MediaType.IMAGE
    }
}

/**
 * Convenience function to determine media type directly from a ModalMedia object.
 * Uses both the type hint and URL for best accuracy.
 *
 * @return One of: MediaType.IMAGE, MediaType.GIF, MediaType.LOTTIE, MediaType.VIDEO
 */
fun ModalMedia.determineType(): String {
    return determineMediaType(url, type)
}

// =============================================================================
// SECTION 3: MODAL TYPE DETECTION (What kind of modal?)
// =============================================================================

/**
 * Checks if this Modal is a carousel (has multiple slides).
 *
 * @return true if content.set contains slides
 */
fun Modal.isCarousel(): Boolean {
    return content?.set?.isNotEmpty() == true
}

/**
 * Checks if this Modal is media-only (just an image/video, no text/CTAs).
 *
 * @return true if has media but no content
 */
fun Modal.isMediaOnly(): Boolean {
    return resolveMedia() != null && content == null
}

// =============================================================================
// SECTION 4: HELPER FUNCTIONS
// =============================================================================

/**
 * Checks if a URL looks like a media file based on extension.
 */
private fun String.isMediaUrl(): Boolean {
    val lower = this.lowercase()
    return lower.endsWith(".png") ||
            lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") ||
            lower.endsWith(".gif") ||
            lower.endsWith(".webp") ||
            lower.endsWith(".mp4") ||
            lower.endsWith(".m3u8") ||
            lower.endsWith(".json")
}

/**
 * Gets the type hint from ModalMedia.type field.
 * Normalizes and filters out "auto" or blank values.
 *
 * @return Normalized type string, or null if not useful
 */
fun ModalMedia.getTypeHint(): String? {
    val normalizedType = type?.trim()?.lowercase()
    return if (normalizedType.isNullOrBlank() || normalizedType == "auto") {
        null
    } else {
        normalizedType
    }
}

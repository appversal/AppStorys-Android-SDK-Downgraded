package com.appversal.appstorys.ui.modals

import com.appversal.appstorys.api.Modal
import com.appversal.appstorys.api.ModalContent
import com.appversal.appstorys.api.ModalMedia

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

fun ModalContent.resolveMediaUrl(): String? {
    return chooseMediaType?.url?.takeIf { it.isNotBlank() }
}

fun ModalContent.resolveMedia(): ModalMedia? {
    return chooseMediaType?.takeIf { !it.url.isNullOrBlank() }
}

object MediaType {
    const val IMAGE = "image"   // PNG, JPG, JPEG, WebP - rendered with AsyncImage
    const val GIF = "gif"       // Animated GIF - rendered with Coil GIF decoder
    const val LOTTIE = "lottie" // Lottie JSON animation - rendered with LottieAnimation
    const val VIDEO = "video"   // MP4, M3U8 - rendered with ExoPlayer
}

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

fun Modal.isCarousel(): Boolean {
    return content?.set?.isNotEmpty() == true
}

fun Modal.isMediaOnly(): Boolean {
    return resolveMedia() != null && content == null
}

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

fun ModalMedia.getTypeHint(): String? {
    val normalizedType = type?.trim()?.lowercase()
    return if (normalizedType.isNullOrBlank() || normalizedType == "auto") {
        null
    } else {
        normalizedType
    }
}

package com.appversal.appstorys.ui.stories

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.rememberAsyncImagePainter
import com.appversal.appstorys.api.StoryContentCta
import com.appversal.appstorys.api.StoryContentCtaStyling
import com.appversal.appstorys.api.StoryContentElement
import com.appversal.appstorys.api.StoryContentElementStyling
import com.appversal.appstorys.api.StoryContentImage
import com.appversal.appstorys.api.StoryContentImageStyling
import com.appversal.appstorys.api.StoryContentText
import com.appversal.appstorys.api.StoryContentVideo
import com.appversal.appstorys.api.StoryContentVideoStyling
import com.appversal.appstorys.api.StorySlide
import com.appversal.appstorys.api.StorySlideBackground
import com.appversal.appstorys.api.StoryTextStyling
import com.appversal.appstorys.utils.VideoCache
import androidx.core.net.toUri

/**
 * Renders the **foreground** content of a studio-editor story slide on top
 * of whatever background (image / video / colour) the outer composable has
 * already drawn. The background is intentionally rendered by the caller —
 * we layer over it here so the existing background ExoPlayer / image path
 * is left untouched.
 *
 * Layer order (bottom → top):
 *   background (handled by caller)
 *   ↓
 *   content.image[]     ← foreground media
 *   content.video[]
 *   ↓
 *   content.elements[]  ← shapes / stickers / frames
 *   ↓
 *   content.text[]      ← text overlays
 *   ↓
 *   content.ctas[]      ← studio-array CTAs
 *   ↓
 *   interactions[]      ← polls / quizzes / etc.
 *
 * Header overlay (progress bar, cross/share/mute) is drawn on top of all of
 * this by the parent composable — unchanged from the original implementation.
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@UnstableApi
@Composable
internal fun StorySlideForeground(
    slide: StorySlide,
    onCtaClick: (redirectUrl: String?) -> Unit,
    onInputFocusChanged: (focused: Boolean) -> Unit,
    onTrack: (event: String, metadata: Map<String, Any>) -> Unit,
    // Seconds elapsed since this slide started playing. Drives studio element
    // entrance/continuous animations and per-element duration windows. Pass the
    // real playback time from the parent (progress * slideDuration) so that
    // duration-gated elements appear/disappear at the right moment. Defaults to
    // 0.0, which keeps animations working for elements that have no duration set.
    currentTime: Double = 0.0
) {
    val content = slide.content ?: return
    val styling = slide.styling
    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize().clipToBounds()) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }

        // Studio slides are authored in the editor's OWN canva space, which is
        // not always 1080×2160 (e.g. iOS exports at 1320×2868). If we scale
        // against the hardcoded design constants, every position/size is divided
        // by the wrong width/height and the content overflows the screen.
        // Drive the scale from the slide's actual canva dimensions when present.
        val designW = content.canva?.width?.takeIf { it > 0f } ?: STORY_DESIGN_WIDTH
        val designH = content.canva?.height?.takeIf { it > 0f } ?: STORY_DESIGN_HEIGHT
        val scope = remember(maxWidthPx, maxHeightPx, density, designW, designH) {
            computeCanvaScope(maxWidthPx, maxHeightPx, density, designW, designH)
        }

        // -------- IMAGES (foreground) --------
        content.image.orEmpty().forEach { img ->
            val styleFor = styling?.image?.firstOrNull { it.id == img.id }
            ForegroundImage(img = img, style = styleFor, scope = scope, currentTime = currentTime)
        }

        // -------- VIDEOS (foreground) --------
        content.video.orEmpty().forEach { vid ->
            val styleFor = styling?.video?.firstOrNull { it.id == vid.id }
            ForegroundVideo(vid = vid, style = styleFor, scope = scope, currentTime = currentTime)
        }

        // -------- ELEMENTS (shapes / stickers / frames) --------
        content.elements.orEmpty().forEach { el ->
            val styleFor = styling?.elements?.firstOrNull { it.id == el.id }
            ForegroundElement(el = el, style = styleFor, scope = scope, currentTime = currentTime)
        }

        // -------- TEXT --------
        content.text.orEmpty().forEach { txt ->
            val styleFor = styling?.text?.firstOrNull { it.id == txt.id }
            ForegroundText(txt = txt, style = styleFor, scope = scope, currentTime = currentTime)
        }

        // -------- CTAS (studio array form) --------
        content.ctas.orEmpty().forEach { cta ->
            val styleFor = styling?.ctas?.firstOrNull { it.id == cta.id }
            ForegroundCta(cta = cta, style = styleFor, scope = scope, currentTime = currentTime, onClick = {
                onCtaClick(cta.redirectUrl)
                onTrack("cta_clicked", mapOf("cta_id" to (cta.id ?: ""), "url" to (cta.redirectUrl ?: "")))
            })
        }

        // -------- INTERACTIONS --------
        slide.interactions.orEmpty().forEach { interaction ->
            val s = interaction.styling
            val posObj = jsonObjectOrNull(s?.get("position"))
            val szObj = jsonObjectOrNull(s?.get("size"))
            val posX = jsonFloat(posObj?.get("x")) ?: 0f
            val posY = jsonFloat(posObj?.get("y")) ?: 0f
            val szW = jsonFloat(szObj?.get("width")) ?: (STORY_DESIGN_WIDTH * 0.8f)
            val szH = jsonFloat(szObj?.get("height")) ?: 400f

            Box(
                modifier = Modifier
                    .offset(x = scope.xDp(posX), y = scope.yDp(posY))
                    .width(scope.sizeDp(szW))
                    .height(scope.sizeDp(szH))
            ) {
                StoryInteractionRenderer(
                    interaction = interaction,
                    scope = scope,
                    onTrack = onTrack,
                    onInputFocusChanged = onInputFocusChanged
                )
            }
        }
    }
}

// =====================================================================
// Background (caller-friendly helper — renders solid + gradient layers)
// The background image/video is still drawn by the outer composable using
// the existing ExoPlayer path; this only paints the colour layer.
// =====================================================================

/**
 * Paints the background colour (solid or gradient) for a studio slide.
 * No-op when [background] is null or when type is unrecognised.
 */
@Composable
internal fun StorySlideBackgroundColour(background: StorySlideBackground?) {
    val bg = background?.color ?: return
    val brush: Brush = when (bg.type) {
        "gradient" -> {
            val grad = bg.gradient
            val from = parseStoryColor(grad?.from) ?: Color.Transparent
            val to = parseStoryColor(grad?.to) ?: Color.Transparent
            val stops: List<Pair<Float, Color>> = grad?.stops
                ?.mapNotNull { stop ->
                    val c = parseStoryColor(stop.color) ?: return@mapNotNull null
                    val off = stop.offset ?: return@mapNotNull null
                    val opacity = (stop.opacity ?: 100f) / 100f
                    off to c.copy(alpha = opacity)
                }
                ?: listOf(0f to from, 1f to to)
            when (grad?.direction) {
                "bottom" -> Brush.verticalGradient(colorStops = stops.toTypedArray())
                "left" -> Brush.horizontalGradient(colorStops = stops.reversed().toTypedArray())
                "right" -> Brush.horizontalGradient(colorStops = stops.toTypedArray())
                "tl", "bl" -> Brush.linearGradient(colorStops = stops.toTypedArray())
                "tr", "br" -> Brush.linearGradient(colorStops = stops.toTypedArray())
                "top" -> Brush.verticalGradient(colorStops = stops.reversed().toTypedArray())
                else -> Brush.verticalGradient(colorStops = stops.toTypedArray())
            }
        }
        "solid" -> {
            val solid = parseStoryColor(bg.solid) ?: Color.Transparent
            val opacity = (bg.opacity ?: 100f) / 100f
            SolidColor(solid.copy(alpha = opacity.coerceIn(0f, 1f)))
        }
        else -> return
    }
    Box(modifier = Modifier.fillMaxSize().background(brush))
}

// =====================================================================
// Foreground IMAGE
// =====================================================================

@Composable
private fun ForegroundImage(
    img: StoryContentImage,
    style: StoryContentImageStyling?,
    scope: StoryCanvaScope,
    currentTime: Double = 0.0
) {
    val url = img.link ?: return
    val x = img.position?.x ?: 0f
    val y = img.position?.y ?: 0f
    val w = img.width ?: STORY_DESIGN_WIDTH
    val h = img.height ?: w
    val opacity = style?.opacity ?: 1f
    val rotation = style?.rotation ?: 0f
    val radius = style?.cornerRadius ?: 0f
    val flipH = style?.flip?.horizontal == true
    val flipV = style?.flip?.vertical == true
    val scaleX = if (flipH) -1f else 1f
    val scaleY = if (flipV) -1f else 1f

    Image(
        painter = rememberAsyncImagePainter(url),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .offset(x = scope.xDp(x), y = scope.yDp(y))
            .size(width = scope.sizeDp(w), height = scope.sizeDp(h))
            .rotate(rotation)
            .scale(scaleX = scaleX, scaleY = scaleY)
            .clip(RoundedCornerShape(scope.sizeDp(radius)))
            .alpha(opacity)
            .studioElementAnimation(style?.animation, style?.duration, currentTime)
    )
}

// =====================================================================
// Foreground VIDEO — each gets its own auto-managed ExoPlayer
// =====================================================================

@UnstableApi
@Composable
private fun ForegroundVideo(
    vid: StoryContentVideo,
    style: StoryContentVideoStyling?,
    scope: StoryCanvaScope,
    currentTime: Double = 0.0
) {
    val url = vid.link ?: return
    val context = LocalContext.current
    val x = style?.position?.x ?: 0f
    val y = style?.position?.y ?: 0f
    val w = style?.width ?: STORY_DESIGN_WIDTH
    val h = style?.height ?: w
    val opacity = style?.opacity ?: 1f
    val rotation = style?.rotation ?: 0f
    val radius = style?.cornerRadius ?: 0f
    val loop = style?.loop ?: true
    val muted = style?.muted ?: false
    val flipH = style?.flip?.horizontal == true
    val flipV = style?.flip?.vertical == true

    // Per-video ExoPlayer — released on disposal.
    val player = remember(url) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(VideoCache.getFactory(context)))
            .build().apply {
                setMediaItem(MediaItem.fromUri(url.toUri()))
                repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                volume = if (muted) 0f else 1f
                playWhenReady = true
                prepare()
            }
    }
    DisposableEffect(player) {
        onDispose {
            runCatching { player.release() }
                .onFailure { Log.w("StorySlideForeground", "release failed", it) }
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                useController = false
            }
        },
        modifier = Modifier
            .offset(x = scope.xDp(x), y = scope.yDp(y))
            .size(width = scope.sizeDp(w), height = scope.sizeDp(h))
            .rotate(rotation)
            .scale(scaleX = if (flipH) -1f else 1f, scaleY = if (flipV) -1f else 1f)
            .clip(RoundedCornerShape(scope.sizeDp(radius)))
            .alpha(opacity)
            .studioElementAnimation(style?.animation, style?.duration, currentTime)
    )
}

// =====================================================================
// Foreground TEXT
// =====================================================================

@Composable
private fun ForegroundText(
    txt: StoryContentText,
    style: StoryTextStyling?,
    scope: StoryCanvaScope,
    currentTime: Double = 0.0
) {
    val text = txt.text ?: return
    val x = style?.position?.x ?: 0f
    val y = style?.position?.y ?: 0f
    val w = style?.size?.width ?: STORY_DESIGN_WIDTH
    val h = style?.size?.height ?: 100f
    val color = parseStoryColor(style?.color) ?: Color.Black
    val opacity = style?.opacity ?: 1f
    val rotation = style?.rotation ?: 0f
    val fontSize = scope.fontDp(style?.font?.fontSize ?: 48f)
    val fontWeightInt = style?.font?.fontWeight ?: 400
    val letterSpacingSp = (style?.letterSpacing ?: 0f) * scope.scale
    val lineHeight = style?.lineHeight ?: 1.4f

    val align = when (style?.alignment?.horizontalAlignment?.lowercase()) {
        "right" -> TextAlign.End
        "center" -> TextAlign.Center
        else -> TextAlign.Start
    }
    val boxAlign = when (style?.alignment?.verticalAlignment?.lowercase()) {
        "bottom" -> Alignment.BottomStart
        "middle" -> Alignment.CenterStart
        else -> Alignment.TopStart
    }

    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset(x = scope.xDp(x), y = scope.yDp(y))
            .size(width = scope.sizeDp(w), height = scope.sizeDp(h))
            .rotate(rotation)
            .alpha(opacity)
            .studioElementAnimation(style?.animation, style?.duration, currentTime),
        contentAlignment = boxAlign
    ) {
        androidx.compose.material3.Text(
            text = text,
            color = color,
            style = TextStyle(
                fontSize = with(density) { fontSize.toSp() },
                fontWeight = FontWeight(fontWeightInt.coerceIn(100, 900)),
                lineHeight = with(density) { (fontSize * lineHeight).toSp() },
                letterSpacing = with(density) { letterSpacingSp.dp.toSp() },
                textAlign = align
            )
        )
    }
}

// =====================================================================
// Foreground CTA (studio array form)
//   - "swipe_up": pill button with arrow icon, bouncy by default
//   - "image":    tap-target image
//   - default:    rectangular button with text
// =====================================================================

@Composable
private fun ForegroundCta(
    cta: StoryContentCta,
    style: StoryContentCtaStyling?,
    scope: StoryCanvaScope,
    currentTime: Double = 0.0,
    onClick: () -> Unit
) {
    val x = style?.position?.x ?: 0f
    val y = style?.position?.y ?: 0f
    val w = style?.size?.width ?: style?.width ?: 500f
    val h = style?.size?.height ?: style?.height ?: 168f
    val bg = parseStoryColor(style?.background) ?: Color.Transparent
    val textColor = parseStoryColor(style?.textColor) ?: Color.Black
    val borderColor = parseStoryColor(style?.borderColor) ?: Color.Transparent
    val borderWidth = style?.borderWidth ?: 0f
    val radius = scope.sizeDp(style?.borderRadius ?: style?.pillBorderRadius ?: 12f)
    val transparent = style?.transparent ?: false
    val opacity = style?.opacity ?: 1f
    val rotation = style?.rotation ?: 0f
    val fontSize = scope.fontDp(style?.fontSize ?: 30f)
    val density = LocalDensity.current

    val baseModifier = Modifier
        .offset(x = scope.xDp(x), y = scope.yDp(y))
        .size(width = scope.sizeDp(w), height = scope.sizeDp(h))
        .rotate(rotation)
        .alpha(opacity)
        .studioElementAnimation(style?.animation, duration = null, currentTime = currentTime)
        .clip(RoundedCornerShape(radius))
        .background(if (transparent) Color.Transparent else bg)
        .let {
            if (borderWidth > 0f && borderColor != Color.Transparent) {
                it.border(scope.sizeDp(borderWidth), borderColor, RoundedCornerShape(radius))
            } else it
        }
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )

    when (cta.type) {
        "image" -> {
            val img = cta.imageUrl ?: cta.svg ?: cta.url
            if (!img.isNullOrEmpty()) {
                Box(modifier = baseModifier, contentAlignment = Alignment.Center) {
                    Image(
                        painter = rememberAsyncImagePainter(img),
                        contentDescription = cta.text,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        "swipe_up" -> {
            Box(modifier = baseModifier, contentAlignment = Alignment.Center) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    // Up-arrow chevron
                    androidx.compose.material3.Text(
                        text = "▲",
                        color = parseStoryColor(style?.arrowColor) ?: textColor,
                        fontSize = with(density) { scope.fontDp(style?.arrowSize ?: 36f).toSp() }
                    )
                    androidx.compose.material3.Text(
                        text = cta.text ?: "Swipe up",
                        color = textColor,
                        fontSize = with(density) { fontSize.toSp() },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        else -> {
            Box(modifier = baseModifier, contentAlignment = Alignment.Center) {
                androidx.compose.material3.Text(
                    text = cta.text ?: "",
                    color = textColor,
                    fontSize = with(density) { fontSize.toSp() },
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// =====================================================================
// Foreground ELEMENTS (shapes / stickers / frames)
//
// We render shapes via their SVG `url` (preserves the exact vector path),
// stickers as a plain image, and frames as a bordered Box. The `svgPath`
// could alternatively be rendered with a custom Canvas — that's a future
// optimisation; using the pre-rendered SVG keeps things faithful and
// simple for the first cut.
// =====================================================================

@Composable
private fun ForegroundElement(
    el: StoryContentElement,
    style: StoryContentElementStyling?,
    scope: StoryCanvaScope,
    currentTime: Double = 0.0
) {
    val x = style?.position?.x ?: el.position?.x ?: 0f
    val y = style?.position?.y ?: el.position?.y ?: 0f
    // iOS reads canvas-element size from the element (defaulting to 200), not
    // the per-id styling, so mirror that here.
    val w = el.size?.width ?: style?.size?.width ?: 200f
    val h = el.size?.height ?: style?.size?.height ?: 200f
    val opacity = style?.opacity ?: 1f
    val rotation = style?.rotation ?: el.rotation ?: 0f

    // Flip is a string on the element styling (e.g. "horizontal", "vertical",
    // "horizontal vertical") — matches StudioElementStyling.flip on iOS.
    val flip = (style?.flip ?: "").lowercase()
    val flipX = if (flip.contains("horizontal")) -1f else 1f
    val flipY = if (flip.contains("vertical")) -1f else 1f

    val baseModifier = Modifier
        .offset(x = scope.xDp(x), y = scope.yDp(y))
        .size(width = scope.sizeDp(w), height = scope.sizeDp(h))
        .scale(scaleX = flipX, scaleY = flipY)
        .rotate(rotation)
        .alpha(opacity)
        .studioElementAnimation(style?.animation, duration = null, currentTime = currentTime)

    when (el.type) {
        "sticker" -> {
            val img = el.image ?: el.url ?: return
            Image(
                painter = rememberAsyncImagePainter(img),
                contentDescription = el.label,
                contentScale = ContentScale.Fit,
                modifier = baseModifier
            )
        }
        "frame" -> {
            val stroke = parseStoryColor(style?.strokeColor ?: el.stroke) ?: Color.Transparent
            val strokeW = style?.strokeWidth ?: el.strokeWidth ?: 0f
            val cr = scope.sizeDp(style?.cornerRadius ?: el.cornerRadius ?: 0f)
            Box(
                modifier = baseModifier
                    .clip(RoundedCornerShape(cr))
                    .let {
                        if (strokeW > 0f) it.border(scope.sizeDp(strokeW), stroke, RoundedCornerShape(cr)) else it
                    }
            )
        }
        "shape" -> {
            // Prefer SVG render via the provided URL — accurate to the
            // editor's exact shape rasterisation. The fill/stroke from
            // styling layers on top via tint (best-effort).
            val img = el.url
            if (!img.isNullOrEmpty()) {
                Image(
                    painter = rememberAsyncImagePainter(img),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = baseModifier
                )
            } else {
                // Fallback: filled box with the shape's fill color
                val fill = parseStoryColor(style?.color ?: el.fill) ?: Color.Transparent
                val cr = scope.sizeDp(style?.cornerRadius ?: el.cornerRadius ?: 0f)
                Box(modifier = baseModifier.clip(RoundedCornerShape(cr)).background(fill))
            }
        }
        else -> {
            // Unknown element type — try to render via url if present.
            val img = el.url ?: el.image
            if (!img.isNullOrEmpty()) {
                Image(
                    painter = rememberAsyncImagePainter(img),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = baseModifier
                )
            }
        }
    }
}

// ---- tiny JSON helpers (for interaction styling — raw JsonObject) ----

private fun jsonObjectOrNull(e: kotlinx.serialization.json.JsonElement?): kotlinx.serialization.json.JsonObject? =
    e as? kotlinx.serialization.json.JsonObject

private fun jsonFloat(e: kotlinx.serialization.json.JsonElement?): Float? = runCatching {
    (e as? kotlinx.serialization.json.JsonPrimitive)?.content?.toFloatOrNull()
}.getOrNull()
package com.appversal.appstorys.ui.stories

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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
import com.appversal.appstorys.utils.FontCache
import com.appversal.appstorys.utils.isLottieUrl
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import androidx.core.net.toUri
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import com.appversal.appstorys.api.StoryAnimation
import kotlinx.serialization.json.JsonElement
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

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
            // position.x / position.y are % of screen width / height
            val posX = jsonFloat(posObj?.get("x")) ?: 0f
            val posY = jsonFloat(posObj?.get("y")) ?: 0f
            // size.width / size.height are % of screen width / height
            val szW = jsonFloat(szObj?.get("width")) ?: 80f
            val szH = jsonFloat(szObj?.get("height")) ?: 20f
            // rotation (degrees) — e.g. interaction.styling.rotation from studio JSON.
            // Previously parsed but never applied to the wrapper, so polls/quizzes/etc.
            // authored with a rotation always rendered upright.
            val rotationVal = jsonFloat(s?.get("rotation")) ?: 0f

            Box(
                modifier = Modifier
                    .offset(x = scope.xPctDp(posX), y = scope.yPctDp(posY))
                    .width(scope.widthPctDp(szW))
                    .height(scope.heightPctDp(szH))
                    .rotate(rotationVal)
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
 *
 * Gradient stops have `offset` in the range 0–100 (percent); they are
 * normalised to 0.0–1.0 before being passed to Compose brushes.
 * Direction semantics:
 *   "top"    – first colour at top,  flows downward
 *   "bottom" – first colour at bottom, flows upward
 *   "left"   – first colour at left,  flows rightward
 *   "right"  – first colour at right, flows leftward
 *   "radial" – radiates from the centre of the composable
 *   "tl/tr/bl/br" – linear diagonal in that quadrant direction
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
internal fun StorySlideBackgroundColour(background: StorySlideBackground?) {
    val bg = background?.color ?: return
    // Wrapped in BoxWithConstraints so the "radial" branch below can size the
    // gradient against the box's actual pixel dimensions (needed for a
    // farthest-corner radius) — every other branch behaves exactly as before.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val brush: Brush = when (bg.type) {
            "gradient" -> {
                val grad = bg.gradient
                val from = parseStoryColor(grad?.from) ?: Color.Transparent
                val to   = parseStoryColor(grad?.to)   ?: Color.Transparent
                // Stops arrive with offset in 0–100; normalise to 0.0–1.0 for Compose.
                val stops: List<Pair<Float, Color>> = grad?.stops
                    ?.mapNotNull { stop ->
                        val c   = parseStoryColor(stop.color) ?: return@mapNotNull null
                        val off = stop.offset              ?: return@mapNotNull null
                        val alpha = ((stop.opacity ?: 100f) / 100f).coerceIn(0f, 1f)
                        (off / 100f) to c.copy(alpha = alpha)
                    }
                    ?: listOf(0f to from, 1f to to)
                val arr = stops.toTypedArray()
                when (grad?.direction) {
                    // ── Linear axial ──────────────────────────────────────────────
                    "top"    -> Brush.verticalGradient(colorStops = arr)
                    "bottom" -> Brush.verticalGradient(
                        colorStops = arr,
                        startY = Float.POSITIVE_INFINITY, endY = 0f
                    )
                    "left"   -> Brush.horizontalGradient(colorStops = arr)
                    "right"  -> Brush.horizontalGradient(
                        colorStops = arr,
                        startX = Float.POSITIVE_INFINITY, endX = 0f
                    )
                    // ── Radial ────────────────────────────────────────────────────
                    // Explicit center + "farthest-corner" radius (same sizing CSS's
                    // radial-gradient defaults to). Compose's own unspecified-radius
                    // default resolves to size.minDimension / 2, which on a portrait
                    // story slide (tall, narrow) clips to a flat "to" colour well
                    // before reaching the top/bottom edges instead of smoothly
                    // reaching every corner like the reference design.
                    "radial" -> {
                        val centerX = widthPx / 2f
                        val centerY = heightPx / 2f
                        val farthestCornerRadius = kotlin.math.sqrt(centerX * centerX + centerY * centerY)
                        Brush.radialGradient(
                            colorStops = arr,
                            center = Offset(centerX, centerY),
                            radius = farthestCornerRadius.coerceAtLeast(1f)
                        )
                    }
                    // ── Diagonal linear ───────────────────────────────────────────
                    "tl" -> Brush.linearGradient(
                        colorStops = arr,
                        start = Offset.Zero,
                        end   = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    )
                    "tr" -> Brush.linearGradient(
                        colorStops = arr,
                        start = Offset(Float.POSITIVE_INFINITY, 0f),
                        end   = Offset(0f, Float.POSITIVE_INFINITY)
                    )
                    "bl" -> Brush.linearGradient(
                        colorStops = arr,
                        start = Offset(0f, Float.POSITIVE_INFINITY),
                        end   = Offset(Float.POSITIVE_INFINITY, 0f)
                    )
                    "br" -> Brush.linearGradient(
                        colorStops = arr,
                        start = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                        end   = Offset.Zero
                    )
                    else -> Brush.verticalGradient(colorStops = arr)
                }
            }
            "solid" -> {
                val solid   = parseStoryColor(bg.solid) ?: Color.Transparent
                val opacity = ((bg.opacity ?: 100f) / 100f).coerceIn(0f, 1f)
                SolidColor(solid.copy(alpha = opacity))
            }
            else -> return@BoxWithConstraints
        }
        Box(modifier = Modifier.fillMaxSize().background(brush))
    }
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
    // position.x / position.y are % of screen width / height
    val x = img.position?.x ?: 0f
    val y = img.position?.y ?: 0f
    // width / height are % of screen width / height
    val w = img.width ?: 100f
    val h = img.height ?: w
    // Opacity in JSON is 0-100; Compose alpha expects 0.0-1.0
    val opacity = ((style?.opacity ?: 100f) / 100f).coerceIn(0f, 1f)
    val rotation = style?.rotation ?: 0f
    val radius = style?.cornerRadius ?: 0f
    val flipH = style?.flip?.horizontal == true
    val flipV = style?.flip?.vertical == true
    val scaleX = if (flipH) -1f else 1f
    val scaleY = if (flipV) -1f else 1f

    val imgModifier = Modifier
        .offset(x = scope.xPctDp(x), y = scope.yPctDp(y))
        .size(width = scope.widthPctDp(w), height = scope.heightPctDp(h))
        .rotate(rotation)
        .scale(scaleX = scaleX, scaleY = scaleY)
        .clip(RoundedCornerShape(scope.heightPctDp(radius)))   // was sizeDp
        .alpha(opacity)
        .studioElementAnimation(style?.animation, style?.duration, currentTime)

    if (isLottieUrl(url)) {
        val composition by rememberLottieComposition(spec = LottieCompositionSpec.Url(url))
        LottieAnimation(
            composition = composition,
            iterations = LottieConstants.IterateForever,
            contentScale = ContentScale.Crop,
            modifier = imgModifier
        )
    } else {
        Image(
            painter = rememberAsyncImagePainter(url),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = imgModifier
        )
    }
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
    // position.x / position.y are % of screen width / height
    val x = style?.position?.x ?: 0f
    val y = style?.position?.y ?: 0f
    // width / height are % of screen width / height
    val w = style?.width ?: 100f
    val h = style?.height ?: w
    // Opacity in JSON is 0-100; Compose alpha expects 0.0-1.0
    val opacity = ((style?.opacity ?: 100f) / 100f).coerceIn(0f, 1f)
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
            .offset(x = scope.xPctDp(x), y = scope.yPctDp(y))
            .size(width = scope.widthPctDp(w), height = scope.heightPctDp(h))
            .rotate(rotation)
            .scale(scaleX = if (flipH) -1f else 1f, scaleY = if (flipV) -1f else 1f)
            .clip(RoundedCornerShape(scope.heightPctDp(radius)))   // was sizeDp
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
    // position.x / position.y are % of screen width / height
    val x = style?.position?.x ?: 0f
    val y = style?.position?.y ?: 0f
    // size.width / size.height are % of screen width / height
    val w = style?.size?.width ?: 100f
    val h = style?.size?.height ?: 10f
    val color = parseStoryColorElement(style?.color) ?: Color.Black
    // Opacity in JSON is 0-100; Compose alpha expects 0.0-1.0
    val opacity = ((style?.opacity ?: 100f) / 100f).coerceIn(0f, 1f)
    val rotation = style?.rotation ?: 0f
    // fontSize is % of screen height
    val fontSize = scope.fontPctDp(style?.font?.fontSize ?: 5f).coerceAtLeast(10.dp)
    val fontWeightInt = style?.font?.fontWeight ?: 400
    val letterSpacingSp = (style?.letterSpacing ?: 0f) * scope.scale
    val lineHeight = style?.lineHeight ?: 1.4f

    // fontDecoration, e.g. ["bold", "italic", "underline"] from studio JSON —
    // previously parsed onto the model but never applied to the rendered text.
    val decoration = style?.font?.fontDecoration.orEmpty()
    val fontWeight = if (decoration.contains("bold")) FontWeight.Bold
    else FontWeight(fontWeightInt.coerceIn(100, 900))
    val fontStyle = if (decoration.contains("italic")) FontStyle.Italic else FontStyle.Normal
    val textDecoration = if (decoration.contains("underline")) TextDecoration.Underline else null

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

    // fontFamily: a URL loads/caches a custom font file (via FontCache, same
    // mechanism used for the legacy single-CTA text path); a recognised named
    // family maps to a built-in Compose font; anything else (e.g. "Open Sans")
    // falls back to the platform default typeface.
    val context = LocalContext.current
    val fontLoadScope = rememberCoroutineScope()
    var fontFamily by remember { mutableStateOf<FontFamily?>(null) }
    LaunchedEffect(style?.font?.fontFamily) {
        val family = style?.font?.fontFamily
        when {
            family.isNullOrBlank() -> fontFamily = FontFamily.SansSerif
            family.startsWith("http://", ignoreCase = true) ||
                    family.startsWith("https://", ignoreCase = true) -> {
                fontLoadScope.launch {
                    fontFamily = try {
                        FontCache.loadFont(
                            context = context,
                            fontUrl = family,
                            weight = fontWeight,
                            style = fontStyle
                        ) ?: FontFamily.SansSerif
                    } catch (e: Exception) {
                        FontFamily.SansSerif
                    }
                }
            }
            else -> fontFamily = when (family.lowercase()) {
                "serif" -> FontFamily.Serif
                "monospace", "mono" -> FontFamily.Monospace
                "cursive" -> FontFamily.Cursive
                "sans-serif", "sans" -> FontFamily.SansSerif
                else -> FontFamily.Default
            }
        }
    }

    Box(
        modifier = Modifier
            .offset(x = scope.xPctDp(x), y = scope.yPctDp(y))
            .size(width = scope.widthPctDp(w), height = scope.heightPctDp(h))
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
                fontFamily = fontFamily ?: FontFamily.SansSerif,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                textDecoration = textDecoration,
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
    val w = style?.size?.width ?: style?.width ?: 50f
    val h = style?.size?.height ?: style?.height ?: 8f
    val bg          = parseStoryColorElement(style?.background)  ?: Color.Transparent
    val textColor   = parseStoryColorElement(style?.textColor)   ?: Color.Black
    val borderColor = parseStoryColorElement(style?.borderColor) ?: Color.Transparent
    val borderWidth = style?.borderWidth ?: 0f
    // borderRadius is % of screen height (same unit system as all other styling values)
    val radius      = scope.heightPctDp(style?.borderRadius ?: style?.pillBorderRadius ?: 2f)
    val transparent = style?.transparent ?: false
    val opacity     = ((style?.opacity ?: 100f) / 100f).coerceIn(0f, 1f)
    val rotation    = style?.rotation ?: 0f
    // fontSize is % of screen height
    val fontSize    = scope.fontPctDp(style?.fontSize ?: 2f).coerceAtLeast(12.dp)
    val density     = LocalDensity.current

    val baseModifier = Modifier
        .offset(x = scope.xPctDp(x), y = scope.yPctDp(y))
        .size(width = scope.widthPctDp(w), height = scope.heightPctDp(h))
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
            indication        = null,
            onClick           = onClick
        )

    when (cta.type) {
        "image" -> {
            val img = cta.imageUrl ?: cta.svg ?: cta.url
            if (!img.isNullOrEmpty()) {
                Box(modifier = baseModifier, contentAlignment = Alignment.Center) {
                    Image(
                        painter            = rememberAsyncImagePainter(img),
                        contentDescription = cta.text,
                        contentScale       = ContentScale.Fit,
                        modifier           = Modifier.fillMaxSize()
                    )
                }
            }
        }
        "swipe_up" -> {
            // Outer container is fully transparent — no background at this level, ever.
            // The gray "bg" fill applies ONLY to the pill below; the arrow sits on the
            // plain slide background above it, matching the reference look.
            val currentOnClick = rememberUpdatedState(onClick)
            // Minimum upward drag distance before a swipe counts as "swipe up".
            val swipeUpThresholdPx = with(density) { 24.dp.toPx() }

            // Swipe-up CTAs animate by default: whole element bobs (shared "bounce") AND the
            // chevron stretches vertically (scaleY 1.0 → 1.10) in lockstep. A configured
            // animation wins and skips the pulse.
            val configuredAnimationType = style?.animation?.type?.trim()?.lowercase()
            val useDefaultBounce =
                configuredAnimationType.isNullOrEmpty() || configuredAnimationType == "none"
            val swipeUpAnimation: StoryAnimation = style?.animation
                ?.takeUnless { it.type.isNullOrBlank() || it.type?.trim()?.lowercase() == "none" }
                ?: StoryAnimation(type = "bounce")

            // Chevron vertical pulse — same 550ms / easeInOut / autoreverse timing as the
            // bounce, starting from 1.0, so the arrow is tallest at the top of the bob and
            // 1.0x at the bottom: vertical stretch and upward movement stay in phase.
            val chevronPulse = rememberInfiniteTransition(label = "swipeUpChevronPulse")
            val chevronScaleRaw by chevronPulse.animateFloat(
                initialValue = 0.5f,
                targetValue = 0.8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 550, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "swipeUpChevronScale",
            )
            val chevronScale = if (useDefaultBounce) chevronScaleRaw else 1f

            val swipeUpModifier = Modifier
                .offset(x = scope.xPctDp(x), y = scope.yPctDp(y))
                .size(width = scope.widthPctDp(w), height = scope.heightPctDp(h))
                .rotate(rotation)
                .alpha(opacity)
                .studioElementAnimation(swipeUpAnimation, duration = null, currentTime = currentTime)
                .pointerInput(Unit) {
                    coroutineScope {
                        // Tap → same as a regular CTA click.
                        launch {
                            detectTapGestures(onTap = { currentOnClick.value() })
                        }
                        // Swipe up → also triggers the CTA click, matching the
                        // "swipe up" affordance shown to the user.
                        launch {
                            var totalDragY = 0f
                            var hasTriggered = false
                            detectVerticalDragGestures(
                                onDragStart = {
                                    totalDragY = 0f
                                    hasTriggered = false
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    totalDragY += dragAmount
                                    change.consume()
                                    // Fire the moment the threshold is crossed, rather than
                                    // waiting for lift-off. A real swipe often decelerates
                                    // (and sometimes drifts back down a little) right before
                                    // the finger leaves the screen — checking only at
                                    // onDragEnd can miss a perfectly good swipe-up because
                                    // totalDragY dips back under threshold in that last frame.
                                    if (!hasTriggered && totalDragY < -swipeUpThresholdPx) {
                                        hasTriggered = true
                                        currentOnClick.value()
                                    }
                                },
                                onDragEnd = {
                                    // Fallback: covers a very fast/low-sample-rate swipe where
                                    // no intermediate onVerticalDrag frame happened to land past
                                    // the threshold, but the net movement still cleared it.
                                    if (!hasTriggered && totalDragY < -swipeUpThresholdPx) {
                                        currentOnClick.value()
                                    }
                                }
                            )
                        }
                    }
                }

            val pillHeightFraction = ((62f) / 100f).coerceIn(0.3f, 0.9f)
            val arrowColor = parseStoryColorElement(style?.arrowColor) ?: textColor

            androidx.compose.foundation.layout.Column(
                modifier             = swipeUpModifier,
                horizontalAlignment  = Alignment.CenterHorizontally,
                verticalArrangement  = androidx.compose.foundation.layout.Arrangement.Bottom
            ) {
                // ── Chevron arrow ──
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f - pillHeightFraction),
                    contentAlignment = Alignment.Center
                ) {
                    val chevronHeight = with(density) {
                        scope.fontPctDp(style?.arrowSize ?: 2.08f).coerceAtLeast(10.dp)
                    }
                    val chevronWidth = chevronHeight * 1.9f
                    val strokeWidthPx = with(density) { chevronHeight.toPx() } * 0.3f

                    androidx.compose.foundation.Canvas(
                        modifier = Modifier
                            .size(chevronWidth, chevronHeight)
                            .scale(scaleX = 1f, scaleY = chevronScale)   // ← vertical-only stretch
                    ) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(0f, size.height)
                            lineTo(size.width / 2f, 0f)
                            lineTo(size.width, size.height)
                        }
                        drawPath(
                            path = path,
                            color = arrowColor,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = strokeWidthPx,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                join = androidx.compose.ui.graphics.StrokeJoin.Round
                            )
                        )
                    }
                }

                // ── Pill (text only) — NOT scaled ──
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(pillHeightFraction)
                        .clip(RoundedCornerShape(50))
                        .background(if (transparent) Color.Transparent else bg),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Text(
                        text       = cta.text ?: "Swipe up",
                        color      = textColor,
                        fontSize   = with(density) { fontSize.toSp() },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        else -> {
            Box(modifier = baseModifier, contentAlignment = Alignment.Center) {
                androidx.compose.material3.Text(
                    text       = cta.text ?: "",
                    color      = textColor,
                    fontSize   = with(density) { fontSize.toSp() },
                    fontWeight = FontWeight.SemiBold,
                    textAlign  = TextAlign.Center
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
    // Position: prefer styling, then element — both are % of screen width/height
    val x = style?.position?.x ?: el.position?.x ?: 0f
    val y = style?.position?.y ?: el.position?.y ?: 0f
    // Size: prefer element, then styling — both are % of screen width/height
    val w = el.size?.width ?: style?.size?.width ?: 20f
    val h = el.size?.height ?: style?.size?.height ?: 20f
    // Opacity in JSON is 0-100; Compose alpha expects 0.0-1.0
    val opacity = ((style?.opacity ?: 100f) / 100f).coerceIn(0f, 1f)
    val rotation = style?.rotation ?: el.rotation ?: 0f

    // Flip is a string on the element styling (e.g. "horizontal", "vertical",
    // "horizontal vertical") — matches StudioElementStyling.flip on iOS.
    val flip = (style?.flip ?: "").lowercase()
    val flipX = if (flip.contains("horizontal")) -1f else 1f
    val flipY = if (flip.contains("vertical")) -1f else 1f

    val baseModifier = Modifier
        .offset(x = scope.xPctDp(x), y = scope.yPctDp(y))
        .size(width = scope.widthPctDp(w), height = scope.heightPctDp(h))
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
            val stroke = parseStoryColorElement(style?.strokeColor ?: el.stroke) ?: Color.Transparent
            // strokeWidth in JSON is in SVG/canva units; scale against sizeDp for consistency
            val strokeW = style?.strokeWidth ?: el.strokeWidth ?: 0f
            val cr = scope.sizeDp(style?.cornerRadius ?: el.cornerRadius ?: 0f)
            Box(
                modifier = baseModifier
                    .clip(RoundedCornerShape(cr))
                    .let {
                        if (strokeW > 0f) it.border(scope.sizeDp(strokeW * 10f).coerceAtLeast(0.5.dp), stroke, RoundedCornerShape(cr)) else it
                    }
            )
        }
        "shape" -> {
            // Shapes are rendered exactly as authored via their SVG url — no fill/stroke
            // colour fields are mapped, so the shape's own colouring is preserved as-is.
            val img = el.url
            if (!img.isNullOrEmpty()) {
                Image(
                    painter = rememberAsyncImagePainter(img),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = baseModifier
                )
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
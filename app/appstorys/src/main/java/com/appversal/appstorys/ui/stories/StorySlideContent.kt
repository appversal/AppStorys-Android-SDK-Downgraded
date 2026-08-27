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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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
import com.appversal.appstorys.api.StoryContentLottie
import com.appversal.appstorys.api.StoryContentLottieStyling
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
    currentTime: Double = 0.0,
    // Slide-level mute state, driven by the header sound toggle. When true every
    // foreground (studio-canvas) video on this slide is silenced, alongside the
    // background video the caller owns — so one toggle governs all of them.
    muted: Boolean = false
) {
    val content = slide.content ?: return
    val styling = slide.styling
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
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
            ForegroundVideo(
                vid = vid,
                style = styleFor,
                scope = scope,
                currentTime = currentTime,
                slideMuted = muted
            )
        }

        // -------- LOTTIE (foreground) --------
        content.lottie.orEmpty().forEach { lot ->
            val styleFor = styling?.lottie?.firstOrNull { it.id == lot.id }
            ForegroundLottie(
                lottie = lot,
                style = styleFor,
                scope = scope,
                currentTime = currentTime
            )
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
            ForegroundCta(
                cta = cta,
                style = styleFor,
                scope = scope,
                currentTime = currentTime,
                onClick = {
                    onCtaClick(cta.redirectUrl)
                    onTrack(
                        "cta_clicked",
                        mapOf("cta_id" to (cta.id ?: ""), "url" to (cta.redirectUrl ?: ""))
                    )
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
            // Every interaction ships styling.animation { type, direction } exactly like
            // the other studio elements, but nothing read it — poll/quiz/rating/… had no
            // entrance animation at all. Applied on this shared wrapper so all eight
            // types are covered in one place.
            // `duration` is deliberately not forwarded: the studio sends a window here
            // but currentTime never advances (see StorySlideForeground's param), so
            // passing it would gate visibility on a clock that is always 0.
            val interactionAnimation = jsonObjectOrNull(s?.get("animation"))?.let {
                StoryAnimation(
                    type = jsonString(it["type"]),
                    direction = jsonString(it["direction"])
                )
            }

            Box(
                modifier = Modifier
                    .canvaPlace(scope, posX, posY, szW, szH)
                    .rotate(rotationVal)
                    .studioElementAnimation(
                        interactionAnimation,
                        duration = null,
                        currentTime = currentTime
                    )
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
                val to = parseStoryColor(grad?.to) ?: Color.Transparent
                // Stops arrive with offset in 0–100; normalise to 0.0–1.0 for Compose.
                val stops: List<Pair<Float, Color>> = grad?.stops
                    ?.mapNotNull { stop ->
                        val c = parseStoryColor(stop.color) ?: return@mapNotNull null
                        val off = stop.offset ?: return@mapNotNull null
                        val alpha = ((stop.opacity ?: 100f) / 100f).coerceIn(0f, 1f)
                        (off / 100f) to c.copy(alpha = alpha)
                    }
                    ?: listOf(0f to from, 1f to to)
                val arr = stops.toTypedArray()
                when (grad?.direction) {
                    // ── Linear axial ──────────────────────────────────────────────
                    "top" -> Brush.verticalGradient(colorStops = arr)
                    "bottom" -> Brush.verticalGradient(
                        colorStops = arr,
                        startY = Float.POSITIVE_INFINITY, endY = 0f
                    )

                    "left" -> Brush.horizontalGradient(colorStops = arr)
                    "right" -> Brush.horizontalGradient(
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
                        val farthestCornerRadius =
                            kotlin.math.sqrt(centerX * centerX + centerY * centerY)
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
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    )

                    "tr" -> Brush.linearGradient(
                        colorStops = arr,
                        start = Offset(Float.POSITIVE_INFINITY, 0f),
                        end = Offset(0f, Float.POSITIVE_INFINITY)
                    )

                    "bl" -> Brush.linearGradient(
                        colorStops = arr,
                        start = Offset(0f, Float.POSITIVE_INFINITY),
                        end = Offset(Float.POSITIVE_INFINITY, 0f)
                    )

                    "br" -> Brush.linearGradient(
                        colorStops = arr,
                        start = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                        end = Offset.Zero
                    )

                    else -> Brush.verticalGradient(colorStops = arr)
                }
            }

            "solid" -> {
                val solid = parseStoryColor(bg.solid) ?: Color.Transparent
                val opacity = ((bg.opacity ?: 100f) / 100f).coerceIn(0f, 1f)
                SolidColor(solid.copy(alpha = opacity))
            }

            else -> return@BoxWithConstraints
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush)
        )
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
        .canvaPlace(scope, x, y, w, h)
        .rotate(rotation)
        .scale(scaleX = scaleX, scaleY = scaleY)
        // The animation has to sit OUTSIDE .clip(). A clip declared before it stays
        // put while the animation's offset/scale moves the content underneath, so a
        // fade/slide made the picture slide around inside a stationary rounded frame
        // instead of the whole element moving. Only the three media composables clip
        // before animating; text/cta/element/interaction already clip afterwards.
        .studioElementAnimation(style?.animation, style?.duration, currentTime)
        .clip(RoundedCornerShape(scope.heightPctDp(radius)))   // was sizeDp
        .alpha(opacity)

    // Honour the studio fit key when present; the default stays crop-fill so
    // existing campaigns render exactly as before.
    val imageScale = when ((style?.objectFit ?: style?.sizing ?: style?.fit)?.lowercase()) {
        "fit", "contain" -> ContentScale.Fit
        else -> ContentScale.Crop
    }

    if (isLottieUrl(url)) {
        val composition by rememberLottieComposition(spec = LottieCompositionSpec.Url(url))
        LottieAnimation(
            composition = composition,
            iterations = LottieConstants.IterateForever,
            contentScale = imageScale,
            modifier = imgModifier
        )
    } else {
        Image(
            painter = rememberAsyncImagePainter(url),
            contentDescription = null,
            contentScale = imageScale,
            modifier = imgModifier
        )
    }
}

// =====================================================================
// Foreground LOTTIE
//
// Studio treats lottie as a first-class media type alongside image and
// video: `content.lottie[]` holds { id, link } and `styling.lottie[]`
// holds the same geometry keys as video styling, matched by id. Rendered
// with the same position / size / rotation / flip / corner-radius /
// opacity / animation pipeline as the other foreground media so a lottie
// behaves identically to an image or video on the canvas.
// =====================================================================

@Composable
private fun ForegroundLottie(
    lottie: StoryContentLottie,
    style: StoryContentLottieStyling?,
    scope: StoryCanvaScope,
    currentTime: Double = 0.0
) {
    val url = lottie.link ?: return
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
    val flipH = style?.flip?.horizontal == true
    val flipV = style?.flip?.vertical == true

    val composition by rememberLottieComposition(spec = LottieCompositionSpec.Url(url))

    // Studio fit key — "fill"/"cover" crop-fills, anything else (incl. absent)
    // letterboxes, which is the natural default for a lottie composition.
    val lottieScale = when ((style?.objectFit ?: style?.sizing ?: style?.fit)?.lowercase()) {
        "fill", "cover" -> ContentScale.Crop
        else -> ContentScale.Fit
    }

    LottieAnimation(
        composition = composition,
        iterations = if (style?.loop == false) 1 else LottieConstants.IterateForever,
        contentScale = lottieScale,
        modifier = Modifier
            .canvaPlace(scope, x, y, w, h)
            .rotate(rotation)
            .scale(scaleX = if (flipH) -1f else 1f, scaleY = if (flipV) -1f else 1f)
            // The animation has to sit OUTSIDE .clip(). A clip declared before it stays
            // put while the animation's offset/scale moves the content underneath, so a
            // fade/slide made the picture slide around inside a stationary rounded frame
            // instead of the whole element moving. Only the three media composables clip
            // before animating; text/cta/element/interaction already clip afterwards.
            .studioElementAnimation(style?.animation, style?.duration, currentTime)
            .clip(RoundedCornerShape(scope.heightPctDp(radius)))
            .alpha(opacity)
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
    currentTime: Double = 0.0,
    // Header sound toggle. Combined with the element's own `style.muted` flag: a
    // video the studio marked muted stays muted, and the toggle can silence
    // everything on top of that.
    slideMuted: Boolean = false
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
    // Either source of mute silences this video.
    val muted = slideMuted || (style?.muted ?: false)
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

    // `player` is remembered against `url`, so its initial volume is only correct
    // for the mute state at creation time. Re-apply whenever the toggle changes.
    LaunchedEffect(player, muted) {
        player.volume = if (muted) 0f else 1f
    }

    // Studio fit key — "fill"/"cover" crop-fills (RESIZE_MODE_ZOOM = 4), anything
    // else letterboxes (RESIZE_MODE_FIT = 0), which is the historical default.
    val videoResizeMode = when ((style?.objectFit ?: style?.sizing ?: style?.fit)?.lowercase()) {
        "fill", "cover" -> 4
        else -> 0
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
        update = { view -> view.resizeMode = videoResizeMode },
        modifier = Modifier
            .canvaPlace(scope, x, y, w, h)
            .rotate(rotation)
            .scale(scaleX = if (flipH) -1f else 1f, scaleY = if (flipV) -1f else 1f)
            // The animation has to sit OUTSIDE .clip(). A clip declared before it stays
            // put while the animation's offset/scale moves the content underneath, so a
            // fade/slide made the picture slide around inside a stationary rounded frame
            // instead of the whole element moving. Only the three media composables clip
            // before animating; text/cta/element/interaction already clip afterwards.
            .studioElementAnimation(style?.animation, style?.duration, currentTime)
            .clip(RoundedCornerShape(scope.heightPctDp(radius)))   // was sizeDp
            .alpha(opacity)
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
            .canvaPlace(scope, x, y, w, h)
            .rotate(rotation)
            .scale(
                scaleX = if (style?.flip?.horizontal == true) -1f else 1f,
                scaleY = if (style?.flip?.vertical == true) -1f else 1f
            )
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
    val bg = parseStoryColorElement(style?.background) ?: Color.Transparent
    val textColor = parseStoryColorElement(style?.textColor) ?: Color.Black
    val borderColor = parseStoryColorElement(style?.borderColor)
    val borderWidth = style?.borderWidth ?: 0f
    // borderRadius is % of screen height (same unit system as all other styling values)
    val radius = scope.heightPctDp(style?.borderRadius ?: style?.pillBorderRadius ?: 2f)
    val transparent = style?.transparent ?: false
    val opacity = ((style?.opacity ?: 100f) / 100f).coerceIn(0f, 1f)
    val rotation = style?.rotation ?: 0f
    // fontSize is % of screen height — prefer the new nested font.fontSize, fall back to
    // the legacy flat fontSize field, then the old default.
    val fontSize =
        scope.fontPctDp(style?.font?.fontSize ?: style?.fontSize ?: 2f).coerceAtLeast(12.dp)
    val fontWeightInt = style?.font?.fontWeight ?: 600
    val density = LocalDensity.current

    // fontDecoration, e.g. ["bold", "italic", "underline"] from the CTA's nested font
    // object — same semantics as ForegroundText's decoration handling.
    val ctaDecoration = style?.font?.fontDecoration.orEmpty()
    val ctaFontWeight = if (ctaDecoration.contains("bold")) FontWeight.Bold
    else FontWeight(fontWeightInt.coerceIn(100, 900))
    val ctaFontStyle = if (ctaDecoration.contains("italic")) FontStyle.Italic else FontStyle.Normal
    val ctaTextDecoration =
        if (ctaDecoration.contains("underline")) TextDecoration.Underline else null

    // fontFamily: same URL / named-family / default resolution used for text elements —
    // a URL loads and caches a custom font file via FontCache, a recognised named family
    // maps to a built-in Compose font, anything else falls back to the platform default.
    val ctaFontLoadContext = LocalContext.current
    val ctaFontLoadScope = rememberCoroutineScope()
    var ctaFontFamily by remember { mutableStateOf<FontFamily?>(null) }
    LaunchedEffect(style?.font?.fontFamily) {
        val family = style?.font?.fontFamily
        when {
            family.isNullOrBlank() -> ctaFontFamily = FontFamily.SansSerif
            family.startsWith("http://", ignoreCase = true) ||
                    family.startsWith("https://", ignoreCase = true) -> {
                ctaFontLoadScope.launch {
                    ctaFontFamily = try {
                        FontCache.loadFont(
                            context = ctaFontLoadContext,
                            fontUrl = family,
                            weight = ctaFontWeight,
                            style = ctaFontStyle
                        ) ?: FontFamily.SansSerif
                    } catch (e: Exception) {
                        FontFamily.SansSerif
                    }
                }
            }

            else -> ctaFontFamily = when (family.lowercase()) {
                "serif" -> FontFamily.Serif
                "monospace", "mono" -> FontFamily.Monospace
                "cursive" -> FontFamily.Cursive
                "sans-serif", "sans" -> FontFamily.SansSerif
                else -> FontFamily.Default
            }
        }
    }

    val baseModifier = Modifier
        .canvaPlace(scope, x, y, w, h)
        .rotate(rotation)
        .alpha(opacity)
        .studioElementAnimation(style?.animation, duration = null, currentTime = currentTime)
        .clip(RoundedCornerShape(radius))
        .background(if (transparent) Color.Transparent else bg)
        .let {
            if (borderWidth > 0f && borderColor != null) {
                it.border(
                    scope.widthPctDp(borderWidth).coerceAtLeast(1.dp),
                    borderColor,
                    RoundedCornerShape(radius)
                )
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
                        // Crop-fill, not Fit: the box's borderRadius is clipped by
                        // baseModifier, so a letterboxed image never reaches the corners
                        // being rounded and the authored radius renders as a no-op.
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
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
            // The bob is swipe-up's AFFORDANCE — the thing that says "swipe me" — so it
            // repeats for the life of the slide whether it is the built-in default or the
            // dashboard explicitly picked "bounce". Any other configured type is a normal
            // one-shot entrance and runs through studioElementAnimation like everything else.
            val useDefaultBounce = configuredAnimationType.isNullOrEmpty() ||
                    configuredAnimationType == "none" ||
                    configuredAnimationType == "bounce"

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

            // Swipe-up's default bob is a standing AFFORDANCE, not an entrance: the whole
            // column — chevron and pill together — keeps bobbing for the life of the slide,
            // in phase with the chevron pulse above. Every other studio animation plays once
            // and rests, so this cannot go through studioElementAnimation's "bounce".
            // A dashboard-configured animation wins and runs once like everything else.
            val defaultBobDy by chevronPulse.animateFloat(
                initialValue = 0f,
                targetValue = -12f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 550, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "swipeUpColumnBob",
            )

            val swipeUpModifier = Modifier
                .canvaPlace(scope, x, y, w, h)
                .rotate(rotation)
                .alpha(opacity)
                .studioElementAnimation(
                    if (useDefaultBounce) null else style?.animation,
                    duration = null,
                    currentTime = currentTime
                )
                .offset(y = if (useDefaultBounce) defaultBobDy.dp else 0.dp)
                .pointerInput(Unit) {
                    // ONE gesture loop handles both tap and swipe-up.
                    //
                    // Previously a detectTapGestures and a detectVerticalDragGestures ran
                    // as two concurrent coroutines over the same pointer stream: the tap
                    // detector consumes the initial down, and the drag detector's own
                    // touch-slop pass then treats that consumption as a cancellation — so
                    // the first swipe was swallowed and only a subsequent gesture got
                    // through. Hence "swipe twice to trigger".
                    //
                    // Running a single loop removes that race, and claiming the gesture
                    // (consuming the moves) as soon as it is recognised as vertical also
                    // stops the enclosing story viewer / bottom sheet from stealing the
                    // drag before the swipe-up threshold is reached.
                    val claimSlopPx = with(density) { 3.dp.toPx() }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // Claim the press immediately (as detectTapGestures used to do)
                        // so the story viewer behind us doesn't also treat this gesture
                        // as a tap-to-advance on the same CTA.
                        down.consume()
                        var totalDragY = 0f
                        var totalDragX = 0f
                        var claimed = false      // gesture recognised as a vertical drag
                        var moved = false        // moved far enough that it is not a tap
                        var hasTriggered = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                            if (change.changedToUpIgnoreConsumed()) {
                                // Lift-off. A gesture that never moved is a plain tap and
                                // triggers the CTA exactly like any other button.
                                if (!hasTriggered) {
                                    if (!moved) {
                                        currentOnClick.value()
                                    } else if (claimed && totalDragY < -swipeUpThresholdPx) {
                                        // Fallback for a very fast / low-sample-rate swipe
                                        // where no intermediate frame landed past the
                                        // threshold but the net movement still cleared it.
                                        currentOnClick.value()
                                    }
                                }
                                change.consume()
                                break
                            }

                            if (!change.pressed) break

                            val delta = change.positionChange()
                            totalDragY += delta.y
                            totalDragX += delta.x

                            if (!moved &&
                                (kotlin.math.abs(totalDragY) > claimSlopPx ||
                                        kotlin.math.abs(totalDragX) > claimSlopPx)
                            ) {
                                moved = true
                                // Only take ownership when the movement is predominantly
                                // vertical; a horizontal drag is left to whatever else
                                // wants it.
                                claimed = kotlin.math.abs(totalDragY) >= kotlin.math.abs(totalDragX)
                            }

                            if (claimed) {
                                change.consume()
                                // Fire the moment the threshold is crossed rather than at
                                // lift-off: a real swipe often decelerates (and drifts back
                                // down slightly) in its final frame.
                                if (!hasTriggered && totalDragY < -swipeUpThresholdPx) {
                                    hasTriggered = true
                                    currentOnClick.value()
                                }
                            }
                        }
                    }
                }

            val pillHeightFraction = ((62f) / 100f).coerceIn(0.3f, 0.9f)
            val arrowColor = parseStoryColorElement(style?.arrowColor) ?: textColor

            androidx.compose.foundation.layout.Column(
                modifier = swipeUpModifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Bottom
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

                    androidx.compose.foundation.Canvas(
                        modifier = Modifier
                            .size(chevronWidth, chevronHeight)
                            .scale(scaleX = 1f, scaleY = chevronScale)   // ← vertical-only stretch
                    ) {
                        val path = Path().apply {
                            moveTo(0f, size.height)
                            lineTo(size.width / 2f, 0f)
                            lineTo(size.width, size.height)
                        }
                        drawPath(
                            path = path,
                            color = arrowColor,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                // Thickness follows the box actually drawn into, not the
                                // requested chevronHeight — Modifier.size is coerced by the
                                // parent row, so deriving from the request made a large
                                // arrowSize paint a stroke wider than the box holding it.
                                // 0.18 is the weight that reads well from ~50px to ~200px:
                                // lower is more delicate, higher is bolder. Tune here.
                                width = size.height * 0.18f,
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
                        text = cta.text ?: "Swipe up",
                        color = textColor,
                        fontSize = with(density) { fontSize.toSp() },
                        fontFamily = ctaFontFamily ?: FontFamily.SansSerif,
                        fontWeight = ctaFontWeight,
                        fontStyle = ctaFontStyle,
                        textDecoration = ctaTextDecoration
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
                    fontFamily = ctaFontFamily ?: FontFamily.SansSerif,
                    fontWeight = ctaFontWeight,
                    fontStyle = ctaFontStyle,
                    textDecoration = ctaTextDecoration,
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
        .canvaPlace(scope, x, y, w, h)
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
            val stroke =
                parseStoryColorElement(style?.strokeColor ?: el.stroke) ?: Color.Transparent
            // Same unit and same field preference as the "shape" branch above.
            val strokeW = el.strokeWidth ?: style?.strokeWidth ?: 0f
            val cr = scope.sizeDp(style?.cornerRadius ?: el.cornerRadius ?: 0f)
            Box(
                modifier = baseModifier
                    .clip(RoundedCornerShape(cr))
                    .let {
                        if (strokeW > 0f) it.border(
                            scope.heightPctDp(strokeW).coerceAtLeast(0.5.dp),
                            stroke,
                            RoundedCornerShape(cr)
                        ) else it
                    }
            )
        }

        "shape" -> {
            // Shapes ship both a vector `svgPath` (authored in a 0-100 viewBox) and a
            // rendered `.svg` url. Coil has no SVG decoder wired up in this SDK, so the
            // url silently failed to decode and shapes never appeared. Draw the path
            // ourselves instead — it needs no extra dependency and, unlike the baked
            // .svg file, it honours the fill / stroke colours the studio sends.
            val parsedPath = remember(el.svgPath) { el.svgPath?.let { parseSvgPathData(it) } }
            val fillColor = parseStoryColorElement(style?.color ?: el.fill)
            val strokeColor = parseStoryColorElement(style?.strokeColor ?: el.stroke)
            // strokeWidth is a % of canva HEIGHT, like every other scalar (font size,
            // border radius). Proof from the studio payload: 0.1667 / 0.0417 / 0.4167
            // map to exactly 4 / 1 / 10 canva px against a 2400-tall canvas, but to
            // 1.8 / 0.45 / 4.5 against its 1080 width. Measuring against width rendered
            // every shape outline 2.22x too thin.
            // Note `content.strokeWidth` is the authored value; `styling.strokeWidth`
            // carries the same stroke divided by 24, so it must stay the fallback.
            val strokeWidthDp = scope.heightPctDp(el.strokeWidth ?: style?.strokeWidth ?: 0f)
            val strokeWidthPx = with(LocalDensity.current) { strokeWidthDp.toPx() }
            val outlined = el.styleType.equals("outline", ignoreCase = true)

            if (parsedPath != null) {
                Canvas(modifier = baseModifier) {
                    // The authored viewBox is 0 0 100 100; stretch it onto the element
                    // box so the shape fills its frame exactly as it does in the editor.
                    val sx = size.width / SVG_PATH_VIEWBOX
                    val sy = size.height / SVG_PATH_VIEWBOX
                    if (sx <= 0f || sy <= 0f) return@Canvas
                    withTransform({
                        scale(sx, sy, pivot = Offset.Zero)
                    }) {
                        if (!outlined && fillColor != null) {
                            drawPath(path = parsedPath, color = fillColor)
                        }
                        if (strokeColor != null && strokeWidthPx > 0f) {
                            // Undo the (possibly non-uniform) canvas scale so the stroke
                            // keeps the requested on-screen thickness.
                            val avgScale = ((sx + sy) / 2f).coerceAtLeast(0.0001f)
                            drawPath(
                                path = parsedPath,
                                color = strokeColor,
                                style = Stroke(width = strokeWidthPx / avgScale)
                            )
                        }
                    }
                }
            } else {
                // No usable path (or an unparsable one) — fall back to the SVG url as
                // before, so nothing regresses for shapes that only ship a url.
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

// =====================================================================
// Minimal SVG path-data parser (shapes)
//
// Studio shapes carry an `svgPath` authored in a 0 0 100 100 viewBox.
// Parsing it into a Compose Path lets us draw shapes natively — no SVG
// image decoder required — and keeps the studio's fill / stroke colours.
// Supports the full command set the editor emits: M L H V C S Q T A Z
// (absolute and relative), including elliptical arcs.
// =====================================================================

internal const val SVG_PATH_VIEWBOX = 100f

private fun parseSvgPathData(data: String): Path? {
    if (data.isBlank()) return null
    val path = Path()
    var i = 0
    val n = data.length

    fun skipSeparators() {
        while (i < n && (data[i] == ',' || data[i].isWhitespace())) i++
    }

    fun readNumber(): Float? {
        skipSeparators()
        val start = i
        if (i < n && (data[i] == '+' || data[i] == '-')) i++
        var sawDigit = false
        while (i < n && data[i].isDigit()) {
            i++; sawDigit = true
        }
        if (i < n && data[i] == '.') {
            i++
            while (i < n && data[i].isDigit()) {
                i++; sawDigit = true
            }
        }
        if (sawDigit && i < n && (data[i] == 'e' || data[i] == 'E')) {
            val save = i
            i++
            if (i < n && (data[i] == '+' || data[i] == '-')) i++
            var sawExpDigit = false
            while (i < n && data[i].isDigit()) {
                i++; sawExpDigit = true
            }
            if (!sawExpDigit) i = save
        }
        if (!sawDigit) {
            i = start; return null
        }
        return data.substring(start, i).toFloatOrNull()
    }

    var command = ' '
    var curX = 0f;
    var curY = 0f
    var startX = 0f;
    var startY = 0f
    var lastCubicCtrlX = 0f;
    var lastCubicCtrlY = 0f
    var lastQuadCtrlX = 0f;
    var lastQuadCtrlY = 0f
    var prevWasCubic = false
    var prevWasQuad = false
    var emitted = false

    while (true) {
        skipSeparators()
        if (i >= n) break
        val c = data[i]
        if (c.isLetter()) {
            command = c; i++
        } else if (command == ' ') {
            i++; continue
        }   // stray token before any command

        when (command) {
            'M', 'm' -> {
                val x = readNumber() ?: break
                val y = readNumber() ?: break
                if (command == 'm') {
                    curX += x; curY += y
                } else {
                    curX = x; curY = y
                }
                path.moveTo(curX, curY)
                startX = curX; startY = curY
                emitted = true
                // Repeated coordinate pairs after a moveto are implicit linetos.
                command = if (command == 'm') 'l' else 'L'
                prevWasCubic = false; prevWasQuad = false
            }

            'L', 'l' -> {
                val x = readNumber() ?: break
                val y = readNumber() ?: break
                if (command == 'l') {
                    curX += x; curY += y
                } else {
                    curX = x; curY = y
                }
                path.lineTo(curX, curY)
                emitted = true
                prevWasCubic = false; prevWasQuad = false
            }

            'H', 'h' -> {
                val x = readNumber() ?: break
                curX = if (command == 'h') curX + x else x
                path.lineTo(curX, curY)
                emitted = true
                prevWasCubic = false; prevWasQuad = false
            }

            'V', 'v' -> {
                val y = readNumber() ?: break
                curY = if (command == 'v') curY + y else y
                path.lineTo(curX, curY)
                emitted = true
                prevWasCubic = false; prevWasQuad = false
            }

            'C', 'c' -> {
                val x1 = readNumber() ?: break;
                val y1 = readNumber() ?: break
                val x2 = readNumber() ?: break;
                val y2 = readNumber() ?: break
                val x = readNumber() ?: break;
                val y = readNumber() ?: break
                val rel = command == 'c'
                val c1x = if (rel) curX + x1 else x1
                val c1y = if (rel) curY + y1 else y1
                val c2x = if (rel) curX + x2 else x2
                val c2y = if (rel) curY + y2 else y2
                val ex = if (rel) curX + x else x
                val ey = if (rel) curY + y else y
                path.cubicTo(c1x, c1y, c2x, c2y, ex, ey)
                lastCubicCtrlX = c2x; lastCubicCtrlY = c2y
                curX = ex; curY = ey
                emitted = true
                prevWasCubic = true; prevWasQuad = false
            }

            'S', 's' -> {
                val x2 = readNumber() ?: break;
                val y2 = readNumber() ?: break
                val x = readNumber() ?: break;
                val y = readNumber() ?: break
                val rel = command == 's'
                val c1x = if (prevWasCubic) 2 * curX - lastCubicCtrlX else curX
                val c1y = if (prevWasCubic) 2 * curY - lastCubicCtrlY else curY
                val c2x = if (rel) curX + x2 else x2
                val c2y = if (rel) curY + y2 else y2
                val ex = if (rel) curX + x else x
                val ey = if (rel) curY + y else y
                path.cubicTo(c1x, c1y, c2x, c2y, ex, ey)
                lastCubicCtrlX = c2x; lastCubicCtrlY = c2y
                curX = ex; curY = ey
                emitted = true
                prevWasCubic = true; prevWasQuad = false
            }

            'Q', 'q' -> {
                val x1 = readNumber() ?: break;
                val y1 = readNumber() ?: break
                val x = readNumber() ?: break;
                val y = readNumber() ?: break
                val rel = command == 'q'
                val cx = if (rel) curX + x1 else x1
                val cy = if (rel) curY + y1 else y1
                val ex = if (rel) curX + x else x
                val ey = if (rel) curY + y else y
                path.quadraticBezierTo(cx, cy, ex, ey)
                lastQuadCtrlX = cx; lastQuadCtrlY = cy
                curX = ex; curY = ey
                emitted = true
                prevWasQuad = true; prevWasCubic = false
            }

            'T', 't' -> {
                val x = readNumber() ?: break;
                val y = readNumber() ?: break
                val rel = command == 't'
                val cx = if (prevWasQuad) 2 * curX - lastQuadCtrlX else curX
                val cy = if (prevWasQuad) 2 * curY - lastQuadCtrlY else curY
                val ex = if (rel) curX + x else x
                val ey = if (rel) curY + y else y
                path.quadraticBezierTo(cx, cy, ex, ey)
                lastQuadCtrlX = cx; lastQuadCtrlY = cy
                curX = ex; curY = ey
                emitted = true
                prevWasQuad = true; prevWasCubic = false
            }

            'A', 'a' -> {
                val rx = readNumber() ?: break
                val ry = readNumber() ?: break
                val rotationDeg = readNumber() ?: break
                val largeArc = readNumber() ?: break
                val sweep = readNumber() ?: break
                val x = readNumber() ?: break
                val y = readNumber() ?: break
                val ex = if (command == 'a') curX + x else x
                val ey = if (command == 'a') curY + y else y
                appendSvgArc(
                    path = path,
                    x0 = curX, y0 = curY,
                    rxIn = rx, ryIn = ry,
                    xAxisRotationDeg = rotationDeg,
                    largeArcFlag = largeArc != 0f,
                    sweepFlag = sweep != 0f,
                    x1 = ex, y1 = ey
                )
                curX = ex; curY = ey
                emitted = true
                prevWasCubic = false; prevWasQuad = false
            }

            'Z', 'z' -> {
                path.close()
                curX = startX; curY = startY
                prevWasCubic = false; prevWasQuad = false
            }

            else -> {
                // Unknown command — skip the character and carry on rather than
                // dropping the whole shape.
                i++
            }
        }
    }

    return if (emitted) path else null
}

/**
 * Appends an SVG elliptical arc (endpoint parameterisation) to [path] by
 * converting it to a series of cubic Bézier segments.
 */
private fun appendSvgArc(
    path: Path,
    x0: Float, y0: Float,
    rxIn: Float, ryIn: Float,
    xAxisRotationDeg: Float,
    largeArcFlag: Boolean,
    sweepFlag: Boolean,
    x1: Float, y1: Float
) {
    if (x0 == x1 && y0 == y1) return
    var rx = kotlin.math.abs(rxIn)
    var ry = kotlin.math.abs(ryIn)
    if (rx == 0f || ry == 0f) {
        path.lineTo(x1, y1); return
    }

    val phi = Math.toRadians(xAxisRotationDeg.toDouble())
    val cosPhi = kotlin.math.cos(phi)
    val sinPhi = kotlin.math.sin(phi)

    val dx2 = (x0 - x1) / 2.0
    val dy2 = (y0 - y1) / 2.0
    val x1p = cosPhi * dx2 + sinPhi * dy2
    val y1p = -sinPhi * dx2 + cosPhi * dy2

    // Scale up the radii if they are too small to span the endpoints.
    val lambda = (x1p * x1p) / (rx * rx.toDouble()) + (y1p * y1p) / (ry * ry.toDouble())
    if (lambda > 1.0) {
        val s = kotlin.math.sqrt(lambda)
        rx = (rx * s).toFloat()
        ry = (ry * s).toFloat()
    }

    val rxSq = rx.toDouble() * rx
    val rySq = ry.toDouble() * ry
    val num = (rxSq * rySq - rxSq * y1p * y1p - rySq * x1p * x1p)
        .coerceAtLeast(0.0)
    val den = rxSq * y1p * y1p + rySq * x1p * x1p
    var coef = if (den == 0.0) 0.0 else kotlin.math.sqrt(num / den)
    if (largeArcFlag == sweepFlag) coef = -coef

    val cxp = coef * rx * y1p / ry
    val cyp = -coef * ry * x1p / rx
    val cx = cosPhi * cxp - sinPhi * cyp + (x0 + x1) / 2.0
    val cy = sinPhi * cxp + cosPhi * cyp + (y0 + y1) / 2.0

    fun angle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
        val dot = ux * vx + uy * vy
        val len = kotlin.math.sqrt(ux * ux + uy * uy) * kotlin.math.sqrt(vx * vx + vy * vy)
        if (len == 0.0) return 0.0
        var a = kotlin.math.acos((dot / len).coerceIn(-1.0, 1.0))
        if (ux * vy - uy * vx < 0) a = -a
        return a
    }

    val startVecX = (x1p - cxp) / rx
    val startVecY = (y1p - cyp) / ry
    val endVecX = (-x1p - cxp) / rx
    val endVecY = (-y1p - cyp) / ry

    val theta1 = angle(1.0, 0.0, startVecX, startVecY)
    var deltaTheta = angle(startVecX, startVecY, endVecX, endVecY)
    if (!sweepFlag && deltaTheta > 0) deltaTheta -= 2 * Math.PI
    else if (sweepFlag && deltaTheta < 0) deltaTheta += 2 * Math.PI

    // One cubic per <= 90° of sweep keeps the approximation error negligible.
    val segments = kotlin.math.ceil(kotlin.math.abs(deltaTheta) / (Math.PI / 2)).toInt()
        .coerceAtLeast(1)
    val delta = deltaTheta / segments
    val t = 4.0 / 3.0 * kotlin.math.tan(delta / 4.0)

    var theta = theta1
    for (s in 0 until segments) {
        val cosTheta1 = kotlin.math.cos(theta)
        val sinTheta1 = kotlin.math.sin(theta)
        val thetaNext = theta + delta
        val cosTheta2 = kotlin.math.cos(thetaNext)
        val sinTheta2 = kotlin.math.sin(thetaNext)

        val e1x = cx + rx * cosPhi * cosTheta1 - ry * sinPhi * sinTheta1
        val e1y = cy + rx * sinPhi * cosTheta1 + ry * cosPhi * sinTheta1
        val e2x = cx + rx * cosPhi * cosTheta2 - ry * sinPhi * sinTheta2
        val e2y = cy + rx * sinPhi * cosTheta2 + ry * cosPhi * sinTheta2

        val d1x = -rx * cosPhi * sinTheta1 - ry * sinPhi * cosTheta1
        val d1y = -rx * sinPhi * sinTheta1 + ry * cosPhi * cosTheta1
        val d2x = -rx * cosPhi * sinTheta2 - ry * sinPhi * cosTheta2
        val d2y = -rx * sinPhi * sinTheta2 + ry * cosPhi * cosTheta2

        path.cubicTo(
            (e1x + t * d1x).toFloat(), (e1y + t * d1y).toFloat(),
            (e2x - t * d2x).toFloat(), (e2y - t * d2y).toFloat(),
            e2x.toFloat(), e2y.toFloat()
        )
        theta = thetaNext
    }
}

// ---- tiny JSON helpers (for interaction styling — raw JsonObject) ----

private fun jsonObjectOrNull(e: kotlinx.serialization.json.JsonElement?): kotlinx.serialization.json.JsonObject? =
    e as? kotlinx.serialization.json.JsonObject

private fun jsonFloat(e: kotlinx.serialization.json.JsonElement?): Float? = runCatching {
    (e as? kotlinx.serialization.json.JsonPrimitive)?.content?.toFloatOrNull()
}.getOrNull()

private fun jsonString(e: kotlinx.serialization.json.JsonElement?): String? = runCatching {
    (e as? kotlinx.serialization.json.JsonPrimitive)?.content
}.getOrNull()
package com.appversal.appstorys.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.appversal.appstorys.api.StoryContentImage
import com.appversal.appstorys.api.StoryContentImageStyling
import com.appversal.appstorys.api.StoryGroup
import com.appversal.appstorys.utils.FontCache
import com.appversal.appstorys.utils.SdkJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Paparazzi snapshots — Stories (STR) campaign.
 *
 * Mirrors ui/stories/StoryComponents.kt 1:1. Every default below is copied
 * from the real composables — DO NOT "improve" a value here without checking
 * the source first:
 *
 *  StoryItem (circles row):
 *   - item size          styling.size ?: 70dp
 *   - ring width         styling.ringWidth ?: 2dp
 *   - ring↔image space   styling.ringAndImageSpace ?: 8
 *   - corner radius      styling.cornerRadius ?: 0 (square by default!)
 *   - unviewed ring      storyGroupNotViewed.ringColor ?: group.ringColor ?: Gray
 *   - name               width 60dp, maxLines 2, fontSize state ?: name.size ?: 12,
 *                        color state.fontColor ?: group.nameColor ?: "#000000"
 *   - LazyRow            padding 8dp, spacedBy 8dp; item Column padding 4dp
 *
 *  StoryScreenContent (viewer):
 *   - sheet              fullscreen, containerColor Black
 *   - slide image        fillMaxSize, ContentScale.Fit, centered
 *   - progress bars      one per slide, height 3dp, clip 2dp, spacedBy 4dp,
 *                        row padding start/end 8 top 8; White on White@0.3
 *                        first frame: current bar progress = 0
 *   - header left        padding top 18 start 18; thumbnail 40dp circle,
 *                        spacer 12dp, name White 15sp medium
 *   - cross button       enabled ?: true, size ?: 32dp, fill Transparent,
 *                        cross White, iconPadding = size * 0.11
 *   - sound toggle       only for video slides; share only when slide has
 *                        link + button_text (both absent in current data)
 *
 * Two snapshots: 01_circles (over real app background), 02_viewer.
 *
 * SETUP (pipeline-config.json):
 *   "paparazziTestClasses": { "STR": "com.appversal.appstorys.ui.StoriesScreenshotTest" }
 *   IMAGE_EXTRACTORS.STR downloads group thumbnails to str_images/<groupId>.png
 *   and slide media to str_images/slide_<slideId>.png (videos via ffmpeg frame).
 */
private const val STR_JSON_RESOURCE = "campaign-data/str_details.json"
private const val STR_IMG_DIR = "str_images"

// The SDK filters eligible campaigns by the screen the host app last reported
// via getScreenCampaigns(), so a campaign only ever renders on ITS screen and
// the snapshot must composite it over THAT screen's background. Layer 3 writes
// the slug next to the details JSON (see lib/cdn.js); this used to be hardcoded
// to home_screen_kotlin, which silently drew a Lab-screen campaign over the
// Home screen. Falls back to the old constant so a campaign with no screen —
// or a checkout without the sibling file — still renders.
private const val STR_SCREEN_RESOURCE = "campaign-data/str_screen.txt"
private const val APP_BG_FALLBACK = "backgrounds/home_screen_kotlin.png"

// Story circles render INLINE in the host app, so WHERE the row sits depends
// on the host screen's layout — it is not a property of the campaign. The
// background used for STR must be captured WITHOUT stories showing, or the row
// appears duplicated.
//
// Each fraction below is measured from a real `uiautomator dump`, not guessed:
//   home_screen_kotlin — Stories() sits directly below the home_one header
//     card, landing ~24.5% down.
//   lab_home_screen_kotlin — LabScreen.kt puts Stories() after the hero image
//     plus a Spacer(100.dp). Measured: hero ends y705 of 2400 (=0.294) and the
//     WIDGET SLOT labels begin at ~0.428, so the SDK's row occupies the gap
//     between them; 0.31 centres it there.
//
// CAVEAT: the background is captured with NO campaigns on screen, so it cannot
// reflow. On a real device the story row PUSHES the content below it down (the
// "GGs" label dumps at y1179 = 0.49 with stories present), whereas the static
// PNG still shows that content at its no-campaign position. The fraction is
// therefore chosen to seat the row in the gap the SDK fills, not at the
// post-reflow device coordinate — placing it at the literal 0.40 drew the
// circles straight through the widget-slot labels.
// Using the home value on the Lab screen drew them on the hero card instead.
private val STORIES_ROW_TOP_BY_SCREEN = mapOf(
    "home_screen_kotlin" to 0.245f,
    "lab_home_screen_kotlin" to 0.31f
)
private const val STORIES_ROW_TOP_DEFAULT = 0.245f

class StoriesScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        // Strict pixel match. Paparazzi defaults to maxPercentDifference = 0.1,
        // measured over the WHOLE frame colour energy — so a changed CTA label or a
        // recoloured icon (~0.06% of the frame) silently PASSED verifyPaparazziDebug
        // and Layer 3 went on showing the stale golden. Every campaign-data change
        // this suite exists to catch is that small, so the tolerance must be 0.
        // Full post-mortem in PipVideoScreenshotTest.
        maxPercentDifference = 0.0
    )

    // Coil's AsyncImagePainter launches its request on Dispatchers.Main —
    // under Paparazzi that queue is never pumped again after composition, so
    // the image never delivers inside the single rendered frame (verified:
    // onStart→onCancel, no onSuccess). Redirecting Main to Unconfined makes
    // the whole request chain (fake engine, disk bitmap) complete INLINE
    // during composition, before Paparazzi draws.
    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun coilMainInline() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    /**
     * Pre-load every custom font the campaign references, BEFORE any snapshot.
     *
     * Both font call sites (StorySlideContent for text elements,
     * rememberInteractionFontFamily for interactions) do the same thing: start
     * with a fallback family, then `launch { FontCache.loadFont(...) }` and swap
     * it in when the download finishes. Paparazzi renders exactly ONE frame, so
     * whether a font makes it into the snapshot is a race against that frame.
     *
     * This removes the NETWORK from the snapshot path, so a font can never be
     * missing merely because a download was slow — verified: all 3 of this
     * campaign's fonts report OK from the warm-up.
     *
     * It is NOT a complete fix. Interaction fonts (rememberInteractionFontFamily)
     * DO render in their real face, but ForegroundText's fonts still fall back to
     * SansSerif in the same warmed run — so the remaining gap is that path's
     * async state swap not landing inside Paparazzi's single frame, not download
     * speed. See str-layer3-audit memory.
     *
     * FontCache.loadFont checks `fontFamilyCache[fontUrl]` first and that cache
     * is keyed by URL alone, so warming each URL once here makes every later
     * lookup resolve instantly and deterministically — the same trick the fake
     * Coil engine plays for images.
     *
     * URLs are harvested by regex over the raw campaign JSON rather than by
     * walking known paths: fonts appear under styling.text[].font, interaction
     * styling (questionFont/optionFont/...), CTA styling and group name styling,
     * and a new one would otherwise be silently missed.
     */
    @Before
    fun warmFontCache() {
        val json = javaClass.classLoader!!
            .getResourceAsStream(STR_JSON_RESOURCE)
            ?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return

        val fontUrls = Regex("""https?://[^"\s]+?\.(?:ttf|otf)""", RegexOption.IGNORE_CASE)
            .findAll(json).map { it.value }.toSet()

        if (fontUrls.isEmpty()) return
        kotlinx.coroutines.runBlocking {
            fontUrls.forEach { url ->
                runCatching { FontCache.loadFont(context = paparazzi.context, fontUrl = url) }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun coilMainReset() {
        Dispatchers.resetMain()
    }

    private fun loadGroups(): List<StoryGroup> {
        val json = javaClass.classLoader!!
            .getResourceAsStream(STR_JSON_RESOURCE)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: return emptyList()
        return runCatching { SdkJson.decodeFromString<List<StoryGroup>>(json) }
            .getOrElse { emptyList() }
    }

    @Test
    fun stories_circles_row() {
        val groups = loadGroups().sortedBy { it.order ?: 0 }

        // Background via classpath — same pattern as the floating tests. The
        // STR background MUST be captured with NO stories on screen, otherwise
        // the real inline row baked into the screenshot doubles up with the
        // overlaid one. When absent, fall back to plain white.
        // Resolve the background from the campaign's screen, falling back to the
        // Home screen when the slug or its PNG is missing.
        val screenSlug = runCatching {
            javaClass.classLoader!!
                .getResourceAsStream(STR_SCREEN_RESOURCE)
                ?.use { it.readBytes().toString(Charsets.UTF_8).trim() }
                ?.takeIf { it.isNotEmpty() }
        }.getOrNull()

        // Where the inline story row sits is a property of the HOST SCREEN.
        val storiesRowTop = STORIES_ROW_TOP_BY_SCREEN[screenSlug] ?: STORIES_ROW_TOP_DEFAULT

        val bgBitmap = runCatching {
            val cl = javaClass.classLoader!!
            val candidates = listOfNotNull(
                screenSlug?.let { "backgrounds/$it.png" },
                APP_BG_FALLBACK
            )
            candidates.firstNotNullOfOrNull { path ->
                cl.getResourceAsStream(path)?.use { BitmapFactory.decodeStream(it) }
            }
        }.getOrNull()

        // The circles row is INLINE — the host app decides where it sits. In
        // the demo app it renders just below the header card, so we overlay it
        // on the real background at STORIES_ROW_TOP_FRACTION down the screen.
        paparazzi.snapshot(name = "01_circles") {
            Box(modifier = Modifier.fillMaxSize()) {
                if (bgBitmap != null) {
                    Image(
                        bitmap = bgBitmap.asImageBitmap(),
                        contentDescription = "App background",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color.White))
                }

                // Push the row down to where the host places it inline.
                Column(modifier = Modifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.fillMaxHeight(storiesRowTop))

                // StoryCircles: LazyRow(padding 8, spacedBy 8) — replicated as
                // Row (static content, no scrolling in a snapshot)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groups.forEach { group ->
                        if (group.thumbnail == null) return@forEach
                        val styling = group.styling
                        val itemSize = (styling?.size ?: 70).dp
                        val ringWidth = (styling?.ringWidth ?: 2).dp
                        val ringAndImageSpace = styling?.ringAndImageSpace ?: 8
                        val notViewed = styling?.storyGroupNotViewed

                        val ringColor = safeColor(
                            notViewed?.ringColor ?: group.ringColor, Color.Gray
                        )
                        val cornerTl = (styling?.cornerRadius?.topLeft ?: 0).dp
                        val cornerTr = (styling?.cornerRadius?.topRight ?: 0).dp
                        val cornerBl = (styling?.cornerRadius?.bottomLeft ?: 0).dp
                        val cornerBr = (styling?.cornerRadius?.bottomRight ?: 0).dp

                        val thumbBitmap = loadImageFromDisk(STR_IMG_DIR, group.id ?: "group")

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(itemSize + (ringWidth * 2) + 4.dp)
                            ) {
                                // Ring — border shape gets ringWidth*2 added to each corner
                                Box(
                                    modifier = Modifier
                                        .size(itemSize + (ringWidth * 2))
                                        .clip(
                                            RoundedCornerShape(
                                                topStart = cornerTl + (ringWidth * 2),
                                                topEnd = cornerTr + (ringWidth * 2),
                                                bottomStart = cornerBl + (ringWidth * 2),
                                                bottomEnd = cornerBr + (ringWidth * 2)
                                            )
                                        )
                                        .border(
                                            width = ringWidth,
                                            color = ringColor,
                                            shape = RoundedCornerShape(
                                                topStart = cornerTl + (ringWidth * 2),
                                                topEnd = cornerTr + (ringWidth * 2),
                                                bottomStart = cornerBl + (ringWidth * 2),
                                                bottomEnd = cornerBr + (ringWidth * 2)
                                            )
                                        )
                                )
                                // Thumbnail
                                val thumbModifier = Modifier
                                    .size(itemSize - ringAndImageSpace.dp)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = cornerTl,
                                            topEnd = cornerTr,
                                            bottomStart = cornerBl,
                                            bottomEnd = cornerBr
                                        )
                                    )
                                if (thumbBitmap != null) {
                                    Image(
                                        bitmap = thumbBitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = thumbModifier,
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(modifier = thumbModifier.background(Color(0xFFBDBDBD)))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            val nameSize = notViewed?.fontSize ?: styling?.name?.size ?: 12
                            Text(
                                text = group.name ?: "",
                                fontSize = nameSize.sp,
                                lineHeight = (nameSize * 1.2).sp,
                                color = safeColor(
                                    notViewed?.fontColor ?: group.nameColor, Color.Black
                                ),
                                maxLines = 2,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(60.dp)
                            )
                        }
                    }
                }
                }
            }
        }
    }

    @Test
    fun stories_viewer() {
        val groups = loadGroups().sortedBy { it.order ?: 0 }

        // EVERY GROUP, ONE SNAPSHOT PER SLIDE — the whole campaign, in order,
        // exactly as a user taps through it. Media comes from
        // str_images/slide_<id>.png: images/GIFs downloaded directly, Lottie
        // as a rendered frame, videos as an ffmpeg first-frame thumbnail (all
        // fetched by the pipeline's IMAGE_EXTRACTORS.STR). A slide whose media
        // couldn't be fetched renders a labelled placeholder instead of failing.
        groups.forEachIndexed { groupIndex, group ->
        val slides = group.slides?.sortedBy { it.order ?: 0 }.orEmpty()
        if (slides.isEmpty()) return@forEachIndexed
        val thumbBitmap = group.id?.let { loadImageFromDisk(STR_IMG_DIR, it) }

        slides.forEachIndexed { slideIndex, currentSlide ->
            val slideBitmap = currentSlide.id?.let { loadImageFromDisk(STR_IMG_DIR, "slide_$it") }
            val mediaLabel = when {
                slideBitmap != null -> null
                currentSlide.video != null -> "video slide — thumbnail unavailable (is ffmpeg installed?)"
                currentSlide.image != null -> "media not downloaded — re-run pipeline"
                else -> "slide has no image/video content"
            }
            val snapName = "02_g" + String.format("%02d", groupIndex + 1) +
                "_slide_" + String.format("%02d", slideIndex + 1) +
                (if (currentSlide.video != null) "_video" else "")

            paparazzi.snapshot(name = snapName) {
            // ModalBottomSheet: fullscreen, RectangleShape, containerColor Black
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

                // Slide content — fillMaxSize, ContentScale.Fit, centered
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (slideBitmap != null) {
                        Image(
                            bitmap = slideBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else if (mediaLabel != null) {
                        Text(
                            text = mediaLabel,
                            color = Color(0xFF888888),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }

                // Header overlay: progress row + header row, top-aligned
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                ) {
                    // Progress indicators — first frame: bars before current
                    // slide full, current 0, rest 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        slides.forEachIndexed { index, _ ->
                            LinearProgressIndicator(
                                progress = { if (index < slideIndex) 1f else 0f },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }

                    // Header row: thumbnail + name (left) / buttons (right)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 18.dp, start = 18.dp)
                        ) {
                            val thumbModifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                            if (thumbBitmap != null) {
                                Image(
                                    bitmap = thumbBitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = thumbModifier,
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(modifier = thumbModifier.background(Color(0xFFBDBDBD)))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = group.name ?: "",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Right buttons. Image slide → no sound toggle; share
                        // only when slide has link + button_text; cross always
                        // (enabled ?: true). Cross: size ?: 32dp, transparent
                        // fill, white X, iconPadding = size * 0.11.
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(0.dp, Alignment.End),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Sound toggle — real viewer shows it ONLY for video
                            // slides (isImage == false), enabled ?: true, size 32
                            val soundToggle = group.styling?.soundToggle
                            if (currentSlide.video != null && soundToggle?.enabled != false) {
                                val stSize = (soundToggle?.unmute?.size ?: 32).dp
                                Box(
                                    modifier = Modifier
                                        .size(stSize)
                                        .clip(CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // speaker glyph approximation (device uses a drawable)
                                    Canvas(modifier = Modifier.fillMaxSize().padding(stSize * 0.22f)) {
                                        val w = size.width
                                        val h = size.height
                                        drawRect(
                                            color = Color.White,
                                            topLeft = Offset(0f, h * 0.3f),
                                            size = androidx.compose.ui.geometry.Size(w * 0.35f, h * 0.4f)
                                        )
                                        val tri = androidx.compose.ui.graphics.Path().apply {
                                            moveTo(w * 0.35f, h * 0.5f)
                                            lineTo(w * 0.75f, h * 0.1f)
                                            lineTo(w * 0.75f, h * 0.9f)
                                            close()
                                        }
                                        drawPath(tri, Color.White)
                                    }
                                }
                            }

                            val closeConfig = group.styling?.crossButton
                            if (closeConfig?.enabled != false) {
                                val crossSize = (closeConfig?.size ?: 32).dp
                                val crossColor = safeColor(
                                    closeConfig?.color?.cross ?: closeConfig?.colors?.cross,
                                    Color.White
                                )
                                val fillColor = safeColor(
                                    closeConfig?.color?.fill ?: closeConfig?.colors?.fill,
                                    Color.Transparent
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(
                                            top = (closeConfig?.margin?.top ?: 0).dp,
                                            end = (closeConfig?.margin?.right ?: 0).dp
                                        )
                                        .size(crossSize)
                                        .clip(CircleShape)
                                        .background(fillColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val pad = crossSize * 0.11f
                                    Canvas(modifier = Modifier.fillMaxSize().padding(pad)) {
                                        val stroke = 2.dp.toPx()
                                        drawLine(
                                            color = crossColor,
                                            start = Offset(0f, 0f),
                                            end = Offset(size.width, size.height),
                                            strokeWidth = stroke
                                        )
                                        drawLine(
                                            color = crossColor,
                                            start = Offset(size.width, 0f),
                                            end = Offset(0f, size.height),
                                            strokeWidth = stroke
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // CTA button — only when slide declares link + button_text
                val ctaText = currentSlide.buttonText
                if (!currentSlide.link.isNullOrEmpty() && !ctaText.isNullOrEmpty()) {
                    val styling = currentSlide.styling
                    val container = styling?.cta?.container
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                            .height((container?.height ?: styling?.ctaHeight ?: 32).dp)
                            .background(
                                safeColor(
                                    container?.backgroundColor
                                        ?: styling?.ctaBackground?.backgroundColor,
                                    Color.White
                                ),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = ctaText,
                            color = safeColor(
                                styling?.cta?.text?.color ?: styling?.ctaText?.fontColor,
                                Color.White
                            ),
                            fontSize = (styling?.cta?.text?.fontSize
                                ?: styling?.ctaText?.fontSize ?: 12).sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
            }
        }
        }
    }

    /**
     * Studio slides (drag-drop editor) — renderer-driven, NOT replicated.
     *
     * Calls the SDK's REAL internal composables (StorySlideBackgroundColour +
     * StorySlideForeground), so whatever the studio renderer supports is
     * automatically covered — new element types need zero test changes.
     * Foreground images are served through coil-test's FakeImageLoaderEngine
     * from files the pipeline downloaded (str_images/). Foreground videos are
     * stripped before rendering (ExoPlayer cannot run under Paparazzi); the
     * background video slot shows its ffmpeg thumbnail instead.
     * currentTime = 2s so entrance animations are past their (often
     * invisible) t=0 state.
     *
     * EVERY group is covered — one snapshot per studio slide per group:
     * 03_gXX_studio_YY. Zero studio slides in the campaign → zero snapshots
     * (test passes silently).
     */
    @Test
    fun stories_studio_slides() {
        val groups = loadGroups().sortedBy { it.order ?: 0 }

        groups.forEachIndexed { groupIndex, group ->
        val slides = group.slides.orEmpty()
            .sortedBy { it.order ?: 0 }
            .filter { it.content != null }
        if (slides.isEmpty()) return@forEachIndexed
        val thumbBitmap = group.id?.let { loadImageFromDisk(STR_IMG_DIR, it) }

        // Route every media URL the studio slides reference to a disk file
        // downloaded by the pipeline (IMAGE_EXTRACTORS.STR naming contract):
        //   background media -> slide_<slideId>.png
        //   content.image[]  -> content_<imageId>.png
        //   elements[]       -> element_<elementId>.png
        val res = android.content.res.Resources.getSystem()
        val engineBuilder = coil.test.FakeImageLoaderEngine.Builder()
        slides.forEach { slide ->
            fun route(url: String?, key: String?) {
                if (url.isNullOrBlank() || key == null) return
                val bmp = loadImageFromDisk(STR_IMG_DIR, key) ?: return
                engineBuilder.intercept(url, android.graphics.drawable.BitmapDrawable(res, bmp))
            }
            route(slide.image, slide.id?.let { "slide_$it" })
            slide.content?.image.orEmpty().forEach { img ->
                route(img.link, img.id?.let { "content_$it" })
            }
            slide.content?.elements.orEmpty().forEach { el ->
                route(el.image ?: el.url, el.id?.let { "element_$it" })
            }
            // Foreground videos render as their ffmpeg first-frame thumb
            // (video_<id>.png) — see the video→image swap below.
            slide.content?.video.orEmpty().forEach { v ->
                route(v.link, v.id?.let { "video_$it" })
            }
            // Image-type CTAs keep their asset in imageUrl/svg, not link/image,
            // so the element/content handlers above never see them. Without this
            // the CTA renders as an empty rounded grey box (the engine default)
            // while the device shows the artwork.
            slide.content?.ctas.orEmpty().forEach { cta ->
                route(cta.imageUrl ?: cta.svg, cta.id?.let { "cta_$it" })
            }
            // Media-quiz option images (interactions[].config.options[].imageUrl)
            // — the renderer loads them via Coil; without routing they snapshot
            // as empty boxes. Plain-quiz options are an object map (no images).
            slide.interactions.orEmpty().forEach { inter ->
                (inter.config?.get("options") as? JsonArray)?.forEach forEachOpt@{ o ->
                    val obj = o as? JsonObject ?: return@forEachOpt
                    val optId = (obj["id"] as? JsonPrimitive)?.contentOrNull
                    val img = (obj["imageUrl"] as? JsonPrimitive)?.contentOrNull
                    if (optId != null && inter.id != null) route(img, "option_${inter.id}_$optId")
                }
            }
        }
        engineBuilder.default(android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#BDBDBD")))
        val fakeEngine = engineBuilder.build()

        slides.forEachIndexed { slideIndex, slide ->
            // ExoPlayer cannot run in Paparazzi — swap each foreground video
            // for its ffmpeg first-frame thumb (video_<id>.png, downloaded by
            // the pipeline), rendered through ForegroundImage at the video's
            // exact geometry. Video geometry lives on the STYLING entry
            // (StoryContentVideoStyling), image geometry on the CONTENT entry
            // (StoryContentImage) — hence the field shuffle below.
            val vids = slide.content?.video.orEmpty()
            val vidStyles = slide.styling?.video.orEmpty()
            val videoAsImages = vids.mapNotNull { v ->
                if (v.id == null || v.link == null) return@mapNotNull null
                val vs = vidStyles.firstOrNull { it.id == v.id }
                StoryContentImage(
                    id = v.id, link = v.link,
                    position = vs?.position, width = vs?.width, height = vs?.height, z = vs?.z
                )
            }
            val videoImageStyles = vidStyles.map { vs ->
                StoryContentImageStyling(
                    id = vs.id, opacity = vs.opacity, rotation = vs.rotation,
                    cornerRadius = vs.cornerRadius, flip = vs.flip, animation = vs.animation
                )
            }
            val safeSlide = slide.copy(
                content = slide.content?.copy(
                    video = null,
                    image = slide.content?.image.orEmpty() + videoAsImages
                ),
                styling = slide.styling?.copy(
                    image = slide.styling?.image.orEmpty() + videoImageStyles
                )
            )
            val bgBitmap = slide.id?.let { loadImageFromDisk(STR_IMG_DIR, "slide_$it") }
            val snapName = "03_g" + String.format("%02d", groupIndex + 1) +
                "_studio_" + String.format("%02d", slideIndex + 1)

            paparazzi.snapshot(name = snapName) {
                // Fake Coil loader for THIS composition. Both the singleton
                // (Coil.setImageLoader — used by code that resolves via
                // context.imageLoader) and the CompositionLocal (the pattern
                // the banner test uses, which rememberAsyncImagePainter reads)
                // are set — the singleton alone verifiably did NOT deliver
                // images under Paparazzi.
                val ctx = androidx.compose.ui.platform.LocalContext.current
                // All dispatchers Unconfined → the whole request pipeline runs
                // inline during composition; Paparazzi renders exactly one
                // frame, so anything that parks on a dispatcher queue is lost.
                val fakeLoader = coil.ImageLoader.Builder(ctx)
                    .components { add(fakeEngine) }
                    .interceptorDispatcher(kotlinx.coroutines.Dispatchers.Unconfined)
                    .fetcherDispatcher(kotlinx.coroutines.Dispatchers.Unconfined)
                    .decoderDispatcher(kotlinx.coroutines.Dispatchers.Unconfined)
                    .transformationDispatcher(kotlinx.coroutines.Dispatchers.Unconfined)
                    .build()
                coil.Coil.setImageLoader(fakeLoader)

                // LocalInspectionMode=false: Paparazzi renders with inspection
                // mode ON, and Coil 2.6's AsyncImagePainter short-circuits to
                // a placeholder in inspection mode — the fake engine is never
                // even asked. Turning it off makes rememberAsyncImagePainter
                // execute the request, which the fake engine then serves
                // synchronously from disk.
                @Suppress("DEPRECATION")
                androidx.compose.runtime.CompositionLocalProvider(
                    coil.compose.LocalImageLoader provides fakeLoader,
                    androidx.compose.ui.platform.LocalInspectionMode provides false
                ) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    // Layer order matches the real viewer: colour layer FIRST,
                    // background media ON TOP of it (drawing colour second
                    // painted an opaque solid over legacy slide.image
                    // backgrounds and hid them entirely).
                    com.appversal.appstorys.ui.stories.StorySlideBackgroundColour(
                        slide.styling?.background
                    )
                    // Background media (image directly; video via ffmpeg thumb).
                    // Same sizing rule as the real viewer: Crop only when
                    // styling.background.media.sizing == "fill", else Fit
                    // (device shows Fit with colour bars around the image).
                    if (bgBitmap != null) {
                        val sizingFill =
                            slide.styling?.background?.media?.sizing == "fill"
                        Image(
                            bitmap = bgBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = if (sizingFill) ContentScale.Crop else ContentScale.Fit
                        )
                    }
                    com.appversal.appstorys.ui.stories.StorySlideForeground(
                        slide = safeSlide,
                        onCtaClick = {},
                        onInputFocusChanged = {},
                        onTrack = { _, _ -> },
                        currentTime = 2.0
                    )

                    // Viewer chrome: progress bars + header (same as legacy viewer)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopStart)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 0.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            slides.forEachIndexed { index, _ ->
                                LinearProgressIndicator(
                                    progress = { if (index < slideIndex) 1f else 0f },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = Color.White,
                                    trackColor = Color.White.copy(alpha = 0.3f)
                                )
                            }
                        }
                        // Header row: thumbnail + name (left) / action buttons
                        // (right) — same conditions as the REAL viewer
                        // (StoryComponents.kt): mute only when the LEGACY
                        // top-level slide.video is set, share only when the
                        // LEGACY slide.link + button_text are set, cross when
                        // enabled ?: true. Studio slides keep link/video null
                        // (data lives in content.*), so by design only the
                        // cross renders — the snapshot must show exactly that.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 18.dp, start = 18.dp)
                            ) {
                                val thumbModifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                if (thumbBitmap != null) {
                                    Image(
                                        bitmap = thumbBitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = thumbModifier,
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(modifier = thumbModifier.background(Color(0xFFBDBDBD)))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = group.name ?: "",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(0.dp, Alignment.End),
                                verticalAlignment = Alignment.Top
                            ) {
                                // Sound toggle — legacy slide.video only
                                val soundToggle = group.styling?.soundToggle
                                if (slide.video != null && soundToggle?.enabled != false) {
                                    val stSize = (soundToggle?.unmute?.size ?: 32).dp
                                    Box(
                                        modifier = Modifier.size(stSize).clip(CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Canvas(modifier = Modifier.fillMaxSize().padding(stSize * 0.22f)) {
                                            val w = size.width
                                            val h = size.height
                                            drawRect(
                                                color = Color.White,
                                                topLeft = Offset(0f, h * 0.3f),
                                                size = androidx.compose.ui.geometry.Size(w * 0.35f, h * 0.4f)
                                            )
                                            val tri = androidx.compose.ui.graphics.Path().apply {
                                                moveTo(w * 0.35f, h * 0.5f)
                                                lineTo(w * 0.75f, h * 0.1f)
                                                lineTo(w * 0.75f, h * 0.9f)
                                                close()
                                            }
                                            drawPath(tri, Color.White)
                                        }
                                    }
                                }

                                // Share — legacy slide.link + button_text only
                                val shareConfig = group.styling?.share
                                if (!slide.link.isNullOrEmpty() && !slide.buttonText.isNullOrEmpty() &&
                                    shareConfig?.enabled != false
                                ) {
                                    val shSize = (shareConfig?.size ?: 32).dp
                                    Box(
                                        modifier = Modifier.size(shSize).clip(CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Canvas(modifier = Modifier.fillMaxSize().padding(shSize * 0.25f)) {
                                            val w = size.width
                                            val h = size.height
                                            val stroke = 2.dp.toPx()
                                            // arrow-out-of-tray share glyph approximation
                                            drawLine(Color.White, Offset(w * 0.5f, h * 0.65f), Offset(w * 0.5f, 0f), stroke)
                                            drawLine(Color.White, Offset(w * 0.5f, 0f), Offset(w * 0.3f, h * 0.2f), stroke)
                                            drawLine(Color.White, Offset(w * 0.5f, 0f), Offset(w * 0.7f, h * 0.2f), stroke)
                                            drawLine(Color.White, Offset(w * 0.2f, h * 0.45f), Offset(w * 0.2f, h), stroke)
                                            drawLine(Color.White, Offset(w * 0.2f, h), Offset(w * 0.8f, h), stroke)
                                            drawLine(Color.White, Offset(w * 0.8f, h), Offset(w * 0.8f, h * 0.45f), stroke)
                                        }
                                    }
                                }

                                // Cross — enabled ?: true (the one studio shows)
                                val closeConfig = group.styling?.crossButton
                                if (closeConfig?.enabled != false) {
                                    val crossSize = (closeConfig?.size ?: 32).dp
                                    val crossColor = safeColor(
                                        closeConfig?.color?.cross ?: closeConfig?.colors?.cross,
                                        Color.White
                                    )
                                    val fillColor = safeColor(
                                        closeConfig?.color?.fill ?: closeConfig?.colors?.fill,
                                        Color.Transparent
                                    )
                                    Box(
                                        modifier = Modifier
                                            .padding(
                                                top = (closeConfig?.margin?.top ?: 0).dp,
                                                end = (closeConfig?.margin?.right ?: 0).dp
                                            )
                                            .size(crossSize)
                                            .clip(CircleShape)
                                            .background(fillColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val pad = crossSize * 0.11f
                                        Canvas(modifier = Modifier.fillMaxSize().padding(pad)) {
                                            val stroke = 2.dp.toPx()
                                            drawLine(
                                                color = crossColor,
                                                start = Offset(0f, 0f),
                                                end = Offset(size.width, size.height),
                                                strokeWidth = stroke
                                            )
                                            drawLine(
                                                color = crossColor,
                                                start = Offset(size.width, 0f),
                                                end = Offset(0f, size.height),
                                                strokeWidth = stroke
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                } // CompositionLocalProvider
            }
        }
        }
    }

    /** Same 3-candidate disk strategy as the other tests — bypasses Gradle classpath caching. */
    private fun loadImageFromDisk(imgDir: String, key: String): android.graphics.Bitmap? {
        return runCatching {
            val moduleDir = File(System.getProperty("user.dir") ?: "")
            val relativePath = "$imgDir/$key.png"
            val candidates = listOf(
                File(moduleDir, "src/test/resources/$relativePath"),
                File(moduleDir, "../../app/appstorys/src/test/resources/$relativePath"),
                File(moduleDir, "app/appstorys/src/test/resources/$relativePath")
            )
            candidates.firstOrNull { it.exists() }
                ?.inputStream()
                ?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    private fun safeColor(hex: String?, fallback: Color): Color {
        if (hex.isNullOrBlank()) return fallback
        return runCatching {
            Color(android.graphics.Color.parseColor(hex))
        }.getOrDefault(fallback)
    }
}

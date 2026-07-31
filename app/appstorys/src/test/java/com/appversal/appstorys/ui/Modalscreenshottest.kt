package com.appversal.appstorys.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.appversal.appstorys.api.Modal
import com.appversal.appstorys.api.ModalContent
import com.appversal.appstorys.api.ModalDetails
import com.appversal.appstorys.api.ModalStyling
import com.appversal.appstorys.api.TextStyling
import com.appversal.appstorys.ui.common_components.CommonText
import com.appversal.appstorys.ui.modals.ModalCTARow
import com.appversal.appstorys.ui.modals.createModalCTAButtonConfig
import com.appversal.appstorys.ui.modals.determineMediaType
import com.appversal.appstorys.utils.SdkJson
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Paparazzi snapshot — Modal (MOD) campaign.
 *
 * ── WHY THIS FILE WAS REBUILT (2026-07-31) ───────────────────────────────
 * The previous version drew a centred Material `Card` at 88% width with
 * centre-aligned text and full-width CTA slabs. The device draws nothing of
 * the sort for the campaign type this suite is pointed at. Divergences that
 * a pixel diff could never have caught, because the test was being compared
 * against its own invention:
 *
 *   1. IMAGE KEY MISMATCH — the downloader (AppStorys-QA lib/cdn.js) writes
 *      `mod_images/<modal.id || 'main'>_slide_<i>.png`, but the test asked
 *      for `"${modal.id}_slide_$si"`. This campaign's `modals[0]` has NO id
 *      field, so Kotlin interpolated the literal string "null" and every
 *      slide fell through to a nonexistent fallback. Result: no media in any
 *      snapshot, ever.
 *   2. PER-SLIDE STYLING IGNORED — backdrop colour/opacity, corner radius
 *      and the cross-button config were read from `modal.styling`, which is
 *      null for a carousel payload (styling lives inside each
 *      `content.set[i]`). FullPageCarouselModal re-resolves all of it from
 *      the CURRENT slide on every page change.
 *   3. BACKDROP ALPHA HARDCODED to 0.3f, so `backdropOpacity` (40 and 26 on
 *      this campaign's slides) was untested.
 *   4. CTA FIELDS READ FROM THE WRONG KEYS — the test read
 *      `primaryCta.backgroundColor` / `.textColor`; the SDK's
 *      createModalCTAButtonConfig prefers `primaryCta.container.backgroundColor`
 *      and `primaryCta.text.color`, plus container height/ctaWidth/border and
 *      the margin/cornerRadius blocks. Nothing of that was rendered.
 *   5. CROSS BUTTON `enabled` IGNORED — the test read `enableCrossButton`;
 *      the payload (and PopupModal) use `enabled`.
 *   6. `video` MEDIA HAD NO BRANCH at all — a video slide rendered as a
 *      blank card even when its thumbnail was on disk.
 *
 * ── WHAT IT DOES NOW ─────────────────────────────────────────────────────
 * The three modal shapes are snapshotted the way PopupModal.kt routes them,
 * matching on payload shape (NOT on details.modal_type — ModalDetails has no
 * field for that key, so it is dropped at parse time):
 *     content.set non-empty -> FullPageCarouselModal : one snapshot per slide
 *     content == null       -> MediaOnlyModal        : media + cross only
 *     otherwise             -> ModalWithCTA          : centred card
 *
 * The frame around the content still has to be replicated, because all three
 * renderers are `internal` and live inside a `Dialog`, which Paparazzi cannot
 * snapshot. Everything INSIDE the frame that can be the real thing now is:
 * `CommonText` and `ModalCTARow` / `createModalCTAButtonConfig` are the SDK's
 * own public composables, so CTA geometry, colours, borders, alignment and
 * text styling are exercised for real rather than re-invented.
 *
 * Media images come from mod_images/<key>.png (see IMAGE_EXTRACTORS.MOD).
 * Media TYPE is resolved with the SDK's own determineMediaType(url), which is
 * URL-extension based — the dashboard's `chooseMediaType.type` hint is never
 * passed by the renderer, so honouring it here would diverge from the device.
 */
private const val MOD_JSON_RESOURCE   = "campaign-data/mod_details.json"
// The campaign's own screen, written by Layer 4 each run. A modal only ever
// renders on the screen its campaign targets, so snapshotting it over the Home
// screen when it lives on the Lab screen shows a composite that never exists on
// a device. Same contract BTS and STR already use.
private const val MOD_SCREEN_RESOURCE = "campaign-data/mod_screen.txt"
private const val APP_BG_FALLBACK     = "backgrounds/home_screen_kotlin.png"
private const val MOD_IMG_DIR         = "mod_images"

class ModalScreenshotTest {

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

    @Test
    fun modal_renders() {

        val modJson = javaClass.classLoader!!
            .getResourceAsStream(MOD_JSON_RESOURCE)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }

        val details: ModalDetails? = modJson?.let {
            runCatching { SdkJson.decodeFromString<ModalDetails>(it) }.getOrNull()
        }

        val screenSlug = runCatching {
            javaClass.classLoader!!
                .getResourceAsStream(MOD_SCREEN_RESOURCE)
                ?.use { it.readBytes().toString(Charsets.UTF_8).trim() }
                ?.takeIf { it.isNotEmpty() }
        }.getOrNull()

        val bgBitmap = runCatching {
            val cl = javaClass.classLoader!!
            listOfNotNull(screenSlug?.let { "backgrounds/$it.png" }, APP_BG_FALLBACK)
                .firstNotNullOfOrNull { path ->
                    cl.getResourceAsStream(path)?.use { BitmapFactory.decodeStream(it) }
                }
        }.getOrNull()

        val modals = details?.modals.orEmpty()

        if (modals.isEmpty()) {
            paparazzi.snapshot(name = "01_modal_empty") {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A2E)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No modal data", color = Color.White, fontSize = 14.sp)
                }
            }
            return
        }

        modals.forEachIndexed { mi, modal ->
            val midx = "%02d".format(mi + 1)
            // Image keys are written by the downloader as
            // "<modal.id || 'main'>_slide_<i>" — the `?: "main"` here is the
            // half of that contract the test used to be missing.
            val modalKey = modal.id ?: "main"
            val slides = modal.content?.set

            when {
                !slides.isNullOrEmpty() -> slides.forEachIndexed { si, slide ->
                    val sidx = "%02d".format(si + 1)
                    paparazzi.snapshot(name = "${midx}_modal_slide_$sidx") {
                        CarouselSlideFrame(
                            bgBitmap = bgBitmap,
                            modal = modal,
                            slide = slide,
                            slideCount = slides.size,
                            selectedIndex = si,
                            mediaBitmap = loadImageFromDisk(MOD_IMG_DIR, "${modalKey}_slide_$si")
                                ?: loadImageFromDisk(MOD_IMG_DIR, modalKey)
                        )
                    }
                }

                modal.content == null -> paparazzi.snapshot(name = "${midx}_modal_media_only") {
                    MediaOnlyFrame(
                        bgBitmap = bgBitmap,
                        modal = modal,
                        mediaBitmap = loadImageFromDisk(MOD_IMG_DIR, modalKey)
                    )
                }

                else -> paparazzi.snapshot(name = "${midx}_modal") {
                    CtaModalFrame(
                        bgBitmap = bgBitmap,
                        modal = modal,
                        mediaBitmap = loadImageFromDisk(MOD_IMG_DIR, modalKey)
                    )
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // FullPageCarouselModal — full-screen, media on top with weight(1f),
    // dots, then the current slide's title / subtitle / CTA row at the
    // bottom, and the cross button pinned to the top-right of the SCREEN.
    // ─────────────────────────────────────────────────────────────────────
    @androidx.compose.runtime.Composable
    private fun CarouselSlideFrame(
        bgBitmap: android.graphics.Bitmap?,
        modal: Modal,
        slide: ModalContent,
        slideCount: Int,
        selectedIndex: Int,
        mediaBitmap: android.graphics.Bitmap?
    ) {
        // effectiveAppearance = slide styling first, modal styling as fallback
        val appearance = slide.styling?.appearance ?: modal.styling?.appearance
        val backdropColor = safeParseColor(
            appearance?.backdrop?.color ?: appearance?.backdropColor, Color.Black
        )
        // FullPageCarouselModal's default is 30 (NOT the 50 used by the other
        // two renderers), and enableBackdrop == false zeroes it entirely.
        val opacity = (appearance?.backdrop?.opacity ?: appearance?.backdropOpacity)
            ?.toFloatOrNull() ?: 30f
        val backdropAlpha =
            if (appearance?.enableBackdrop == false) 0f else (opacity / 100f).coerceIn(0f, 1f)

        Box(modifier = Modifier.fillMaxSize()) {

            AppBackground(bgBitmap)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backdropColor.copy(alpha = backdropAlpha))
            )

            Column(modifier = Modifier.fillMaxSize()) {

                // Media — takes all remaining space, content centred inside it
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    SlideMedia(
                        mediaBitmap = mediaBitmap,
                        mediaUrl = slide.chooseMediaType?.url,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Dots (only for a real carousel). DotsIndicator is internal in
                // the SDK, so the shape is replicated: 8dp dots, the selected
                // one stretched to 20dp, white on white-50%.
                if (slideCount > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(slideCount) { i ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .height(8.dp)
                                    .width(if (i == selectedIndex) 20.dp else 8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (i == selectedIndex) Color.White
                                        else Color.White.copy(alpha = 0.5f)
                                    )
                            )
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        val titleStyling = slide.styling?.title ?: modal.styling?.title
                        val subtitleStyling = slide.styling?.subTitle ?: modal.styling?.subTitle

                        slide.titleText?.let { title ->
                            Spacer(modifier = Modifier.height(12.dp))
                            CommonText(
                                modifier = Modifier.fillMaxWidth(),
                                text = title,
                                styling = TextStyling(
                                    color = titleStyling?.color,
                                    fontSize = titleStyling?.fontSize ?: titleStyling?.size ?: 18,
                                    fontFamily = titleStyling?.fontFamily ?: "",
                                    textAlign = titleStyling?.textAlign
                                        ?: titleStyling?.alignment?.trim()?.lowercase(),
                                    fontDecoration = titleStyling?.fontDecoration
                                )
                            )
                        }

                        slide.subtitleText?.let { subtitle ->
                            Spacer(modifier = Modifier.height(6.dp))
                            CommonText(
                                modifier = Modifier.fillMaxWidth(),
                                text = subtitle,
                                styling = TextStyling(
                                    color = subtitleStyling?.color,
                                    fontSize = subtitleStyling?.fontSize ?: subtitleStyling?.size ?: 14,
                                    fontFamily = subtitleStyling?.fontFamily ?: "",
                                    textAlign = subtitleStyling?.textAlign
                                        ?: subtitleStyling?.alignment?.trim()?.lowercase(),
                                    fontDecoration = subtitleStyling?.fontDecoration
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // CTAs sit OUTSIDE the horizontal padding so their own
                    // margins control the inset — same as the renderer.
                    // Text fallbacks mirror FullPageCarouselModal exactly.
                    SdkCtaRow(
                        primaryText = slide.primaryCtaText ?: slide.primaryCta
                            ?: modal.content?.primaryCtaText ?: modal.content?.primaryCta,
                        secondaryText = slide.secondaryCtaText ?: slide.secondayCta
                            ?: slide.secondaryCtaAlt ?: modal.content?.secondaryCtaText
                            ?: modal.content?.secondaryCtaAlt,
                        primaryStyling = slide.styling ?: modal.styling,
                        secondaryStyling = slide.styling ?: modal.styling,
                        primaryLink = slide.primaryCtaRedirection?.url
                            ?: slide.primaryCtaRedirection?.value,
                        secondaryLink = slide.secondaryCtaRedirection?.url
                            ?: slide.secondaryCtaRedirection?.value,
                        defaultHeight = 48.dp,
                        defaultWidth = null
                    )
                }
            }

            // Cross button — top-right of the SCREEN, below the status bar.
            // useDefaults = false: FullPageCarouselModal reads only the flat
            // keys and ignores everything under `crossButton.default`.
            CrossButtonReplica(
                styling = slide.styling?.crossButton ?: modal.styling?.crossButton,
                useDefaults = false,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // ModalWithCTA — a centred card whose WIDTH the SDK reads from
    // `appearance.dimension.height` (yes, height — see the audit note; that
    // is reproduced here on purpose so the snapshot shows what ships).
    // ─────────────────────────────────────────────────────────────────────
    @androidx.compose.runtime.Composable
    private fun CtaModalFrame(
        bgBitmap: android.graphics.Bitmap?,
        modal: Modal,
        mediaBitmap: android.graphics.Bitmap?
    ) {
        val appearance = modal.styling?.appearance
        val cornerRadius = appearance?.cornerRadius
        val cornerShape = RoundedCornerShape(
            topStart = cornerRadius?.topLeft?.dp ?: 0.dp,
            topEnd = cornerRadius?.topRight?.dp ?: 0.dp,
            bottomStart = cornerRadius?.bottomLeft?.dp ?: 0.dp,
            bottomEnd = cornerRadius?.bottomRight?.dp ?: 0.dp
        )
        val modalWidth = appearance?.dimension?.height?.toFloatOrNull()?.dp ?: 300.dp
        val backgroundColor = safeParseColor(appearance?.backgroundColor, Color.White)
        val backdropColor = safeParseColor(
            appearance?.backdrop?.color ?: appearance?.backdropColor, Color.Black
        )
        val opacity = (appearance?.backdrop?.opacity ?: appearance?.backdropOpacity)
            ?.toFloatOrNull() ?: 50f
        val backdropAlpha =
            if (appearance?.enableBackdrop == false) 0f else (opacity / 100f).coerceIn(0f, 1f)

        val padding = appearance?.padding

        Box(modifier = Modifier.fillMaxSize()) {
            AppBackground(bgBitmap)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backdropColor.copy(alpha = backdropAlpha))
            )
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.wrapContentSize()) {
                    Box(
                        modifier = Modifier
                            .width(modalWidth)
                            .wrapContentHeight()
                            .clip(cornerShape)
                            .background(backgroundColor)
                    ) {
                        Column(modifier = Modifier.width(modalWidth).wrapContentHeight()) {
                            SlideMedia(
                                mediaBitmap = mediaBitmap,
                                mediaUrl = modal.content?.chooseMediaType?.url
                                    ?: modal.chooseMediaType?.url,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            start = padding?.left?.dp ?: 16.dp,
                                            end = padding?.right?.dp ?: 16.dp,
                                            top = padding?.top?.dp ?: 16.dp,
                                            bottom = 0.dp
                                        ),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    modal.content?.titleText?.let { title ->
                                        val ts = modal.styling?.title
                                        Spacer(modifier = Modifier.height(12.dp))
                                        CommonText(
                                            modifier = Modifier.fillMaxWidth(),
                                            text = title,
                                            styling = TextStyling(
                                                color = ts?.color,
                                                fontSize = ts?.fontSize ?: ts?.size ?: 16,
                                                fontFamily = ts?.fontFamily ?: "",
                                                textAlign = ts?.textAlign
                                                    ?: ts?.alignment?.trim()?.lowercase(),
                                                fontDecoration = ts?.fontDecoration
                                            )
                                        )
                                    }
                                    modal.content?.subtitleText?.let { subtitle ->
                                        val ss = modal.styling?.subTitle
                                        Spacer(modifier = Modifier.height(6.dp))
                                        CommonText(
                                            modifier = Modifier.fillMaxWidth(),
                                            text = subtitle,
                                            styling = TextStyling(
                                                color = ss?.color,
                                                fontSize = ss?.fontSize ?: ss?.size ?: 14,
                                                fontFamily = ss?.fontFamily ?: "",
                                                textAlign = ss?.textAlign
                                                    ?: ss?.alignment?.trim()?.lowercase(),
                                                fontDecoration = ss?.fontDecoration
                                            )
                                        )
                                    }
                                }

                                SdkCtaRow(
                                    primaryText = modal.content?.primaryCtaText,
                                    secondaryText = modal.content?.secondaryCtaText,
                                    primaryStyling = modal.styling,
                                    secondaryStyling = modal.styling,
                                    primaryLink = modal.content?.primaryCtaRedirection?.url,
                                    secondaryLink = modal.content?.secondaryCtaRedirection?.url,
                                    defaultHeight = 40.dp,
                                    defaultWidth = 120.dp
                                )
                            }
                        }
                    }

                    // ModalWithCTA honours crossButton.default.* but has NO
                    // flat modal.enableCrossButton / modal.crossButtonImage
                    // fallback — only MediaOnlyModal has those.
                    CrossButtonReplica(
                        styling = modal.styling?.crossButton,
                        useDefaults = true,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // MediaOnlyModal — transparent container, media clipped to the corner
    // radius, cross button at the media's top-right. Width comes from the
    // flat `size` field, radius from cornerRadius ?: flat borderRadius ?: 16.
    // ─────────────────────────────────────────────────────────────────────
    @androidx.compose.runtime.Composable
    private fun MediaOnlyFrame(
        bgBitmap: android.graphics.Bitmap?,
        modal: Modal,
        mediaBitmap: android.graphics.Bitmap?
    ) {
        val appearance = modal.styling?.appearance
        val flatRadius = modal.borderRadius ?: 16
        val cr = appearance?.cornerRadius
        val cornerShape = RoundedCornerShape(
            topStart = (cr?.topLeft ?: flatRadius).dp,
            topEnd = (cr?.topRight ?: flatRadius).dp,
            bottomStart = (cr?.bottomLeft ?: flatRadius).dp,
            bottomEnd = (cr?.bottomRight ?: flatRadius).dp
        )
        val modalWidth = modal.size?.toFloatOrNull()?.dp ?: 300.dp
        val backdropColor = safeParseColor(
            appearance?.backdrop?.color ?: appearance?.backdropColor, Color.Black
        )
        val opacity = (appearance?.backdrop?.opacity ?: appearance?.backdropOpacity
            ?: modal.backgroundOpacity)?.toFloatOrNull() ?: 50f
        val enabled = (appearance?.enableBackdrop ?: modal.enableBackdrop) != false
        val backdropAlpha = if (enabled) (opacity / 100f).coerceIn(0f, 1f) else 0f

        Box(modifier = Modifier.fillMaxSize()) {
            AppBackground(bgBitmap)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backdropColor.copy(alpha = backdropAlpha))
            )
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.width(modalWidth).wrapContentHeight()) {
                    Box(modifier = Modifier.clip(cornerShape)) {
                        SlideMedia(
                            mediaBitmap = mediaBitmap,
                            mediaUrl = modal.chooseMediaType?.url ?: modal.url,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    // MediaOnlyModal is the only renderer that falls back to
                    // the FLAT modal.enableCrossButton / modal.crossButtonImage.
                    CrossButtonReplica(
                        styling = modal.styling?.crossButton,
                        useDefaults = true,
                        flatEnable = modal.enableCrossButton,
                        flatImage = modal.crossButtonImage,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }
            }
        }
    }

    // ── Shared pieces ────────────────────────────────────────────────────

    @androidx.compose.runtime.Composable
    private fun AppBackground(bgBitmap: android.graphics.Bitmap?) {
        if (bgBitmap != null) {
            Image(
                bitmap = bgBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D1117)))
        }
    }

    /**
     * The downloader turns every media kind into a PNG on disk: images and
     * GIFs directly, Lottie via a rendered frame, video via an ffmpeg
     * first-frame thumb. When one is missing we say WHICH kind is missing
     * rather than drawing an anonymous grey box — a blank rectangle in a
     * golden is indistinguishable from "the campaign has no media".
     */
    @androidx.compose.runtime.Composable
    private fun SlideMedia(
        mediaBitmap: android.graphics.Bitmap?,
        mediaUrl: String?,
        modifier: Modifier = Modifier
    ) {
        if (mediaBitmap != null) {
            // The renderer passes fillMaxWidth().wrapContentHeight() with the
            // default ContentScale.Fit and NO preloaded aspect ratio, so the
            // media takes the full width and whatever height its own aspect
            // ratio implies — measured on device for slide 1: 1080x608 for a
            // 16:9 asset, NOT the full height of the pager area. Pinning the
            // ratio explicitly reproduces that; plain fillMaxWidth let the
            // Image expand into all the space the Box offered and FillWidth
            // then cropped it, so the golden showed a zoomed centre crop the
            // device never draws.
            val ratio = if (mediaBitmap.height > 0)
                mediaBitmap.width.toFloat() / mediaBitmap.height.toFloat() else 1f
            Image(
                bitmap = mediaBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = modifier.aspectRatio(ratio),
                contentScale = ContentScale.FillWidth
            )
            return
        }
        if (mediaUrl.isNullOrBlank()) return
        // Same resolution the renderer uses: URL extension, not the payload's
        // type hint (ModalMediaRendererWithCallback never passes the hint).
        val kind = determineMediaType(mediaUrl)
        Box(
            modifier = modifier.height(180.dp).background(Color(0xFFBDBDBD)),
            contentAlignment = Alignment.Center
        ) {
            Text("$kind media not downloaded", color = Color(0xFF555555), fontSize = 11.sp)
        }
    }

    /**
     * Real SDK CTA rendering — createModalCTAButtonConfig + ModalCTARow are
     * public, so container height/width/border/alignment, margin, corner
     * radius and text styling are the shipped code paths, not a replica.
     */
    @androidx.compose.runtime.Composable
    private fun SdkCtaRow(
        primaryText: String?,
        secondaryText: String?,
        primaryStyling: ModalStyling?,
        secondaryStyling: ModalStyling?,
        primaryLink: String?,
        secondaryLink: String?,
        defaultHeight: Dp,
        defaultWidth: Dp?
    ) {
        val primaryConfig = primaryText?.takeIf { it.isNotBlank() }?.let {
            createModalCTAButtonConfig(
                text = it,
                styling = primaryStyling?.primaryCta,
                redirectionUrl = primaryLink,
                defaultHeight = defaultHeight,
                defaultWidth = defaultWidth,
                defaultBackgroundColor = Color.Black
            )
        }
        val secondaryConfig = secondaryText?.takeIf { it.isNotBlank() }?.let {
            createModalCTAButtonConfig(
                text = it,
                styling = secondaryStyling?.secondaryCta,
                redirectionUrl = secondaryLink,
                defaultHeight = defaultHeight,
                defaultWidth = defaultWidth,
                defaultBackgroundColor = Color.DarkGray
            )
        }
        if (primaryConfig == null && secondaryConfig == null) return
        ModalCTARow(
            primaryConfig = primaryConfig,
            secondaryConfig = secondaryConfig,
            onPrimaryCta = null,
            onSecondaryCta = null
        )
    }

    /**
     * CrossButton is `internal`, so its geometry is replicated exactly:
     * circle of `size`, fill, a size*0.05 border when strokeColor is not
     * transparent, glyph inset size*0.11, default size 18dp. The glyph is the
     * SDK's own R.drawable.cross so the shape can never drift from the app.
     *
     * `useDefaults` exists because THE THREE RENDERERS DO NOT AGREE on how to
     * resolve this control, and a single replica would silently diverge from
     * two of them:
     *   MediaOnlyModal / ModalWithCTA — `default.size`, `default.color`,
     *       `default.spacing.margin`, `default.crossButtonImage` are consulted
     *       BEFORE the flat keys.
     *   FullPageCarouselModal        — reads ONLY the flat keys; anything
     *       under `default` is ignored outright.
     * `flatEnable` / `flatImage` carry `modal.enableCrossButton` and
     * `modal.crossButtonImage`, which only MediaOnlyModal falls back to.
     */
    @androidx.compose.runtime.Composable
    private fun CrossButtonReplica(
        styling: com.appversal.appstorys.api.ModalCrossButton?,
        modifier: Modifier = Modifier,
        useDefaults: Boolean = true,
        flatEnable: Boolean? = null,
        flatImage: String? = null
    ) {
        val enabledFlag = styling?.enabled ?: styling?.enableCrossButton ?: flatEnable
        if (enabledFlag == false) return

        val rawSize = if (useDefaults) (styling?.default?.size ?: styling?.size) else styling?.size
        val size = (rawSize ?: 18).dp
        val colors = if (useDefaults) (styling?.default?.color ?: styling?.color ?: styling?.colors)
        else (styling?.color ?: styling?.colors)
        val margin = if (useDefaults) (styling?.default?.spacing?.margin ?: styling?.margin)
        else styling?.margin
        val imageUrl = if (useDefaults)
            (styling?.uploadImage?.url ?: styling?.default?.crossButtonImage ?: styling?.image ?: flatImage)
        else (styling?.uploadImage?.url ?: styling?.image)

        val fill = safeParseColor(colors?.fill, Color.Transparent)
        val stroke = safeParseColor(colors?.stroke, Color.Transparent)
        val glyph = safeParseColor(colors?.cross, Color.White)

        Box(
            modifier = modifier
                .padding(top = (margin?.top ?: 0).dp, end = (margin?.right ?: 0).dp)
                .size(size)
                .clip(CircleShape)
                .background(fill)
                .then(
                    if (stroke != Color.Transparent) Modifier.border(size * 0.05f, stroke, CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!imageUrl.isNullOrBlank()) {
                // The device loads this over the network with Coil and draws it
                // INSTEAD of the glyph. Paparazzi has no network, so mark the
                // frame explicitly — silently falling back to the default cross
                // would make a custom-icon campaign look identical to a default
                // one in the golden, which is the failure mode this whole suite
                // exists to prevent.
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF7E57C2)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("IMG", color = Color.White, fontSize = 7.sp)
                }
            } else {
                Icon(
                    painter = painterResource(com.appversal.appstorys.R.drawable.cross),
                    contentDescription = "Close",
                    tint = glyph,
                    modifier = Modifier.padding(size * 0.11f)
                )
            }
        }
    }

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

    private fun safeParseColor(hex: String?, fallback: Color): Color {
        if (hex.isNullOrBlank()) return fallback
        return runCatching {
            Color(android.graphics.Color.parseColor(hex))
        }.getOrDefault(fallback)
    }
}

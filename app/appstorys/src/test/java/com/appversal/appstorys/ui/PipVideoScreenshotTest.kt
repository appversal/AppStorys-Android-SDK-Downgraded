package com.appversal.appstorys.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.appversal.appstorys.api.PipDetails
import com.appversal.appstorys.ui.common_components.CrossButton
import com.appversal.appstorys.ui.common_components.CTAButton
import com.appversal.appstorys.ui.common_components.ExpandButton
import com.appversal.appstorys.ui.common_components.SoundToggleButton
import com.appversal.appstorys.ui.common_components.createCrossButtonConfig
import com.appversal.appstorys.ui.common_components.createCTAButtonConfig
import com.appversal.appstorys.ui.common_components.createExpandButtonConfig
import com.appversal.appstorys.ui.common_components.createSoundToggleButtonConfig
import com.appversal.appstorys.utils.SdkJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import org.junit.Rule
import org.junit.Test

/**
 * Paparazzi snapshot — PIP video campaign.
 *
 * The pipeline writes the live campaign styling to:
 *   src/test/resources/campaign-data/pip_details.json
 * before this test runs (same mechanism as ban_details.json for BAN).
 * All dimensions, button colours, CTA text, and position come from that
 * file, so the snapshot always reflects the currently published campaign —
 * no hardcoded values here.
 *
 * Two named snapshots (shown as a left/right carousel in the dashboard):
 *   01_small     — floating PIP card in its corner position
 *   02_maximized — full-screen PIP after the user taps to expand
 *
 * WHY NO REAL VIDEO:
 * Paparazzi runs on the JVM without GPU/media hardware, so ExoPlayer cannot
 * decode frames. The video area is a dark placeholder — all layout, sizing,
 * corner radius, and button overlays are still validated exactly.
 *
 * SETUP:
 *   1. Add your PIP campaign ID to pipeline-config.json:
 *        "paparazziCampaigns": { "BAN": "...", "PIP": "<your-pip-campaign-id>" }
 *   2. Run the pipeline once — it fetches pip_details.json automatically.
 *   3. First run fails (no golden yet) → click "Approve New Baseline".
 *   4. All future runs compare against that approved baseline.
 */

private const val PIP_JSON_RESOURCE = "campaign-data/pip_details.json"
private const val APP_BG_RESOURCE   = "backgrounds/home_screen_kotlin.png"
private const val PIP_THUMB_RESOURCE = "pip_images/thumbnail.png"


// Nav-bar gap measured empirically — see PinnedBannerScreenshotTest for method.
private val MEASURED_BOTTOM_NAV_GAP = 79.dp

// PIP card sits 12dp from screen edges (matches boundaryPadding in PipVideoPlayer.kt)
private val PIP_EDGE_PADDING = 12.dp

// Dark placeholder that simulates a buffering/paused video frame
private val VIDEO_PLACEHOLDER_COLOR = Color(0xFF1C1C1C)

class PipVideoScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        // Paparazzi's default maxPercentDifference is 0.1 (%) of the TOTAL colour
        // energy of the frame, which is far too loose for this pipeline: changing
        // the CTA label from "Continue" to "Here is the link" moves only 0.058% of
        // the frame, so verifyPaparazziDebug PASSED and Layer 3 reported "matches
        // baseline" while showing the stale golden. Every campaign-data change we
        // test for (button text, colours, small icons) is a sub-0.1% change, so the
        // tolerance must be 0 — the render is deterministic on a fixed layoutlib,
        // and goldens are always re-recorded through Approve New Baseline.
        maxPercentDifference = 0.0
    )

    @Test
    fun pipVideo_smallAndMaximized() {

        // ── Load live campaign data written by the pipeline ───────────────────
        val pipJson = javaClass.classLoader!!
            .getResourceAsStream(PIP_JSON_RESOURCE)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }

        val details: PipDetails? = pipJson?.let {
            runCatching { SdkJson.decodeFromString<PipDetails>(it) }.getOrNull()
        }

        val styling = details?.styling

        // Try to load the thumbnail extracted from small_video by the pipeline.
        // Falls back to null gracefully if ffmpeg wasn't available or pipeline hasn't run yet.
        val videoThumbnailBitmap = runCatching {
            javaClass.classLoader!!
                .getResourceAsStream(PIP_THUMB_RESOURCE)
                ?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()

        // Real video aspect from the extracted first-frame thumbnail. The device
        // derives this from ExoPlayer's videoSize (PipPlayerView.kt:326-330) and
        // aspect-FITS the frame — it never assumes 16:9. Using the thumbnail's
        // own width/height reproduces that: a portrait 1080x1920 video → 0.563,
        // which must render TALL (letterboxed on the sides when small, top/bottom
        // when maximized), exactly like the real phone.
        val videoAspect: Float = videoThumbnailBitmap
            ?.takeIf { it.height > 0 }
            ?.let { it.width.toFloat() / it.height.toFloat() }
            ?: (16f / 9f)

        // Parse a JsonElement CTA dimension exactly like PipVideoPlayer.kt:580-586.
        fun jsonToInt(e: JsonElement?): Int? = (e as? JsonPrimitive)?.let {
            it.intOrNull ?: it.content.takeIf(String::isNotBlank)?.toIntOrNull()
        }

        // Dimensions — mirrors AppStorys.kt's exact fallback chain
        val pipWidth: Dp  = styling?.appearance?.pipWidth?.toIntOrNull()?.dp
            ?: details?.width?.dp  ?: 200.dp
        val pipHeight: Dp = styling?.appearance?.pipHeight?.toIntOrNull()?.dp
            ?: details?.height?.dp ?: 113.dp

        // Button configs — identical factory calls to AppStorys.kt
        val crossButtonCfg = run {
            val cb = styling?.crossButton
            val colors = cb?.color ?: cb?.colors
            createCrossButtonConfig(
                fillColorString   = colors?.fill,
                crossColorString  = colors?.cross,
                strokeColorString = colors?.stroke,
                marginTop         = cb?.margin?.top,
                marginEnd         = cb?.margin?.right,
                size              = cb?.size
            )
        }
        val maximiseCfg = run {
            val m = styling?.expandControls?.maximise
            val colors = m?.color ?: m?.colors
            createExpandButtonConfig(
                fillColorString   = colors?.fill,
                iconColorString   = colors?.cross,
                strokeColorString = colors?.stroke,
                size              = m?.size
            )
        }
        val minimiseCfg = run {
            val m = styling?.expandControls?.minimise
            val colors = m?.color ?: m?.colors
            createExpandButtonConfig(
                fillColorString   = colors?.fill,
                iconColorString   = colors?.cross,
                strokeColorString = colors?.stroke,
                size              = m?.size
            )
        }
        val muteCfg = run {
            val s = styling?.soundToggle?.mute
            val colors = s?.color ?: s?.colors
            createSoundToggleButtonConfig(
                fillColorString   = colors?.fill,
                iconColorString   = colors?.cross,
                strokeColorString = colors?.stroke,
                size              = s?.size
            )
        }
        val unmuteCfg = run {
            val s = styling?.soundToggle?.unmute
            val colors = s?.color ?: s?.colors
            createSoundToggleButtonConfig(
                fillColorString   = colors?.fill,
                iconColorString   = colors?.cross,
                strokeColorString = colors?.stroke,
                size              = s?.size
            )
        }

        // Device honors per-control enabled flags (PipVideoPlayer.kt):
        // sound/cross/expand each default true unless styling disables them
        val isSoundToggleEnabled = styling?.soundToggle?.enabled ?: true
        val isCrossEnabled       = styling?.crossButton?.enabled ?: true
        val isExpandEnabled      = styling?.expandControls?.enabled ?: true

        val hasFullScreen = !details?.large_video.isNullOrEmpty() && isExpandEnabled
        val ctaText       = details?.button_text?.takeIf { it.isNotBlank() } ?: ""

        val ctaConfig = styling?.cta.let { cta ->
            val container = cta?.container
            val text      = cta?.text
            val margin    = cta?.margin
            val radius    = cta?.cornerRadius
            createCTAButtonConfig(
                textColor             = text?.color ?: styling?.ctaButtonTextColor ?: "#FFFFFF",
                textSize              = text?.fontSize ?: styling?.fontSize?.toIntOrNull() ?: 14,
                backgroundColorString = container?.backgroundColor ?: styling?.ctaButtonBackgroundColor ?: "#F7921C",
                borderColorString     = container?.borderColor ?: "#FE6B35",
                // Mirror PipVideoPlayer.kt: the device draws the container's real
                // border (2px) and height (50), not a hardcoded borderless 48.
                borderWidth           = jsonToInt(container?.borderWidth) ?: 0,
                height                = jsonToInt(container?.height) ?: styling?.ctaHeight?.toIntOrNull() ?: 48,
                // Mirror PipVideoPlayer.kt:612 — the device passes the fixed
                // ctaWidth. Without it, width=null + fullWidth=false hits
                // CTAButton's else branch (no width constraint), and the inner
                // fillMaxWidth Text then stretches the button to full width —
                // making the snapshot ignore ctaFullWidth:false. Passing the
                // width reproduces the real device's fixed-width button.
                width                 = container?.ctaWidth ?: styling?.ctaWidth?.toIntOrNull(),
                fullWidth             = container?.ctaFullWidth ?: styling?.ctaFullWidth ?: true,
                marginTop             = margin?.top ?: styling?.marginTop?.toIntOrNull() ?: 0,
                marginEnd             = margin?.right ?: styling?.marginRight?.toIntOrNull() ?: 0,
                marginBottom          = margin?.bottom ?: styling?.marginBottom?.toIntOrNull() ?: 0,
                marginStart           = margin?.left ?: styling?.marginLeft?.toIntOrNull() ?: 0,
                borderRadiusTopLeft   = radius?.topLeft ?: styling?.cornerRadius?.toIntOrNull() ?: 0,
                borderRadiusTopRight  = radius?.topRight ?: styling?.cornerRadius?.toIntOrNull() ?: 0,
                borderRadiusBottomLeft  = radius?.bottomLeft ?: styling?.cornerRadius?.toIntOrNull() ?: 0,
                borderRadiusBottomRight = radius?.bottomRight ?: styling?.cornerRadius?.toIntOrNull() ?: 0
            )
        }

        // ── Snapshot 1: Small floating PIP ───────────────────────────────────
        paparazzi.snapshot(name = "01_small") {

            val bgBitmap = runCatching {
                javaClass.classLoader!!
                    .getResourceAsStream(APP_BG_RESOURCE)
                    ?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()

            Box(modifier = Modifier.fillMaxSize()) {

                // Real app background
                if (bgBitmap != null) {
                    Image(
                        bitmap             = bgBitmap.asImageBitmap(),
                        contentDescription = "App background",
                        modifier           = Modifier.fillMaxSize(),
                        contentScale       = ContentScale.Crop
                    )
                }

                // Outer box applies nav-bar gap + edge padding so the Card's
                // align(BottomEnd) matches exactly where the runtime PIP lands
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            bottom = MEASURED_BOTTOM_NAV_GAP + PIP_EDGE_PADDING,
                            end    = PIP_EDGE_PADDING
                        )
                ) {
                    Card(
                        modifier  = Modifier
                            .size(width = pipWidth, height = pipHeight)
                            .align(Alignment.BottomEnd),
                        shape     = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                        ) {
                            // Video placeholder
                            // Video area — real thumbnail if pipeline extracted it, dark placeholder otherwise
                            Box(
                                modifier         = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (videoThumbnailBitmap != null) {
                                    // ContentScale.Fit (not Crop) mirrors the device:
                                    // PipPlayerView aspect-FITS the frame, so a portrait
                                    // video in the 98x148 card fills height and leaves the
                                    // real black side-bars. Crop wrongly filled the card.
                                    Image(
                                        bitmap             = videoThumbnailBitmap.asImageBitmap(),
                                        contentDescription = "Video thumbnail",
                                        modifier           = Modifier.fillMaxSize(),
                                        contentScale       = ContentScale.Fit
                                    )
                                } else {
                                    Box(
                                        modifier         = Modifier.fillMaxSize().background(VIDEO_PLACEHOLDER_COLOR),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("▶", color = Color.White.copy(alpha = 0.45f), fontSize = 22.sp)
                                    }
                                }
                            }

                            if (isSoundToggleEnabled) {
                                SoundToggleButton(
                                    modifier     = Modifier.align(Alignment.TopStart),
                                    muteConfig   = muteCfg,
                                    unmuteConfig = unmuteCfg,
                                    // Device passes `isMuted = !isMuted` here (PipVideoPlayer.kt:259)
                                    // with the state starting false → true → the MUTE glyph shows on
                                    // the small pip. (The maximized view passes `isMuted` un-negated,
                                    // so it shows unmute — the two views are inconsistent in the SDK
                                    // itself; the snapshot mirrors the device rather than "correcting" it.)
                                    isMuted      = true,
                                    onToggle     = {}
                                )
                            }

                            if (isCrossEnabled) {
                                CrossButton(
                                    modifier = Modifier.align(Alignment.TopEnd),
                                    config   = crossButtonCfg,
                                    onClose  = {}
                                )
                            }

                            if (hasFullScreen) {
                                ExpandButton(
                                    modifier       = Modifier.align(Alignment.BottomEnd),
                                    maximiseConfig = maximiseCfg,
                                    minimiseConfig = minimiseCfg,
                                    isExpanded     = false,
                                    onToggle       = {}
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Snapshot 2: Maximized full-screen PIP ────────────────────────────
        paparazzi.snapshot(name = "02_maximized") {

            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                // Video area — real video aspect, centred. The device fills width
                // and letterboxes top/bottom (PipPlayerView), so a portrait video
                // renders TALL here — not the old hardcoded 16:9 landscape band.
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .aspectRatio(videoAspect),
                    contentAlignment = Alignment.Center
                ) {
                    if (videoThumbnailBitmap != null) {
                        Image(
                            bitmap             = videoThumbnailBitmap.asImageBitmap(),
                            contentDescription = "Video thumbnail",
                            modifier           = Modifier.fillMaxSize(),
                            contentScale       = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier         = Modifier.fillMaxSize().background(VIDEO_PLACEHOLDER_COLOR),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("▶", color = Color.White.copy(alpha = 0.35f), fontSize = 48.sp)
                        }
                    }
                }

                // Minimise button — top-start (hidden when expand controls disabled)
                if (isExpandEnabled) {
                    ExpandButton(
                        modifier       = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp),
                        maximiseConfig = maximiseCfg.copy(size = 46.dp),
                        minimiseConfig = minimiseCfg.copy(size = 46.dp),
                        isExpanded     = true,
                        onToggle       = {}
                    )
                }

                // Sound + Close — top-end row (each honors its enabled flag)
                Row(
                    modifier              = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    if (isSoundToggleEnabled) {
                        SoundToggleButton(
                            modifier     = Modifier,
                            muteConfig   = muteCfg.copy(size = 46.dp),
                            unmuteConfig = unmuteCfg.copy(size = 46.dp),
                            isMuted      = false,
                            onToggle     = {}
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    if (isCrossEnabled) {
                        CrossButton(
                            modifier = Modifier,
                            config   = crossButtonCfg.copy(size = 46.dp),
                            onClose  = {}
                        )
                    }
                }

                // CTA button — bottom-centre (only when campaign has one)
                if (ctaText.isNotBlank()) {
                    CTAButton(
                        text     = ctaText,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        config   = ctaConfig,
                        onClick  = {}
                    )
                }
            }
        }
    }
}
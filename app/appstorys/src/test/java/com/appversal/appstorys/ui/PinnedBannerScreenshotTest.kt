package com.appversal.appstorys.ui

import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.compose.LocalImageLoader
import coil.test.FakeImageLoaderEngine
import com.appversal.appstorys.api.BannerDetails
import com.appversal.appstorys.ui.common_components.createCrossButtonConfig
import com.appversal.appstorys.utils.SdkJson
import org.junit.Rule
import org.junit.Test

/**
 * Paparazzi snapshot — PinnedBanner at its real on-screen position.
 *
 * LIVE DATA: the pipeline (Layer 3 in pipeline-server.js) fetches the latest
 * campaign JSON from the CDN before every run and writes it to
 * src/test/resources/campaign-data/banner_details.json. This test reads
 * that file — so the snapshot always reflects what's actually published,
 * not a value frozen at whatever moment someone hardcoded it.
 *
 * Running this test directly in Android Studio (outside the pipeline) falls
 * back to FALLBACK_BANNER_JSON below, with a console warning, so the test
 * still works standalone — but for trustworthy CI results, always go
 * through the pipeline so the live-fetch step runs first.
 *
 * POSITION & SIZE: matches AppStorys.kt's actual structure exactly —
 * the bottom-nav gap (~79dp, see MEASURED_BOTTOM_NAV_GAP) is applied to the
 * OUTER full-screen container via .padding(bottom = ...), and the banner is
 * then placed inside via .align(Alignment.BottomCenter). This is critical:
 * applying that gap to the banner's own bottomMargin instead (as an earlier
 * version of this test did) squeezes the aspect-ratio-sized box's visible
 * content rather than shifting its screen position — producing a banner
 * that renders roughly half its real height. bottomMargin here is
 * campaign-styling-only (style.marginBottom), matching production.
 *
 * BACKGROUND SETUP (one-time per client):
 *   1. Disable campaign in dashboard, open app on the correct screen
 *   2. Click "Capture Background" in the pipeline dashboard
 *      — OR run manually:
 *        adb shell screencap /sdcard/appstorys_bg.png
 *        adb pull /sdcard/appstorys_bg.png src/test/resources/backgrounds/home_screen_kotlin.png
 *   3. .\gradlew :app:appstorys:recordPaparazziDebug  (approve the new baseline)
 */

// Fallback only — used if the pipeline's live-fetch step hasn't run yet
// (e.g. running this test directly from Android Studio). The pipeline
// always overwrites src/test/resources/campaign-data/banner_details.json
// with fresh CDN data before invoking Paparazzi.
private const val FALLBACK_BANNER_JSON = """
{
  "height": 1152,
  "id": "231265ee-2e9d-4f71-96ed-89d1265661a3",
  "image": "https://appstorysmediabucketdev.s3.ap-south-1.amazonaws.com/banners/download_5.png",
  "link": "https://whereuelevate.com/jobs",
  "styling": {
    "bottomLeftRadius": 0,
    "bottomRightRadius": 0,
    "crossButton": {
      "color": { "cross": "#FFFFFF", "fill": "#000000", "stroke": "#F7921C" },
      "enabled": true,
      "image": "",
      "margin": { "bottom": 0, "left": 0, "right": 4, "top": 4 },
      "selectedStyle": "cross4",
      "size": 18
    },
    "marginBottom": 0,
    "marginLeft": 0,
    "marginRight": 0,
    "originalHeight": 1152,
    "originalWidth": 2048,
    "topLeftRadius": 0,
    "topRightRadius": 0,
    "userModifiedHeight": false
  },
  "width": 2048
}
"""

// Written fresh by the pipeline before every run — see class doc above.
// Path matches refreshAllCampaignDataFromCdn() in pipeline-server.js, which
// writes to campaign-data/<type>_details.json using the lowercase campaign
// type code (from pipeline-config.json's paparazziCampaigns key "BAN").
private const val LIVE_BANNER_DETAILS_RESOURCE = "campaign-data/ban_details.json"

// Bottom gap above the app's nav bar, empirically measured from a real device
// screenshot: 223px / 2400px total height = 9.29%, converted to Paparazzi's
// PIXEL_5 profile (2340px height @ 2.75x density) ≈ 79dp.
// The exact runtime source of this gap isn't visible in static XML (likely
// set programmatically in MainActivity based on measured BottomNavigationView
// height) — this constant keeps the snapshot visually matched to reality
// regardless of where it comes from. Re-measure and update if the host app's
// nav bar height changes.
private val MEASURED_BOTTOM_NAV_GAP = 79.dp

// Path matches refreshAllCampaignDataFromCdn()'s IMAGE_EXTRACTORS.BAN entry,
// which downloads to <resourceRoot>/ban_images/main.png (key="main" for the
// banner's single image field).
private const val BANNER_IMAGE_RESOURCE = "ban_images/main.png"
private const val APP_BG_RESOURCE       = "backgrounds/home_screen_kotlin.png"

class PinnedBannerScreenshotTest {

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

    @OptIn(ExperimentalCoilApi::class)
    @Test
    fun pinnedBanner_rendersAtBottomOnRealAppBackground_4ce78510() {
        // Try live data first (written by the pipeline before this test runs).
        // Fall back to the hardcoded snapshot only for standalone Android
        // Studio runs, with a loud console warning so it's never silently stale.
        val liveJson = javaClass.classLoader!!
            .getResourceAsStream(LIVE_BANNER_DETAILS_RESOURCE)
            ?.bufferedReader()?.use { it.readText() }

        val bannerJson = if (liveJson != null) {
            println("[PinnedBannerScreenshotTest] Using LIVE campaign data from CDN (fetched by pipeline)")
            liveJson
        } else {
            println("[PinnedBannerScreenshotTest] WARNING: no live data found — using FALLBACK_BANNER_JSON. " +
                    "Run via the pipeline (not directly in Android Studio) to test against real published data.")
            FALLBACK_BANNER_JSON
        }

        val details = SdkJson.decodeFromString<BannerDetails>(bannerJson)
        val style   = details.styling

        // BannerStyling has marginBottom/Left/Right only — no marginTop
        val marginBottom = style?.marginBottom ?: 0
        val marginLeft   = style?.marginLeft   ?: 0
        val marginRight  = style?.marginRight  ?: 0

        val aspectRatio: Float? = run {
            val w = details.width; val h = details.height
            if (w != null && h != null && w > 0 && h > 0) h.toFloat() / w.toFloat() else null
        }
        val detailsHeight = details.height
        val forcedHeight = if (details.width == null && detailsHeight != null) detailsHeight.dp else null

        val crossColors   = style?.crossButton?.color ?: style?.crossButton?.colors
        val crossImageUrl = style?.crossButton?.image ?: details.crossButtonImage

        paparazzi.snapshot {

            // ── Real app background ───────────────────────────────────────────
            // Captured via "Capture Background" in the pipeline dashboard.
            // If missing: snapshot renders on plain background until captured.
            val bgBitmap = runCatching {
                javaClass.classLoader!!
                    .getResourceAsStream(APP_BG_RESOURCE)
                    ?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()

            // Live creative, downloaded fresh by the pipeline before this test
            // runs. Graceful fallback (not a crash) if missing — same pattern
            // as the JSON above — so a download hiccup fails loudly via a
            // visibly blank banner in the snapshot, not a hard test crash.
            val bannerBitmap = runCatching {
                javaClass.classLoader!!
                    .getResourceAsStream(BANNER_IMAGE_RESOURCE)
                    ?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()

            if (bannerBitmap == null) {
                println("[PinnedBannerScreenshotTest] WARNING: no banner creative found at " +
                        "$BANNER_IMAGE_RESOURCE — pipeline's image download step may not have run yet.")
            }

            val fakeEngine = FakeImageLoaderEngine.Builder()
                .apply {
                    if (bannerBitmap != null) {
                        intercept(
                            details.image ?: "",
                            BitmapDrawable(LocalContext.current.resources, bannerBitmap)
                        )
                    }
                }
                .build()
            val fakeLoader = ImageLoader.Builder(LocalContext.current)
                .components { add(fakeEngine) }
                .build()

            CompositionLocalProvider(LocalImageLoader provides fakeLoader) {

                // Full device canvas
                Box(modifier = Modifier.fillMaxSize()) {

                    // Layer 0 — real app background
                    if (bgBitmap != null) {
                        Image(
                            bitmap             = bgBitmap.asImageBitmap(),
                            contentDescription = "App background",
                            modifier           = Modifier.fillMaxSize(),
                            contentScale       = ContentScale.Crop
                        )
                    }

                    // Layer 1 — banner at BOTTOM, above nav bar.
                    // Mirrors AppStorys.kt's actual structure: the nav-bar gap is
                    // applied to the OUTER container BEFORE the banner is sized,
                    // not to the banner's own bottomMargin (which is campaign-only).
                    // Applying it to bottomMargin instead squeezes the aspect-ratio
                    // box's visible content rather than shifting its position —
                    // that was the original bug causing a visibly shorter banner.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = MEASURED_BOTTOM_NAV_GAP)
                    ) {
                        PinnedBanner(
                            modifier     = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth(),
                            imageUrl     = details.image ?: "",
                            lottieUrl    = details.lottie_data,
                            contentScale = ContentScale.FillWidth,
                            shape        = RoundedCornerShape(
                                topStart    = style?.topLeftRadius?.toIntOrNull()?.dp     ?: 0.dp,
                                topEnd      = style?.topRightRadius?.toIntOrNull()?.dp    ?: 0.dp,
                                bottomEnd   = style?.bottomRightRadius?.toIntOrNull()?.dp ?: 0.dp,
                                bottomStart = style?.bottomLeftRadius?.toIntOrNull()?.dp  ?: 0.dp,
                            ),
                            exitIcon     = (style?.crossButton?.enabled
                                ?: style?.enableCloseButton) != false,
                            exitUnit     = {},
                            bottomMargin = marginBottom.dp,   // campaign styling only — no nav gap here
                            leftMargin   = marginLeft.dp,
                            rightMargin  = marginRight.dp,
                            aspectRatio  = aspectRatio,
                            forcedHeight = forcedHeight,
                            onClick      = {},
                            crossButtonConfig = createCrossButtonConfig(
                                fillColorString   = crossColors?.fill,
                                crossColorString  = crossColors?.cross,
                                strokeColorString = crossColors?.stroke,
                                marginTop         = style?.crossButton?.margin?.top,
                                marginEnd         = style?.crossButton?.margin?.right,
                                size              = style?.crossButton?.size,
                                imageUrl          = crossImageUrl
                            )
                        )
                    }
                }
            }
        }
    }
}
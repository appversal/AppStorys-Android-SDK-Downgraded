package com.appversal.appstorys.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.appversal.appstorys.api.FloaterDetails
import com.appversal.appstorys.utils.SdkJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

private const val FLT_JSON_RESOURCE = "campaign-data/flt_details.json"
private const val FLT_IMG_RESOURCE  = "flt_images/main.png"
private const val APP_BG_RESOURCE   = "backgrounds/home_screen_kotlin.png"

class FloaterScreenshotTest {

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

    /**
     * Guards [floaterAlignmentUnderTest] against drifting away from the SDK.
     *
     * The screenshot test has to mirror the SDK's positioning by hand, and that
     * mirror silently broke once already: the test rendered a "left" floater on
     * the RIGHT while the SDK put it on the left, and the pixel comparison
     * could never notice because it only ever compares this test to its own
     * baseline. So instead of comparing pixels, this reads the SDK source and
     * checks the actual mapping, with NO change to the SDK required.
     *
     * If someone edits the SDK's `when (floaterDetails.position)`, this fails
     * and names the file to update — turning an invisible wrong-golden into a
     * loud, specific failure.
     */
    @Test
    fun floaterAlignmentMatchesSdk() {
        val sdkFile = File("src/main/java/com/appversal/appstorys/AppStorys.kt")
        assertTrue(
            "Cannot find AppStorys.kt at ${sdkFile.absolutePath} — if the SDK moved, " +
                "update this guard so the mirror in this file keeps being checked.",
            sdkFile.exists()
        )

        // Grab the `when (floaterDetails.position) { ... }` block.
        val src = sdkFile.readText()
        val start = src.indexOf("when (floaterDetails.position)")
        assertTrue(
            "AppStorys.kt no longer contains `when (floaterDetails.position)`. The floater " +
                "positioning moved or was renamed; re-point this guard and re-check " +
                "floaterAlignmentUnderTest in this file.",
            start >= 0
        )
        val block = src.substring(start, minOf(src.length, start + 400))
            .substringAfter("{").substringBefore("}")

        // Every branch the SDK declares must agree with the mirror above.
        val branches = Regex("\"(\\w+)\"\\s*->\\s*Alignment\\.(\\w+)")
            .findAll(block)
            .map { it.groupValues[1] to it.groupValues[2] }
            .toList()
        assertTrue(
            "Expected the SDK to map at least \"left\" and \"right\"; found $branches",
            branches.size >= 2
        )
        // Compare Alignment OBJECTS, never their toString(): Alignment.BottomEnd
        // prints as "BiasAlignment(horizontalBias=1.0, verticalBias=1.0)", so a
        // string match on "BottomEnd" never succeeds.
        branches.forEach { (position, sdkAlignment) ->
            val expected = alignmentNamed(sdkAlignment)
            assertTrue(
                "AppStorys.kt uses Alignment.$sdkAlignment, which this guard does not know. " +
                    "Add it to alignmentNamed() below.",
                expected != null
            )
            assertEquals(
                "SDK maps position \"$position\" -> Alignment.$sdkAlignment, but this test's " +
                    "floaterAlignmentUnderTest disagrees. Update floaterAlignmentUnderTest in " +
                    "Floaterscreenshottest.kt to match the SDK, then re-record the floater baseline.",
                expected,
                floaterAlignmentUnderTest(position)
            )
        }

        // And the fallback: the SDK sends unknown values to the start edge.
        val elseBranch = Regex("else\\s*->\\s*Alignment\\.(\\w+)").find(block)?.groupValues?.get(1)
        if (elseBranch != null) {
            assertEquals(
                "SDK's else branch is Alignment.$elseBranch; floaterAlignmentUnderTest must " +
                    "send unknown positions to the same place.",
                alignmentNamed(elseBranch),
                floaterAlignmentUnderTest("a-position-the-sdk-does-not-know")
            )
        }
    }

    @Test
    fun floater_rendersAtPosition() {

        // JSON via classpath — always worked, Gradle always copies JSON correctly
        val floJson = javaClass.classLoader!!
            .getResourceAsStream(FLT_JSON_RESOURCE)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }

        val details: FloaterDetails? = floJson?.let {
            runCatching { SdkJson.decodeFromString<FloaterDetails>(it) }.getOrNull()
        }

        // Background via classpath — no issues here
        val bgBitmap = runCatching {
            javaClass.classLoader!!
                .getResourceAsStream(APP_BG_RESOURCE)
                ?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()

        // Image via DISK — bypasses Gradle classpath caching that was
        // keeping the stale WebP file instead of sharp's converted JPEG/PNG.
        // Paparazzi's user.dir is the Gradle module directory
        // (app/appstorys), so we go up two levels to reach the project root.
        val floaterBitmap = runCatching {
            val moduleDir = java.io.File(System.getProperty("user.dir") ?: "")
            // Try module-relative path first (app/appstorys working dir)
            val candidates = listOf(
                java.io.File(moduleDir, "src/test/resources/$FLT_IMG_RESOURCE"),
                java.io.File(moduleDir, "../../app/appstorys/src/test/resources/$FLT_IMG_RESOURCE"),
                java.io.File(moduleDir, "app/appstorys/src/test/resources/$FLT_IMG_RESOURCE")
            )
            candidates.firstOrNull { it.exists() }
                ?.inputStream()
                ?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()

        val width  = (details?.width  ?: 60).dp
        val height = (details?.height ?: 60).dp

        val styling   = details?.styling
        val bottomPad = styling?.marginBottom?.toFloatOrNull()?.dp ?: 0.dp
        val rightPad  = styling?.marginRight?.toFloatOrNull()?.dp  ?: 0.dp
        val leftPad   = styling?.marginLeft?.toFloatOrNull()?.dp   ?: 0.dp

        val shape = RoundedCornerShape(
            topStart    = styling?.topLeftRadius?.toFloatOrNull()?.dp    ?: 0.dp,
            topEnd      = styling?.topRightRadius?.toFloatOrNull()?.dp   ?: 0.dp,
            bottomStart = styling?.bottomLeftRadius?.toFloatOrNull()?.dp ?: 0.dp,
            bottomEnd   = styling?.bottomRightRadius?.toFloatOrNull()?.dp ?: 0.dp
        )

        val alignment = floaterAlignmentUnderTest(details?.position)

        val snapshotName =
            "01_floater_${details?.position?.replace("-", "_") ?: "bottom_right"}"

        paparazzi.snapshot(name = snapshotName) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (bgBitmap != null) {
                    Image(
                        bitmap = bgBitmap.asImageBitmap(),
                        contentDescription = "App background",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5)))
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = bottomPad, start = leftPad, end = rightPad),
                    contentAlignment = alignment
                ) {
                    // Device: Surface with Color.Transparent, 16dp padding outside,
                    // image drawn ContentScale.FillBounds (OverlayFloater.kt)
                    Box(
                        modifier = Modifier
                            .padding(16.dp)
                            .size(width = width, height = height)
                            .clip(shape)
                    ) {
                        if (floaterBitmap != null) {
                            Image(
                                bitmap = floaterBitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.FillBounds
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize().background(Color(0xFFBDBDBD)))
                        }
                    }
                }
            }
        }
    }
}

/**
 * MIRRORS the SDK's floater positioning in AppStorys.kt's `Floater()`:
 *
 *     when (floaterDetails.position) {
 *         "right" -> Alignment.BottomEnd
 *         "left"  -> Alignment.BottomStart
 *         else    -> Alignment.BottomStart
 *     }
 *
 * This test paints its own copy of OverlayFloater (the real one loads its image
 * over the network via Coil, which Paparazzi cannot do), so the positioning has
 * to be mirrored here. THAT MIRROR IS THE RISK: the previous version matched on
 * "topleft" / "topright" / "bottomleft" with `else -> Alignment.BottomEnd`,
 * but the backend sends "left" / "right". A "left" campaign matched none of
 * those branches, fell to `else`, and this test rendered the floater
 * bottom-RIGHT while the SDK rendered it bottom-LEFT — confirmed on device at
 * bounds [41,1986][194,2139]. The golden was even named `01_floater_left`
 * while showing a floater on the right.
 *
 * Layer 3 cannot catch this class of bug: it only ever compares this test
 * against this test's own baseline, so a wrong test yields a permanently green
 * wrong golden. `floaterAlignmentMatchesSdk` below is the guard — if anyone
 * edits the SDK's mapping, that test fails and points here.
 */
internal fun floaterAlignmentUnderTest(position: String?): Alignment = when (position) {
    "right" -> Alignment.BottomEnd
    "left" -> Alignment.BottomStart
    else -> Alignment.BottomStart
}

/** Resolves an `Alignment.X` name parsed out of the SDK source to the real constant. */
internal fun alignmentNamed(name: String): Alignment? = when (name) {
    "TopStart" -> Alignment.TopStart
    "TopCenter" -> Alignment.TopCenter
    "TopEnd" -> Alignment.TopEnd
    "CenterStart" -> Alignment.CenterStart
    "Center" -> Alignment.Center
    "CenterEnd" -> Alignment.CenterEnd
    "BottomStart" -> Alignment.BottomStart
    "BottomCenter" -> Alignment.BottomCenter
    "BottomEnd" -> Alignment.BottomEnd
    else -> null
}
package com.appversal.appstorys.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.appversal.appstorys.api.ScratchCardDetails
import com.appversal.appstorys.utils.SdkJson
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Paparazzi snapshot — Scratch Card (SCRT) campaign.
 *
 * Two snapshots:
 *   01_scratchcard_cover    — unscratched state: coverImage overlays the card
 *   02_scratchcard_revealed — revealed state:    bannerImage + coupon_code + CTA
 *
 * Both images loaded from disk to bypass Gradle classpath caching.
 *
 * SETUP (pipeline-config.json):
 *   "paparazziTestClasses": { "SCRT": "com.appversal.appstorys.ui.ScratchCardScreenshotTest" }
 * IMAGE_EXTRACTORS.SCRT → downloads bannerImage → scrt_images/banner.png
 *                          downloads coverImage  → scrt_images/cover.png
 */
private const val SCRT_JSON_RESOURCE = "campaign-data/scrt_details.json"
private const val APP_BG_RESOURCE    = "backgrounds/home_screen_kotlin.png"
private const val SCRT_IMG_DIR       = "scrt_images"

class ScratchCardScreenshotTest {

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
    fun scratchcard_states() {

        val scrtJson = javaClass.classLoader!!
            .getResourceAsStream(SCRT_JSON_RESOURCE)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }

        val details: ScratchCardDetails? = scrtJson?.let {
            runCatching { SdkJson.decodeFromString<ScratchCardDetails>(it) }.getOrNull()
        }

        val bgBitmap     = loadClasspath(APP_BG_RESOURCE)
        val bannerBitmap = loadDisk(SCRT_IMG_DIR, "banner")
        val coverBitmap  = loadDisk(SCRT_IMG_DIR, "cover")

        // Card dimensions from JSON
        val cardW = (details?.width  ?: 320).dp
        val cardH = (details?.height ?: 180).dp

        // ── Snapshot 1: Cover (unscratched) ──────────────────────────────────
        paparazzi.snapshot(name = "01_scratchcard_cover") {
            Box(modifier = Modifier.fillMaxSize()) {
                // App background
                if (bgBitmap != null) {
                    Image(bitmap = bgBitmap.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A2E)))
                }
                // Backdrop
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))

                // Scratch card centred on screen
                Box(modifier = Modifier.align(Alignment.Center)) {
                    Card(
                        modifier  = Modifier
                            .then(if (cardW > 0.dp) Modifier.fillMaxWidth(0.88f) else Modifier)
                            .wrapContentHeight(),
                        shape     = RoundedCornerShape(32.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                        colors    = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Box {
                            // Layer 0 — banner image (peeking underneath)
                            if (bannerBitmap != null) {
                                Image(
                                    bitmap = bannerBitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxWidth().height(cardH),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxWidth().height(cardH)
                                    .background(Color(0xFFE0E0E0)))
                            }

                            // Layer 1 — cover image (the scratchable surface)
                            if (coverBitmap != null) {
                                Image(
                                    bitmap = coverBitmap.asImageBitmap(),
                                    contentDescription = "Scratch to reveal",
                                    modifier = Modifier.fillMaxWidth().height(cardH),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                // Fallback cover — silver gradient appearance
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(cardH)
                                        .background(Color(0xFFC0C0C0)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("🪙", fontSize = 40.sp)
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = "Scratch to reveal!",
                                            color = Color(0xFF555555),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Snapshot 2: Revealed ──────────────────────────────────────────────
        paparazzi.snapshot(name = "02_scratchcard_revealed") {
            Box(modifier = Modifier.fillMaxSize()) {
                if (bgBitmap != null) {
                    Image(bitmap = bgBitmap.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A2E)))
                }
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))

                Box(modifier = Modifier.align(Alignment.Center)) {
                    Card(
                        modifier  = Modifier.fillMaxWidth(0.88f).wrapContentHeight(),
                        shape     = RoundedCornerShape(32.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                        colors    = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Revealed banner image
                            if (bannerBitmap != null) {
                                Image(
                                    bitmap = bannerBitmap.asImageBitmap(),
                                    contentDescription = "Revealed reward",
                                    modifier = Modifier.fillMaxWidth().height(cardH),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(cardH)
                                        .background(Color(0xFFFFF8E1)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("🎉", fontSize = 48.sp)
                                }
                            }

                            // Coupon code (if available)
                            if (!details?.coupon_code.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 12.dp)
                                        .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0xFFDDDDDD), RoundedCornerShape(8.dp))
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text       = details!!.coupon_code!!,
                                        color      = Color(0xFF333333),
                                        fontSize   = 14.sp,   // device default couponFontSize ?: 14
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 3.sp
                                    )
                                }
                            }

                            // CTA button
                            if (!details?.button_text.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 4.dp)
                                        .height(48.dp)   // device: ctaHeight 48, corner 12, margin 4
                                        .background(Color(0xFFF97316), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text       = details!!.button_text!!,
                                        color      = Color.White,
                                        fontSize   = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            } else {
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadClasspath(res: String): android.graphics.Bitmap? = runCatching {
        javaClass.classLoader!!.getResourceAsStream(res)?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    private fun loadDisk(dir: String, key: String): android.graphics.Bitmap? = runCatching {
        val mod = File(System.getProperty("user.dir") ?: "")
        val rel = "$dir/$key.png"
        listOf(
            File(mod, "src/test/resources/$rel"),
            File(mod, "../../app/appstorys/src/test/resources/$rel"),
            File(mod, "app/appstorys/src/test/resources/$rel")
        ).firstOrNull { it.exists() }?.inputStream()?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}
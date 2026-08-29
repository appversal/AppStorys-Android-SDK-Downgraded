package com.example.carousal

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appversal.appstorys.utils.appstorys

/**
 * Lab — the isolated campaign + analytics testbed. Bottom-nav tab 0, and the
 * DEFAULT tab, so a launch lands here.
 *
 * Registers the screen "Lab Home Screen Kotlin". Four campaign types target it:
 * BTS, MOD, STR and TTP (see the campaign-data screen .txt files under
 * src/test/resources).
 *
 * Laid out like HomeScreen so campaigns render over realistic content, with two
 * deliberate differences:
 *
 *  1. NO Buttons. bts_smoke.yaml and bts_backpress.yaml both record that the
 *     "Open Bottom Sheet" button lives on the HOME screen and that this screen
 *     has none. That button opens the demo app's OWN ModalBottomSheet, which
 *     registers the separate screen "Bottom Sheet Kotlin" — a different code
 *     path from the SDK composable the BTS campaign renders through. Keeping
 *     controls off this screen means a campaign is the only thing a flow can
 *     hit, which is the whole point of the Lab tab.
 *
 *  2. CopyUserIdText() is pinned near the top. It prints
 *     AppStorys.getUserId(), which is the id the SDK was initialised with —
 *     the per-run QA id from shared_prefs/qa_override.xml when the pipeline
 *     set one (see App.qaOverrideUserId), otherwise the persisted local id. It
 *     makes the run's analytics identity visible on screen and copyable.
 *
 * The two tagged elements are tooltip anchors: ttp_details.json targets
 * "lab_hero" and "lab_switch_user".
 */
@Composable
fun LabScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val campaignManager = App.appStorys

    LaunchedEffect(Unit) {
        val screenName = "Lab Home Screen Kotlin"
        val positions = listOf("widget_one")
        campaignManager.getScreenCampaigns(
            screenName,
            positions,
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F2F4))
            .appstorys("lab_lazy_column")
            .padding(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding()
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            campaignManager.Stories()
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Lab",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0752AD),
                    modifier = Modifier.appstorys("lab_hero")
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "QA user",
                    fontSize = 12.sp,
                    color = Color(0xFF5A5A5A),
                    modifier = Modifier.appstorys("lab_switch_user")
                )
                CopyUserIdText()
            }
        }

        item {
            Image(
                painter = painterResource(id = R.drawable.home_top),
                contentDescription = "Lab banner",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Fit
            )
        }

        item {
            campaignManager.Widget(
                modifier = Modifier.fillMaxWidth(),
                placeholder = context.getDrawable(R.drawable.ic_launcher_foreground),
                position = "widget_one"
            )
        }

        item {
            Image(
                painter = painterResource(id = R.drawable.home_one),
                contentDescription = "Lab content",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Fit
            )
        }

        item {
            Image(
                painter = painterResource(id = R.drawable.home_two),
                contentDescription = "Lab content",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Fit
            )
        }

        item {
            Spacer(modifier = Modifier.height(120.dp))
        }
    }
}

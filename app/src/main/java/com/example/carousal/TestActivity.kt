package com.example.carousal

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.appversal.appstorys.ui.xml.BottomSheetView
import com.appversal.appstorys.ui.xml.OverlayLayoutView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class TestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        findViewById<OverlayLayoutView>(R.id.overlay_layout)?.setActivity(this)

        findViewById<Button>(R.id.open_bottom_sheet).setOnClickListener {
//            findViewById<BottomSheetView>(R.id.bottom_sheet_view).open()
        }
        findViewById<Button>(R.id.open_more_screen).setOnClickListener {
            startActivity(Intent(this, MoreActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        App.appStorys.getScreenCampaigns(
            "Home Screen",
            emptyList()
        )
        lifecycleScope.launch {
            App.appStorys.setUserProperties(mapOf("hello" to "world"))
        }
    }
}
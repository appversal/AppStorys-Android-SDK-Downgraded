package com.example.carousal

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import com.appversal.appstorys.AppStorys
import com.appversal.appstorys.domain.model.ScreenOptions
import com.appversal.appstorys.presentation.xml.ScreenView


class TestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val screen = findViewById<ScreenView>(R.id.activity_home)
        screen.setName("Home Screen")
        screen.setOptions(
            ScreenOptions(
                padding = PaddingValues(top = 70.dp),
                pipPadding = PaddingValues(bottom = 70.dp)
            )
        )

        findViewById<Button>(R.id.open_bottom_sheet).setOnClickListener {
//            findViewById<BottomSheetView>(R.id.bottom_sheet_view).open()
        }
        findViewById<Button>(R.id.open_more_screen).setOnClickListener {
            startActivity(Intent(this, MoreActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        AppStorys.setUserProperties(this, mapOf("hello" to "world"))
    }
}
package com.example.carousal

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.appversal.appstorys.presentation.xml.ScreenView


class MoreActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_more)
        findViewById<ScreenView>(R.id.activity_more).setName("Cashbook Tab")
    }
}
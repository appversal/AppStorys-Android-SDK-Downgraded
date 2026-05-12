package com.example.carousal

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.example.carousal.databinding.ActivityHomeBinding
import kotlinx.coroutines.launch

class TestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_host_activity)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, TestHomeFragment())
                .commit()
        }
    }
}

//package com.example.carousal
//
//import android.content.Intent
//import android.os.Bundle
//import android.widget.Button
//import androidx.appcompat.app.AppCompatActivity          // ← changed from ComponentActivity
//import androidx.lifecycle.lifecycleScope
//import com.appversal.appstorys.ui.xml.OverlayLayoutView
//import kotlinx.coroutines.launch
//
//class TestActivity : AppCompatActivity() {              // ← changed from ComponentActivity
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_home)
//
////        findViewById<OverlayLayoutView>(R.id.root_overlay).setActivity(this)
//
//        findViewById<Button>(R.id.open_bottom_sheet).setOnClickListener {
//            TestBottomSheetFragment().show(supportFragmentManager, "TestBottomSheet")
//        }
//
//        findViewById<Button>(R.id.open_more_screen).setOnClickListener {
//            startActivity(Intent(this, MoreActivity::class.java))
//        }
//    }
//
//    override fun onResume() {
//        super.onResume()
//        App.appStorys.getScreenCampaigns("Home Screen Kotlin XML", emptyList())
//        lifecycleScope.launch {
//            App.appStorys.setUserProperties(mapOf("hello" to "world"))
//        }
//    }
//}
package com.example.carousal

import android.app.Application
import android.content.Context
import android.util.Log
import com.appversal.appstorys.AppStorys
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class App : Application() {

    val screenNameNavigation = MutableStateFlow("")
    private val appScope = MainScope()

    override fun onCreate() {
        super.onCreate()

        val userId = getOrCreateUserId()

        // Initialize CampaignManager with userId and appId
        AppStorys.initialize(
            context = this,
//            appId =  "37ca2d75-8484-4cc1-97ed-d9475ce5a631",
//            accountId = "4e109ac3-be92-4a5c-bbe6-42e6c712ec9a",
//            appId = "9e1b21a2-350a-4592-918c-2a19a73f249a",  // prod test
//            accountId = "4350bf8e-0c9a-46bd-b953-abb65ab21d11",  // prod test
            appId = "f69bdccf-b20f-4938-b39e-7075d76db791",  // dev test
            accountId = "12a9eac5-94ee-4735-9aa6-b8a94cb8fbbb",  // dev test
//            userId = userId,
            userId = "nameisanirudh",
//            userId = "b119f894-37fe-44a2-9108-77d06dedf912",
            navigateToScreen = { screen ->
                println("Navigating to $screen")
                navigateToScreen(screen)
            }
        )

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                AppStorys.setFirebaseToken(token)
                Log.d("FCM_TOKEN", token)
            }
            .addOnFailureListener { e ->
                Log.w("FCM_TOKEN", "FCM token fetch failed", e)
            }

        appStorys = AppStorys
    }

    private fun getOrCreateUserId(): String {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val existingUserId = prefs.getString("appstorys_user_id", null)
        return if (existingUserId != null) {
            existingUserId
        } else {
            val newUserId = UUID.randomUUID().toString()
            prefs.edit().putString("appstorys_user_id", newUserId).apply()
            newUserId
        }
    }


    fun navigateToScreen(name: String) {
        appScope.launch {
            screenNameNavigation.emit(name)
        }
    }

    fun resetNavigation() {
        appScope.launch {
            screenNameNavigation.emit("")
        }
    }

    companion object {
        lateinit var appStorys: AppStorys
            private set
    }
}

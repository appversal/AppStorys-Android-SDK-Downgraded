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
            appId =  "",
            accountId = "",
//            userId = userId,
//            userId = "nameisanirudh",
            userId = "001e77ce-7c2b-407e-a948b",
            navigateToScreen = { screen ->
                println("Navigating to $screen")
                navigateToScreen(screen)
            }
        )

        Log.d("FCM_TOKEN", "requesting token...")
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    AppStorys.setFirebaseToken(token)
                    Log.d("FCM_TOKEN", "success: $token")
                }
                .addOnFailureListener { e ->
                    Log.w("FCM_TOKEN", "FCM token fetch failed", e)
                }
        } catch (e: Exception) {
            Log.e("FCM_TOKEN", "getInstance().token threw synchronously", e)
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

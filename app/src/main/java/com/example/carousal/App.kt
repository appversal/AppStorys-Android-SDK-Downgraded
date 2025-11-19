package com.example.carousal

import android.app.Application
import android.content.Context
import com.appversal.appstorys.AppStorys
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class App : Application() {

    val screenNameNavigation = MutableStateFlow("")
    private val appScope = MainScope()

    override fun onCreate() {
        super.onCreate()
        val attributes: Map<String, Any> = mapOf("name" to "Alice", "age" to 25)

        val userId = getOrCreateUserId()

        // Initialize CampaignManager with userId and appId
        AppStorys.initialize(
            context = this,
            appId = "c2f8cc49-2c90-4086-9b9c-b64db3ca93f2",
            accountId = "0d29cb83-bd10-44df-987d-a59521f13bf7",
//            appId = "",
//            accountId = "",
//            userId = userId,
            userId = "08eef7e4-aa86-48ec-9255-b3762a4ab091",
            attributes = attributes,
            navigateToScreen = { screen ->
                println("Navigating to $screen")
                navigateToScreen(screen)
            }
        )

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

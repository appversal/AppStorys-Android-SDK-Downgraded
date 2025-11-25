package com.appversal.appstorys.domain

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.appversal.appstorys.api.Campaign
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonElement

internal object State {
    internal lateinit var prefs: SharedPreferences

    private val _userId = MutableStateFlow("")
    val userId: StateFlow<String> = _userId.asStateFlow()

    private val _appId = MutableStateFlow("")
    val appId = _appId.asStateFlow()

    private val _accountId = MutableStateFlow("")
    val accountId = _accountId.asStateFlow()

    private val _campaigns = MutableStateFlow<List<Campaign>>(emptyList())
    val campaigns = _campaigns.asStateFlow()

    private val _trackedEvents = MutableStateFlow<List<String>>(emptyList())
    val trackedEvents = _trackedEvents.asStateFlow()

    private val _impressions = MutableStateFlow<List<String>>(emptyList())
    val impressions = _impressions.asStateFlow()

    private val _attributes = MutableStateFlow<Map<String, JsonElement>>(emptyMap())
    val attributes = _attributes.asStateFlow()

    private val _disabledCampaigns = MutableStateFlow<Set<String>>(emptySet())
    val disabledCampaigns = _disabledCampaigns.asStateFlow()

    private val _isCapturing = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val isCapturing = _isCapturing.asStateFlow()

    private val _isCapturingEnabled = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val isCapturingEnabled = _isCapturingEnabled.asStateFlow()

    /**
     * Tells the SDK whether the sdk components are visible to the user,
     * this is very important for features like pip where the sdk needs to know
     * whether the user can see the pip or not to pause/resume the pip video
     */
    var isVisible by mutableStateOf(true)

    /**
     * Initialize encrypted storage
     */
    fun initialize(context: Context) {
        if (this::prefs.isInitialized) {
            return
        }

        prefs = EncryptedSharedPreferences.create(
            context,
            "appstorys_encrypted_prefs",
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveCampaigns(campaigns: List<Campaign>) {
        _campaigns.value += campaigns
    }

    fun setUserId(userId: String) {
        _userId.value = userId
    }

    fun setAppId(appId: String) {
        _appId.value = appId
    }

    fun setAccountId(accountId: String) {
        _accountId.value = accountId
    }

    fun setAttributes(attributes: Map<String, JsonElement>) {
        _attributes.value = attributes
    }

    fun setTrackedEvents(trackedEvents: List<String>) {
        _trackedEvents.value = trackedEvents
    }

    fun removeTrackedEvent(event: String) {
        _trackedEvents.update {
            it.filter { e -> e != event }
        }
    }

    fun addImpression(impression: String) {
        _impressions.update {
            it + impression
        }
    }

    fun addDisabledCampaign(campaignId: String) {
        _disabledCampaigns.update {
            it + campaignId
        }
    }

    fun setIsCapturing(screenName: String, isCapturing: Boolean) {
        _isCapturing.update {
            buildMap {
                putAll(it)
                put(screenName, isCapturing)
            }
        }
    }

    fun setIsCapturingEnabled(screenName: String, isEnabled: Boolean) {
        _isCapturingEnabled.update {
            buildMap {
                putAll(it)
                put(screenName, isEnabled)
            }
        }
    }

    fun saveAccessToken(token: String) = prefs.edit {
        putString("access_token", token)
    }

    fun getAccessToken(): String? = prefs.getString("access_token", null)?.let {
        when {
            it.isNotBlank() -> "Bearer $it"
            else -> null
        }
    }

    fun clearAccessToken() = prefs.edit {
        remove("access_token")
    }
}
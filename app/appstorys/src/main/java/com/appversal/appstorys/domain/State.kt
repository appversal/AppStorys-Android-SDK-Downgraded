package com.appversal.appstorys.domain

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInRoot
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.appversal.appstorys.api.Campaign
import com.appversal.appstorys.domain.model.AppStorysCoordinates
import com.appversal.appstorys.domain.model.AppStorysSdkState
import com.appversal.appstorys.domain.model.Impression
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal object State {
    internal lateinit var prefs: SharedPreferences

    private val _sdkState = MutableStateFlow(AppStorysSdkState.Uninitialized)
    val sdkState = _sdkState.asStateFlow()

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

    private val _impressions = MutableStateFlow<Set<Impression>>(emptySet())
    val impressions = _impressions.asStateFlow()

    private val _attributes = MutableStateFlow<Map<String, Any>>(emptyMap())
    val attributes = _attributes.asStateFlow()

    private val _disabledCampaigns = MutableStateFlow<Set<String>>(emptySet())
    val disabledCampaigns = _disabledCampaigns.asStateFlow()

    private val _isCapturing = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val isCapturing = _isCapturing.asStateFlow()

    private val _isCapturingEnabled = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val isCapturingEnabled = _isCapturingEnabled.asStateFlow()

    /**
     * Stores the mapping of target IDs to their corresponding coordinates.
     */
    private val _constraints = MutableStateFlow<Map<String, AppStorysCoordinates>>(emptyMap())
    val constraints = _constraints.asStateFlow()

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

    fun setSdkState(state: AppStorysSdkState) {
        _sdkState.value = state
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

    fun setAttributes(attributes: Map<String, Any>) {
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

    fun addImpression(impression: Impression) {
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

    /**
     * Adds a constraint for a target using its `LayoutCoordinates`.
     *
     * @param id The unique identifier for the target.
     * @param coordinates The `LayoutCoordinates` of the target.
     */
    fun addConstraint(id: String, coordinates: LayoutCoordinates) {
        _constraints.update {
            buildMap {
                putAll(it)
                put(id, coordinates.toAppStorysCoordinates())
            }
        }
    }

    /**
     * Adds a constraint for a target using its `View` object.
     *
     * @param id The unique identifier for the target.
     * @param view The `View` object representing the target.
     */
    fun addViewConstraint(id: String, view: View) {
        _constraints.update {
            buildMap {
                putAll(it)
                put(id, view.toAppStorysCoordinates())
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


    /**
     * Converts `LayoutCoordinates` to `AppStorysCoordinates`.
     *
     * @return The converted `AppStorysCoordinates`.
     */
    private fun LayoutCoordinates.toAppStorysCoordinates(): AppStorysCoordinates {
        return AppStorysCoordinates(
            x = positionInRoot().x,
            y = positionInRoot().y,
            width = size.width,
            height = size.height,
            boundsInParent = { boundsInParent() },
            boundsInRoot = { boundsInRoot() },
            boundsInWindow = { boundsInWindow() }
        )
    }

    /**
     * Converts a `View` to `AppStorysCoordinates`.
     *
     * This method calculates position values similar to the `positionInRoot()` approach
     * used in Compose's LayoutCoordinates to ensure consistent positioning between
     * XML views and Compose UI elements.
     *
     * @return The converted `AppStorysCoordinates`.
     */
    private fun View.toAppStorysCoordinates(): AppStorysCoordinates {
        // Get location in window (similar to Compose's coordinates system)
        val locationInWindow = IntArray(2)
        getLocationInWindow(locationInWindow)

        // Get status bar height to adjust for proper positioning
        val rootRect = Rect()
        getWindowVisibleDisplayFrame(rootRect)
        val statusBarHeight = rootRect.top

        // Calculate position relative to root, adjusting for status bar
        val rootX = locationInWindow[0].toFloat()
        val rootY = (locationInWindow[1] - statusBarHeight).toFloat()

        return AppStorysCoordinates(
            // TODO: Find out why I need to subtract 55 from rootX and rootY
            x = rootX - 55,
            y = rootY - 55,
            width = width,
            height = height,
            boundsInParent = {
                androidx.compose.ui.geometry.Rect(
                    left = left.toFloat(),
                    top = top.toFloat(),
                    right = right.toFloat(),
                    bottom = bottom.toFloat()
                )
            },
            boundsInRoot = {
                androidx.compose.ui.geometry.Rect(
                    left = rootX,
                    top = rootY,
                    right = rootX + width,
                    bottom = rootY + height
                )
            },
            boundsInWindow = {
                val locationOnScreen = IntArray(2)
                getLocationOnScreen(locationOnScreen)
                androidx.compose.ui.geometry.Rect(
                    left = locationOnScreen[0].toFloat(),
                    top = locationOnScreen[1].toFloat(),
                    right = (locationOnScreen[0] + width).toFloat(),
                    bottom = (locationOnScreen[1] + height).toFloat()
                )
            }
        )
    }
}
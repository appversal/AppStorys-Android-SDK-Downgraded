package com.appversal.appstorys

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.util.Patterns
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.appversal.appstorys.api.ApiRepository
import com.appversal.appstorys.api.ApiResult
import com.appversal.appstorys.api.BannerDetails
import com.appversal.appstorys.api.BottomSheetDetails
import com.appversal.appstorys.api.CSATDetails
import com.appversal.appstorys.api.Campaign
import com.appversal.appstorys.api.CampaignVariant
import com.appversal.appstorys.api.CsatFeedbackPostRequest
import com.appversal.appstorys.api.FloaterDetails
import com.appversal.appstorys.api.MilestoneDetails
import com.appversal.appstorys.api.ModalDetails
import com.appversal.appstorys.api.PipDetails
import com.appversal.appstorys.api.ReconcileUserRequest
import com.appversal.appstorys.api.ReelStatusRequest
import com.appversal.appstorys.api.ReelsDetails
import com.appversal.appstorys.api.RetrofitClient
import com.appversal.appstorys.api.ScratchCardDetails
import com.appversal.appstorys.api.StoriesDetails
import com.appversal.appstorys.api.SurveyDetails
import com.appversal.appstorys.api.Tooltip
import com.appversal.appstorys.api.TooltipsDetails
import com.appversal.appstorys.api.UpdateUserPropertiesRequest
import com.appversal.appstorys.api.WidgetDetails
import com.appversal.appstorys.api.WidgetImage
import com.appversal.appstorys.api.safeApiCall
import com.appversal.appstorys.ui.AutoSlidingCarousel
import com.appversal.appstorys.ui.BottomSheetComponent
import com.appversal.appstorys.ui.CardScratch
import com.appversal.appstorys.ui.CarousalImage
import com.appversal.appstorys.ui.CsatDialog
import com.appversal.appstorys.ui.DoubleWidgets
import com.appversal.appstorys.ui.reels.FullScreenVideoScreen
import com.appversal.appstorys.ui.ImageCard
import com.appversal.appstorys.ui.MilestoneBanner
import com.appversal.appstorys.ui.MilestoneModal
import com.appversal.appstorys.ui.MilestoneWidgets
import com.appversal.appstorys.ui.OverlayContainer
import com.appversal.appstorys.ui.floater.OverlayFloater
import com.appversal.appstorys.ui.pipvideo.PipVideo
import com.appversal.appstorys.ui.modals.PopupModal
import com.appversal.appstorys.ui.reels.ReelsRow
import com.appversal.appstorys.ui.stories.StoryAppMain
import com.appversal.appstorys.ui.SurveyBottomSheet
import com.appversal.appstorys.ui.common_components.createCrossButtonConfig
import com.appversal.appstorys.ui.reels.getLikedReels
import com.appversal.appstorys.ui.getScratchedCampaigns
import com.appversal.appstorys.ui.common_components.createExpandButtonConfig
import com.appversal.appstorys.ui.common_components.createSoundToggleButtonConfig
import com.appversal.appstorys.ui.reels.saveLikedReels
import com.appversal.appstorys.ui.saveScratchedCampaigns
import com.appversal.appstorys.utils.AppStorysSdkState
import com.appversal.appstorys.utils.ViewTreeAnalyzer
import com.appversal.appstorys.utils.toJsonElementMap
import com.appversal.appstorys.utils.toMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.toString

object AppStorys {
    private lateinit var context: Application

    private lateinit var appId: String

    private lateinit var accountId: String

    private var userId: String = ""

    private var isAnonymousUser: Boolean = true

    private const val PREFS_USER_ID = "appstorys_user_id"
    private const val PREFS_IS_ANONYMOUS = "appstorys_is_anonymous"

    internal lateinit var navigateToScreen: (String) -> Unit

    private val apiService = RetrofitClient.apiService

    private val webSocketService = RetrofitClient.webSocketApiService

    internal lateinit var repository: ApiRepository

    private val campaigns = MutableStateFlow<List<Campaign>>(emptyList())

    private val disabledCampaigns = MutableStateFlow<List<String>>(emptyList())

    private val impressions = MutableStateFlow<List<String>>(emptyList())

    private val viewsCoordinates = MutableStateFlow<Map<String, LayoutCoordinates>>(emptyMap())

    val tooltipTargetView = MutableStateFlow<Tooltip?>(null)

    private val tooltipViewed = MutableStateFlow<List<String>>(emptyList())

    private val showcaseVisible = MutableStateFlow(false)
    private val selectedReelIndex = MutableStateFlow(0)

    private val reelFullScreenVisible = MutableStateFlow(false)

    private val scratchedCampaigns = MutableStateFlow<List<String>>(emptyList())

    private var accessToken = ""

    private var currentScreen = ""

    private var isScreenCaptureEnabled by mutableStateOf(false)

    private var showCsat by mutableStateOf(false)

    private var showModal by mutableStateOf(true)

    private var showBottomSheet by mutableStateOf(true)

    internal var sdkState = AppStorysSdkState.Uninitialized
        private set

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var campaignsJob: Job? = null

    private var trackedEventNames = mutableStateListOf<String>()

    private var widgetPositionList = listOf<String>()

    private val viewedTooltips = MutableStateFlow<Set<String>>(emptySet())

    private val campaignVariants = MutableStateFlow<List<CampaignVariant>>(emptyList())

    private var personalizationData: Map<String, String>? = null

    /**
     * Tells the SDK whether the sdk components are visible to the user,
     * this is very important for features like pip where the sdk needs to know
     * whether the user can see the pip or not to pause/resume the pip video
     */
    var isVisible by mutableStateOf(true)

    private val currentMilestoneIndex = MutableStateFlow(0)
    private var showMilestone by mutableStateOf(true)

    private fun generateAnonymousUserId(): String {
        val timestamp = System.currentTimeMillis()
        val deviceModel = Build.MODEL.replace(" ", "_").lowercase()
        return "${timestamp}_${deviceModel}"
    }

    /**
     * Gets the stored user ID from SharedPreferences or generates a new one
     */
    private fun getOrCreateUserId(): String {
        val prefs = context.getSharedPreferences("AppStory", Context.MODE_PRIVATE)

        // Check if we have a saved user ID
        val savedUserId = prefs.getString(PREFS_USER_ID, null)
        val savedIsAnonymous = prefs.getBoolean(PREFS_IS_ANONYMOUS, true)

        return if (!savedUserId.isNullOrEmpty()) {
            // Use saved user ID
            isAnonymousUser = savedIsAnonymous
            savedUserId
        } else {
            // Generate new anonymous user ID
            val anonymousId = generateAnonymousUserId()
            isAnonymousUser = true

            // Save to SharedPreferences
            prefs.edit().apply {
                putString(PREFS_USER_ID, anonymousId)
                putBoolean(PREFS_IS_ANONYMOUS, true)
                apply()
            }

            anonymousId
        }
    }

    /**
     * Saves user ID to SharedPreferences
     */
    private fun saveUserId(userId: String, isAnonymous: Boolean) {
        val prefs = context.getSharedPreferences("AppStory", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(PREFS_USER_ID, userId)
            putBoolean(PREFS_IS_ANONYMOUS, isAnonymous)
            apply()
        }
    }

    fun initialize(
        context: Application,
        appId: String,
        accountId: String,
        userId: String = "",
        navigateToScreen: (String) -> Unit
    ) {
        if (::context.isInitialized) {
            Log.w("AppStorys", "SDK is already initialized")
            return
        }

        this.context = context
        this.appId = appId
        this.accountId = accountId
        this.navigateToScreen = navigateToScreen

        if (userId.isNotEmpty()) {
            // User provided an ID
            this.userId = userId
            this.isAnonymousUser = false
            saveUserId(userId, false)
        } else {
            // Generate or retrieve anonymous user ID
            this.userId = getOrCreateUserId()
        }

        this.repository = ApiRepository(context, apiService, webSocketService) {
            currentScreen
        }

        if (sdkState == AppStorysSdkState.Initialized || sdkState == AppStorysSdkState.Initializing) {
            return
        }

        sdkState = AppStorysSdkState.Initializing

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    super.onResume(owner)
                    if (sdkState == AppStorysSdkState.Paused && currentScreen.isNotBlank()) {
                        sdkState = AppStorysSdkState.Initialized
                        getScreenCampaigns(currentScreen, emptyList())
                    }
                }

                override fun onStop(owner: LifecycleOwner) {
                    sdkState = AppStorysSdkState.Paused
                    campaigns.update { emptyList() }
//                    tooltipViewed.update { emptyList() }
                    showModal = true
                    showCsat = false
                    showBottomSheet = true
                    trackedEventNames.clear()
                    campaignsJob?.cancel()
                    campaignsJob = null
                }
            }
        )
        coroutineScope.launch {
            try {
                val accessToken = repository.getAccessToken(appId, accountId, this@AppStorys.userId)
                if (!accessToken.isNullOrBlank()) {
                    this@AppStorys.accessToken = accessToken
                    sdkState = AppStorysSdkState.Initialized
                    val savedScratchedCampaigns = getScratchedCampaigns(
                        context.getSharedPreferences("AppStory", Context.MODE_PRIVATE)
                    )
                    scratchedCampaigns.emit(savedScratchedCampaigns)
                    if (campaignsJob?.isActive != true) {
                        getScreenCampaigns("Home Screen", emptyList())
                    }
                }
            } catch (exception: Exception) {
                Log.e("AppStorys", exception.message ?: "Error Fetch Data")
                sdkState = AppStorysSdkState.Error
            }
            showCaseInformation()
        }
    }

    fun getScreenCampaigns(
        screenName: String,
        positionList: List<String> = emptyList()
    ) {
        campaignsJob?.cancel()
        campaignsJob = coroutineScope.launch {
                if (!checkIfInitialized()) {
                    return@launch
                }
                ensureActive()
                try {
                    if (currentScreen != screenName) {
                        disabledCampaigns.emit(emptyList())
                        impressions.emit(emptyList())
                        campaigns.emit(emptyList())
                        currentScreen = screenName

                        delay(100)
                    }

                    ensureActive()

                    widgetPositionList = positionList

                    ensureActive()

                    val (campaignsList, variants, personalizationResponse, isTestUser)= repository.getScreenCampaignsData(
                        accessToken = accessToken,
                        accountId = accountId,
                        screenName = currentScreen,
                        userId = userId
                    )

                    isScreenCaptureEnabled = isTestUser ?: false

                    personalizationData = personalizationResponse

                    ensureActive()

                    campaignsList?.let { campaigns.emit(it) }
                    campaignVariants.emit(variants ?: emptyList())
                    Log.e("AppStorys", "Campaign: ${campaigns.value}")
                } catch (exception: Exception) {
                    Log.e("AppStorys", "Error getting campaigns for $screenName", exception)
                }
        }
    }

    /**
     * Get the personalization data for the current user.
     *
     * This data is fetched from the backend when campaigns are loaded and includes
     * user properties that can be used to personalize campaign content.
     * This data is automatically used by CommonText components to replace placeholders
     * like {{propertyName | fallbackValue}} with actual user property values.
     *
     * Note: This returns data from the server response. To SET user properties,
     * use setUserProperties() method.
     *
     * @return Map of personalization key-value pairs
     */
    fun getPersonalizationData(): Map<String, String> {
        return personalizationData ?: emptyMap()
    }

    fun trackEvents(
        campaign_id: String? = null,
        event: String,
        metadata: Map<String, Any>? = null
    ) {
        coroutineScope.launch {
            if (accessToken.isNotEmpty()) {
                if (event != "viewed" && event != "clicked" && event != "csat captured" && event != "survey captured" && event != "shared") {
                    trackedEventNames.add(event)
                }
                try {
                    val variantId = campaign_id?.let { campId ->
                        campaignVariants.value.find { it.id == campId }?.v_id
                    }

                    val updatedMetadata = if (variantId != null) {
                        (metadata ?: emptyMap()) + mapOf("variant_id" to variantId)
                    } else {
                        metadata ?: emptyMap()
                    }

                    val deviceInfo = getDeviceInfo(context)

                    val mergedMetadata = if (event != "viewed" && event != "clicked" && event != "csat captured" && event != "survey captured" && event != "shared") {
                        updatedMetadata + deviceInfo
                    } else {
                        updatedMetadata
                    }
                    val requestBody = JSONObject().apply {
                        put("user_id", userId)
                        campaign_id?.let { put("campaign_id", it) }
                        put("event", event)
                        if (mergedMetadata.isNotEmpty()) {
                            put("metadata", JSONObject(mergedMetadata))
                        }
                    }
                    val client = OkHttpClient()
                    val request = Request.Builder()
                        .url("https://tracking.appstorys.com/capture-event")
                        .post(
                            requestBody.toString()
                                .toRequestBody("application/json".toMediaTypeOrNull())
                        )
                        .addHeader("Authorization", "Bearer $accessToken")
                        .build()

                    val response = client.newCall(request).execute()

                    Log.i("Event Captured", response.toString())
                    Log.i("Event Captured", requestBody.toString())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun viaAppStorys(
        event: String,
    ) {
        coroutineScope.launch {
            trackedEventNames.add(event)
        }
    }

    fun setUserProperties(attributes: Map<String, Any>) {
        coroutineScope.launch {
            if (userId.isBlank() || !checkIfInitialized()) {
                Log.e("AppStorys", "Cannot set user properties: SDK not initialized or user ID not available")
                return@launch
            }

            Log.d("AppStorys", "Setting user properties: ${attributes.keys.joinToString(", ")}")

            val result = safeApiCall {
                webSocketService.updateUserProperties(
                    token = "Bearer $accessToken",
                    request = UpdateUserPropertiesRequest(
                        user_id = userId,
                        attributes = attributes.toJsonElementMap()
                    )
                )
            }
            when (result) {
                is ApiResult.Success -> {
                    Log.i("AppStorys", "User properties updated successfully: ${attributes.keys.joinToString(", ")}")
                }

                is ApiResult.Error -> {
                    Log.e("AppStorys", "Error updating user properties: ${result.message}")
                }
            }
        }
    }

    fun setUserId(newUserId: String) {
        if (newUserId.isEmpty()) {
            Log.w("AppStorys", "Cannot set empty user ID")
            return
        }

        coroutineScope.launch {
            if (!::context.isInitialized) {
                Log.e("AppStorys", "SDK not initialized. Call initialize() first")
                return@launch
            }

            val previousUserId = userId
            val wasAnonymous = isAnonymousUser

            // If already using this identified user ID, no need to reconcile
            if (!wasAnonymous && previousUserId == newUserId) {
                Log.d("AppStorys", "User ID already set to: $newUserId")
                return@launch
            }

            try {
                // Only call reconcile endpoint if we're transitioning from anonymous to identified
                if (wasAnonymous) {
                    Log.d("AppStorys", "Reconciling anonymous user $previousUserId with identified user $newUserId")

                    val result = webSocketService.reconcileAnonymousUser(

                        token = "Bearer $accessToken",
                        request = ReconcileUserRequest(
                            anonymous_user_id = previousUserId,
                            identified_user_id = newUserId
                        )
                    )

                    when (result) {
                        else -> {
                            Log.i(
                                "AppStorys",
                                "Successfully reconciled anonymous user with identified user"
                            )
                        }
                    }
                }

                // Update user ID
                userId = newUserId
                isAnonymousUser = false
                saveUserId(newUserId, false)

                Log.i("AppStorys", "User ID updated to: $newUserId")

            } catch (e: Exception) {
                Log.e("AppStorys", "Error setting user ID: ${e.message}", e)
            }
        }
    }

    @Composable
    fun overlayElements(
        bottomPadding: Dp = 0.dp,
        topPadding: Dp = 0.dp,
        activity: Activity? = null,
        bannerBottomPadding: Dp = 0.dp,
        floaterBottomPadding: Dp = 0.dp,
        pipTopPadding: Dp = 0.dp,
        pipBottomPadding: Dp = 0.dp,
        csatBottomPadding: Dp = 0.dp,
    ) {
        OverlayContainer.Content(
            bottomPadding = bottomPadding,
            topPadding = topPadding,
            activity = activity,
            bannerBottomPadding = bannerBottomPadding,
            floaterBottomPadding = floaterBottomPadding,
            pipTopPadding = pipTopPadding,
            pipBottomPadding = pipBottomPadding,
            csatBottomPadding = csatBottomPadding
        )
    }

    suspend fun analyzeViewRoot(
        root: View, screenName: String, activity: Activity
    ) = runCatching {
        val TAG = "AnalyzeViewRoot"
        Log.i(TAG, "Calling ViewTreeAnalyzer.analyzeViewRoot()")

        ViewTreeAnalyzer.analyzeViewRoot(
            root = root,
            screenName = screenName,
            user_id = userId,
            accessToken = accessToken,
            activity = activity,
            context = context
        ).also {
            Log.i(TAG, "ViewTreeAnalyzer.analyzeViewRoot() completed successfully")
        }
    }.onFailure { error ->
        Log.i("AnalyzeViewRoot", "Error analyzing view root", error)
    }.onSuccess {
        Log.i("AnalyzeViewRoot", "analyzeViewRoot() finished with success result: $it")
    }

    @Composable
    fun CSAT(
        bottomPadding: Dp = 0.dp
    ) {
        if (!showCsat) {
            val campaignsData = campaigns.collectAsStateWithLifecycle()

            val campaign = campaignsData.value.firstOrNull { it.campaignType == "CSAT" }
            val csatDetails = when (val details = campaign?.details) {
                is CSATDetails -> details
                else -> null
            }

            val triggerEventValue = when (val event = campaign?.triggerEvent) {
                "viaAppStorys" -> "viaAppStorys${campaign?.id}"
                null, "" -> null
                else -> event
            }

            val shouldShowCSAT = remember(triggerEventValue, trackedEventNames.size) {
                triggerEventValue.isNullOrEmpty() || trackedEventNames.contains(triggerEventValue)
            }

            if (csatDetails != null && shouldShowCSAT) {
                val style = csatDetails.styling
                var isVisibleState by remember { mutableStateOf(false) }
                val delaySeconds = remember(style) {
                    style?.appearance?.displayDelay?.let { element ->
                        if (element is kotlinx.serialization.json.JsonPrimitive) {
                            element.content.toIntOrNull() ?: 0
                        } else {
                            0
                        }
                    } ?: 0
                }

                LaunchedEffect(Unit) {
                    campaign?.id?.let {
                        trackEvents(it, "viewed")
                    }
                    delay(delaySeconds * 1000L)
                    isVisibleState = true
                }

                val bottomPaddingValue =
                    (style?.appearance?.margin?.bottom?.dp?.plus(bottomPadding)) ?: bottomPadding

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            bottom = bottomPaddingValue
                        ),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    AnimatedVisibility(
                        modifier = Modifier,
                        visible = isVisibleState,
                        enter = slideInVertically() { it },
                        exit = slideOutVertically { it }
                    ) {
                        CsatDialog(
                            onDismiss = {
                                isVisibleState = false
                                coroutineScope.launch {
                                    delay(500L)
                                    showCsat = true
                                }
                            },
                            onSubmitFeedback = { feedback ->
                                coroutineScope.launch {
                                    repository.captureCSATResponse(
                                        accessToken,
                                        CsatFeedbackPostRequest(
                                            user_id = userId,
                                            csat = csatDetails.id,
                                            rating = feedback.rating,
                                            additional_comments = feedback.additionalComments,
                                            feedback_option = feedback.feedbackOption
                                        )
                                    )
                                    trackEvents(
                                        campaign_id = campaign?.id,
                                        event = "csat captured",
                                        metadata = mapOf(
                                            "starCount" to feedback.rating,
                                            "selectedOption" to (feedback.feedbackOption
                                                ?: "") as Any,
                                            "additionalComments" to feedback.additionalComments
                                        )
                                    )
                                }
                            },
                            csatDetails = csatDetails
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun Floater(
        modifier: Modifier = Modifier,
        bottomPadding: Dp = 0.dp
    ) {
        val campaignsData = campaigns.collectAsStateWithLifecycle()

        val campaign =
            campaignsData.value.firstOrNull { it.campaignType == "FLT" && it.details is FloaterDetails }

        val floaterDetails = when (val details = campaign?.details) {
            is FloaterDetails -> details
            else -> null
        }

        val triggerEventValue = when (val event = campaign?.triggerEvent) {
            "viaAppStorys" -> "viaAppStorys${campaign?.id}"
            null, "" -> null
            else -> event
        }

        val shouldShowFloater = remember(triggerEventValue, trackedEventNames.size) {
            triggerEventValue.isNullOrEmpty() || trackedEventNames.contains(triggerEventValue)
        }

        if (floaterDetails != null && (!floaterDetails.image.isNullOrEmpty() || !floaterDetails.lottie_data.isNullOrEmpty()) && shouldShowFloater) {
            LaunchedEffect(Unit) {
                campaign?.id?.let {
                    trackEvents(it, "viewed")
                }
            }

            val styling = floaterDetails.styling

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        bottom = styling?.floaterBottomPadding?.toFloatOrNull()?.dp
                            ?: bottomPadding,
                        start = styling?.floaterLeftPadding?.toFloatOrNull()?.dp ?: 0.dp,
                        end = styling?.floaterRightPadding?.toFloatOrNull()?.dp ?: 0.dp,
                    ),
                content = {
                    OverlayFloater(
                        modifier = modifier.align(
                            when (floaterDetails.position) {
                                "right" -> Alignment.BottomEnd
                                "left" -> Alignment.BottomStart
                                else -> Alignment.BottomStart
                            }
                        ),
                        onClick = {
                            if (campaign?.id != null && !floaterDetails.link.isNullOrEmpty()) {
                                clickEvent(link = floaterDetails.link, campaignId = campaign.id)
                                trackEvents(campaign.id, "clicked")
                            }
                        },
                        image = floaterDetails.image ?: "",
                        lottieUrl = floaterDetails.lottie_data ?: "",
                        height = floaterDetails.height?.dp ?: 60.dp,
                        width = floaterDetails.width?.dp ?: 60.dp,
                        borderRadiusValues = RoundedCornerShape(
                            topStart = (styling?.topLeftRadius?.toFloatOrNull() ?: 0f).dp,
                            topEnd = (styling?.topRightRadius?.toFloatOrNull() ?: 0f).dp,
                            bottomStart = (styling?.bottomLeftRadius?.toFloatOrNull() ?: 0f).dp,
                            bottomEnd = (styling?.bottomRightRadius?.toFloatOrNull() ?: 0f).dp
                        )
                    )
                }
            )
        }
    }


    @Composable
    fun Pip(
        modifier: Modifier = Modifier,
        bottomPadding: Dp = 0.dp,
        topPadding: Dp = 0.dp,
    ) {
        val campaignsData = campaigns.collectAsStateWithLifecycle()

        val campaign =
            campaignsData.value.firstOrNull { it.campaignType == "PIP" && it.details is PipDetails }

        val pipDetails = when (val details = campaign?.details) {
            is PipDetails -> details
            else -> null
        }

        val triggerEventValue = when (val event = campaign?.triggerEvent) {
            "viaAppStorys" -> "viaAppStorys${campaign?.id}"
            null, "" -> null
            else -> event
        }

        val shouldShowPip = remember(triggerEventValue, trackedEventNames.size) {
            triggerEventValue.isNullOrEmpty() || trackedEventNames.contains(triggerEventValue)
        }

        if (pipDetails != null && !pipDetails.small_video.isNullOrEmpty() && shouldShowPip) {
            key(
                campaign?.id, campaign?.triggerEvent
            ) {

                var showPip by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    campaign?.id?.let {
                        trackEvents(it, "viewed")
                    }
                }

                Box(modifier = modifier?.fillMaxWidth() ?: Modifier.fillMaxWidth()) {
                    if (showPip) {
                        // Debug logging for expand button images
                        Log.d("AppStorys", "PIP Details - maximiseImage: ${pipDetails.maximiseImage}")
                        Log.d("AppStorys", "PIP Details - minimiseImage: ${pipDetails.minimiseImage}")

                        // Use appearance dimensions if available, otherwise fall back to root-level dimensions
                        val pipHeight = pipDetails.styling?.appearance?.pipHeight?.toIntOrNull()?.dp
                            ?: pipDetails.height?.dp ?: 200.dp
                        val pipWidth = pipDetails.styling?.appearance?.pipWidth?.toIntOrNull()?.dp
                            ?: pipDetails.width?.dp ?: 113.dp

                        PipVideo(
                            videoUri = pipDetails.small_video,
                            fullScreenVideoUri = if (!pipDetails.large_video.isNullOrEmpty()) {
                                pipDetails.large_video
                            } else {
                                null
                            },
                            onClose = {
                                showPip = false
                                triggerEventValue?.let { trackedEventNames.remove(it) }
                            },
                            height = pipHeight,
                            width = pipWidth,
                            button_text = pipDetails.button_text.toString(),
                            link = pipDetails.link.toString(),
                            position = pipDetails.position.toString(),
                            bottomPadding = bottomPadding,
                            topPadding = topPadding,
                            isMovable = pipDetails.styling?.isMovable ?: false,
                            pipStyling = pipDetails.styling,

                            crossButtonConfig = run {
                                // Support new backend format with "color" (singular) instead of "colors"
                                val pipCrossButton = pipDetails.styling?.crossButton
                                val pipCrossColors = pipCrossButton?.color ?: pipCrossButton?.colors
                                val pipCrossImageUrl = pipCrossButton?.image
                                val pipCrossMargin = pipCrossButton?.margin

                                createCrossButtonConfig(
                                    fillColorString = pipCrossColors?.fill,
                                    crossColorString = pipCrossColors?.cross,
                                    strokeColorString = pipCrossColors?.stroke,
                                    marginTop = pipCrossButton?.margin?.top,
                                    marginEnd = pipCrossButton?.margin?.right,
                                    size = pipCrossButton?.size,
                                    imageUrl = pipCrossImageUrl
                                )
                            },

                            maximiseButtonConfig = run {
                                val expandControls = pipDetails.styling?.expandControls
                                val maximise = expandControls?.maximise
                                // Check color (singular) first, then fall back to colors (plural) for legacy support
                                val maximiseColors = maximise?.color ?: maximise?.colors
                                val maximiseMargin = maximise?.margin

                                createExpandButtonConfig(
                                    fillColorString = maximiseColors?.fill,
                                    iconColorString = maximiseColors?.cross,
                                    strokeColorString = maximiseColors?.stroke,
                                    marginTop = maximiseMargin?.top,
                                    marginEnd = maximiseMargin?.right,
                                    marginBottom = maximiseMargin?.bottom,
                                    marginStart = maximiseMargin?.left,
                                    size = maximise?.size,
                                    imageUrl = maximise?.image ?: pipDetails.maximiseImage
                                )
                            },

                            minimiseButtonConfig = run {
                                val expandControls = pipDetails.styling?.expandControls
                                val minimise = expandControls?.minimise
                                // Check color (singular) first, then fall back to colors (plural) for legacy support
                                val minimiseColors = minimise?.color ?: minimise?.colors
                                val minimiseMargin = minimise?.margin

                                createExpandButtonConfig(
                                    fillColorString = minimiseColors?.fill,
                                    iconColorString = minimiseColors?.cross,
                                    strokeColorString = minimiseColors?.stroke,
                                    marginTop = minimiseMargin?.top,
                                    marginEnd = minimiseMargin?.right,
                                    marginBottom = minimiseMargin?.bottom,
                                    marginStart = minimiseMargin?.left,
                                    size = minimise?.size,
                                    imageUrl = minimise?.image ?: pipDetails.minimiseImage
                                )
                            },

                            muteButtonConfig = run {
                                val soundToggle = pipDetails.styling?.soundToggle
                                val mute = soundToggle?.mute
                                // Check color (singular) first, then fall back to colors (plural) for legacy support
                                val muteColors = mute?.color ?: mute?.colors
                                val muteMargin = mute?.margin

                                createSoundToggleButtonConfig(
                                    fillColorString = muteColors?.fill,
                                    iconColorString = muteColors?.cross,
                                    strokeColorString = muteColors?.stroke,
                                    marginTop = muteMargin?.top,
                                    marginEnd = muteMargin?.right,
                                    marginBottom = muteMargin?.bottom,
                                    marginStart = muteMargin?.left,
                                    size = mute?.size,
                                    imageUrl = mute?.image ?: pipDetails.muteImage
                                )
                            },

                            unmuteButtonConfig = run {
                                val soundToggle = pipDetails.styling?.soundToggle
                                val unmute = soundToggle?.unmute
                                // Check color (singular) first, then fall back to colors (plural) for legacy support
                                val unmuteColors = unmute?.color ?: unmute?.colors
                                val unmuteMargin = unmute?.margin

                                createSoundToggleButtonConfig(
                                    fillColorString = unmuteColors?.fill,
                                    iconColorString = unmuteColors?.cross,
                                    strokeColorString = unmuteColors?.stroke,
                                    marginTop = unmuteMargin?.top,
                                    marginEnd = unmuteMargin?.right,
                                    marginBottom = unmuteMargin?.bottom,
                                    marginStart = unmuteMargin?.left,
                                    size = unmute?.size,
                                    imageUrl = unmute?.image ?: pipDetails.unmuteImage
                                )
                            },


                            onButtonClick = {
                                campaign?.id?.let { campaignId ->
                                    trackEvents(campaignId, "clicked")
                                }
                            },
                            onExpandClick = {
                                campaign?.id?.let { campaignId ->
                                    trackEvents(campaignId, "viewed")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun showCaseInformation() {
        coroutineScope.launch {
            combine(
                campaigns,
                viewsCoordinates
            ) { campaignList, coordinates -> campaignList to coordinates }.collectLatest { (campaignList, coordinates) ->
                val campaign =
                    campaignList.firstOrNull { it.campaignType == "TTP" && it.details is TooltipsDetails }
                val tooltipsDetails = campaign?.details as? TooltipsDetails

                val shouldShowTooltip = campaign?.triggerEvent.isNullOrEmpty() ||
                        trackedEventNames.contains(campaign?.triggerEvent)
                if (tooltipsDetails != null) {
                    for (tooltip in tooltipsDetails.tooltips?.sortedBy { it.order }
                        ?: emptyList()) {
                        if (tooltip.target != null && !tooltipViewed.value.contains(tooltip.target)) {
                            while (tooltipTargetView.value != null) {
                                delay(500L)
                            }
                            tooltipTargetView.emit(tooltip)
                            showcaseVisible.emit(true)
                            tooltipViewed.update {
                                it + tooltip.target
                            }
                        }
                    }
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    @Composable
    fun Stories() {
        val campaignsData = campaigns.collectAsStateWithLifecycle()
        val campaign = campaignsData.value.firstOrNull { it.campaignType == "STR" }
        val storiesDetails = campaign?.details as? StoriesDetails

        val triggerEventValue = when (val event = campaign?.triggerEvent) {
            "viaAppStorys" -> "viaAppStorys${campaign?.id}"
            null, "" -> null
            else -> event
        }

        val shouldShowStories = remember(triggerEventValue, trackedEventNames.size) {
            triggerEventValue.isNullOrEmpty() || trackedEventNames.contains(triggerEventValue)
        }

        if (storiesDetails != null && !storiesDetails.groups.isNullOrEmpty() && shouldShowStories) {
            StoryAppMain(
                apiStoriesDetails = storiesDetails,
                sendEvent = {
                    coroutineScope.launch {
                        trackEvents(campaign.id, "viewed", mapOf("story_slide" to it.first.id!!))
                    }
                },
                sendClickEvent = {
                    trackEvents(campaign.id, it.second, mapOf("story_slide" to it.first.id!!))
                },
                campaignId = campaign.id ?: ""
            )
        }
    }

    @Composable
    fun Reels(modifier: Modifier = Modifier) {
        val campaignsData = campaigns.collectAsStateWithLifecycle()
        val campaign =
            campaignsData.value.firstOrNull { it.campaignType == "REL" && it.details is ReelsDetails }
        val reelsDetails = campaign?.details as? ReelsDetails
        val selectedReelIndex by selectedReelIndex.collectAsStateWithLifecycle()
        val visibility by reelFullScreenVisible.collectAsStateWithLifecycle()

        val triggerEventValue = when (val event = campaign?.triggerEvent) {
            "viaAppStorys" -> "viaAppStorys${campaign?.id}"
            null, "" -> null
            else -> event
        }

        val shouldShowReels = remember(triggerEventValue, trackedEventNames.size) {
            triggerEventValue.isNullOrEmpty() || trackedEventNames.contains(triggerEventValue)
        }

        if (reelsDetails?.reels != null && reelsDetails.reels.isNotEmpty() && shouldShowReels) {
            Box(modifier = Modifier.fillMaxSize()) {

                ReelsRow(
                    modifier = modifier,
                    reels = reelsDetails.reels,
                    onReelClick = { index ->
                        coroutineScope.launch {
                            this@AppStorys.selectedReelIndex.emit(index)
                            reelFullScreenVisible.emit(true)
                        }
                    },
                    height = reelsDetails.styling?.thumbnailHeight?.toIntOrNull()?.dp ?: 180.dp,
                    width = reelsDetails.styling?.thumbnailWidth?.toIntOrNull()?.dp ?: 120.dp,
                    cornerRadius = reelsDetails.styling?.cornerRadius?.toIntOrNull()?.dp ?: 12.dp
                )

                if (visibility) {
                    ReelFullScreen(
                        campaignId = campaign.id,
                        reelsDetails = reelsDetails,
                        selectedReelIndex = selectedReelIndex
                    ) {
                        coroutineScope.launch {
                            this@AppStorys.selectedReelIndex.emit(0)
                            reelFullScreenVisible.emit(false)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ReelFullScreen(
        campaignId: String?,
        reelsDetails: ReelsDetails,
        selectedReelIndex: Int,
        onDismiss: () -> Unit
    ) {
        if (!reelsDetails.reels.isNullOrEmpty()) {

            var likedReels by remember {
                mutableStateOf(
                    getLikedReels(
                        context.getSharedPreferences(
                            "AppStory",
                            Context.MODE_PRIVATE
                        )
                    )
                )
            }

            Dialog(
                onDismissRequest = onDismiss,
                properties = DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false
                )
            ) {

                BackHandler {
                    coroutineScope.launch {
                        this@AppStorys.selectedReelIndex.emit(0)
                        reelFullScreenVisible.emit(false)
                    }
                }

                FullScreenVideoScreen(
                    reelsDetails = reelsDetails,
                    reels = reelsDetails.reels,
                    likedReels = likedReels,
                    startIndex = selectedReelIndex,
                    sendLikesStatus = {
                        coroutineScope.launch {
                            if (it.second == "like") {
                                val list = ArrayList(likedReels)
                                list.add(it.first.id)
                                likedReels = list.distinct()
                                saveLikedReels(
                                    idList = list.distinct(),
                                    sharedPreferences = context.getSharedPreferences(
                                        "AppStory",
                                        Context.MODE_PRIVATE
                                    )
                                )
                            } else {
                                val list = ArrayList(likedReels)
                                list.remove(it.first.id)
                                likedReels = list.distinct()
                                saveLikedReels(
                                    idList = list.distinct(),
                                    sharedPreferences = context.getSharedPreferences(
                                        "AppStory",
                                        Context.MODE_PRIVATE
                                    )
                                )
                            }

                            repository.sendReelLikeStatus(
                                accessToken = accessToken,
                                actions = ReelStatusRequest(
                                    user_id = userId,
                                    action = it.second,
                                    reel = it.first.id
                                )
                            )
                        }
                    },
                    sendEvents = {
                        if (it.second == "IMP") {
                            if (!impressions.value.contains(it.first.id)) {
                                coroutineScope.launch {
                                    val impressions = ArrayList(impressions.value)
                                    impressions.add(it.first.id)
                                    this@AppStorys.impressions.emit(impressions)
                                    trackEvents(
                                        campaignId,
                                        "viewed",
                                        mapOf("reel_id" to it.first.id!!)
                                    )
                                }
                            }
                        } else {
                            coroutineScope.launch {
                                trackEvents(
                                    campaignId,
                                    "clicked",
                                    mapOf("reel_id" to it.first.id!!)
                                )
                            }
                        }

                    },
                    onBack = {
                        coroutineScope.launch {
                            this@AppStorys.selectedReelIndex.emit(0)
                            reelFullScreenVisible.emit(false)
                        }
                    }
                )
            }
        }
    }

    @Composable
    fun getBannerHeight(): Dp {
        val campaignsData = campaigns.collectAsStateWithLifecycle()
        val defaultHeight = 100.dp

        val campaign =
            campaignsData.value.firstOrNull { it.campaignType == "BAN" && it.details is BannerDetails }
        val bannerDetails = campaign?.details as? BannerDetails

        return bannerDetails?.height?.dp ?: defaultHeight
    }

    @Composable
    fun getUserId(): String {
        return userId
    }


    @Composable
    fun PinnedBanner(
        modifier: Modifier = Modifier,
        placeholder: Drawable? = null,
        placeholderContent: (@Composable () -> Unit)? = null,
        bottomPadding: Dp = 0.dp,
    ) {
        val campaignsData = campaigns.collectAsStateWithLifecycle()
        val disabledCampaigns = disabledCampaigns.collectAsStateWithLifecycle()

        val configuration = LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp.dp

        val campaign = campaignsData.value.firstOrNull {
            it.campaignType == "BAN" && it.details is BannerDetails
        }

        val triggerEventValue = when (val event = campaign?.triggerEvent) {
            "viaAppStorys" -> "viaAppStorys${campaign?.id}"
            null, "" -> null
            else -> event
        }

        val shouldShowBanner = remember(triggerEventValue, trackedEventNames.size) {
            triggerEventValue.isNullOrEmpty() || trackedEventNames.contains(triggerEventValue)
        }

        val bannerDetails = campaign?.details as? BannerDetails

        if (bannerDetails != null && !disabledCampaigns.value.contains(campaign.id) && shouldShowBanner) {
            val style = bannerDetails.styling
            val bannerUrl = bannerDetails.image

            val aspectRatio: Float? = remember(bannerDetails) {
                val w = bannerDetails.width
                val h = bannerDetails.height
                if (w != null && h != null && w > 0 && h > 0) {
                    h.toFloat() / w.toFloat()
                } else null
            }

            val forcedHeight: Dp? = remember(bannerDetails) {
                if (bannerDetails.width == null && bannerDetails.height != null) {
                    bannerDetails.height.dp   // 👈 explicit stretch intent
                } else null
            }

//
//            val calculatedHeight =
//                if (bannerDetails.width != null && bannerDetails.height != null) {
//                    val aspectRatio = bannerDetails.height.toFloat() / bannerDetails.width.toFloat()
//
//                    val marginLeft = style?.marginLeft?.dp ?: 0.dp
//                    val marginRight = style?.marginRight?.dp ?: 0.dp
//
//                    val actualWidth = screenWidth - marginLeft - marginRight
//
//                    (actualWidth.value * aspectRatio).dp
//                } else {
//                    bannerDetails.height?.dp
//                }

            LaunchedEffect(Unit) {
                campaign.id?.let {
                    trackEvents(it, "viewed")
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = bottomPadding)
            ) {
                com.appversal.appstorys.ui.PinnedBanner(
                    modifier = modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    imageUrl = bannerUrl ?: "",
                    lottieUrl = bannerDetails.lottie_data,
                    //width = bannerDetails.width?.dp ?: screenWidth,
                    exitIcon = (style?.crossButton?.enabled ?: style?.enableCloseButton) != false,
                    exitUnit = {
                        val ids: ArrayList<String> = ArrayList(disabledCampaigns.value)
                        campaign.id?.let {
                            ids.add(it)
                            coroutineScope.launch {
                                this@AppStorys.disabledCampaigns.emit(ids.toList())
                            }
                        }
                    },
                    shape = RoundedCornerShape(
                        topStart = style?.topLeftRadius?.toIntOrNull()?.dp ?: 0.dp,
                        topEnd = style?.topRightRadius?.toIntOrNull()?.dp ?: 0.dp,
                        bottomEnd = style?.bottomRightRadius?.toIntOrNull()?.dp ?: 0.dp,
                        bottomStart = style?.bottomLeftRadius?.toIntOrNull()?.dp ?: 0.dp
                    ),
                    bottomMargin = style?.marginBottom?.dp ?: 0.dp,
                    leftMargin = style?.marginLeft?.dp ?: 0.dp,
                    rightMargin = style?.marginRight?.dp ?: 0.dp,
                    contentScale = ContentScale.FillWidth,
                    //height = null,
                    aspectRatio = aspectRatio,
                    forcedHeight = forcedHeight,
                    placeHolder = placeholder,
                    placeholderContent = placeholderContent,
                    crossButtonConfig = run {
                        // Support new backend format with "color" (singular) instead of "colors"
                        val crossColors = style?.crossButton?.color ?: style?.crossButton?.colors
                        val crossImageUrl = style?.crossButton?.image ?: bannerDetails.crossButtonImage
                        createCrossButtonConfig(
                            fillColorString = crossColors?.fill,
                            crossColorString = crossColors?.cross,
                            strokeColorString = crossColors?.stroke,
                            marginTop = style?.crossButton?.margin?.top,
                            marginEnd = style?.crossButton?.margin?.right,
                            size = style?.crossButton?.size,
                            imageUrl = crossImageUrl
                        )
                    },
                    onClick = {
                        if(bannerDetails.link.toString().trim().removeSurrounding("\"").isNotEmpty()){
                            campaign.id?.let {
                                clickEvent(link = bannerDetails.link.toString().trim().removeSurrounding("\""), campaignId = it)
                                trackEvents(it, "clicked")
                            }
                        }
                    }
                )
            }
        }
    }

    @Composable
    fun Widget(
        modifier: Modifier = Modifier,
        placeholder: Drawable? = null,
        position: String? = null
    ) {
        val campaignsData = campaigns.collectAsStateWithLifecycle()
        val campaign =
            campaignsData.value.filter { it.campaignType == "WID" && it.details is WidgetDetails }
                .firstOrNull {
                    if (position == null) {
                        it.position == null
                    } else {
                        it.position == position
                    }
                }
        val widgetDetails = campaign?.details as? WidgetDetails

        val triggerEventValue = when (val event = campaign?.triggerEvent) {
            "viaAppStorys" -> "viaAppStorys${campaign?.id}"
            null, "" -> null
            else -> event
        }

        val shouldShowWidget = remember(triggerEventValue, trackedEventNames.size) {
            triggerEventValue.isNullOrEmpty() || trackedEventNames.contains(triggerEventValue)
        }

        if (widgetDetails != null && shouldShowWidget) {

            if (widgetDetails.type == "full") {

                FullWidget(
                    modifier = modifier,
                    staticWidth = LocalConfiguration.current.screenWidthDp.dp,
                    placeHolder = placeholder,
                    contentScale = ContentScale.FillWidth,
                    position = position,
                )

            } else if (widgetDetails.type == "half") {
                DoubleWidget(
                    modifier = modifier,
                    staticWidth = LocalConfiguration.current.screenWidthDp.dp,
                    position = position,
                    placeHolder = placeholder,
                )
            }
        }
    }


    @Composable
    private fun FullWidget(
        modifier: Modifier = Modifier,
        contentScale: ContentScale = ContentScale.FillWidth,
        staticWidth: Dp? = null,
        placeHolder: Drawable?,
        placeholderContent: (@Composable () -> Unit)? = null,
        position: String?
    ) {
        val campaignsData = campaigns.collectAsStateWithLifecycle()
        val disabledCampaigns = disabledCampaigns.collectAsStateWithLifecycle()
        val campaign = campaignsData.value
            .filter { it.campaignType == "WID" && it.details is WidgetDetails && it.position == position }
            .firstOrNull { (it.details as WidgetDetails).type == "full" }

        val widgetDetails = (campaign?.details as? WidgetDetails)

        var isVisible by remember { mutableStateOf(false) }
        val configuration = LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp.dp


        if (widgetDetails?.widgetImages != null && widgetDetails.widgetImages.isNotEmpty() && campaign.id != null && !disabledCampaigns.value.contains(
                campaign.id
            ) && widgetDetails.type == "full"
        ) {
            val sortedWidgetImages = widgetDetails.widgetImages.sortedBy { it.order }
            val pagerState = rememberPagerState(pageCount = {
                sortedWidgetImages.count()
            })
            val widthInDp: Dp? = widgetDetails.width?.dp

            val calculatedHeight =
                if (widgetDetails.width != null && widgetDetails.height != null) {
                    val aspectRatio = widgetDetails.height.toFloat() / widgetDetails.width.toFloat()

                    val marginLeft = (widgetDetails.styling?.leftMargin ?: 0).dp
                    val marginRight = (widgetDetails.styling?.rightMargin ?: 0).dp

                    val actualWidth = (staticWidth ?: screenWidth) - marginLeft - marginRight
                    (actualWidth.value.minus( 0
//                        32
                        // for the new widget
//                            +26
                    ) * aspectRatio).dp
                } else {
                    widgetDetails.height?.dp
                }

            LaunchedEffect(pagerState.currentPage, isVisible) {
                if (isVisible) {
                    campaign?.id?.let {
                        val currentWidgetId = sortedWidgetImages[pagerState.currentPage].id

                        if (currentWidgetId != null && !impressions.value.contains(currentWidgetId)) {
                            val impressions = ArrayList(impressions.value)
                            impressions.add(currentWidgetId)
                            this@AppStorys.impressions.emit(impressions)
                            trackEvents(
                                it,
                                "viewed",
                                mapOf("widget_image" to currentWidgetId)
                            )

                        }
                    }
                }
            }

            AutoSlidingCarousel(
                modifier = modifier
                    .padding(
                        top = (widgetDetails.styling?.topMargin ?: 0).dp,
                        bottom = (widgetDetails.styling?.bottomMargin ?: 0).dp,
                        start = (widgetDetails.styling?.leftMargin ?: 0).dp,
                        end = (widgetDetails.styling?.rightMargin ?: 0).dp,
                    )
                    .onGloballyPositioned { layoutCoordinates ->
                        val visibilityRect = layoutCoordinates.boundsInWindow()
                        val parentHeight =
                            layoutCoordinates.parentLayoutCoordinates?.size?.height ?: 0
                        val widgetHeight = layoutCoordinates.size.height
                        val isAtLeastHalfVisible = visibilityRect.top < parentHeight &&
                                visibilityRect.bottom > 0 &&
                                (visibilityRect.height >= widgetHeight * 0.5f)

                        isVisible = isAtLeastHalfVisible
                    },
                widgetDetails = widgetDetails,
                pagerState = pagerState,
                itemsCount = sortedWidgetImages.count(),
                width = staticWidth,
                itemContent = { index ->

                    sortedWidgetImages[index].takeIf {
                        it.image != null || it.lottie_data != null
                    }?.let {

                        CarousalImage(
                            modifier = modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                if(sortedWidgetImages[index].link.toString().trim().removeSurrounding("\"").isNotEmpty()){
                                    clickEvent(
                                        link = sortedWidgetImages[index].link.toString().trim().removeSurrounding("\""),
                                        campaignId = campaign.id,
                                        widgetImageId = sortedWidgetImages[index].id
                                    )

                                    trackEvents(
                                        campaign.id,
                                        "clicked",
                                        mapOf("widget_image" to sortedWidgetImages[index].id!!)
                                    )
                                }
                            },
                            contentScale = contentScale,
                            imageUrl = sortedWidgetImages[index].image ?: "",
                            lottieUrl = sortedWidgetImages[index].lottie_data ?: "",
                            placeHolder = placeHolder,
                            height = calculatedHeight,
                            width = widthInDp ?: staticWidth,
                            placeholderContent = placeholderContent
                        )
                    }
                }
            )

        }
    }


    @Composable
    private fun DoubleWidget(
        modifier: Modifier = Modifier,
        staticWidth: Dp? = null,
        position: String?,
        placeHolder: Drawable?,
        placeholderContent: (@Composable () -> Unit)? = null,
    ) {
        val campaignsData = campaigns.collectAsStateWithLifecycle()
        val disabledCampaigns = disabledCampaigns.collectAsStateWithLifecycle()

        val campaign = campaignsData.value
            .filter { it.campaignType == "WID" && it.details is WidgetDetails && it.position == position }
            .firstOrNull { (it.details as WidgetDetails).type == "half" }

        val widgetDetails = (campaign?.details as? WidgetDetails)

        var isVisible by remember { mutableStateOf(false) }
        val configuration = LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp.dp

        if (widgetDetails != null && campaign.id != null &&
            !disabledCampaigns.value.contains(campaign.id) && widgetDetails.widgetImages != null && widgetDetails.type == "half"
        ) {
            val widthInDp: Dp? = widgetDetails.width?.dp

            val calculatedHeight =
                if (widgetDetails.width != null && widgetDetails.height != null) {
                    val aspectRatio = widgetDetails.height.toFloat() / widgetDetails.width.toFloat()

                    val marginLeft = (widgetDetails.styling?.leftMargin ?: 0).dp
                    val marginRight = (widgetDetails.styling?.rightMargin ?: 0).dp

                    val horizontalMargin = marginLeft + marginRight

                    val actualWidth = (staticWidth ?: screenWidth) - horizontalMargin
                    ((actualWidth.value.minus(12) * aspectRatio).div(2)).dp
                } else {
                    (widgetDetails.height?.minus(12))?.div(2)?.dp
                }

            val widgetImagesPairs = widgetDetails.widgetImages.turnToPair()
            val pagerState = rememberPagerState(pageCount = {
                widgetImagesPairs.count()
            })

            LaunchedEffect(pagerState.currentPage, isVisible) {
                if (isVisible) {
                    campaign?.id?.let {

                        if (widgetImagesPairs[pagerState.currentPage].first.id != null && !impressions.value.contains(
                                widgetImagesPairs[pagerState.currentPage].first.id
                            )
                        ) {
                            val impressions = ArrayList(impressions.value)
                            impressions.add(widgetImagesPairs[pagerState.currentPage].first.id)
                            this@AppStorys.impressions.emit(impressions)
                            trackEvents(
                                it,
                                "viewed",
                                mapOf("widget_image" to widgetImagesPairs[pagerState.currentPage].first.id!!)
                            )

                        }

                        if (widgetImagesPairs[pagerState.currentPage].second.id != null && !impressions.value.contains(
                                widgetImagesPairs[pagerState.currentPage].second.id
                            )
                        ) {
                            val impressions = ArrayList(impressions.value)
                            impressions.add(widgetImagesPairs[pagerState.currentPage].second.id)
                            this@AppStorys.impressions.emit(impressions)
                            trackEvents(
                                it,
                                "viewed",
                                mapOf("widget_image" to widgetImagesPairs[pagerState.currentPage].second.id!!)
                            )

                        }
                    }
                }
            }

            DoubleWidgets(
                modifier = modifier
                    .padding(
                        top = (widgetDetails.styling?.topMargin ?: 0).dp,
                        bottom = (widgetDetails.styling?.bottomMargin ?: 0).dp,
                        start = (widgetDetails.styling?.leftMargin ?: 0).dp,
                        end = (widgetDetails.styling?.rightMargin ?: 0).dp,
                    )
                    .onGloballyPositioned { layoutCoordinates ->
                        val visibilityRect = layoutCoordinates.boundsInWindow()
                        val parentHeight =
                            layoutCoordinates.parentLayoutCoordinates?.size?.height ?: 0
                        val widgetHeight = layoutCoordinates.size.height
                        val isAtLeastHalfVisible = visibilityRect.top < parentHeight &&
                                visibilityRect.bottom > 0 &&
                                (visibilityRect.height >= widgetHeight * 0.5f)

                        isVisible = isAtLeastHalfVisible
                    },
                pagerState = pagerState,
                itemsCount = widgetImagesPairs.count(),
                width = widthInDp ?: staticWidth,
                itemContent = { index ->
                    val (leftImage, rightImage) = widgetImagesPairs[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (leftImage.image != null || leftImage.lottie_data != null) {
                            ImageCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) {
                                        if (leftImage.link.toString().trim()
                                                .removeSurrounding("\"").isNotEmpty()
                                        ) {
                                            clickEvent(
                                                link = leftImage.link.toString().trim()
                                                    .removeSurrounding("\""),
                                                campaignId = campaign.id,
                                                widgetImageId = leftImage.id
                                            )

                                            trackEvents(
                                                campaign.id,
                                                "clicked",
                                                mapOf("widget_image" to leftImage.id!!)
                                            )
                                        }

                                    },
                                imageUrl = leftImage.image ?: "",
                                lottieUrl = leftImage.lottie_data ?: "",
                                widgetDetails = widgetDetails,
                                height = calculatedHeight,
                                placeHolder = placeHolder,
                                placeholderContent = placeholderContent
                            )
                        }
                        if (rightImage.image != null || rightImage.lottie_data != null) {
                            ImageCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) {
                                        if (rightImage.link.toString().trim()
                                                .removeSurrounding("\"").isNotEmpty()
                                        ) {
                                            clickEvent(
                                                link = rightImage.link.toString().trim()
                                                    .removeSurrounding("\""),
                                                campaignId = campaign.id,
                                                widgetImageId = rightImage.id
                                            )

                                            trackEvents(
                                                campaign.id,
                                                "clicked",
                                                mapOf("widget_image" to rightImage.id!!)
                                            )
                                        }
                                    },
                                imageUrl = rightImage.image ?: "",
                                lottieUrl = rightImage.lottie_data ?: "",
                                widgetDetails = widgetDetails,
                                height = calculatedHeight,
                                placeHolder = placeHolder,
                                placeholderContent = placeholderContent
                            )
                        }
                    }
                }
            )
        }
    }

    @Composable
    fun BottomSheet() {

        val campaignsData = campaigns.collectAsStateWithLifecycle()

        val campaign =
            campaignsData.value.firstOrNull { it.campaignType == "BTS" && it.details is BottomSheetDetails }

        val bottomSheetDetails = when (val details = campaign?.details) {
            is BottomSheetDetails -> details
            else -> null
        }

        val triggerEventValue = when (val event = campaign?.triggerEvent) {
            "viaAppStorys" -> "viaAppStorys${campaign?.id}"
            null, "" -> null
            else -> event
        }

        val shouldShowBottomSheet = remember(triggerEventValue, trackedEventNames.size) {
            triggerEventValue.isNullOrEmpty() || trackedEventNames.contains(triggerEventValue)
        }

        if (bottomSheetDetails != null && showBottomSheet && shouldShowBottomSheet) {

            LaunchedEffect(Unit) {
                campaign?.id?.let {
                    trackEvents(it, "viewed")
                }
            }

            BottomSheetComponent(
                onDismissRequest = {
                    showBottomSheet = false
                    triggerEventValue?.let { trackedEventNames.remove(it) }
                },
                bottomSheetDetails = bottomSheetDetails,
                onClick = { ctaLink ->
                    if (!ctaLink.isNullOrEmpty()) {
                        campaign?.id?.let { campaignId ->
                            clickEvent(link = ctaLink, campaignId = campaignId)
                            trackEvents(campaignId, "clicked")
                        }
                    }
                },
            )
        }
    }

    @Composable
    fun Survey() {
        var showSurvey by remember { mutableStateOf(true) }

        val campaignsData = campaigns.collectAsStateWithLifecycle()

        val campaign =
            campaignsData.value.firstOrNull { it.campaignType == "SUR" && it.details is SurveyDetails }

        val surveyDetails = when (val details = campaign?.details) {
            is SurveyDetails -> details
            else -> null
        }

        val triggerEventValue = when (val event = campaign?.triggerEvent) {
            "viaAppStorys" -> "viaAppStorys${campaign?.id}"
            null, "" -> null
            else -> event
        }

        val shouldShowSurvey = remember(triggerEventValue, trackedEventNames.size) {
            triggerEventValue.isNullOrEmpty() || trackedEventNames.contains(triggerEventValue)
        }

        if (surveyDetails != null && showSurvey && shouldShowSurvey) {

            LaunchedEffect(Unit) {
                campaign?.id?.let {
                    trackEvents(it, "viewed")
                }
            }

            SurveyBottomSheet(
                onDismissRequest = {
                    showSurvey = false
                },
                surveyDetails = surveyDetails,
                onSubmitFeedback = { feedback ->
                    coroutineScope.launch {
                        trackEvents(
                            campaign_id = campaign?.id,
                            event = "survey captured",
                            metadata = mapOf(
                                "selectedOptions" to (feedback.responseOptions ?: ""),
                                "otherText" to feedback.comment
                            )
                        )
                    }
                },
            )
        }
    }

    @Composable
    fun Modals() {
        val campaignsData = campaigns.collectAsStateWithLifecycle()

        val campaign =
            campaignsData.value.firstOrNull { it.campaignType == "MOD" && it.details is ModalDetails }

        val modalDetails = when (val details = campaign?.details) {
            is ModalDetails -> details
            else -> null
        }

        val triggerEventValue = when (val event = campaign?.triggerEvent) {
            "viaAppStorys" -> "viaAppStorys${campaign?.id}"
            null, "" -> null
            else -> event
        }

        val shouldShowModals = remember(triggerEventValue, trackedEventNames.size) {
            triggerEventValue.isNullOrEmpty() || trackedEventNames.contains(triggerEventValue)
        }

        if (modalDetails != null && showModal && shouldShowModals) {

            LaunchedEffect(Unit) {
                campaign?.id?.let {
                    trackEvents(it, "viewed")
                }
            }

            PopupModal(
                onCloseClick = {
                    showModal = false
                },
                modalDetails = modalDetails,
                onModalClick = {
                    val modal = modalDetails.modals?.getOrNull(0)

                    val link =
                        modal?.content?.primaryCtaRedirection?.url
                            ?: modal?.content?.primaryCtaRedirection?.value
                            ?: modal?.redirection?.url
                            ?: modal?.redirection?.value

                    campaign?.id?.let { campaignId ->
                        trackEvents(campaignId, "clicked")
                        clickEvent(link = link, campaignId = campaignId)
                    }
                },


                onPrimaryCta = { link ->
                    // primary CTA receives resolved URL string
                    campaign?.id?.let { campaignId ->
                        trackEvents(campaignId, "clicked")
                        clickEvent(link = link, campaignId = campaignId)
                    }
                },
                onSecondaryCta = { link ->
                    campaign?.id?.let { campaignId ->
                        trackEvents(campaignId, "clicked")
                        clickEvent(link = link, campaignId = campaignId)
                        showModal = false
                    }
                },
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @Composable
    fun ScratchCard() {

        var confettiTrigger by remember { mutableStateOf(0) }
        val campaignsData = campaigns.collectAsStateWithLifecycle()

        val scratchedCampaignsData = scratchedCampaigns.collectAsStateWithLifecycle()

        val campaign =
            campaignsData.value.firstOrNull { it.campaignType == "SCRT" && it.details is ScratchCardDetails }

        val scratchCardDetails = when (val details = campaign?.details) {
            is ScratchCardDetails -> details
            else -> null
        }

        val triggerEventValue = when (val event = campaign?.triggerEvent) {
            "viaAppStorys" -> "viaAppStorys${campaign?.id}"
            null, "" -> null
            else -> event
        }

        val shouldShowScratchCard = remember(triggerEventValue, trackedEventNames.size) {
            triggerEventValue.isNullOrEmpty() || trackedEventNames.contains(triggerEventValue)
        }

        val isAlreadyScratched = campaign?.id?.let {
            scratchedCampaignsData.value.contains(it)
        } ?: false

        var wasFullyScratched by remember(campaign?.id, isAlreadyScratched) {
            mutableStateOf(isAlreadyScratched)
        }

        var isPresented by remember(campaign?.id) { mutableStateOf(true) }

        LaunchedEffect(shouldShowScratchCard) {
            if (shouldShowScratchCard && !isPresented) {
                isPresented = true
            }
        }

        if (scratchCardDetails != null && shouldShowScratchCard && isPresented) {

            LaunchedEffect(Unit) {
                campaign?.id?.let {
                    trackEvents(it, "viewed")
                }
            }

            LaunchedEffect(wasFullyScratched) {
                if (wasFullyScratched && campaign?.id != null && !isAlreadyScratched) {
                    trackEvents(campaign.id, "scratched")

                    val currentScratchedCampaigns = ArrayList(scratchedCampaigns.value)
                    currentScratchedCampaigns.add(campaign.id)
                    scratchedCampaigns.emit(currentScratchedCampaigns.distinct())

                    saveScratchedCampaigns(
                        campaignIds = currentScratchedCampaigns.distinct(),
                        sharedPreferences = context.getSharedPreferences(
                            "AppStory",
                            Context.MODE_PRIVATE
                        )
                    )
                }
            }

            val ctaUrl = scratchCardDetails.content?.get("cta")
                ?.jsonObject?.get("url")
                ?.jsonPrimitive
                ?.contentOrNull ?: ""

            CardScratch(
                isPresented = isPresented,
                onDismiss = { isPresented = false
                    triggerEventValue?.let { trackedEventNames.remove(it) }
                            },
                onConfettiTrigger = {
                    confettiTrigger++
                },
                wasFullyScratched = wasFullyScratched,
                onWasFullyScratched = { wasFullyScratched = it },
                scratchCardDetails = scratchCardDetails,
                onCtaClick = {
                    campaign?.id?.let {
                        clickEvent(link = ctaUrl, campaignId = it)
                        trackEvents(it, "clicked")
                    }
                }
            )
        }
    }

    @Composable
    fun Milestone(
        topPadding: Dp = 0.dp,
        bottomPadding: Dp = 0.dp,
        isWidgets: Boolean = true
    ) {
        val campaignsData = campaigns.collectAsStateWithLifecycle()
        val disabledCampaigns = disabledCampaigns.collectAsStateWithLifecycle()

        val campaign = campaignsData.value.firstOrNull {
            it.campaignType == "MIL" && it.details is MilestoneDetails
        }

        val milestoneDetails = campaign?.details as? MilestoneDetails

        val triggerEventValue = when (val event = campaign?.triggerEvent) {
            "viaAppStorys" -> "viaAppStorys${campaign?.id}"
            null, "" -> null
            else -> event
        }

        val shouldShowMilestone = remember(triggerEventValue, trackedEventNames.size) {
            triggerEventValue.isNullOrEmpty() || trackedEventNames.contains(triggerEventValue)
        }

        val currentIndex by currentMilestoneIndex.collectAsStateWithLifecycle()

        // Track events and update milestone index
        LaunchedEffect(trackedEventNames.size, milestoneDetails) {
            milestoneDetails?.milestoneItems?.let { items ->
                val sortedItems = items.sortedBy { it.order }

                for ((index, item) in sortedItems.withIndex()) {
                    item.triggerEvents?.forEach { trigger ->
                        trigger.eventName?.let { eventName ->
                            if (trackedEventNames.contains(eventName) && index > currentIndex) {
                                currentMilestoneIndex.emit(index)
                                return@LaunchedEffect
                            }
                        }
                    }
                }
            }
        }

        if (milestoneDetails != null &&
            campaign?.id != null &&
            !disabledCampaigns.value.contains(campaign.id) &&
            shouldShowMilestone &&
            showMilestone
        ) {
            val sortedItems = milestoneDetails.milestoneItems?.sortedBy { it.order } ?: return

            if (currentIndex >= sortedItems.size) return

            val currentItem = sortedItems[currentIndex]
            val showAs = milestoneDetails.content?.showStreaksAs ?: "banner"

//            LaunchedEffect(currentIndex) {
//                campaign.id?.let {
//                    trackEvents(it, "viewed", mapOf("milestone_item" to currentItem.id!!))
//                }
//            }

            if((showAs == "banner" || showAs == "modals") && !isWidgets){
                when (showAs) {
                    "banner" -> MilestoneBanner(
                        milestoneItem = currentItem,
                        styling = milestoneDetails.styling,
                        bottomPadding = bottomPadding,
                        onClose = {
                            showMilestone = false
                            val ids = ArrayList(disabledCampaigns.value)
                            campaign.id?.let { ids.add(it) }
                            coroutineScope.launch {
                                this@AppStorys.disabledCampaigns.emit(ids.toList())
                            }
                        },
                        onClick = {
//                        campaign.id?.let { campaignId ->
//                            trackEvents(campaignId, "clicked", mapOf("milestone_item" to currentItem.id!!))
//                        }
                        }
                    )
                    "modals" -> MilestoneModal(
                        milestoneItem = currentItem,
                        styling = milestoneDetails.styling,
                        bottomPadding = bottomPadding,
                        onClose = {
                            showMilestone = false
                            val ids = ArrayList(disabledCampaigns.value)
                            campaign.id?.let { ids.add(it) }
                            coroutineScope.launch {
                                this@AppStorys.disabledCampaigns.emit(ids.toList())
                            }
                        },
                        onClick = {
//                        campaign.id?.let { campaignId ->
//                            trackEvents(campaignId, "clicked", mapOf("milestone_item" to currentItem.id!!))
//                        }
                        }
                    )
                }
            } else if(showAs == "widgets"&& isWidgets){
                // MileStone Widgets
                MilestoneWidgets(
                    milestoneItem = currentItem,
                    styling = milestoneDetails.styling,
                    onClick = {
//                        campaign.id?.let { campaignId ->
//                            trackEvents(campaignId, "clicked", mapOf("milestone_item" to currentItem.id!!))
//                        }
                    }
                )
            }
        }
    }

    @Composable
    fun TestUserButton(
        modifier: Modifier = Modifier,
        screenName: String? = null,
        activity: Activity? = null
    ) {
        val TAG = "TestUserButton"

        val activityRef = activity ?: LocalContext.current as? Activity

        var shouldAnalyze by remember { mutableStateOf(false) }
        var isCapturing by remember { mutableStateOf(false) }

        val snackbarHostState = remember { SnackbarHostState() }
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(shouldAnalyze) {
            Log.i(TAG, "LaunchedEffect triggered. shouldAnalyze = $shouldAnalyze")

            if (shouldAnalyze) {
                Log.i(TAG, "Starting screen capture flow")
                isCapturing = true
                Log.i(TAG, "isCapturing = true")
                delay(500)
                val activity = activityRef
                Log.i(TAG, "Activity reference: $activity")
                val rootView = activity?.window?.decorView?.rootView
                Log.i(TAG, "Root view acquired: $rootView")
                rootView?.let {
                    val screenToAnalyze = screenName ?: currentScreen
                    Log.i(TAG, "Screen to analyze: $screenToAnalyze")

                    Log.i(TAG, "Calling analyzeViewRoot()")
                    analyzeViewRoot(it, screenToAnalyze, activity)
                    Log.i(TAG, "analyzeViewRoot() completed")

                    coroutineScope.launch {
                        Log.i(TAG, "Showing snackbar")
                        snackbarHostState.showSnackbar("Screen captured successfully!")
                    }
                }
                shouldAnalyze = false
                isCapturing = false

                if(widgetPositionList.isNotEmpty() && widgetPositionList[0].isNotEmpty()){
                    Log.i(TAG, "widgetPositionList is valid")
                    coroutineScope.launch {
                        Log.i(TAG, "Calling repository.sendWidgetPositions()")
                        repository.sendWidgetPositions(
                            accessToken = accessToken,
                            screenName = currentScreen,
                            positionList = widgetPositionList
                        )
                    }
                }
            }
        }

        if (
            isScreenCaptureEnabled &&
            !isCapturing
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                FloatingActionButton(
                    onClick = {
                        Log.i(TAG, "Capture button clicked")
                        shouldAnalyze = true

                        Log.i(TAG, "shouldAnalyze = true")
                    },
                    modifier = modifier
                        .padding(bottom = 86.dp, end = 16.dp)
                        .align(Alignment.BottomEnd),
                    containerColor = Color.White
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        text = "Capture Screen"
                    )
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp)
                )
            }
        }
    }


    internal fun handleTooltipAction(tooltip: Tooltip, isClick: Boolean = false) {
        coroutineScope.launch {
            val campaign = campaigns.value.firstOrNull { campaign ->
                campaign.campaignType == "TTP" && campaign.details is TooltipsDetails && campaign.details.tooltips?.any { it.id == tooltip.id } != null
            } ?: campaigns.value.firstOrNull { campaign ->
                campaign.campaignType == "TTP" && campaign.details is TooltipsDetails
            }

            val tooltipId = tooltip.id ?: return@launch

            if (!viewedTooltips.value.contains(tooltipId)) {
                trackEvents(
                    campaign?.id,
                    "viewed",
                    mapOf("tooltip_id" to tooltipId)
                )
                viewedTooltips.update { it + tooltipId }
            }

            if (isClick) {
                if (!tooltip.link.isNullOrEmpty()) {
                    trackEvents(
                        campaign?.id,
                        "clicked",
                        mapOf("tooltip_id" to tooltipId)
                    )

                    if (tooltip.link.isNotEmpty()) {
                        if (!isValidUrl(tooltip.link)) {
                            navigateToScreen(tooltip.link)
                        } else {
                            openUrl(tooltip.link)
                        }
                    } else {
                        dismissTooltip()
                    }
                } else {
                    dismissTooltip()
                }
            }
        }
    }

    internal fun dismissTooltip() {
        coroutineScope.launch {
            tooltipTargetView.emit(null)
            showcaseVisible.emit(false)
        }
    }

    private fun clickEvent(link: Any?, campaignId: String, widgetImageId: String? = null) {

        viaAppStorys(event = "viaAppStorys${link}")

        if (link != null && link is String) {
            if (link.isNotEmpty()) {
                if (!isValidUrl(link)) {
                    navigateToScreen(link)
                } else {
                    openUrl(link)
                }
            }
        } else if (link is Map<*, *>) {
            val json = JSONObject(link)
            handleDeepLink(json, campaignId, widgetImageId)
        } else if (link is JSONObject) {
            handleDeepLink(link, campaignId, widgetImageId)
        }
    }

    private fun handleDeepLink(json: JSONObject, campaignId: String, widgetImageId: String?) {
        try {
            val value = json.optString("value", null)
            val type = json.optString("type", null)
            val context = json.optJSONObject("context")?.toMap()

            if (value != null) {
                navigateToScreen(value)
            }

        } catch (e: Exception) {
            Log.e("DeepLinkException", e.message.toString())
        }
    }

    private fun List<WidgetImage>.turnToPair(): List<Pair<WidgetImage, WidgetImage>> {
        if (this.isEmpty()) {
            return emptyList()
        }
        val widgetImagePairs: List<Pair<WidgetImage, WidgetImage>> = this
            .sortedBy { it.order }
            .windowed(2, 2, partialWindows = false) { (first, second) ->
                first to second
            }

        return widgetImagePairs
    }

    private fun getDeviceInfo(context: Context): Map<String, Any> {
        val packageManager = context.packageManager
        val packageName = context.packageName
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        val installTime = packageInfo.firstInstallTime
        val updateTime = packageInfo.lastUpdateTime

        val metrics = context.resources.displayMetrics
        val configuration = context.resources.configuration

        return mapOf(
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "os_version" to Build.VERSION.RELEASE,
            "api_level" to Build.VERSION.SDK_INT,
            "language" to when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> configuration.locales[0].language
                else -> configuration.locale.language
            },
            "locale" to when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> configuration.locales[0].toString()
                else -> configuration.locale.toString()
            },
            "timezone" to java.util.TimeZone.getDefault().id,
            "screen_width_px" to metrics.widthPixels,
            "screen_height_px" to metrics.heightPixels,
            "screen_density" to metrics.densityDpi,
            "orientation" to if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) "portrait" else "landscape",
            "app_version" to packageInfo.versionName,
            "package_name" to packageName,
            "device_type" to "mobile",
            "platform" to "android"
        )
    }

    private suspend fun checkIfInitialized(): Boolean {
        while (sdkState == AppStorysSdkState.Initializing) {
            delay(100)
        }
        return !(sdkState != AppStorysSdkState.Initialized || accessToken.isBlank())
    }

    internal fun isValidUrl(url: String?): Boolean {
        return !url.isNullOrEmpty() && Patterns.WEB_URL.matcher(url).matches()
    }

    internal fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.i("Click", "Link has $e")
        }
    }

    @JvmStatic
    fun getInstance() = this
}
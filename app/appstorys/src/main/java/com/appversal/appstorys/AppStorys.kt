package com.appversal.appstorys

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.util.Patterns
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appversal.appstorys.api.ApiRepository
import com.appversal.appstorys.api.BottomSheetDetails
import com.appversal.appstorys.api.Campaign
import com.appversal.appstorys.api.ModalDetails
import com.appversal.appstorys.api.RetrofitClient
import com.appversal.appstorys.api.ScratchCardDetails
import com.appversal.appstorys.api.SurveyDetails
import com.appversal.appstorys.api.Tooltip
import com.appversal.appstorys.api.TooltipsDetails
import com.appversal.appstorys.api.WidgetDetails
import com.appversal.appstorys.api.WidgetImage
import com.appversal.appstorys.domain.model.AppStorysSdkState
import com.appversal.appstorys.domain.usecase.ClickEvent
import com.appversal.appstorys.presentation.Placeholder
import com.appversal.appstorys.ui.AutoSlidingCarousel
import com.appversal.appstorys.ui.BottomSheetComponent
import com.appversal.appstorys.ui.CardScratch
import com.appversal.appstorys.ui.CarousalImage
import com.appversal.appstorys.ui.DoubleWidgets
import com.appversal.appstorys.ui.ImageCard
import com.appversal.appstorys.ui.OverlayContainer
import com.appversal.appstorys.ui.PopupModal
import com.appversal.appstorys.ui.SurveyBottomSheet
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

object AppStorys {
    private lateinit var context: Application

    private lateinit var appId: String

    private lateinit var accountId: String

    private lateinit var userId: String

    private var attributes: Map<String, Any>? = null

    private val apiService = RetrofitClient.apiService

    internal lateinit var repository: ApiRepository

    private val campaigns = MutableStateFlow<List<Campaign>>(emptyList())

    private val disabledCampaigns = MutableStateFlow<List<String>>(emptyList())

    private val impressions = MutableStateFlow<List<String>>(emptyList())

    private val viewsCoordinates = MutableStateFlow<Map<String, LayoutCoordinates>>(emptyMap())

    val tooltipTargetView = MutableStateFlow<Tooltip?>(null)

    private val tooltipViewed = MutableStateFlow<List<String>>(emptyList())

    private val showcaseVisible = MutableStateFlow(false)

    private var accessToken = ""

    private var currentScreen = ""

    private var isScreenCaptureEnabled by mutableStateOf(false)

    private var showModal by mutableStateOf(true)

    private var showBottomSheet by mutableStateOf(true)

    internal var sdkState = AppStorysSdkState.Uninitialized
        private set

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var campaignsJob: Job? = null

    private var trackedEventNames = mutableStateListOf<String>()

    private var widgetPositionList = listOf<String>()

    private val viewedTooltips = MutableStateFlow<Set<String>>(emptySet())

    fun initialize(
        context: Application,
        appId: String,
        accountId: String,
        userId: String,
        attributes: Map<String, Any>?,
        navigateToScreen: (String) -> Unit
    ) {
        if (::context.isInitialized) {
            Log.w("AppStorys", "SDK is already initialized")
            return
        }

        this.context = context
        this.appId = appId
        this.accountId = accountId
        this.userId = userId
        this.attributes = attributes
        ClickEvent.initialize(context, navigateToScreen)

        this.repository = ApiRepository(context, apiService) {
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
                    showBottomSheet = true
                    trackedEventNames.clear()
                    repository.disconnect()
                    campaignsJob?.cancel()
                    campaignsJob = null
                }
            }
        )
        coroutineScope.launch {
            try {
                val accessToken = repository.getAccessToken(appId, accountId)
                if (!accessToken.isNullOrBlank()) {
                    this@AppStorys.accessToken = accessToken
                    sdkState = AppStorysSdkState.Initialized
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

                val deviceInfo = getDeviceInfo(context)

                val mergedAttributes = (attributes ?: emptyMap()) + deviceInfo

                ensureActive()

                val (campaignResponse, webSocketResponse) = repository.triggerScreenData(
                    accessToken = accessToken,
                    screenName = currentScreen,
                    userId = userId,
                    attributes = mergedAttributes
                )

                ensureActive()

                webSocketResponse?.let { response ->
                    isScreenCaptureEnabled = response.screen_capture_enabled ?: false
                }

                campaignResponse?.campaigns?.let { campaigns.emit(it) }
                Log.e("AppStorys", "Campaign: ${campaigns.value}")
            } catch (exception: Exception) {
                Log.e("AppStorys", "Error getting campaigns for $screenName", exception)
            }
        }
    }

    fun trackEvent(campaignId: String? = null, event: String, metadata: Map<String, Any>? = null) {
        coroutineScope.launch {
            com.appversal.appstorys.domain.usecase.trackEvent(context, event, campaignId, metadata)
        }
    }

    fun setUserProperties(attributes: Map<String, Any>) {
        coroutineScope.launch {
            com.appversal.appstorys.domain.usecase.setUserProperties(context, attributes)
        }
    }

    @Composable
    fun overlayElements(
        bottomPadding: Dp = 0.dp,
        topPadding: Dp = 0.dp,
    ) {
        OverlayContainer.Content(
            bottomPadding = bottomPadding,
            topPadding = topPadding,
        )
    }


    @Composable
    fun Banner(
        modifier: Modifier = Modifier,
        placeholder: Placeholder? = null,
        bottomPadding: Dp = 0.dp,
    ) {
        com.appversal.appstorys.presentation.Banner(
            modifier = modifier,
            placeholder = placeholder,
            bottomPadding = bottomPadding
        )
    }

    @Composable
    fun CaptureScreenButton(
        modifier: Modifier = Modifier,
        activity: Activity? = null,
    ) {
        com.appversal.appstorys.presentation.CaptureScreenButton(
            modifier = modifier,
            activity = activity
        )
    }

    @Composable
    fun Csat(modifier: Modifier = Modifier, bottomPadding: Dp = 0.dp) {
        com.appversal.appstorys.presentation.Csat(modifier, bottomPadding)
    }

    @Composable
    fun Floater(
        modifier: Modifier = Modifier,
        bottomPadding: Dp = 0.dp
    ) {
        com.appversal.appstorys.presentation.Floater(
            modifier = modifier,
            bottomPadding = bottomPadding
        )
    }

    @Composable
    fun Pip(
        modifier: Modifier = Modifier,
        topPadding: Dp = 0.dp,
        bottomPadding: Dp = 0.dp,
    ) {
        com.appversal.appstorys.presentation.Pip(
            modifier = modifier,
            topPadding = topPadding,
            bottomPadding = bottomPadding
        )
    }

    @Composable
    fun Reels(modifier: Modifier = Modifier) {
        com.appversal.appstorys.presentation.Reels(
            modifier = modifier,
        )
    }

    @Composable
    fun Stories(
        modifier: Modifier = Modifier,
    ) {
        com.appversal.appstorys.presentation.Stories(
            modifier = modifier,
        )
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

    @Composable
    fun getUserId(): String {
        return userId
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

        val shouldShowWidget = campaign?.triggerEvent.isNullOrEmpty() ||
                trackedEventNames.contains(campaign?.triggerEvent)

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
            val pagerState = rememberPagerState(pageCount = {
                widgetDetails.widgetImages.count()
            })
            val widthInDp: Dp? = widgetDetails.width?.dp

            val calculatedHeight =
                if (widgetDetails.width != null && widgetDetails.height != null) {
                    val aspectRatio = widgetDetails.height.toFloat() / widgetDetails.width.toFloat()

                    val marginLeft = widgetDetails.styling?.leftMargin?.toFloatOrNull()?.dp ?: 0.dp
                    val marginRight =
                        widgetDetails.styling?.rightMargin?.toFloatOrNull()?.dp ?: 0.dp

                    val actualWidth = (staticWidth ?: screenWidth) - marginLeft - marginRight
                    (actualWidth.value.minus(
                        32
                        // for the new widget
//                            +26
                    ) * aspectRatio).dp
                } else {
                    widgetDetails.height?.dp
                }

            LaunchedEffect(pagerState.currentPage, isVisible) {
                if (isVisible) {
                    campaign?.id?.let {
                        val currentWidgetId = widgetDetails.widgetImages[pagerState.currentPage].id

                        if (currentWidgetId != null && !impressions.value.contains(currentWidgetId)) {
                            val impressions = ArrayList(impressions.value)
                            impressions.add(currentWidgetId)
                            this@AppStorys.impressions.emit(impressions)
                            trackEvent(
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
                        top = (widgetDetails.styling?.topMargin?.toFloatOrNull() ?: 0f).dp,
                        bottom = (widgetDetails.styling?.bottomMargin?.toFloatOrNull() ?: 0f).dp,
                        start = (widgetDetails.styling?.leftMargin?.toFloatOrNull() ?: 0f).dp,
                        end = (widgetDetails.styling?.rightMargin?.toFloatOrNull() ?: 0f).dp,
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
                itemsCount = widgetDetails.widgetImages.count(),
                width = staticWidth,
                itemContent = { index ->
                    widgetDetails.widgetImages[index].image?.let {
                        CarousalImage(
                            modifier = modifier.clickable {
                                ClickEvent(
                                    link = widgetDetails.widgetImages[index].link,
                                    campaignId = campaign.id,
                                    widgetImageId = widgetDetails.widgetImages[index].id
                                )
                            },
                            contentScale = contentScale,
                            imageUrl = widgetDetails.widgetImages[index].image ?: "",
                            lottieUrl = widgetDetails.widgetImages[index].lottie_data ?: "",
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

                    val marginLeft = widgetDetails.styling?.leftMargin?.toFloatOrNull()?.dp ?: 0.dp
                    val marginRight =
                        widgetDetails.styling?.rightMargin?.toFloatOrNull()?.dp ?: 0.dp

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
                            trackEvent(
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
                            trackEvent(
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
                        top = (widgetDetails.styling?.topMargin?.toFloatOrNull() ?: 0f).dp,
                        bottom = (widgetDetails.styling?.bottomMargin?.toFloatOrNull() ?: 0f).dp,
                        start = (widgetDetails.styling?.leftMargin?.toFloatOrNull() ?: 0f).dp,
                        end = (widgetDetails.styling?.rightMargin?.toFloatOrNull() ?: 0f).dp,
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
                        if (leftImage.image != null) {
                            ImageCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (leftImage.link != null) {
                                            ClickEvent(
                                                link = leftImage.link,
                                                campaignId = campaign.id,
                                                widgetImageId = leftImage.id
                                            )

                                            trackEvent(
                                                campaign.id,
                                                "clicked",
                                                mapOf("widget_image" to leftImage.id!!)
                                            )
                                        }

                                    },
                                imageUrl = leftImage.image,
                                widgetDetails = widgetDetails,
                                height = calculatedHeight,
                                placeHolder = placeHolder,
                                placeholderContent = placeholderContent
                            )
                        }
                        if (rightImage.image != null) {
                            ImageCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (rightImage.link != null) {
                                            ClickEvent(
                                                link = rightImage.link,
                                                campaignId = campaign.id,
                                                widgetImageId = rightImage.id
                                            )
                                        }
                                    },
                                imageUrl = rightImage.image,
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

        val shouldShowBottomSheet = campaign?.triggerEvent.isNullOrEmpty() ||
                trackedEventNames.contains(campaign?.triggerEvent)

        if (bottomSheetDetails != null && showBottomSheet && shouldShowBottomSheet) {

            LaunchedEffect(Unit) {
                campaign?.id?.let {
                    trackEvent(it, "viewed")
                }
            }

            BottomSheetComponent(
                onDismissRequest = {
                    showBottomSheet = false
                },
                bottomSheetDetails = bottomSheetDetails,
                onClick = { ctaLink ->
                    if (!ctaLink.isNullOrEmpty()) {
                        campaign?.id?.let { campaignId ->
                            ClickEvent(link = ctaLink, campaignId = campaignId)
                            trackEvent(campaignId, "clicked")
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

        val shouldShowSurvey = campaign?.triggerEvent.isNullOrEmpty() ||
                trackedEventNames.contains(campaign?.triggerEvent)

        if (surveyDetails != null && showSurvey && shouldShowSurvey) {

            LaunchedEffect(Unit) {
                campaign?.id?.let {
                    trackEvent(it, "viewed")
                }
            }

            SurveyBottomSheet(
                onDismissRequest = {
                    showSurvey = false
                },
                surveyDetails = surveyDetails,
                onSubmitFeedback = { feedback ->
                    coroutineScope.launch {
                        trackEvent(
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

        val shouldShowModals = campaign?.triggerEvent.isNullOrEmpty() ||
                trackedEventNames.contains(campaign?.triggerEvent)

        if (modalDetails != null && showModal && shouldShowModals) {

            LaunchedEffect(Unit) {
                campaign?.id?.let {
                    trackEvent(it, "viewed")
                }
            }

            PopupModal(
                onCloseClick = {
                    showModal = false
                },
                modalDetails = modalDetails,
                onModalClick = {
                    ClickEvent(modalDetails.modals?.getOrNull(0)?.link, campaign?.id)
                    showModal = false
                },
            )
        }
    }

    @Composable
    fun ScratchCard() {

        var confettiTrigger by remember { mutableStateOf(0) }
        var wasFullyScratched by remember { mutableStateOf(false) }
        var isPresented by remember { mutableStateOf(true) }

        val campaignsData = campaigns.collectAsStateWithLifecycle()

        val campaign =
            campaignsData.value.firstOrNull { it.campaignType == "SCRT" && it.details is ScratchCardDetails }

        val scratchCardDetails = when (val details = campaign?.details) {
            is ScratchCardDetails -> details
            else -> null
        }

        val shouldShowScratchCard = campaign?.triggerEvent.isNullOrEmpty() ||
                trackedEventNames.contains(campaign?.triggerEvent)

        if (scratchCardDetails != null && shouldShowScratchCard) {

            LaunchedEffect(Unit) {
                campaign?.id?.let {
                    trackEvent(it, "viewed")
                }
            }

            CardScratch(
                isPresented = isPresented,
                onDismiss = { isPresented = false },
                onConfettiTrigger = {
                    confettiTrigger++
                    // Trigger your confetti animation here
                },
                wasFullyScratched = wasFullyScratched,
                onWasFullyScratched = { wasFullyScratched = it },
                scratchCardDetails = scratchCardDetails
            )
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
                trackEvent(
                    campaign?.id,
                    "viewed",
                    mapOf("tooltip_id" to tooltipId)
                )
                viewedTooltips.update { it + tooltipId }
            }

            if (isClick) {
                if (!tooltip.deepLinkUrl.isNullOrEmpty()) {
                    trackEvent(
                        campaign?.id,
                        "clicked",
                        mapOf("tooltip_id" to tooltipId)
                    )

                    if (tooltip.clickAction == "deepLink") {
                        ClickEvent(tooltip.deepLinkUrl)
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

    private fun isValidUrl(url: String?): Boolean {
        return !url.isNullOrEmpty() && Patterns.WEB_URL.matcher(url).matches()
    }
}
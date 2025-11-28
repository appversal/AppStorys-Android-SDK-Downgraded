package com.appversal.appstorys

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appversal.appstorys.domain.State
import com.appversal.appstorys.domain.State.sdkState
import com.appversal.appstorys.domain.State.setSdkState
import com.appversal.appstorys.domain.model.AppStorysSdkState
import com.appversal.appstorys.domain.model.ScreenOptions
import com.appversal.appstorys.domain.usecase.ClickEvent
import com.appversal.appstorys.domain.usecase.launchTask
import com.appversal.appstorys.domain.usecase.verifyAccount
import com.appversal.appstorys.presentation.Placeholder
import com.appversal.appstorys.utils.ReleaseTree
import kotlinx.coroutines.delay
import timber.log.Timber

object AppStorys {

    private val log: Timber.Tree
        get() = Timber.tag("AppStorys")

    suspend fun initialize(
        context: Context,
        appId: String,
        accountId: String,
        userId: String,
        attributes: Map<String, Any>?,
        navigateToScreen: (String) -> Unit
    ) {
        if (sdkState.value == AppStorysSdkState.Initialized && State.appId.value == appId && State.accountId.value == accountId) {
            log.i("SDK is already initialized with the same App ID and Account ID")
            return
        }

        if (sdkState.value == AppStorysSdkState.Initializing) {
            log.w("SDK is initializing")
            return
        }

        setSdkState(AppStorysSdkState.Initializing)
        log.i("Initializing SDK with App ID: $appId and Account ID: $accountId")

        ClickEvent.initialize(navigateToScreen)
        State.initialize(context)

        if (Timber.treeCount == 0) {
            Timber.plant(if (BuildConfig.DEBUG) Timber.DebugTree() else ReleaseTree())
        }

        try {
            if (verifyAccount(accountId, appId)) {
                State.setAppId(appId)
                State.setAccountId(accountId)
                State.setUserId(userId)
                if (!attributes.isNullOrEmpty()) {
                    State.setAttributes(attributes)
                }

                setSdkState(AppStorysSdkState.Initialized)
                log.i("SDK initialized successfully")
            }
        } catch (e: Exception) {
            log.e(e, "Account verification failed")
            setSdkState(AppStorysSdkState.Error)
            return
        }
    }

    fun trackEvent(
        context: Context,
        event: String,
        campaignId: String? = null,
        metadata: Map<String, Any>? = null
    ) = launchTask {
        ifInitialized {
            com.appversal.appstorys.domain.usecase.trackEvent(context, event, campaignId, metadata)
        }
    }

    fun trackScreen(
        screenName: String,
        emitTrackEvent: Boolean = true,
    ) = launchTask {
        ifInitialized {
            com.appversal.appstorys.domain.usecase.trackScreen(screenName, emitTrackEvent)
        }
    }


    fun setUserProperties(
        context: Context, attributes: Map<String, Any>
    ) = launchTask {
        ifInitialized {
            com.appversal.appstorys.domain.usecase.setUserProperties(context, attributes)
        }
    }

    @Composable
    fun Banner(
        modifier: Modifier = Modifier,
        placeholder: Placeholder? = null,
        bottomPadding: Dp = 0.dp,
    ) = IfInitialized {
        com.appversal.appstorys.presentation.Banner(
            modifier = modifier,
            placeholder = placeholder,
            bottomPadding = bottomPadding
        )
    }

    @Composable
    fun BottomSheet(
        modifier: Modifier = Modifier,
    ) = IfInitialized {
        com.appversal.appstorys.presentation.BottomSheet(
            modifier = modifier,
        )
    }

    @Composable
    fun CaptureScreenButton(
        modifier: Modifier = Modifier,
        activity: Activity? = null,
    ) = IfInitialized {
        com.appversal.appstorys.presentation.CaptureScreenButton(
            modifier = modifier,
            activity = activity
        )
    }

    @Composable
    fun Container(
        modifier: Modifier = Modifier,
        name: String? = null,
        options: ScreenOptions? = null,
        content: @Composable () -> Unit
    ) = com.appversal.appstorys.presentation.Container(
        modifier = modifier,
        name = name,
        options = options,
        content = content
    )

    @Composable
    fun Csat(
        modifier: Modifier = Modifier,
        bottomPadding: Dp = 0.dp
    ) = IfInitialized {
        com.appversal.appstorys.presentation.Csat(modifier, bottomPadding)
    }

    @Composable
    fun Floater(
        modifier: Modifier = Modifier,
        bottomPadding: Dp = 0.dp
    ) = IfInitialized {
        com.appversal.appstorys.presentation.Floater(
            modifier = modifier,
            bottomPadding = bottomPadding
        )
    }

    @Composable
    fun Modal(
        modifier: Modifier = Modifier
    ) = IfInitialized {
        com.appversal.appstorys.presentation.Modal(
            modifier = modifier,
        )
    }

    @Composable
    fun Overlay(
        modifier: Modifier = Modifier
    ) = IfInitialized {
        com.appversal.appstorys.presentation.Overlay(
            modifier = modifier,
        )
    }

    @Composable
    fun Pip(
        modifier: Modifier = Modifier,
        topPadding: Dp = 0.dp,
        bottomPadding: Dp = 0.dp,
    ) = IfInitialized {
        com.appversal.appstorys.presentation.Pip(
            modifier = modifier,
            topPadding = topPadding,
            bottomPadding = bottomPadding
        )
    }

    @Composable
    fun Reels(
        modifier: Modifier = Modifier
    ) = IfInitialized {
        com.appversal.appstorys.presentation.Reels(
            modifier = modifier,
        )
    }

    @Composable
    fun ScratchCard(
        modifier: Modifier = Modifier,
    ) = IfInitialized {
        com.appversal.appstorys.presentation.ScratchCard(
            modifier = modifier,
        )
    }

    @Composable
    fun Screen(
        modifier: Modifier = Modifier,
        name: String,
        options: ScreenOptions? = null,
        content: @Composable () -> Unit
    ) = com.appversal.appstorys.presentation.Screen(
        modifier = modifier,
        name = name,
        options = options,
        content = content
    )

    @Composable
    fun Survey(
        modifier: Modifier = Modifier,
    ) = IfInitialized {
        com.appversal.appstorys.presentation.Survey(
            modifier = modifier,
        )
    }

    @Composable
    fun Stories(
        modifier: Modifier = Modifier,
    ) = IfInitialized {
        com.appversal.appstorys.presentation.Stories(
            modifier = modifier,
        )
    }

    @Composable
    fun Tooltip(
        modifier: Modifier = Modifier,
    ) = IfInitialized {
        com.appversal.appstorys.presentation.Tooltip(
            modifier = modifier,
        )
    }

    @Composable
    fun Widget(
        modifier: Modifier = Modifier,
        placeholder: Placeholder? = null,
        position: String? = null
    ) = IfInitialized {
        com.appversal.appstorys.presentation.Widget(
            modifier = modifier,
            placeholder = placeholder,
            position = position
        )
    }

    private suspend fun checkIfInitialized(): Boolean {
        while (sdkState.value == AppStorysSdkState.Initializing) {
            delay(100)
        }
        return !(sdkState.value != AppStorysSdkState.Initialized || State.getAccessToken()
            .isNullOrBlank())
    }

    private suspend fun ifInitialized(block: suspend () -> Unit) {
        if (checkIfInitialized()) {
            block()
        } else {
            log.e("SDK is not initialized. Please initialize the SDK before using its features.")
        }
    }

    @Composable
    private fun IfInitialized(content: @Composable () -> Unit) {
        val sdkState by sdkState.collectAsStateWithLifecycle()
        if (sdkState == AppStorysSdkState.Initialized) {
            content()
        }
    }
}
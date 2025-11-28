package com.appversal.appstorys.domain

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appversal.appstorys.api.CampaignDetails
import com.appversal.appstorys.domain.State.campaigns
import com.appversal.appstorys.domain.State.disabledCampaigns
import com.appversal.appstorys.domain.State.trackedEvents
import com.appversal.appstorys.domain.model.TypedCampaign

@Composable
internal inline fun <reified T : CampaignDetails> rememberCampaign(
    type: String,
    position: String? = null,
): TypedCampaign<T>? {
    val screenName = LocalScreenContext.current?.name
    val campaigns by campaigns.collectAsStateWithLifecycle()
    val trackedEvents by trackedEvents.collectAsStateWithLifecycle()
    val disabledCampaigns by disabledCampaigns.collectAsStateWithLifecycle()

    return remember(screenName, campaigns, trackedEvents, disabledCampaigns) {
        campaigns.firstOrNull {
            !it.id.isNullOrBlank() &&
                    it.screen == screenName
                    && it.details is T
                    && it.campaignType == type
                    && (position.isNullOrBlank() || it.position == position)
                    && (it.triggerEvent.isNullOrBlank() || trackedEvents.contains(it.triggerEvent))
                    && !disabledCampaigns.contains(it.id)
        }?.let {
            TypedCampaign(
                id = it.id.orEmpty(),
                type = it.campaignType.orEmpty(),
                details = it.details as T,
                screen = it.screen.orEmpty(),
                position = it.position,
                triggerEvent = it.triggerEvent,
            )
        }
    }
}
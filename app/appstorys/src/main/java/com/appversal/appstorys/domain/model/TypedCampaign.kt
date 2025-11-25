package com.appversal.appstorys.domain.model

import com.appversal.appstorys.api.CampaignDetails

internal data class TypedCampaign<T : CampaignDetails>(
    val id: String,
    val type: String,
    val details: T,
    val screen: String,
    val position: String?,
    val triggerEvent: String?,
)
package com.appversal.appstorys.domain.usecase

import com.appversal.appstorys.api.ApiService
import com.appversal.appstorys.api.IdentifyPositionsRequest
import com.appversal.appstorys.domain.State.getAccessToken
import timber.log.Timber

/**
 * Identifies widget positions for a given screen
 */
internal suspend fun identifyWidgetPositions(
    screenName: String,
    positionList: List<String>? = null
) {
    val log = Timber.tag("IdentifyWidgetPositions")

    if (positionList.isNullOrEmpty()) {
        log.w("No positions to identify for widgets.")
        return
    }

    val accessToken = getAccessToken()
    if (accessToken.isNullOrBlank()) {
        log.e("Error in identify widget position. Access token not found")
        return
    }

    try {
        ApiService.getInstance().identifyPositions(
            accessToken,
            IdentifyPositionsRequest(screenName, positionList)
        )
        log.d("Successfully identified positions for screen: $screenName")
    } catch (e: Exception) {
        log.e(e, "Something went wrong in identify widget position: ${e.message}")
    }
}
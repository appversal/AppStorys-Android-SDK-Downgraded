package com.appversal.appstorys.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a queued HTTP request for offline support
 */
@Serializable
internal data class QueuedRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val body: String? = null,
    val retryCount: Int = 0,
    val lastAttempt: Long = 0L,
    val authRetried: Boolean = false
)


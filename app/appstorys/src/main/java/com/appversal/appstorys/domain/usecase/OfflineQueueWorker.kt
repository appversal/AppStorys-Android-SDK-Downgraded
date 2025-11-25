package com.appversal.appstorys.domain.usecase

import android.content.Context
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.appversal.appstorys.api.ApiService
import com.appversal.appstorys.domain.State
import com.appversal.appstorys.domain.State.getAccessToken
import com.appversal.appstorys.domain.model.QueuedRequest
import com.appversal.appstorys.utils.SdkJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber
import kotlin.math.min
import kotlin.math.pow

/**
 * WorkManager worker to flush the offline request queue
 */
internal class OfflineQueueWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            flushQueue()
            Result.success()
        } catch (e: Exception) {
            log.e(e, "Error flushing queue: ${e.message}")
            Result.retry()
        }
    }

    private suspend fun flushQueue() {
        val queue = getQueue(applicationContext)
        if (queue.isEmpty()) return

        log.d("🚀 Flushing ${queue.size} queued requests...")

        val newQueue = mutableListOf<QueuedRequest>()

        for (req in queue) {
            // Check if request should be retried based on backoff policy
            if (!shouldRetryRequest(req)) {
                log.d("⏰ Skipping request (backoff): ${req.url}")
                newQueue.add(req)
                continue
            }

            try {
                val result = executeRequest(req)

                when {
                    result.isSuccess -> {
                        log.d("✅ Successfully resent: ${req.url}")
                        // Don't add to newQueue - request succeeded
                    }

                    result.statusCode in 401..403 -> {
                        // Authentication/Authorization errors
                        if (!req.authRetried) {
                            handleAuthError(req, newQueue)
                        } else {
                            log.d("❌ Auth retry failed, discarding request: ${req.url}")
                            // Already tried auth refresh, discard
                        }
                    }

                    result.statusCode != null && result.statusCode >= 500 -> {
                        // Server errors - apply backoff policy
                        handleCriticalError(req, result.statusCode, null, newQueue)
                    }

                    result.statusCode in 400..499 -> {
                        // Other client errors (4xx) - discard immediately
                        log.d("❌ Client error (${result.statusCode}), discarding request: ${req.url}")
                        // Don't add to newQueue - discard client errors
                    }

                    else -> {
                        // Network or other errors
                        handleCriticalError(req, null, result.error, newQueue)
                    }
                }
            } catch (err: Exception) {
                // Network errors - treat as temporary, keep in queue with backoff
                handleCriticalError(req, null, err, newQueue)
            }
        }

        saveQueue(applicationContext, newQueue)
    }

    private fun executeRequest(request: QueuedRequest): RequestResult {
        return try {
            val requestBody = request.body?.toRequestBody("application/json".toMediaTypeOrNull())
            val httpRequest = Request.Builder()
                .url(request.url)
                .method(request.method, requestBody)
                .apply {
                    request.headers.forEach { (key, value) ->
                        addHeader(key, value)
                    }
                }
                .build()

            val response = ApiService.getClient().newCall(httpRequest).execute()
            RequestResult(
                isSuccess = response.isSuccessful,
                statusCode = response.code,
                error = null
            )
        } catch (e: Exception) {
            log.e(e, "Network error for ${request.url}: ${e.message}")
            RequestResult(
                isSuccess = false,
                statusCode = null,
                error = e
            )
        }
    }

    private suspend fun handleAuthError(
        request: QueuedRequest,
        newQueue: MutableList<QueuedRequest>
    ) {
        log.d("🔐 Auth error, validating credentials: ${request.url}")
        val credentialsValid = validateCredentials()

        if (credentialsValid) {
            // Mark as auth retried and keep in queue for retry
            val updatedRequest = request.copy(
                authRetried = true,
                lastAttempt = System.currentTimeMillis()
            )
            newQueue.add(updatedRequest)
            log.d("🔄 Credentials refreshed, will retry: ${request.url}")
        } else {
            log.d("❌ Credentials invalid, discarding request: ${request.url}")
            // Don't add to newQueue - discard the request
        }
    }

    private fun handleCriticalError(
        request: QueuedRequest,
        statusCode: Int?,
        error: Exception?,
        newQueue: MutableList<QueuedRequest>
    ) {
        val retryCount = request.retryCount + 1

        if (retryCount <= MAX_RETRIES) {
            val updatedRequest = request.copy(
                retryCount = retryCount,
                lastAttempt = System.currentTimeMillis()
            )
            newQueue.add(updatedRequest)

            if (statusCode != null) {
                log.d(
                    "🔄 Server error ($statusCode), retry $retryCount/$MAX_RETRIES with backoff: ${request.url}"
                )
            } else if (error != null) {
                log.e(
                    error,
                    "⚠️ Network error, retry $retryCount/$MAX_RETRIES with backoff: ${request.url}",
                )
            }
        } else {
            if (statusCode != null) {
                log.d("❌ Max retries exceeded for server error, discarding: ${request.url}")
            } else if (error != null) {
                log.e(error, "❌ Max network retries exceeded, discarding: ${request.url}")
            }
            // Don't add to newQueue - discard after max retries
        }
    }

    private suspend fun validateCredentials(): Boolean {
        val accountId = State.accountId.value
        val appId = State.appId.value

        if (accountId.isBlank() || appId.isBlank()) {
            log.d("❌ Missing accountId or appId for credential validation.")
            return false
        }

        val success = verifyAccount(accountId, appId)
        if (success) {
            log.d("🔐 Validating credentials...")
        }
        return success
    }

    private fun getBackoffDelay(retryCount: Int): Long {
        // Exponential backoff: 30s, 1min, 2min (max 2min)
        return min(30000L * 2.0.pow(retryCount).toLong(), 120000L)
    }

    private fun shouldRetryRequest(request: QueuedRequest): Boolean {
        val retryCount = request.retryCount
        val lastAttempt = request.lastAttempt
        val backoffDelay = getBackoffDelay(retryCount)

        return retryCount < MAX_RETRIES && System.currentTimeMillis() - lastAttempt >= backoffDelay
    }

    private data class RequestResult(
        val isSuccess: Boolean,
        val statusCode: Int?,
        val error: Exception?
    )

    companion object {
        private const val MAX_RETRIES = 3

        private const val OFFLINE_QUEUE_KEY = "offline_queue"

        private val client = OkHttpClient()

        private val log: Timber.Tree
            get() = Timber.tag("OfflineQueueWorker")

        suspend operator fun invoke(
            context: Context,
            request: QueuedRequest
        ): Response? = withContext(Dispatchers.IO) {
            // Check network connectivity
            if (!isNetworkAvailable(context)) {
                addToQueue(context, request)
                return@withContext null
            }

            // First flush old requests before sending this one
            WorkManager.getInstance(context).enqueueUniqueWork(
                OfflineQueueWorker::class.simpleName!!,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<OfflineQueueWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()
            )

            try {
                val requestBody = request.body?.toRequestBody(
                    "application/json".toMediaTypeOrNull()
                )
                val httpRequest = Request.Builder()
                    .url(request.url)
                    .method(request.method, requestBody)
                    .apply {
                        request.headers.forEach { (key, value) ->
                            addHeader(
                                key,
                                value = when {
                                    key.equals("Authorization", ignoreCase = true) ->
                                        getAccessToken() ?: value

                                    else -> value
                                }
                            )
                        }
                    }
                    .build()

                val response = client.newCall(httpRequest).execute()

                when {
                    response.isSuccessful -> {
                        log.d("✅ Request sent successfully: ${request.url}")
                        response
                    }

                    response.code in 401..403 -> {
                        // Auth errors - queue with auth retry flag
                        handleAuthError(context, request)
                        null
                    }

                    response.code >= 500 -> {
                        // Server errors - queue with backoff policy
                        handleCriticalError(context, request, response.code, null)
                        null
                    }

                    else -> {
                        // Other client errors (4xx) - don't queue
                        log.d("❌ Client error (${response.code}), not queuing: ${request.url}")
                        null
                    }
                }
            } catch (err: Exception) {
                // Network errors - queue with backoff policy
                handleCriticalError(context, request, null, err)
                null
            }
        }


        /**
         * Get all queued requests from encrypted storage
         */
        private suspend fun getQueue(context: Context): List<QueuedRequest> =
            withContext(Dispatchers.IO) {
                try {
                    State.initialize(context)
                    val queueJson: String? = State.prefs.getString(OFFLINE_QUEUE_KEY, null)
                    if (queueJson.isNullOrBlank()) {
                        emptyList()
                    } else {
                        SdkJson.decodeFromString<List<QueuedRequest>>(queueJson)
                    }
                } catch (e: Exception) {
                    log.e(e, "Error reading queue: ${e.message}")
                    emptyList()
                }
            }

        /**
         * Save queued requests to encrypted storage
         */
        private suspend fun saveQueue(context: Context, queue: List<QueuedRequest>): Unit =
            withContext(Dispatchers.IO) {
                try {
                    State.initialize(context)
                    val queueJson = SdkJson.encodeToString(queue)
                    State.prefs.edit {
                        putString(OFFLINE_QUEUE_KEY, queueJson)
                    }
                } catch (e: Exception) {
                    log.e(e, "Error saving queue: ${e.message}")
                }
            }

        /**
         * Add a request to the queue
         */
        private suspend fun addToQueue(context: Context, request: QueuedRequest) {
            saveQueue(
                context,
                buildList {
                    addAll(getQueue(context))
                    add(request)
                }
            )
            log.d("📦 Request saved offline: ${request.url}")
        }

        /**
         * Clear the entire queue
         */
        private suspend fun clearQueue(context: Context) {
            saveQueue(context, emptyList())
        }

        private suspend fun handleAuthError(context: Context, request: QueuedRequest) {
            if (!request.authRetried) {
                val updatedRequest = request.copy(
                    authRetried = true,
                    lastAttempt = System.currentTimeMillis()
                )
                addToQueue(context, updatedRequest)
                log.d("🔐 Auth error, queuing for retry: ${request.url}")
            } else {
                log.d("❌ Auth retry already attempted, not queuing: ${request.url}")
            }
        }

        private suspend fun handleCriticalError(
            context: Context,
            request: QueuedRequest,
            statusCode: Int?,
            error: Exception?
        ) {
            val retryCount = request.retryCount + 1

            if (retryCount <= 3) {
                val updatedRequest = request.copy(
                    retryCount = retryCount,
                    lastAttempt = System.currentTimeMillis()
                )
                addToQueue(context, updatedRequest)

                if (statusCode != null) {
                    log.d("🔄 Server error ($statusCode), queuing retry $retryCount/3: ${request.url}")
                } else if (error != null) {
                    log.e(
                        error,
                        "⚠️ Network error, queuing retry $retryCount/3: ${request.url}",
                    )
                }
            } else {
                if (statusCode != null) {
                    log.d("❌ Max retries exceeded for server error, not queuing: ${request.url}")
                } else if (error != null) {
                    log.e(
                        error,
                        "❌ Max network retries exceeded, not queuing: ${request.url}",
                    )
                }
            }
        }
    }
}

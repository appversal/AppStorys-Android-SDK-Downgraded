package com.appversal.appstorys.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Canonical states from the SDK Error-Tracking State Machine spec.
 *
 * The [code] is what travels in the `from_state` / `to_state` metadata fields.
 */
internal enum class SdkState(val code: String) {
    INIT("S0"),
    AUTHENTICATING("S1"),
    AUTHENTICATED("S2"),
    FETCHING("S3"),
    CAMPAIGN_AVAILABLE("S4"),
    RENDERING("S5"),
    WAITING_TRIGGER("S6"),
    RENDERED("S7"),
    ENGAGING("S8"),
    DONE("S9"),
    IDLE("S10");

    companion object {
        fun fromCode(code: String?): SdkState? = values().firstOrNull { it.code == code }
    }
}

/**
 * Failure buckets from section 4 of the spec.
 *
 * P0 (auth broken) is deliberately **never transmitted** — see [SdkErrorTracker.report].
 */
internal enum class SdkFailureClass(val code: String) {
    P0("P0"),
    P1("P1"),
    P2("P2"),
    P3("P3")
}

/**
 * Client-side error/state tracking for the campaign delivery flow.
 *
 * Design rules, in priority order:
 *
 *  1. **This must never affect the host app.** Every public entry point is wrapped in a
 *     catch-all. If anything in here throws, it is swallowed and the SDK continues exactly
 *     as it did before. In the worst case the reporting simply does not happen.
 *  2. **No behaviour is changed anywhere else.** Nothing in this file mutates campaign
 *     state, cancels work, retries a business call, or short-circuits a render.
 *  3. **Never recursive.** A failure of the error endpoint itself is only logged locally.
 *
 * State machines are scoped **per screen** (plus a child machine per campaign for S4..S9),
 * because the SDK re-runs fetch/render for every screen the host navigates to while a single
 * auth token is shared across all of them.
 */
internal object SdkErrorTracker {

    /** Version stamped into every error payload as `metadata.sdk_version`. */
    const val SDK_VERSION = "5.0.1"

    private const val TAG = "AppStorysErrorTracker"
    private const val COMPONENT = "sdk.state_machine"
    private const val ENDPOINT = "https://tracking.appstorys.com/capture-error-log"
    private const val SDK_PACKAGE = "com.appversal.appstorys"

    private const val PREFS = "appstorys_error_tracker"
    private const val KEY_PENDING = "pending_events"
    private const val KEY_MACHINES = "machines"
    private const val KEY_SESSION = "session_id"

    private const val MAX_PENDING = 200
    private const val MAX_ATTEMPTS = 3
    private const val DEBOUNCE_MS = 2_000L
    private const val STUCK_TTL_MS = 24L * 60L * 60L * 1000L

    // ---------------------------------------------------------------------------------------
    // Wiring
    // ---------------------------------------------------------------------------------------

    @Volatile
    private var enabled: Boolean = true

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var userIdProvider: (() -> String)? = null

    @Volatile
    private var tokenProvider: (() -> String)? = null

    @Volatile
    private var sessionId: String = UUID.randomUUID().toString()

    @Volatile
    private var crashReporterInstalled = false

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, t ->
            Log.w(TAG, "error tracker coroutine failed: ${t.message}")
        }
    )

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /** screen -> current state of that screen's machine */
    private val screenStates = ConcurrentHashMap<String, String>()

    /** campaignId -> current state of that campaign's machine */
    private val campaignStates = ConcurrentHashMap<String, String>()

    /** campaignId -> screen it belongs to */
    private val campaignScreens = ConcurrentHashMap<String, String>()

    /**
     * campaignType -> campaignId for the current screen. The SDK renders at most one campaign of
     * each type per screen, so this lets deep UI callbacks (image/video load errors) attribute a
     * failure without threading a campaign id through every composable signature.
     */
    private val campaignByType = ConcurrentHashMap<String, String>()

    private val queueLock = Mutex()
    private val batchBuffer = mutableListOf<JSONObject>()
    private var batchJob: Job? = null

    // ---------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------

    fun initialize(
        context: Context,
        userIdProvider: () -> String,
        tokenProvider: () -> String
    ) = safe {
        this.appContext = context.applicationContext
        this.userIdProvider = userIdProvider
        this.tokenProvider = tokenProvider
        this.sessionId = UUID.randomUUID().toString()
        prefs()?.edit()?.putString(KEY_SESSION, sessionId)?.apply()
    }

    /** Host-app kill switch. When disabled nothing is buffered, persisted or sent. */
    fun setEnabled(value: Boolean) = safe {
        enabled = value
        if (!value) {
            synchronized(batchBuffer) { batchBuffer.clear() }
            batchJob?.cancel()
            batchJob = null
        }
    }

    fun isEnabled(): Boolean = enabled

    fun currentSessionId(): String = sessionId

    /**
     * Called when the process returns to the foreground. Starts a fresh session id, because the
     * SDK already tears down campaigns/triggers on `onStop`, so the previous machines are dead.
     */
    fun onSessionForeground() = safe {
        reconcileInternal()
        sessionId = UUID.randomUUID().toString()
        screenStates.clear()
        campaignStates.clear()
        campaignScreens.clear()
        campaignByType.clear()
        prefs()?.edit()?.putString(KEY_SESSION, sessionId)?.apply()
        persistMachines()
        flushPending()
    }

    // ---------------------------------------------------------------------------------------
    // Transitions — screen scoped
    // ---------------------------------------------------------------------------------------

    /** S2 -> S3: a campaign fetch for [screen] has started. */
    fun onFetchStarted(screen: String) = safe {
        campaignByType.clear()
        screenStates[screen] = SdkState.FETCHING.code
        persistMachines()
    }

    /** Records which campaign id is currently in play for a given campaign type. */
    fun registerCampaign(campaignType: String?, campaignId: String?, screen: String?) = safe {
        if (campaignType.isNullOrBlank() || campaignId.isNullOrBlank()) return@safe
        campaignByType[campaignType] = campaignId
        campaignStates[campaignId] = SdkState.CAMPAIGN_AVAILABLE.code
        screen?.let { campaignScreens[campaignId] = it }
    }

    private fun resolveCampaignId(campaignId: String?, campaignType: String?): String? =
        campaignId ?: campaignType?.let { campaignByType[it] }

    /** S3 -> S4 (non-empty) or S3 -> S10 (empty result — a normal terminal state). */
    fun onFetchCompleted(screen: String, campaignCount: Int) = safe {
        screenStates[screen] =
            if (campaignCount > 0) SdkState.CAMPAIGN_AVAILABLE.code else SdkState.IDLE.code
        persistMachines()
    }

    /** S3 -> failure. */
    fun onFetchFailed(
        screen: String,
        step: String,
        actual: String,
        message: String,
        httpStatus: Int? = null,
        latencyMs: Long? = null,
        retryable: Boolean = true
    ) = safe {
        report(
            failureClass = SdkFailureClass.P1,
            step = step,
            fromState = SdkState.FETCHING,
            toState = SdkState.CAMPAIGN_AVAILABLE,
            actual = actual,
            message = message,
            screen = screen,
            httpStatus = httpStatus,
            latencyMs = latencyMs,
            retryable = retryable
        )
    }

    // ---------------------------------------------------------------------------------------
    // Transitions — campaign scoped
    // ---------------------------------------------------------------------------------------

    /** S4 -> S6: a triggered campaign armed its listener. Never a failure on its own. */
    fun onTriggerArmed(campaignId: String?, screen: String?) = safe {
        val id = campaignId ?: return@safe
        campaignStates[id] = SdkState.WAITING_TRIGGER.code
        screen?.let { campaignScreens[id] = it }
        persistMachines()
    }

    /** S4 -> S6 failed: the listener could not be registered. */
    fun onTriggerRegistrationFailed(
        campaignId: String?,
        campaignType: String?,
        screen: String?,
        message: String
    ) = safe {
        report(
            failureClass = SdkFailureClass.P2,
            step = "trigger-registration",
            fromState = SdkState.CAMPAIGN_AVAILABLE,
            toState = SdkState.WAITING_TRIGGER,
            actual = "listener_dropped",
            message = message,
            campaignId = campaignId,
            campaignType = campaignType,
            screen = screen
        )
    }

    /** S4/S6 -> S5: the campaign started rendering. */
    fun onRenderStarted(campaignId: String?, campaignType: String?, screen: String?) = safe {
        val id = campaignId ?: return@safe
        campaignStates[id] = SdkState.RENDERING.code
        screen?.let { campaignScreens[id] = it }
        persistMachines()
    }

    /** S5 -> S7: the campaign is on screen. */
    fun onRendered(campaignId: String?, campaignType: String?, screen: String?) = safe {
        val id = campaignId ?: return@safe
        campaignStates[id] = SdkState.RENDERED.code
        screen?.let { campaignScreens[id] = it }
        persistMachines()
    }

    /** S5 -> failure: render threw, or the view never surfaced. */
    fun onRenderFailed(
        campaignId: String?,
        campaignType: String?,
        screen: String?,
        message: String,
        throwable: Throwable? = null,
        actual: String = "render_exception"
    ) = safe {
        val extra = LinkedHashMap<String, String>()
        throwable?.let {
            extra["exception"] = it::class.java.name
            it.stackTrace.firstOrNull()?.let { frame -> extra["at"] = frame.toString() }
        }
        report(
            failureClass = SdkFailureClass.P2,
            step = "render",
            fromState = SdkState.RENDERING,
            toState = SdkState.RENDERED,
            actual = actual,
            message = message,
            campaignId = resolveCampaignId(campaignId, campaignType),
            campaignType = campaignType,
            screen = screen,
            retryable = false,
            extra = extra
        )
    }

    /** Asset (image / video) that belongs to a rendered campaign failed to load. */
    fun onAssetFailed(
        campaignId: String?,
        campaignType: String?,
        screen: String?,
        assetType: String,
        url: String?,
        message: String
    ) = safe {
        val extra = LinkedHashMap<String, String>()
        extra["asset_type"] = assetType
        url?.let { extra["asset_url"] = it }
        report(
            failureClass = SdkFailureClass.P2,
            step = "asset-load",
            fromState = SdkState.RENDERING,
            toState = SdkState.RENDERED,
            actual = "asset_load_failed",
            message = message,
            campaignId = resolveCampaignId(campaignId, campaignType),
            campaignType = campaignType,
            screen = screen,
            retryable = false,
            extra = extra
        )
    }

    /** S7 -> S8: an engagement event is in flight. */
    fun onEngageStarted(campaignId: String?) = safe {
        val id = campaignId ?: return@safe
        campaignStates[id] = SdkState.ENGAGING.code
        persistMachines()
    }

    /** S8 -> S9: engagement acknowledged. */
    fun onEngageAcked(campaignId: String?) = safe {
        val id = campaignId ?: return@safe
        campaignStates[id] = SdkState.DONE.code
        persistMachines()
    }

    /** S8 -> failure: telemetry loss. */
    fun onEngageFailed(
        campaignId: String?,
        step: String,
        actual: String,
        message: String,
        httpStatus: Int? = null,
        latencyMs: Long? = null,
        retryable: Boolean = true,
        screen: String? = null
    ) = safe {
        report(
            failureClass = SdkFailureClass.P3,
            step = step,
            fromState = SdkState.ENGAGING,
            toState = SdkState.DONE,
            actual = actual,
            message = message,
            campaignId = campaignId,
            screen = screen,
            httpStatus = httpStatus,
            latencyMs = latencyMs,
            retryable = retryable
        )
    }

    // ---------------------------------------------------------------------------------------
    // Generic API failure hook (used for the auxiliary endpoints)
    // ---------------------------------------------------------------------------------------

    fun onApiFailed(
        step: String,
        failureClass: SdkFailureClass,
        message: String,
        actual: String = "error",
        campaignId: String? = null,
        screen: String? = null,
        httpStatus: Int? = null,
        latencyMs: Long? = null,
        retryable: Boolean = true,
        fromState: SdkState? = null,
        toState: SdkState? = null
    ) = safe {
        report(
            failureClass = failureClass,
            step = step,
            fromState = fromState,
            toState = toState,
            actual = actual,
            message = message,
            campaignId = campaignId,
            screen = screen,
            httpStatus = httpStatus,
            latencyMs = latencyMs,
            retryable = retryable
        )
    }

    /** Any caught exception in SDK logic that is not tied to a specific transition. */
    fun onLogicError(
        step: String,
        message: String,
        throwable: Throwable? = null,
        campaignId: String? = null,
        campaignType: String? = null,
        screen: String? = null,
        failureClass: SdkFailureClass = SdkFailureClass.P2
    ) = safe {
        val extra = LinkedHashMap<String, String>()
        throwable?.let {
            extra["exception"] = it::class.java.name
            it.stackTrace.firstOrNull()?.let { frame -> extra["at"] = frame.toString() }
        }
        report(
            failureClass = failureClass,
            step = step,
            fromState = null,
            toState = null,
            actual = "exception",
            message = message,
            campaignId = campaignId,
            campaignType = campaignType,
            screen = screen,
            retryable = false,
            extra = extra
        )
    }

    // ---------------------------------------------------------------------------------------
    // Uncaught exceptions (composition crashes)
    // ---------------------------------------------------------------------------------------

    /**
     * Reports uncaught exceptions whose stack touches the SDK, then hands off to whatever handler
     * was already installed.
     *
     * This exists because the Compose compiler forbids `try`/`catch` around composable
     * invocations, so a render failure cannot be caught from inside composition. A chained
     * default handler is the only way to observe it.
     *
     * **The crash is never swallowed.** The previous handler is always invoked with the original
     * thread and throwable, so the host app's crash behaviour — including any Crashlytics or
     * Sentry handler already installed — is unchanged. If no previous handler exists, this
     * installs nothing at all rather than becoming the terminal handler itself.
     *
     * Call this *after* the host app has installed its own crash reporting.
     */
    fun installCrashReporter() = safe {
        if (crashReporterInstalled) return@safe
        val previous = Thread.getDefaultUncaughtExceptionHandler() ?: return@safe
        crashReporterInstalled = true
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                if (enabled && involvesSdk(throwable)) persistCrash(throwable)
            } catch (_: Throwable) {
                // A failure here must never stop the crash from reaching the real handler.
            }
            previous.uncaughtException(thread, throwable)
        }
    }

    /** True when the throwable, or anything in its cause chain, has an SDK frame. */
    private fun involvesSdk(t: Throwable?): Boolean {
        var current = t
        var depth = 0
        while (current != null && depth < 10) {
            if (current.stackTrace.any { it.className.startsWith(SDK_PACKAGE) }) return true
            current = current.cause
            depth++
        }
        return false
    }

    /**
     * Writes a P2 render failure straight to the offline queue, synchronously. The process is
     * about to die, so there is no time to make a network call — the event goes out on the next
     * launch via [flushPending].
     */
    private fun persistCrash(t: Throwable) {
        val userId = runCatching { userIdProvider?.invoke() }.getOrNull().orEmpty()
        if (userId.isBlank()) return

        val frame = t.stackTrace.firstOrNull { it.className.startsWith(SDK_PACKAGE) }

        val metadata = JSONObject().apply {
            put("session_id", sessionId)
            put("sdk_version", SDK_VERSION)
            put("platform", "android")
            put("app_version", appVersion())
            put("from_state", SdkState.RENDERING.code)
            put("to_state", SdkState.RENDERED.code)
            put("expected", "success")
            put("actual", "uncaught_exception")
            put("failure_class", SdkFailureClass.P2.code)
            put("step", "render")
            put("http_status", "0")
            put("attempt", "1")
            put("retryable", "false")
            put("exception", t::class.java.name)
            frame?.let { put("at", it.toString()) }
        }

        val payload = JSONObject().apply {
            put("user_id", userId)
            put("component", COMPONENT)
            put("error", t.message ?: t::class.java.simpleName)
            put("metadata", metadata)
        }

        persist(payload, immediate = true)
    }

    // ---------------------------------------------------------------------------------------
    // Reconciliation (section 6)
    // ---------------------------------------------------------------------------------------

    /**
     * Called on SDK start. Any machine left mid-flow by a previous process is reported as
     * `actual = "abandoned"`.
     *
     * The spec's "re-run the step once" is already satisfied by the SDK's normal boot sequence
     * (validate-account -> track-user-res runs again anyway), so nothing is re-issued here — that
     * would add network calls the SDK does not make today.
     *
     * `S6` (waiting trigger) is dropped silently: a trigger that never fired is an acceptable
     * terminal state, not a failure.
     */
    fun reconcile() = safe { reconcileInternal() }

    private fun reconcileInternal() {
        val store = prefs() ?: return
        val raw = store.getString(KEY_MACHINES, null) ?: return
        store.edit().remove(KEY_MACHINES).apply()

        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val previousSession = root.optString("session_id", "")
        val savedAt = root.optLong("ts", 0L)
        val age = System.currentTimeMillis() - savedAt
        val entries = root.optJSONArray("entries") ?: return

        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            val stateCode = entry.optString("state", "")
            val state = SdkState.fromCode(stateCode) ?: continue

            // Acceptable terminal states — never flagged.
            if (state == SdkState.WAITING_TRIGGER ||
                state == SdkState.DONE ||
                state == SdkState.IDLE ||
                state == SdkState.INIT
            ) continue

            val inFlight = state == SdkState.AUTHENTICATING ||
                    state == SdkState.FETCHING ||
                    state == SdkState.ENGAGING

            // Non in-flight states are only flagged once they are older than the TTL.
            if (!inFlight && age < STUCK_TTL_MS) continue

            val campaignId = entry.optString("campaign_id", "").ifBlank { null }
            val screen = entry.optString("screen", "").ifBlank { null }

            val bucket = when (state) {
                // P0 is dropped downstream anyway; kept for completeness.
                SdkState.AUTHENTICATING -> SdkFailureClass.P0
                SdkState.FETCHING, SdkState.CAMPAIGN_AVAILABLE -> SdkFailureClass.P1
                SdkState.ENGAGING -> SdkFailureClass.P3
                else -> SdkFailureClass.P2
            }

            report(
                failureClass = bucket,
                step = "reconcile",
                fromState = state,
                toState = null,
                actual = "abandoned",
                message = "session left in state ${state.code} (${state.name})",
                campaignId = campaignId,
                screen = screen,
                retryable = false,
                extra = linkedMapOf(
                    "abandoned_session_id" to previousSession,
                    "age_ms" to age.toString()
                )
            )
        }
    }

    private fun persistMachines() {
        val store = prefs() ?: return
        runCatching {
            val entries = JSONArray()
            screenStates.forEach { (screen, state) ->
                entries.put(
                    JSONObject()
                        .put("screen", screen)
                        .put("state", state)
                )
            }
            campaignStates.forEach { (campaignId, state) ->
                entries.put(
                    JSONObject()
                        .put("campaign_id", campaignId)
                        .put("screen", campaignScreens[campaignId] ?: "")
                        .put("state", state)
                )
            }
            val root = JSONObject()
                .put("session_id", sessionId)
                .put("ts", System.currentTimeMillis())
                .put("entries", entries)
            store.edit().putString(KEY_MACHINES, root.toString()).apply()
        }
    }

    // ---------------------------------------------------------------------------------------
    // Reporting
    // ---------------------------------------------------------------------------------------

    private fun report(
        failureClass: SdkFailureClass,
        step: String,
        fromState: SdkState?,
        toState: SdkState?,
        actual: String,
        message: String,
        campaignId: String? = null,
        campaignType: String? = null,
        screen: String? = null,
        httpStatus: Int? = null,
        latencyMs: Long? = null,
        retryable: Boolean = false,
        attempt: Int = 1,
        extra: Map<String, String>? = null
    ) {
        if (!enabled) return

        // Auth failures are intentionally not reported: the error endpoint itself needs a valid
        // JWT, and by definition we do not have one when auth is what broke.
        if (failureClass == SdkFailureClass.P0 || step == "validate-account") return

        val userId = runCatching { userIdProvider?.invoke() }.getOrNull().orEmpty()
        if (userId.isBlank()) return

        val metadata = JSONObject().apply {
            put("session_id", sessionId)
            put("sdk_version", SDK_VERSION)
            put("platform", "android")
            put("app_version", appVersion())
            fromState?.let { put("from_state", it.code) }
            toState?.let { put("to_state", it.code) }
            put("expected", "success")
            put("actual", actual)
            put("failure_class", failureClass.code)
            put("step", step)
            put("http_status", (httpStatus ?: 0).toString())
            latencyMs?.let { put("latency_ms", it.toString()) }
            put("attempt", attempt.toString())
            put("retryable", retryable.toString())
            screen?.let { put("screen", it) }
            campaignType?.let { put("campaign_type", it) }
            extra?.forEach { (k, v) -> put(k, v) }
        }

        val payload = JSONObject().apply {
            put("user_id", userId)
            campaignId?.let { put("campaign_id", it) }
            put("component", COMPONENT)
            put("error", message)
            put("metadata", metadata)
        }

        // P1 goes out immediately; P2/P3 are debounced so an offline burst does not hammer the
        // network. Batching only controls cadence — nothing is ever dropped.
        if (failureClass == SdkFailureClass.P1) {
            scope.launch { deliver(payload, retryable) }
        } else {
            enqueueBatched(payload, retryable)
        }
    }

    private fun enqueueBatched(payload: JSONObject, retryable: Boolean) {
        synchronized(batchBuffer) { batchBuffer.add(payload) }
        if (batchJob?.isActive == true) return
        batchJob = scope.launch {
            delay(DEBOUNCE_MS)
            val drained = synchronized(batchBuffer) {
                val copy = batchBuffer.toList()
                batchBuffer.clear()
                copy
            }
            drained.forEach { deliver(it, retryable) }
        }
    }

    private suspend fun deliver(payload: JSONObject, retryable: Boolean) {
        val token = runCatching { tokenProvider?.invoke() }.getOrNull().orEmpty()
        if (token.isBlank()) {
            persist(payload)
            return
        }

        var backoff = 1_000L
        val maxAttempts = if (retryable) MAX_ATTEMPTS else 1

        for (attempt in 1..maxAttempts) {
            val sent = send(payload, token)
            if (sent) return
            if (attempt < maxAttempts) {
                delay(backoff)
                backoff *= 2
            }
        }
        // Offline / rejected: keep it on disk and flush on the next SDK activity. No loss.
        persist(payload)
    }

    /**
     * Fires the request. Never reports its own failure — that is the recursion guard.
     */
    private fun send(payload: JSONObject, token: String): Boolean {
        return try {
            val request = Request.Builder()
                .url(ENDPOINT)
                .post(payload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                // 204 is the documented success. Any 2xx is accepted.
                // 4xx (other than 401/403) means the payload will never be accepted — do not retry.
                when {
                    response.isSuccessful -> {
                        Log.d(TAG, "capture-error-log ${response.code} ← ${payload}")
                        true
                    }

                    response.code in 400..499 && response.code != 401 && response.code != 403 -> {
                        Log.w(
                            TAG,
                            "capture-error-log rejected payload: ${response.code} ← ${payload}"
                        )
                        true
                    }

                    else -> {
                        Log.w(TAG, "capture-error-log failed: ${response.code} ← Token: $token ← ${payload}")
                        false
                    }
                }
            }
        } catch (t: Throwable) {
            // Local log only. Reporting a reporting failure would recurse.
            Log.w(TAG, "capture-error-log delivery failed: ${t.message}")
            false
        }
    }

    // ---------------------------------------------------------------------------------------
    // Offline queue (bounded ring in SharedPreferences)
    // ---------------------------------------------------------------------------------------

    /**
     * @param immediate when true the write is committed synchronously. Required when the process
     *   is about to die (an uncaught exception), because `apply()` would never reach disk.
     */
    private fun persist(payload: JSONObject, immediate: Boolean = false) = safe {
        val store = prefs() ?: return@safe
        synchronized(this) {
            val existing = runCatching {
                JSONArray(store.getString(KEY_PENDING, "[]"))
            }.getOrDefault(JSONArray())

            val trimmed = JSONArray()
            // Drop the oldest entries once the ring is full.
            val start = maxOf(0, existing.length() - (MAX_PENDING - 1))
            for (i in start until existing.length()) {
                existing.optJSONObject(i)?.let { trimmed.put(it) }
            }
            trimmed.put(payload)

            val editor = store.edit().putString(KEY_PENDING, trimmed.toString())
            if (immediate) editor.commit() else editor.apply()
        }
    }

    /** Drains the offline queue. Safe to call as often as you like. */
    fun flushPending() = safe {
        if (!enabled) return@safe
        scope.launch {
            queueLock.withLock {
                val store = prefs() ?: return@withLock
                val token = runCatching { tokenProvider?.invoke() }.getOrNull().orEmpty()
                if (token.isBlank()) return@withLock

                val queued = runCatching {
                    JSONArray(store.getString(KEY_PENDING, "[]"))
                }.getOrDefault(JSONArray())
                if (queued.length() == 0) return@withLock

                store.edit().remove(KEY_PENDING).apply()

                val failed = JSONArray()
                for (i in 0 until queued.length()) {
                    val item = queued.optJSONObject(i) ?: continue
                    if (!send(item, token)) failed.put(item)
                }
                if (failed.length() > 0) {
                    store.edit().putString(KEY_PENDING, failed.toString()).apply()
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private fun prefs() = try {
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    } catch (t: Throwable) {
        null
    }

    private fun appVersion(): String = try {
        val ctx = appContext
        if (ctx == null) "" else {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
        }
    } catch (t: Throwable) {
        ""
    }

    /**
     * Catch-all wrapper. Nothing this object does is allowed to propagate into the host app.
     */
    private inline fun safe(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.w(TAG, "error tracker no-op: ${t.message}")
        }
    }
}
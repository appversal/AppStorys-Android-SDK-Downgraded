package com.appversal.appstorys.utils

import androidx.compose.runtime.Composable

/**
 * Marks an SDK render call for error attribution.
 *
 * ### Why this is a passthrough
 *
 * The Compose compiler rejects `try`/`catch` around composable invocations
 * ("Try catch is not supported around composable function invocations"), so a failure raised
 * during composition cannot be caught here — or anywhere else inside composition. Those failures
 * are reported instead by [SdkErrorTracker.installCrashReporter], which chains the default
 * uncaught-exception handler and always delegates to the handler that was already installed.
 *
 * This function is kept because it is `inline` and therefore free at runtime, it marks every
 * campaign render site for future instrumentation that *is* legal inside composition (side
 * effects, state observation), and removing it would mean editing 18 call sites for no gain.
 *
 * It does not alter composition in any way: non-local `return` from [content] still returns from
 * the calling composable, exactly as it did before this wrapper existed.
 */
@Composable
internal inline fun SdkErrorBoundary(
    campaignType: String?,
    campaignId: String? = null,
    screen: String? = null,
    content: @Composable () -> Unit
) {
    content()
}

/**
 * Runs [block], reports any exception as an SDK logic error, then re-throws it.
 *
 * Only valid around **non-composable** code, where `try`/`catch` is legal. Use this where an
 * exception would previously have propagated. Where the SDK already has a `catch` that swallows,
 * call [SdkErrorTracker.onLogicError] inside that existing `catch` instead — that keeps the
 * original control flow byte-for-byte identical.
 */
internal inline fun <T> runReported(
    step: String,
    campaignId: String? = null,
    campaignType: String? = null,
    screen: String? = null,
    block: () -> T
): T {
    return try {
        block()
    } catch (t: Throwable) {
        if (t !is kotlin.coroutines.cancellation.CancellationException) {
            SdkErrorTracker.onLogicError(
                step = step,
                message = t.message ?: t::class.java.simpleName,
                throwable = t,
                campaignId = campaignId,
                campaignType = campaignType,
                screen = screen
            )
        }
        throw t
    }
}
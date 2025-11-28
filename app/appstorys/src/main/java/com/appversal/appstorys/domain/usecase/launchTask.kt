package com.appversal.appstorys.domain.usecase

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// A CoroutineScope suitable for sdk-wide background tasks.
// Using SupervisorJob ensures child failures don't cancel the entire scope.
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

internal fun launchTask(block: suspend CoroutineScope.() -> Unit) {
    scope.launch(block = block)
}
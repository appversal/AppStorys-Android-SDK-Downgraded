package com.appversal.appstorys.presentation

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appversal.appstorys.domain.LocalScreenContext
import com.appversal.appstorys.domain.State
import com.appversal.appstorys.domain.usecase.captureScreen
import com.appversal.appstorys.utils.findActivity
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
internal fun CaptureScreenButton(
    modifier: Modifier = Modifier,
    activity: Activity? = null,
) {
    val screenName = LocalScreenContext.current.name

    val isEnabled by State.isCapturingEnabled.map {
        it[screenName] ?: false
    }.collectAsStateWithLifecycle(false)
    val isCapturing by State.isCapturing.map {
        it[screenName] ?: false
    }.collectAsStateWithLifecycle(false)

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val activity = activity ?: LocalContext.current.findActivity()

    if (isEnabled) {
        Box(
            modifier = Modifier.fillMaxSize(),
            content = {
                FloatingActionButton(
                    onClick = {
                        if (activity != null) {
                            scope.launch {
                                captureScreen(
                                    activity = activity,
                                    screenName = screenName
                                )?.let { error ->
                                    snackbarHostState.showSnackbar(message = error)
                                }
                            }
                        }
                    },
                    modifier = modifier
                        .padding(bottom = 86.dp, end = 16.dp)
                        .align(Alignment.BottomEnd),
                    containerColor = Color.White,
                    content = {
                        AnimatedVisibility(
                            visible = isCapturing,
                            content = {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(20.dp),
                                    color = Color.Black,
                                    strokeWidth = 2.dp,
                                )
                            }
                        )
                        Text(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            text = when {
                                activity == null -> "No Activity"
                                isCapturing -> "Capturing..."
                                else -> "Capture Screen"
                            }
                        )
                    }
                )

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp)
                )
            }
        )
    }
}
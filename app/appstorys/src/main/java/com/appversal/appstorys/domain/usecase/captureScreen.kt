package com.appversal.appstorys.domain.usecase

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowMetrics
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.getOrNull
import androidx.core.graphics.createBitmap
import com.appversal.appstorys.api.ApiService
import com.appversal.appstorys.domain.State.getAccessToken
import com.appversal.appstorys.domain.State.isCapturing
import com.appversal.appstorys.domain.State.setIsCapturing
import com.appversal.appstorys.domain.State.userId
import com.appversal.appstorys.utils.SdkJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Field
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

internal var SemanticsPropertyReceiver.appstorysViewTagProperty by AppstorysViewTagKey

/**
 * Take screenshot and identify elements
 */
internal suspend fun captureScreen(
    activity: Activity,
    screenName: String,
    positionList: List<String>? = null
): String? {
    val response = withContext(Dispatchers.Main) {
        if (isCapturing.value[screenName] == true) {
            return@withContext "Screen capture already in progress for screen: $screenName"
        }

        try {
            if (activity.isFinishing || activity.isDestroyed) {
                return@withContext "Activity is not in a valid state for capturing screen."
            }

            val accessToken = getAccessToken()
            val userId = userId.value
            if (accessToken.isNullOrBlank() || userId.isBlank()) {
                return@withContext "Error in capture screen. Access token or user ID not found"
            }

            if (screenName.isBlank()) {
                return@withContext "Screen name is not set. Cannot capture screen."
            }

            // Small delay to ensure UI is settled
            delay(100)

            val rootView = activity.window?.decorView?.rootView
                ?: return@withContext "Root view is null. Cannot capture screen."

            log.d("Starting capture for screen: $screenName")
            setIsCapturing(screenName, true)

            val elements = buildJsonArray {
                getScreenElements(
                    rootView,
                    onElementAnalyzed = {
                        add(it)
                    },
                    activity = activity
                )
            }
            log.d("Captured ${elements.size} elements on screen: $screenName")

            val screenshotFile = getScreenshot(activity, rootView)
                ?: return@withContext "Screenshot capture failed"
            log.d("Screenshot saved to: ${screenshotFile.absolutePath}")

            val mediaType = "text/plain".toMediaTypeOrNull()

            // Send to server for element identification
            ApiService.getInstance().identifyElements(
                token = "Bearer $accessToken",
                userId = userId.toRequestBody(mediaType),
                screenName = screenName.toRequestBody(mediaType),
                children = SdkJson.encodeToString(elements).toRequestBody(
                    "application/json".toMediaTypeOrNull()
                ),
                screenshot = MultipartBody.Part.createFormData(
                    "screenshot",
                    screenshotFile.name,
                    screenshotFile.asRequestBody("image/png".toMediaTypeOrNull())
                )
            )

            log.d("Screen capture and identification completed for screen: $screenName")

            identifyWidgetPositions(
                screenName = screenName,
                positionList = positionList
            )

            null
        } catch (error: Exception) {
            log.e(error, "Screen capture failed")
            "Screen capture failed: ${error.message}"
        } finally {
            setIsCapturing(screenName, false)
        }
    }
    if (response != null) {
        log.e(response)
    }
    return response
}

/**
 * Capture screen using PixelCopy or Canvas fallback
 */
private suspend fun getScreenshot(activity: Activity, view: View): File? {
    return try {
        if (view.width <= 0 || view.height <= 0) {
            log.e("Cannot capture screenshot: View has invalid dimensions")
            return null
        }

        val bitmap = createBitmap(view.width, view.height)
        val file = File.createTempFile("screenshot_", ".png", view.context.cacheDir)

        when {
            // PixelCopy-based screenshot (API 26+)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> suspendCancellableCoroutine<File?> { continuation ->
                val location = IntArray(2)
                view.getLocationInWindow(location)
                val rect = Rect(
                    location[0],
                    location[1],
                    location[0] + view.width,
                    location[1] + view.height
                )

                PixelCopy.request(
                    activity.window,
                    rect,
                    bitmap,
                    { result ->
                        when {
                            result == PixelCopy.SUCCESS -> try {
                                FileOutputStream(file).use { out ->
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                }
                                continuation.resume(file)
                            } catch (e: Exception) {
                                log.e(e, "Failed to save screenshot")
                                continuation.resumeWithException(e)
                            }

                            else -> {
                                val error = Exception("PixelCopy failed with code $result")
                                log.e(error.message ?: "Unknown error")
                                continuation.resumeWithException(error)
                            }
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
            }

            // Software rendering fallback (for API < 26)
            else -> {
                view.draw(Canvas(bitmap))
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                log.d("Screenshot captured successfully (fallback)")
                file
            }
        }
    } catch (e: Exception) {
        Timber.tag("CaptureService").e(e, "Error capturing screenshot")
        null
    }
}

/**
 * Recursively analyzes a single View element and its children.
 * Reports coordinates relative to the application window.
 *
 * @param view The current View to analyze.
 * @param onElementAnalyzed Callback to receive the JsonObject for the analyzed element.
 */
private fun getScreenElements(
    view: View,
    onElementAnalyzed: (JsonElement) -> Unit,
    activity: Activity
) {
    // Skip views with no ID
    val viewId = try {
        if (view.id != View.NO_ID) {
            view.resources.getResourceEntryName(view.id)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    if (viewId != null) {
        // Calculate window-relative position for traditional Views
        val locationInWindow = IntArray(2)
        view.getLocationInWindow(locationInWindow)
        val xInWindow = locationInWindow[0]
        val yInWindow = locationInWindow[1]

        val (screenWidth, screenHeight) = getScreenSize(activity)

        val elementJson = buildJsonObject {
            put("id", viewId)
            put("frame", buildJsonObject {
                put("x", xInWindow)
                put("y", yInWindow)
                put("width", view.width)
                put("height", view.height)
                put("screenWidth", screenWidth)
                put("screenHeight", screenHeight)
            })
        }

        onElementAnalyzed(elementJson)
    }

    // Recursively analyze children for ViewGroups
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            getScreenElements(view.getChildAt(i), onElementAnalyzed, activity = activity)
        }
    }

    // Handle embedded AndroidComposeView
    if (view::class.java.name == ANDROID_COMPOSE_VIEW_CLASS_NAME) {
        analyzeComposeView(view, onElementAnalyzed)
    }
}

/**
 * Analyzes the Compose tree within a AndroidComposeView using reflection.
 * WARNING: This uses reflection on internal Compose APIs and is highly fragile.
 * Reports coordinates relative to the application window.
 *
 * @param view The AndroidComposeView to analyze.
 * @param onElementAnalyzed Callback to receive the JsonElement for the analyzed element.
 */
private fun analyzeComposeView(
    view: View,
    onElementAnalyzed: (JsonElement) -> Unit
) {
    @Suppress("TooGenericExceptionCaught")
    try {
        val semanticsOwner = semanticsOwnerField?.get(view) as? SemanticsOwner

        if (semanticsOwner != null) {
            // Analyze the root semantics node of the Compose tree
            analyzeSemanticsNode(
                semanticsOwner.rootSemanticsNode,
                view.context,
                onElementAnalyzed,
                mutableListOf<Int>()
            )
        } else {
            log.w("Could not get SemanticsOwner from ComposeView via reflection.")
        }

    } catch (ex: Exception) {
        // Catching and swallowing exceptions here with the Compose view handling in case
        // something changes in the future that breaks the expected structure being accessed
        // through reflection here. If anything goes wrong within this block, prefer to continue
        // processing the remainder of the view tree as best we can.
        log.e(
            ex,
            "Error processing Compose layout via reflection: ${ex.message}"
        )
    }
}

/**
 * Recursively analyzes a SemanticsNode and its children.
 * Reports coordinates relative to the application window.
 *
 * @param semanticsNode The current SemanticsNode to analyze.
 * @param context The Android Context (needed for density if converting units).
 * @param onElementAnalyzed Callback to receive the JsonObject for the analyzed element.
 */
private fun analyzeSemanticsNode(
    semanticsNode: SemanticsNode,
    context: Context,
    onElementAnalyzed: (JsonElement) -> Unit,
    path: MutableList<Int>
) {
    val explicitId = semanticsNode.config.getOrNull(AppstorysViewTagKey)
    // Attempt to extract a valid ID from the Semantics config
    val nodeId = explicitId

    // Skip nodes that do not have a valid ID
    if (nodeId != null) {
        val xInWindow = semanticsNode.positionInWindow.x.roundToInt()
        val yInWindow = semanticsNode.positionInWindow.y.roundToInt()
        val widthPx = semanticsNode.size.width
        val heightPx = semanticsNode.size.height

        val elementJson = buildJsonObject {
            put("id", nodeId)
            put("frame", buildJsonObject {
                put("x", xInWindow)
                put("y", yInWindow)
                put("width", widthPx)
                put("height", heightPx)
            })
        }

        onElementAnalyzed(elementJson)
    }

    // Recursively analyze children nodes
    semanticsNode.children.forEachIndexed { index, child ->
        val childPath = path.toMutableList()
        childPath.add(index)
        analyzeSemanticsNode(child, context, onElementAnalyzed, childPath)
    }
}

private fun getScreenSize(context: Context): Pair<Int, Int> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val windowMetrics: WindowMetrics =
            context.getSystemService(WindowManager::class.java).currentWindowMetrics
        val bounds = windowMetrics.bounds
        Pair(bounds.width(), bounds.height())
    } else {
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getMetrics(
            displayMetrics
        )
        Pair(displayMetrics.widthPixels, displayMetrics.heightPixels)
    }
}

private val log: Timber.Tree
    get() = Timber.tag("CaptureScreen")

private const val ANDROID_COMPOSE_VIEW_CLASS_NAME =
    "androidx.compose.ui.platform.AndroidComposeView"

private val AppstorysViewTagKey = SemanticsPropertyKey<String>("AppstorysViewTagKey")

private val semanticsOwnerField: Field? by lazy {
    try {
        Class.forName(ANDROID_COMPOSE_VIEW_CLASS_NAME)
            .getDeclaredField("semanticsOwner")
            .apply { isAccessible = true }
    } catch (e: Exception) {
        log.e(e, "Reflection failed: Could not find semanticsOwner field")
        null
    }
}
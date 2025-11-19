package com.appversal.appstorys.utils

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
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
import com.appversal.appstorys.AppStorys.repository
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Field
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

val AppstorysViewTagKey = SemanticsPropertyKey<String>("AppstorysViewTagKey")

var SemanticsPropertyReceiver.appstorysViewTagProperty by AppstorysViewTagKey

internal object ViewTreeAnalyzer {
    private const val ANDROID_COMPOSE_VIEW_CLASS_NAME =
        "androidx.compose.ui.platform.AndroidComposeView"

    // Bitmap to store the captured screenshot
    private var screenBitmap: Bitmap? = null

    // Getter for the screenshot
    fun getScreenshot(): Bitmap? = screenBitmap

    private val semanticsOwnerField: Field? by lazy {
        try {
            Class.forName(ANDROID_COMPOSE_VIEW_CLASS_NAME)
                .getDeclaredField("semanticsOwner")
                .apply { isAccessible = true }
        } catch (e: Exception) {
            Log.e("ViewTreeAnalyzer", "Reflection failed: Could not find semanticsOwner field", e)
            null
        }
    }

    /**
     * Analyzes the view tree starting from the root View and generates a JSON representation.
     * Includes both traditional Android Views and Compose Views embedded within.
     * Reports coordinates relative to the application window.
     *
     * @param root The root View of the hierarchy to analyze.
     * @param screenName A name for the screen being analyzed.
     * @return A JsonObject representing the view tree.
     */
    suspend fun analyzeViewRoot(
        root: View,
        screenName: String,
        user_id: String,
        accessToken: String,
        activity: Activity,
        context: Context
    ): kotlinx.serialization.json.JsonObject {
        Log.i("ViewTreeAnalyzer", "===== analyzeViewRoot() START =====")
        val children = buildJsonArray {
            Log.i("ViewTreeAnalyzer", "Starting view tree analysis...")
            analyzeViewElement(
                root,
                onElementAnalyzed = {
                    Log.i("ViewTreeAnalyzer", "Element analyzed: $it")
                    add(it)
                    },
                activity = activity
            )
        }

        Log.i("ViewTreeAnalyzer", "View tree analysis complete. Total elements: ${children.size}")

        val resultJson = buildJsonObject {
            put("name", screenName)
            put("children", children)
        }

        val formattedJson = SdkJson.encodeToString(children)
        Log.e(
            "ViewTreeAnalyzer",
            formattedJson
        )

        Log.i("ViewTreeAnalyzer", "Capturing screenshot...")

        val screenshot = captureScreenshot(
            view = root,
            activity = activity
        )

        if (screenshot != null) {
            Log.i("ViewTreeAnalyzer", "Screenshot captured. Sending to server...")

            try {
                repository.tooltipIdentify(
                    accessToken = accessToken,
                    user_id = user_id,
                    screenName = screenName,
                    childrenJson = formattedJson,
                    screenshotFile = screenshot
                )
                Log.i("ViewTreeAnalyzer", "tooltipIdentify() sent successfully.")
            } catch (e: Exception) {
                Log.e("ViewTreeAnalyzer", "tooltipIdentify() failed", e)
            }
        }

        return resultJson
    }

    /**
     * Captures a screenshot of the provided view and stores it in the screenBitmap variable.
     *
     * @param view The view to capture.
     */
    private suspend fun captureScreenshot(activity: Activity, view: View): File? {
        Log.i("ViewTreeAnalyzer", "captureScreenshot() called")
        return try {
            if (view.width <= 0 || view.height <= 0) {
                Log.e("ViewTreeAnalyzer", "Cannot capture screenshot: View has invalid dimensions")
                return null
            }

            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val file = File.createTempFile("screenshot_", ".png", view.context.cacheDir)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // PixelCopy-based screenshot (API 26+)
                suspendCancellableCoroutine<File?> { continuation ->
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
                            if (result == PixelCopy.SUCCESS) {
                                try {
                                    FileOutputStream(file).use { out ->
                                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                    }
                                    screenBitmap = bitmap
                                    continuation.resume(file, onCancellation = null)
                                } catch (e: Exception) {
                                    Log.e("ViewTreeAnalyzer", "Failed to save screenshot", e)
                                    continuation.resumeWithException(e)
                                }
                            } else {
                                val error = Exception("PixelCopy failed with code $result")
                                Log.e("ViewTreeAnalyzer", error.message ?: "Unknown error")
                                continuation.resumeWithException(error)
                            }
                        },
                        Handler(Looper.getMainLooper())
                    )
                }
            } else {
                // Software rendering fallback (for API < 26)
                val canvas = Canvas(bitmap)
                view.draw(canvas)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                screenBitmap = bitmap
                Log.i("ViewTreeAnalyzer", "Screenshot captured successfully (fallback)")
                file
            }
        } catch (e: Exception) {
            Log.e("ViewTreeAnalyzer", "Error capturing screenshot", e)
            screenBitmap = null
            null
        }
    }

    fun getScreenSize(context: Context): Pair<Int, Int> {
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

    /**
     * Recursively analyzes a single View element and its children.
     * Reports coordinates relative to the application window.
     *
     * @param view The current View to analyze.
     * @param onElementAnalyzed Callback to receive the JsonObject for the analyzed element.
     */
    private fun analyzeViewElement(
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
                analyzeViewElement(view.getChildAt(i), onElementAnalyzed, activity = activity)
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
        onElementAnalyzed: (kotlinx.serialization.json.JsonElement) -> Unit
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
                Log.w(
                    "ViewTreeAnalyzer",
                    "Could not get SemanticsOwner from ComposeView via reflection."
                )
            }

        } catch (ex: Exception) {
            // Catching and swallowing exceptions here with the Compose view handling in case
            // something changes in the future that breaks the expected structure being accessed
            // through reflection here. If anything goes wrong within this block, prefer to continue
            // processing the remainder of the view tree as best we can.
            Log.e(
                "ViewTreeAnalyzer",
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
        onElementAnalyzed: (kotlinx.serialization.json.JsonElement) -> Unit,
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

}

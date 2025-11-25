package com.appversal.appstorys.domain.usecase

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import java.util.Locale
import java.util.TimeZone

internal fun getDeviceInfo(context: Context): Map<String, Any> {
    return try {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)

        val locale = Locale.getDefault()
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val density = displayMetrics.density

        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName ?: "unknown"

        val isTablet = (context.resources.configuration.screenLayout and
                Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE

        val orientation = if (width < height) "portrait" else "landscape"

        mapOf(
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "os_version" to Build.VERSION.RELEASE,
            "api_level" to Build.VERSION.SDK_INT,
            "language" to locale.language,
            "locale" to locale.toLanguageTag(),
            "timezone" to TimeZone.getDefault().id,
            "screen_width_px" to width,
            "screen_height_px" to height,
            "screen_density" to density,
            "orientation" to orientation,
            "app_version" to versionName,
            "package_name" to context.packageName,
            "device_type" to if (isTablet) "tablet" else "mobile",
            "platform" to "android"
        )
    } catch (error: Exception) {
        error.printStackTrace()
        emptyMap()
    }
}

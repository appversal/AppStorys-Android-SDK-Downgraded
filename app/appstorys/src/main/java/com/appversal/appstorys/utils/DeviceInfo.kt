package com.appversal.appstorys.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build

internal fun getDeviceInfo(context: Context): Map<String, Any> {
    val packageManager = context.packageManager
    val packageName = context.packageName
    val packageInfo = packageManager.getPackageInfo(packageName, 0)
    val appInfo = packageManager.getApplicationInfo(packageName, 0)
    val installTime = packageInfo.firstInstallTime
    val updateTime = packageInfo.lastUpdateTime

    val metrics = context.resources.displayMetrics
    val configuration = context.resources.configuration

    return mapOf(
        "manufacturer" to Build.MANUFACTURER,
        "model" to Build.MODEL,
        "os_version" to Build.VERSION.RELEASE,
        "api_level" to Build.VERSION.SDK_INT,
        "language" to when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> configuration.locales[0].language
            else -> configuration.locale.language
        },
        "locale" to when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> configuration.locales[0].toString()
            else -> configuration.locale.toString()
        },
        "timezone" to java.util.TimeZone.getDefault().id,
        "screen_width_px" to metrics.widthPixels,
        "screen_height_px" to metrics.heightPixels,
        "screen_density" to metrics.densityDpi,
        "orientation" to if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) "portrait" else "landscape",
        "app_version" to packageInfo.versionName,
        "package_name" to packageName,
        "device_type" to "mobile",
        "platform" to "android"
    )
}
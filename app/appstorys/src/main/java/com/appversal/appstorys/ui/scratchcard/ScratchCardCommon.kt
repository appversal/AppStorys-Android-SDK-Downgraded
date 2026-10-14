package com.appversal.appstorys.ui.scratchcard

import android.content.SharedPreferences
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.*

internal fun parseColorSafe(colorString: String, defaultColor: Color = Color.White): Color {
    return try {
        if (colorString.isNotEmpty()) {
            Color(android.graphics.Color.parseColor(colorString))
        } else {
            defaultColor
        }
    } catch (e: Exception) {
        defaultColor
    }
}

fun saveScratchedCampaigns(
    campaignIds: List<String>,
    sharedPreferences: SharedPreferences
) {
    val editor = sharedPreferences.edit()
    val idsString = campaignIds.joinToString(",")
    editor.putString("scratched_campaigns", idsString)
    editor.apply()
}

fun getScratchedCampaigns(sharedPreferences: SharedPreferences): List<String> {
    val idsString = sharedPreferences.getString("scratched_campaigns", "") ?: ""
    return if (idsString.isNotEmpty()) {
        idsString.split(",").filter { it.isNotEmpty() }
    } else {
        emptyList()
    }
}

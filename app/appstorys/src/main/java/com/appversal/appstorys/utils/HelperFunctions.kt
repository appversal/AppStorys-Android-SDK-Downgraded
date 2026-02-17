package com.appversal.appstorys.utils

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import org.json.JSONArray
import org.json.JSONObject

fun isGifUrl(url: String): Boolean {
    return url.lowercase().endsWith(".gif")
}

fun isLottieUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.endsWith(".json") || lower.endsWith(".lottie")
}

fun String?.toColor(defaultColor: Color): Color {
    return try {
        if (this.isNullOrEmpty()){
            defaultColor
        }else{
            Color(android.graphics.Color.parseColor(this))
        }
    }catch(_: Exception){
        defaultColor
    }
}

fun String.removeDoubleQuotes(): String = this.replace("\"", "")

fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
    clickable(
        indication = null,
        interactionSource = remember { MutableInteractionSource() }) {
        onClick()
    }
}


fun Context.pxToDp(px: Float): Dp {
    return (px / resources.displayMetrics.density).dp
}

fun JSONObject.toMap(): Map<String, Any> {
    return try {
        val map = mutableMapOf<String, Any>()
        val keys = this.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = this.opt(key)

            map[key] = when (value) {
                is JSONObject -> value.toMap()
                is JSONArray -> value.toList()
                else -> value ?: ""
            }
        }
        map
    } catch (e: Exception) {
        e.printStackTrace()
        emptyMap()
    }
}

fun JSONArray.toList(): List<Any> {
    return try {
        val list = mutableListOf<Any>()
        for (i in 0 until this.length()) {
            val value = this.opt(i)

            list.add(
                when (value) {
                    is JSONObject -> value.toMap()
                    is JSONArray -> value.toList()
                    else -> value ?: ""
                }
            )
        }
        list
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

fun JsonElement?.asInt(default: Int = 0): Int =
    (this as? JsonPrimitive)?.intOrNull ?: default
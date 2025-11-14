package com.appversal.appstorys.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
internal val SdkJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}


internal fun Map<String, Any>.toJsonElementMap(): Map<String, JsonElement> {
    return mapValues { (_, value) ->
        when (value) {
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Map<*, *> -> buildJsonObject {
                @Suppress("UNCHECKED_CAST")
                (value as Map<String, Any>).toJsonElementMap().forEach { (k, v) ->
                    put(k, v)
                }
            }

            is List<*> -> buildJsonArray {
                value.forEach { item ->
                    when (item) {
                        is String -> add(JsonPrimitive(item))
                        is Number -> add(JsonPrimitive(item))
                        is Boolean -> add(JsonPrimitive(item))
                        else -> add(JsonNull)
                    }
                }
            }

            else -> JsonNull
        }
    }
}
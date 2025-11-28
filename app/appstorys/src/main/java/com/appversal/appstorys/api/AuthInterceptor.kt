package com.appversal.appstorys.api

import com.appversal.appstorys.domain.State
import com.appversal.appstorys.domain.State.userId
import com.appversal.appstorys.utils.SdkJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        if (request.url.toString()
                .contains("validate-account") || request.headers["Authorization"] != null
        ) {
            return chain.proceed(request)
        }

        val accessToken = State.getAccessToken()
        if (accessToken.isNullOrBlank()) {
            throw IOException("Access token is missing. Make sure to initialize the SDK properly.")
        }

        val uid = userId.value

        val newRequestBuilder = request.newBuilder().header("Authorization", accessToken)

        if (request.method.equals("POST", ignoreCase = true) && uid.isNotBlank()) {
            when (val body = request.body) {
                is MultipartBody -> {
                    val builder = MultipartBody.Builder().setType(body.type)

                    // copy existing parts
                    for (part in body.parts) {
                        builder.addPart(part)
                    }

                    // add user_id as a form-data part
                    builder.addFormDataPart("user_id", uid)

                    newRequestBuilder.method(request.method, builder.build())
                }

                is FormBody -> {
                    val builder = FormBody.Builder()
                    for (i in 0 until body.size) {
                        builder.addEncoded(body.encodedName(i), body.encodedValue(i))
                    }
                    builder.add("user_id", uid)

                    newRequestBuilder.method(request.method, builder.build())
                }

                else -> {
                    val newBody = SdkJson.encodeToString(
                        buildJsonObject {
                            val data = SdkJson.decodeFromString<JsonObject>(
                                body?.let { bodyContent ->
                                    val buffer = okio.Buffer()
                                    bodyContent.writeTo(buffer)
                                    buffer.readUtf8()
                                } ?: "{}"
                            )
                            for ((key, value) in data) {
                                put(key, value)
                            }
                            put("user_id", JsonPrimitive(uid))
                        }
                    ).toRequestBody(body?.contentType())
                    newRequestBuilder.method(request.method, newBody)
                }
            }
        } else {
            newRequestBuilder.method(request.method, request.body)
        }

        return chain.proceed(newRequestBuilder.build())
    }
}
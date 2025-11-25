package com.appversal.appstorys.api

import com.appversal.appstorys.utils.SdkJson
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
internal object RetrofitClient {

    private val client by lazy {
        OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )
            .build()
    }

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://backend.appstorys.com/")
            .addConverterFactory(SdkJson.asConverterFactory("application/json".toMediaType()))
            .client(client)
            .build()
            .create(ApiService::class.java)
    }

    val webSocketApiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://users.appstorys.com/")
            .addConverterFactory(SdkJson.asConverterFactory("application/json".toMediaType()))
            .client(client)
            .build()
            .create(ApiService::class.java)
    }
}

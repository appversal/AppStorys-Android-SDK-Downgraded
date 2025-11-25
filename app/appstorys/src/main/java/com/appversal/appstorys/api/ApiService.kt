package com.appversal.appstorys.api

import androidx.annotation.Nullable
import com.appversal.appstorys.utils.SdkJson
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Url
import java.io.IOException

internal interface ApiService {

    @POST
    suspend fun validateAccount(
        @Url url: String = "https://users.appstorys.com/validate-account",
        @Body request: ValidateAccountRequest
    ): ValidateAccountResponse

    @POST("api/v2/appinfo/identify-positions/")
    suspend fun identifyPositions(
        @Header("Authorization") token: String,
        @Body request: IdentifyPositionsRequest
    ): Nullable

    @POST
    suspend fun getWebSocketConnectionDetails(
        @Url url: String = "https://users.appstorys.com/track-user",
        @Header("Authorization") token: String,
        @Body request: TrackUserWebSocketRequest
    ): WebSocketConnectionResponse

    @POST("api/v1/campaigns/capture-csat-response/")
    suspend fun sendCsatResponse(
        @Header("Authorization") token: String,
        @Body request: CsatFeedbackPostRequest
    )

    @POST("api/v1/campaigns/capture-survey-response/")
    suspend fun sendSurveyResponse(
        @Header("Authorization") token: String,
        @Body request: SurveyFeedbackPostRequest
    )

    @POST("api/v1/campaigns/reel-like/")
    suspend fun sendReelLikeStatus(
        @Header("Authorization") token: String,
        @Body request: ReelStatusRequest
    )

    @POST("api/v1/users/track-action/")
    suspend fun trackUserAction(
        @Header("Authorization") token: String,
        @Body request: JsonObject
    )

    @Multipart
    @POST("api/v1/appinfo/identify-elements/")
    suspend fun identifyElements(
        @Header("Authorization") token: String,
        @Part("screenName") screenName: RequestBody,
        @Part("user_id") userId: RequestBody,
        @Part("children") children: RequestBody,
        @Part screenshot: MultipartBody.Part
    )

    companion object {
        @Volatile
        private var client: OkHttpClient? = null

        @Volatile
        private var instance: ApiService? = null

        fun getClient(): OkHttpClient {
            return client ?: synchronized(this) {
                client = OkHttpClient.Builder()
                    .addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BODY
                        }
                    )
                    .build()
                client!!
            }
        }

        fun getInstance(): ApiService {
            return instance ?: synchronized(this) {
                instance = Retrofit.Builder()
                    .baseUrl("https://backend.appstorys.com/")
                    .addConverterFactory(SdkJson.asConverterFactory("application/json".toMediaType()))
                    .client(getClient())
                    .build()
                    .create(ApiService::class.java)
                instance!!
            }
        }
    }
}

internal sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val code: Int? = null) : ApiResult<Nothing>()
}

internal suspend fun <T> safeApiCall(apiCall: suspend () -> T): ApiResult<T> {
    return try {
        ApiResult.Success(apiCall())
    } catch (e: HttpException) {
        ApiResult.Error(e.message ?: "Unknown error", e.code())
    } catch (e: IOException) {
        ApiResult.Error("Network error. Please check your internet connection.")
    } catch (e: Exception) {
        ApiResult.Error("Unexpected error occurred.")
    }
}
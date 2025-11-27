package com.appversal.appstorys.api

import androidx.annotation.Nullable
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.io.IOException

internal interface ApiService {

    @POST("validate-account")
    suspend fun validateAccount(
        @Body request: ValidateAccountRequest
    ): ValidateAccountResponse

    @POST("api/v2/appinfo/identify-positions/")
    suspend fun identifyPositions(
        @Header("Authorization") token: String,
        @Body request: IdentifyPositionsRequest
    ): Nullable

    @POST("track-user")
    suspend fun getWebSocketConnectionDetails(
        @Header("Authorization") token: String,
        @Body request: TrackUserWebSocketRequest
    ): WebSocketConnectionResponse

    @POST("update-user-atr")
    suspend fun updateUserProperties(
        @Header("Authorization") token: String,
        @Body request: UpdateUserPropertiesRequest
    ): Response<Unit>

    @POST("api/v1/campaigns/capture-csat-response/")
    suspend fun sendCSATResponse(
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
    suspend fun trackReelAction(
        @Header("Authorization") token: String,
        @Body request: ReelActionRequest
    )

    @POST("api/v1/users/track-action/")
    suspend fun trackStoriesAction(
        @Header("Authorization") token: String,
        @Body request: TrackActionStories
    )

    @POST("api/v1/users/track-action/")
    suspend fun trackTooltipsAction(
        @Header("Authorization") token: String,
        @Body request: TrackActionTooltips
    )

    @Multipart
    @POST("api/v1/appinfo/identify-elements/")
    suspend fun identifyTooltips(
        @Header("Authorization") token: String,
        @Part("screenName") screenName: RequestBody,
        @Part("user_id") user_id: RequestBody,
        @Part("children") children: RequestBody,
        @Part screenshot: MultipartBody.Part
    )
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
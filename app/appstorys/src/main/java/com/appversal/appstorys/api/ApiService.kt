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
import retrofit2.http.Path
import java.io.IOException

internal interface ApiService {

    @POST("{accountId}/validate-account")
    suspend fun validateAccount(
        @Path("accountId") accountId: String,
        @Body request: ValidateAccountRequest
    ): ValidateAccountResponse

    @POST("api/v2/appinfo/identify-positions/")
    suspend fun identifyPositions(
        @Header("Authorization") token: String,
        @Body request: IdentifyPositionsRequest
    ): Nullable

    @POST("v2/{accountId}/track-user-res")
    suspend fun getEligibleCampaigns(
        @Path("accountId") accountId: String,
        @Header("Authorization") token: String,
        @Body request: TrackUserWebSocketRequest
    ): EligibleCampaignsResponse

    @POST("load-campaign-data")
    suspend fun loadMissingCampaigns(
        @Header("Authorization") token: String,
        @Body campaignIds: List<String>
    ): List<Campaign>

    @POST("reconcile-anonymous-user")
    suspend fun reconcileAnonymousUser(
        @Header("Authorization") token: String,
        @Body request: ReconcileUserRequest
    ): Response<Unit>

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

    @POST("api/v1/campaigns/reel-like/")
    suspend fun sendReelLikeStatus(
        @Header("Authorization") token: String,
        @Body request: ReelStatusRequest
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
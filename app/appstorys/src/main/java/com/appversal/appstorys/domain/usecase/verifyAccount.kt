package com.appversal.appstorys.domain.usecase

import com.appversal.appstorys.api.ApiService
import com.appversal.appstorys.api.ValidateAccountRequest
import com.appversal.appstorys.domain.State
import timber.log.Timber

/**
 * Verify account credentials and store access tokens
 */
internal suspend fun verifyAccount(accountId: String, appId: String): Boolean {
    val log = Timber.tag("VerifyAccount")
    return try {
        val response = ApiService.getInstance().validateAccount(
            request = ValidateAccountRequest(
                app_id = appId,
                account_id = accountId
            )
        )

        if (response.access_token != null) {
            log.d("Account verified")

            State.saveAccessToken(response.access_token)
            true
        } else {
            log.w("Account verification failed: no access token")
            false
        }
    } catch (error: Exception) {
        log.e(error, "Error when verifying AppStorys account")
        false
    }
}

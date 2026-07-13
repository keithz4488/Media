package com.kzaller.shelf.data

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.kzaller.shelf.BuildConfig

data class AuthUser(val idToken: String, val email: String)

/**
 * Thin wrapper over Credential Manager for Google Sign-In. [signIn] shows the account picker
 * (needs an Activity context); [silentToken] returns a fresh ID token with no UI when an account
 * has already been authorized (used on launch and to refresh an expired token).
 */
class AuthManager(private val appContext: Context) {
    private val credentialManager = CredentialManager.create(appContext)
    private val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID

    suspend fun signIn(activityContext: Context): Result<AuthUser> = runCatching {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false) // let them pick any Google account the first time
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        parse(credentialManager.getCredential(activityContext, request))
    }

    /** Silent (no-UI) token for an already-authorized account, or null if a picker is required. */
    suspend fun silentToken(): AuthUser? = runCatching {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(true)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        parse(credentialManager.getCredential(appContext, request))
    }.getOrNull()

    suspend fun signOut() {
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
    }

    private fun parse(response: GetCredentialResponse): AuthUser {
        val cred = response.credential
        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val google = GoogleIdTokenCredential.createFrom(cred.data)
            return AuthUser(idToken = google.idToken, email = google.id)
        }
        error("Unexpected credential type: ${cred.type}")
    }
}

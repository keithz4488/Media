package com.kzaller.shelf.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kzaller.shelf.data.AuthManager
import com.kzaller.shelf.data.api.AuthTokenProvider
import com.kzaller.shelf.data.preferences.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val email: String) : AuthState
}

class AuthViewModel(
    private val appContext: Context,
    private val prefs: AppPreferences,
) : ViewModel() {

    private val auth = AuthManager(appContext)

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        // Let the API layer silently refresh an expired token via Credential Manager.
        AuthTokenProvider.refresher = { refreshToken() }
        viewModelScope.launch { bootstrap() }
    }

    private suspend fun bootstrap() {
        val stored = prefs.observeIdToken().first()
        val email = prefs.observeEmail().first()
        if (stored.isNotBlank()) {
            AuthTokenProvider.idToken = stored
            _state.value = AuthState.SignedIn(email)
        }
        // Try to mint a fresh token silently (the stored one may be expired).
        val user = auth.silentToken()
        if (user != null) {
            AuthTokenProvider.idToken = user.idToken
            prefs.setAuth(user.idToken, user.email)
            _state.value = AuthState.SignedIn(user.email)
        } else if (stored.isBlank()) {
            _state.value = AuthState.SignedOut
        }
    }

    private suspend fun refreshToken(): String? {
        val user = auth.silentToken() ?: return null
        prefs.setAuth(user.idToken, user.email)
        return user.idToken
    }

    /** Interactive sign-in. Pass an Activity context so the account picker can render. */
    fun signIn(activityContext: Context) {
        viewModelScope.launch {
            _state.value = AuthState.Loading
            _error.value = null
            auth.signIn(activityContext)
                .onSuccess {
                    AuthTokenProvider.idToken = it.idToken
                    prefs.setAuth(it.idToken, it.email)
                    _state.value = AuthState.SignedIn(it.email)
                }
                .onFailure {
                    _error.value = it.message ?: "Sign-in failed"
                    _state.value = AuthState.SignedOut
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            prefs.clearAuth()
            AuthTokenProvider.idToken = null
            // Drop the local cache so the next account doesn't briefly see this one's shelf.
            runCatching { com.kzaller.shelf.data.ShelfRepository(appContext).clearLocal() }
            _state.value = AuthState.SignedOut
        }
    }

    fun clearError() { _error.value = null }

    companion object {
        fun factory(context: Context, prefs: AppPreferences): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AuthViewModel(context.applicationContext, prefs) as T
            }
    }
}

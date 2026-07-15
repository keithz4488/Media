package com.kzaller.shelf.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kzaller.shelf.data.AuthManager
import com.kzaller.shelf.data.ShelfRepository
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
    private val repo = ShelfRepository(appContext)

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        // Only used on a 401 (a session shouldn't expire mid-use): silently re-mint a session.
        AuthTokenProvider.refresher = { renewSession() }
        viewModelScope.launch { bootstrap() }
    }

    private suspend fun bootstrap() {
        val token = prefs.observeSessionToken().first()
        val expiry = prefs.observeSessionExpiry().first()
        val email = prefs.observeEmail().first()
        // Use the cached session token straight away — no Google prompt on launch. The session
        // lasts ~30 days, so this is the path virtually every launch takes.
        if (token.isNotBlank() && (expiry == 0L || expiry > System.currentTimeMillis())) {
            AuthTokenProvider.idToken = token
            _state.value = AuthState.SignedIn(email)
        } else {
            _state.value = AuthState.SignedOut
        }
    }

    /** Exchange the current Google auth (already set as the bearer) for a session token. */
    private suspend fun exchangeForSession(email: String): Boolean {
        val res = repo.createSession().getOrNull() ?: return false
        if (res.token.isBlank()) return false
        AuthTokenProvider.idToken = res.token
        prefs.setSession(res.token, res.expiresAt, email)
        return true
    }

    /** 401 handler: get a fresh Google token silently and swap it for a new session. */
    private suspend fun renewSession(): String? {
        val user = auth.silentToken() ?: return null
        AuthTokenProvider.idToken = user.idToken
        return if (exchangeForSession(user.email)) prefs.observeSessionToken().first() else null
    }

    /** Interactive sign-in. Pass an Activity context so the account picker can render. */
    fun signIn(activityContext: Context) {
        viewModelScope.launch {
            _state.value = AuthState.Loading
            _error.value = null
            auth.signIn(activityContext)
                .onSuccess { user ->
                    // Use the Google token just long enough to mint a durable session token.
                    AuthTokenProvider.idToken = user.idToken
                    if (exchangeForSession(user.email)) {
                        _state.value = AuthState.SignedIn(user.email)
                    } else {
                        // Couldn't reach the session endpoint; fall back to the Google token so the
                        // user is still signed in (short-lived), and store it so a launch retries.
                        prefs.setSession(user.idToken, System.currentTimeMillis() + 50 * 60 * 1000, user.email)
                        _state.value = AuthState.SignedIn(user.email)
                    }
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
            runCatching { repo.clearLocal() }
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

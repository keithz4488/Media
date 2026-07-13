package com.kzaller.shelf.data.api

import kotlinx.coroutines.runBlocking

/**
 * Holds the Bearer token the API interceptor should send, decoupled from the auth UI. When the
 * user is signed in with Google this is their ID token; when null the interceptor falls back to
 * the legacy shared token so the app keeps working through the transition.
 *
 * [refresher] is installed by the auth layer and performs a silent Google token refresh; the
 * OkHttp authenticator calls it (blocking) when the server rejects an expired token.
 */
object AuthTokenProvider {
    @Volatile var idToken: String? = null

    @Volatile var refresher: (suspend () -> String?)? = null

    /** Blocking silent refresh for use inside an OkHttp Authenticator. Returns a fresh token or null. */
    fun refreshBlocking(): String? =
        runCatching { runBlocking { refresher?.invoke() } }.getOrNull()
}

package com.kzaller.shelf.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.kzaller.shelf.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object ApiClient {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private fun baseUrl(): String {
        val raw = BuildConfig.API_BASE.trimEnd('/')
        return "$raw/"
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                // Prefer the signed-in user's Google ID token; fall back to the legacy shared
                // token so the app still works before/without Google sign-in.
                val token = AuthTokenProvider.idToken ?: BuildConfig.API_TOKEN
                val req = chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
                chain.proceed(req)
            }
            .authenticator { _, response ->
                // A 401 usually means the Google ID token expired. Try one silent refresh and
                // retry; the header guard prevents an infinite auth loop.
                if (response.request.header("X-Auth-Retry") != null) return@authenticator null
                val fresh = AuthTokenProvider.refreshBlocking() ?: return@authenticator null
                AuthTokenProvider.idToken = fresh
                response.request.newBuilder()
                    .header("Authorization", "Bearer $fresh")
                    .header("X-Auth-Retry", "1")
                    .build()
            }
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                }
            }
            .build()
    }

    val api: ShelfApi by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl())
            .client(httpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ShelfApi::class.java)
    }
}

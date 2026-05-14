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
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun baseUrl(): String {
        val raw = BuildConfig.API_BASE.trimEnd('/')
        return "$raw/"
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Authorization", "Bearer ${BuildConfig.API_TOKEN}")
                    .build()
                chain.proceed(req)
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

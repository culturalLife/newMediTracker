package com.example.ai

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Lightweight DI singleton for the Mistral network stack. Built lazily and shared
 * across the process. Kept intentionally small — full DI (Hilt) would be overkill here.
 */
object NetworkModule {

    private const val MISTRAL_BASE_URL = "https://api.mistral.ai/"

    val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            // Keep at HEADERS so we never accidentally log the Bearer token in BODY mode.
            level = HttpLoggingInterceptor.Level.NONE
        }
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    val mistralApi: MistralApi by lazy {
        Retrofit.Builder()
            .baseUrl(MISTRAL_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(MistralApi::class.java)
    }
}

package com.example.ai

import com.example.ai.model.ChatRequest
import com.example.ai.model.ChatResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit interface for the Mistral chat completions endpoint.
 * Auth is provided per-call so callers can inject the BuildConfig key without leaking it
 * into a singleton OkHttp interceptor.
 */
interface MistralApi {
    @POST("v1/chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") authorization: String,
        @Body body: ChatRequest
    ): ChatResponse
}

package com.example.ai.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Wire-format models for the Mistral Chat Completions API.
 * See: https://docs.mistral.ai/api/#tag/chat
 */

@JsonClass(generateAdapter = true)
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.3,
    @Json(name = "max_tokens") val maxTokens: Int? = 800,
    @Json(name = "response_format") val responseFormat: ResponseFormat? = null
)

@JsonClass(generateAdapter = true)
data class ChatMessage(
    val role: String, // "system" | "user" | "assistant"
    val content: String
)

@JsonClass(generateAdapter = true)
data class ResponseFormat(
    val type: String // "text" or "json_object"
)

@JsonClass(generateAdapter = true)
data class ChatResponse(
    val id: String?,
    val model: String?,
    val choices: List<Choice>?
)

@JsonClass(generateAdapter = true)
data class Choice(
    val index: Int?,
    val message: ChatMessage?,
    @Json(name = "finish_reason") val finishReason: String?
)

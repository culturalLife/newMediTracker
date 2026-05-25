package com.example.ai

import com.example.BuildConfig
import com.example.ai.model.ChatMessage
import com.example.ai.model.ChatRequest
import com.example.ai.model.MedicineInfo
import com.example.ai.model.ResponseFormat
import com.squareup.moshi.JsonDataException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thin domain wrapper around [MistralApi] that:
 *   1. Injects the BuildConfig API key as a bearer token.
 *   2. Pre-pends a strict, medication-only system prompt so the model stays on-topic.
 *   3. Offers two modes:
 *        - [chat] for free-form conversational replies (returns plain text).
 *        - [getMedicineInfo] for a structured info card (returns parsed [MedicineInfo]).
 */
class MistralRepository(
    private val api: MistralApi = NetworkModule.mistralApi,
    private val apiKey: String = BuildConfig.MISTRAL_API_KEY,
    private val model: String = "mistral-small-latest"
) {

    /** True when the build was assembled without a real API key. */
    fun isConfigured(): Boolean =
        apiKey.isNotBlank() && apiKey != "YOUR_MISTRAL_API_KEY_HERE"

    /**
     * Free-form chat. Maintains conversational history so the model can reference earlier turns.
     * The system prompt is injected automatically; callers should pass only user/assistant turns.
     */
    suspend fun chat(history: List<ChatMessage>): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(isConfigured()) { "Missing MISTRAL_API_KEY. Add it to your local .env file." }
            val messages = listOf(ChatMessage("system", CHAT_SYSTEM_PROMPT)) + history
            val response = api.chatCompletions(
                authorization = "Bearer $apiKey",
                body = ChatRequest(model = model, messages = messages, temperature = 0.4)
            )
            response.choices?.firstOrNull()?.message?.content?.trim()
                ?: error("Empty response from Mistral.")
        }
    }

    /**
     * Asks the model for a structured JSON description of [medicineName].
     * Returns a parsed [MedicineInfo] on success, or a failure if the model fell back to prose.
     */
    suspend fun getMedicineInfo(medicineName: String): Result<MedicineInfo> = withContext(Dispatchers.IO) {
        runCatching {
            require(isConfigured()) { "Missing MISTRAL_API_KEY. Add it to your local .env file." }
            val userPrompt = "Provide structured information about: $medicineName"
            val response = api.chatCompletions(
                authorization = "Bearer $apiKey",
                body = ChatRequest(
                    model = model,
                    messages = listOf(
                        ChatMessage("system", STRUCTURED_SYSTEM_PROMPT),
                        ChatMessage("user", userPrompt)
                    ),
                    temperature = 0.2,
                    responseFormat = ResponseFormat("json_object")
                )
            )
            val raw = response.choices?.firstOrNull()?.message?.content
                ?: error("Empty response from Mistral.")
            parseMedicineInfo(raw) ?: error("Could not parse JSON medicine info.")
        }
    }

    private fun parseMedicineInfo(raw: String): MedicineInfo? {
        // Some models wrap JSON in code fences despite json_object mode — strip defensively.
        val cleaned = raw
            .substringAfter("```json", raw)
            .substringAfter("```", raw)
            .substringBeforeLast("```")
            .trim()
        val adapter = NetworkModule.moshi.adapter(MedicineInfo::class.java).lenient()
        return try {
            adapter.fromJson(cleaned)
        } catch (_: JsonDataException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        // Conservative system prompt for free-form chat. Refuses non-medication topics
        // and always pivots clinical questions back to a healthcare professional.
        private val CHAT_SYSTEM_PROMPT = """
            You are MediTracker's AI Pharmacist Assistant. Your role is strictly limited to:
              - Providing general educational information about medications (purpose, mechanism, common side effects, interactions, food advice).
              - Helping users understand medication labels and instructions in plain language.

            Strict rules you MUST follow:
              1. NEVER diagnose conditions, prescribe doses, or recommend stopping/starting any medicine.
              2. NEVER cite specific clinical numbers (mmHg, mg/dL, percentages of risk) unless they are explicitly listed on a typical drug label.
              3. ALWAYS append a short reminder to consult a doctor or pharmacist for personal medical decisions.
              4. If asked about non-medication topics, briefly explain you can only help with medicine questions and stop.
              5. Keep answers concise (3-6 short sentences) and easy to read on a phone screen.
              6. Use plain language; avoid heavy jargon.
        """.trimIndent()

        // Structured prompt: instruct strict JSON output for the rich info card UI.
        private val STRUCTURED_SYSTEM_PROMPT = """
            You are MediTracker's AI Pharmacist Assistant. You provide GENERAL educational information about medications only.

            For every request, respond with a single JSON object matching exactly this schema:
            {
              "medicineName": "<canonical name>",
              "purpose": "<1-2 sentence description of what it is commonly used for>",
              "commonDosage": "<typical adult dosage range as printed on common labels, or 'Varies — see your prescription'>",
              "foodAdvice": "<with food / on empty stomach / no special instruction>",
              "warnings": ["<short bullet>", "<short bullet>"],
              "interactions": ["<short bullet>", "<short bullet>"],
              "sideEffects": ["<common side effect>", "<common side effect>"]
            }

            Hard rules:
              - Output ONLY the JSON object, no prose, no code fences, no commentary.
              - Keep every string short and phone-readable (under 140 characters).
              - Limit each list to at most 4 items.
              - If you are not confident the requested name is a real medicine, return JSON with all fields empty/null.
              - Never include exact mg dosages tied to a person's condition — only typical label ranges.
              - Use plain language; avoid heavy jargon.
        """.trimIndent()
    }
}

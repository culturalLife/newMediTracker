package com.example.ai

import com.example.BuildConfig
import com.example.ai.model.AdherenceInsight
import com.example.ai.model.ChatMessage
import com.example.ai.model.ChatRequest
import com.example.ai.model.ResponseFormat
import com.example.ai.model.SafetyAssessment
import com.example.data.model.Medicine
import com.squareup.moshi.JsonDataException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Domain wrapper around [MistralApi] for non-conversational health insights:
 *   - [analyzeSafety]: scans the user's full medicine list for potential interactions
 *     and returns a tri-state safety badge (green/yellow/red/unknown) plus concerns.
 *   - [getAdherenceInsight]: given the user's recent miss/take counts, returns a
 *     supportive note, a generic compensation tip, and a doctor-consult fallback.
 *
 * Both methods use Mistral's strict JSON response_format so the parsed result is reliable
 * for structured UI rendering. If the model isn't confident, we surface that as
 * "Unknown" / "consult your doctor" rather than guessing.
 */
class HealthInsightsRepository(
    private val api: MistralApi = NetworkModule.mistralApi,
    private val apiKey: String = BuildConfig.MISTRAL_API_KEY,
    private val model: String = "mistral-small-latest"
) {

    /** True when a real Mistral API key was injected at build time. */
    fun isConfigured(): Boolean =
        apiKey.isNotBlank() && apiKey != "YOUR_MISTRAL_API_KEY_HERE"

    /**
     * Asks Mistral to scan the given medicine list for potential interactions or
     * obvious contraindications. No personal patient context is sent — only generic
     * medicine names and dosages — so the model stays in safe educational territory.
     */
    suspend fun analyzeSafety(medicines: List<Medicine>): Result<SafetyAssessment> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(isConfigured()) { "Missing MISTRAL_API_KEY. Add it to your local .env file." }
                require(medicines.isNotEmpty()) { "Add at least one medicine before running a safety check." }

                val medList = medicines.joinToString(separator = "\n") { med ->
                    "- ${med.name} (${med.dosage})"
                }
                val userPrompt = "Analyze this medication list for potential interactions and " +
                    "general safety concerns:\n$medList"

                val response = api.chatCompletions(
                    authorization = "Bearer $apiKey",
                    body = ChatRequest(
                        model = model,
                        messages = listOf(
                            ChatMessage("system", SAFETY_SYSTEM_PROMPT),
                            ChatMessage("user", userPrompt)
                        ),
                        temperature = 0.2,
                        responseFormat = ResponseFormat("json_object")
                    )
                )
                val raw = response.choices?.firstOrNull()?.message?.content
                    ?: error("Empty response from Mistral.")
                parseSafety(raw) ?: error("Could not parse safety JSON.")
            }
        }

    /**
     * Asks Mistral for a supportive insight given recent adherence data.
     *
     * @param medicines all of the active medicines for the profile (just names + dosages).
     * @param missedSummary "med name: N missed (over last D days)" formatted lines, or
     *                     "no missed doses" if the user is on track. The repo is
     *                     intentionally agnostic about how the caller formats this so
     *                     adherence math can live in the ViewModel.
     */
    suspend fun getAdherenceInsight(
        medicines: List<Medicine>,
        missedSummary: String
    ): Result<AdherenceInsight> = withContext(Dispatchers.IO) {
        runCatching {
            require(isConfigured()) { "Missing MISTRAL_API_KEY. Add it to your local .env file." }
            require(medicines.isNotEmpty()) { "No active medicines to analyze." }

            val medList = medicines.joinToString(separator = "\n") { med ->
                "- ${med.name} (${med.dosage})"
            }
            val userPrompt = """
                Active medicines:
                $medList

                Recent adherence:
                $missedSummary

                Provide a supportive, non-alarming insight.
            """.trimIndent()

            val response = api.chatCompletions(
                authorization = "Bearer $apiKey",
                body = ChatRequest(
                    model = model,
                    messages = listOf(
                        ChatMessage("system", INSIGHT_SYSTEM_PROMPT),
                        ChatMessage("user", userPrompt)
                    ),
                    temperature = 0.4,
                    responseFormat = ResponseFormat("json_object")
                )
            )
            val raw = response.choices?.firstOrNull()?.message?.content
                ?: error("Empty response from Mistral.")
            parseInsight(raw) ?: error("Could not parse insight JSON.")
        }
    }

    private fun parseSafety(raw: String): SafetyAssessment? {
        val cleaned = stripFences(raw)
        val adapter = NetworkModule.moshi.adapter(SafetyAssessment::class.java).lenient()
        return try {
            adapter.fromJson(cleaned)
        } catch (_: JsonDataException) { null } catch (_: Exception) { null }
    }

    private fun parseInsight(raw: String): AdherenceInsight? {
        val cleaned = stripFences(raw)
        val adapter = NetworkModule.moshi.adapter(AdherenceInsight::class.java).lenient()
        return try {
            adapter.fromJson(cleaned)
        } catch (_: JsonDataException) { null } catch (_: Exception) { null }
    }

    private fun stripFences(raw: String): String =
        raw.substringAfter("```json", raw)
            .substringAfter("```", raw)
            .substringBeforeLast("```")
            .trim()

    companion object {
        // Strict JSON-only output. Conservative: prefer "unknown" + "consult your pharmacist"
        // over confident hallucinations. NEVER include patient-specific clinical numbers.
        private val SAFETY_SYSTEM_PROMPT = """
            You are MediTracker's medication safety scanner. You analyze a list of medicines
            for potential interactions and obvious safety concerns at a general educational level.

            Respond with a single JSON object matching exactly this schema:
            {
              "level": "green" | "yellow" | "red" | "unknown",
              "summary": "<one short sentence overall>",
              "concerns": [
                {
                  "medicineA": "<name>",
                  "medicineB": "<name or null for solo concern>",
                  "severity": "low" | "moderate" | "high" | "unknown",
                  "description": "<short plain-language explanation, under 140 chars>"
                }
              ]
            }

            Hard rules:
              - Output ONLY the JSON object, no prose, no code fences.
              - Use "green" only when the medicines are commonly co-prescribed without notable issues.
              - Use "yellow" for moderate, well-known interactions where caution is advised.
              - Use "red" for high-severity interactions that are clearly documented.
              - Use "unknown" if any medicine name is ambiguous or you cannot confidently assess.
              - NEVER cite specific clinical numbers (mmHg, mg/dL, exact percentages).
              - Always end every concern's description with a brief "verify with pharmacist" style nudge if severity is moderate or higher.
              - Limit "concerns" to at most 6 items, ranked by severity.
              - If there is only one medicine, level is "green" and concerns is an empty array.
        """.trimIndent()

        // Empathetic adherence coach. Strongly biased toward "consult your doctor" when uncertain.
        private val INSIGHT_SYSTEM_PROMPT = """
            You are MediTracker's adherence coach. Given a user's active medicines and recent
            missed-dose summary, you provide a SHORT supportive insight — never alarming,
            never prescriptive.

            Respond with a single JSON object matching exactly this schema:
            {
              "supportiveNote": "<one sentence acknowledging consistency is hard>",
              "compensationTip": "<generic label-style guidance for catching up safely, OR null>",
              "consultDoctorNote": "<short message advising to call doctor or pharmacist when uncertain, OR null>",
              "encouragement": "<one sentence of gentle encouragement>"
            }

            Hard rules:
              - Output ONLY the JSON object, no prose, no code fences.
              - Tone: warm, calm, evidence-based. NEVER use fear language.
              - NEVER cite specific clinical numbers, risks, or outcomes (no "5% stroke risk" etc.).
              - "compensationTip" must reflect generic medication-label guidance only
                (e.g. "If you remember within a few hours, take it; otherwise skip and resume next dose").
              - If the type of medicine matters and the model isn't confident, set "compensationTip"
                to null and put the doctor-consult message in "consultDoctorNote".
              - At least one of "compensationTip" or "consultDoctorNote" must be non-null.
              - Keep every string under 200 characters and phone-readable.
              - Use plain language; avoid heavy jargon.
        """.trimIndent()
    }
}

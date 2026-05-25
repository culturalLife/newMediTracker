package com.example.ai.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Bio-safety assessment returned by the AI when scanning the user's full medicine list
 * for potential interactions and contraindications.
 *
 * The Mistral system prompt instructs the model to populate exactly this JSON shape.
 * Any field may be empty/null when the model isn't confident — we surface "Unknown"
 * to the user rather than fabricating a green light.
 */
@JsonClass(generateAdapter = true)
data class SafetyAssessment(
    /** Overall risk band. One of: "green", "yellow", "red", "unknown". */
    val level: String? = null,
    /** Single short sentence summarizing the overall picture. */
    val summary: String? = null,
    /** Specific pairwise or single-medicine concerns. */
    val concerns: List<SafetyConcern>? = null
) {
    fun normalizedLevel(): Level = Level.fromRaw(level)

    fun isEmpty(): Boolean =
        level.isNullOrBlank() && summary.isNullOrBlank() && concerns.isNullOrEmpty()

    enum class Level { GREEN, YELLOW, RED, UNKNOWN;
        companion object {
            fun fromRaw(raw: String?): Level = when (raw?.trim()?.lowercase()) {
                "green" -> GREEN
                "yellow", "amber" -> YELLOW
                "red" -> RED
                else -> UNKNOWN
            }
        }
    }
}

@JsonClass(generateAdapter = true)
data class SafetyConcern(
    /** First medicine involved (or the only one for a solo concern). */
    val medicineA: String? = null,
    /** Second medicine for an interaction; null for a solo concern. */
    val medicineB: String? = null,
    /** "low" | "moderate" | "high" | "unknown". */
    val severity: String? = null,
    /** Plain-language explanation, kept under ~140 chars to read well on a phone. */
    val description: String? = null
)

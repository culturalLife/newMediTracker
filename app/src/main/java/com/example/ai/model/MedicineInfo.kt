package com.example.ai.model

import com.squareup.moshi.JsonClass

/**
 * Structured medicine info card returned by the AI when the user taps
 * "Ask AI about this medicine" or asks a focused medicine question.
 *
 * The Mistral system prompt instructs the model to return JSON in this exact shape.
 * Any field may be absent/empty if the model does not have confident information.
 */
@JsonClass(generateAdapter = true)
data class MedicineInfo(
    val medicineName: String? = null,
    val purpose: String? = null,
    val commonDosage: String? = null,
    val foodAdvice: String? = null,
    val warnings: List<String>? = null,
    val interactions: List<String>? = null,
    val sideEffects: List<String>? = null
) {
    fun isEmpty(): Boolean =
        medicineName.isNullOrBlank() &&
            purpose.isNullOrBlank() &&
            commonDosage.isNullOrBlank() &&
            foodAdvice.isNullOrBlank() &&
            warnings.isNullOrEmpty() &&
            interactions.isNullOrEmpty() &&
            sideEffects.isNullOrEmpty()
}

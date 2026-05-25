package com.example.ai.model

import com.squareup.moshi.JsonClass

/**
 * Supportive insight surfaced on Home when the user has missed doses recently.
 *
 * Designed to be educational and encouraging — never alarmist, never prescriptive.
 * The schema enforces a "consult your doctor" pivot whenever advice is uncertain.
 */
@JsonClass(generateAdapter = true)
data class AdherenceInsight(
    /** One-sentence empathetic acknowledgement (e.g. "Staying on track can be tough"). */
    val supportiveNote: String? = null,
    /**
     * Generic, label-style guidance for getting back on schedule.
     * E.g. "Take your missed dose as soon as you remember unless your next dose is close."
     */
    val compensationTip: String? = null,
    /**
     * When [compensationTip] doesn't apply or the model can't decide safely,
     * this carries a clear "talk to your doctor" message instead.
     */
    val consultDoctorNote: String? = null,
    /** Optional micro-encouragement footer, e.g. "Even small consistency improvements add up." */
    val encouragement: String? = null
) {
    fun isEmpty(): Boolean =
        supportiveNote.isNullOrBlank() &&
            compensationTip.isNullOrBlank() &&
            consultDoctorNote.isNullOrBlank() &&
            encouragement.isNullOrBlank()
}

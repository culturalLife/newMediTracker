package com.example.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai.HealthInsightsRepository
import com.example.ai.model.AdherenceInsight
import com.example.ai.model.SafetyAssessment
import com.example.data.model.DoseLog
import com.example.data.model.Medicine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Drives the AI-powered insights surfaced on the Medicines and Home screens:
 *   - [safety]: result of an explicit "Run Safety Check" against the current medicines list.
 *   - [insight]: a supportive adherence insight computed from recent missed doses.
 *
 * Each flow runs on demand so we don't hammer the API on every recomposition.
 */
class HealthInsightsViewModel(
    private val repository: HealthInsightsRepository = HealthInsightsRepository()
) : ViewModel() {

    sealed class SafetyState {
        data object Idle : SafetyState()
        data object Loading : SafetyState()
        data class Success(val assessment: SafetyAssessment) : SafetyState()
        data class Error(val message: String) : SafetyState()
    }

    sealed class InsightState {
        data object Idle : InsightState()
        data object Loading : InsightState()
        data class Success(val insight: AdherenceInsight) : InsightState()
        data class Error(val message: String) : InsightState()
    }

    private val _safety = MutableStateFlow<SafetyState>(SafetyState.Idle)
    val safety: StateFlow<SafetyState> = _safety.asStateFlow()

    private val _insight = MutableStateFlow<InsightState>(InsightState.Idle)
    val insight: StateFlow<InsightState> = _insight.asStateFlow()

    val isConfigured: Boolean = repository.isConfigured()

    /** Triggered by the "Run Safety Check" button on the Medicines screen. */
    fun runSafetyCheck(medicines: List<Medicine>) {
        if (_safety.value is SafetyState.Loading) return
        _safety.value = SafetyState.Loading
        viewModelScope.launch {
            repository.analyzeSafety(medicines)
                .onSuccess { assessment ->
                    _safety.value = if (assessment.isEmpty()) {
                        SafetyState.Error("The assistant returned an empty assessment. Please try again.")
                    } else {
                        SafetyState.Success(assessment)
                    }
                }
                .onFailure { err ->
                    _safety.value = SafetyState.Error(err.message ?: "Couldn't run safety check.")
                }
        }
    }

    /** Reset the safety card so the user can run another check after editing meds. */
    fun resetSafety() {
        _safety.value = SafetyState.Idle
    }

    /**
     * Asks Mistral for a supportive adherence insight, using a 7-day rolling miss summary
     * computed from [allLogs] for the active medicines.
     */
    fun loadAdherenceInsight(medicines: List<Medicine>, allLogs: List<DoseLog>) {
        if (_insight.value is InsightState.Loading) return
        if (medicines.isEmpty()) {
            _insight.value = InsightState.Idle
            return
        }
        _insight.value = InsightState.Loading
        viewModelScope.launch {
            val summary = buildMissedSummary(medicines, allLogs, days = 7)
            repository.getAdherenceInsight(medicines, summary)
                .onSuccess { result ->
                    _insight.value = if (result.isEmpty()) {
                        InsightState.Error("No insight returned. Please try again.")
                    } else {
                        InsightState.Success(result)
                    }
                }
                .onFailure { err ->
                    _insight.value = InsightState.Error(err.message ?: "Couldn't load insight.")
                }
        }
    }

    fun resetInsight() {
        _insight.value = InsightState.Idle
    }

    /**
     * Build a "med name: N missed (over last D days)" formatted string the AI can reason on.
     * Returns "no missed doses in the last D days" when the user is on track.
     */
    private fun buildMissedSummary(
        medicines: List<Medicine>,
        logs: List<DoseLog>,
        days: Int
    ): String {
        val today = LocalDate.now()
        val cutoff = today.minusDays((days - 1).toLong())
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        val recent = logs.filter { log ->
            runCatching { LocalDate.parse(log.dateStr, fmt) }.getOrNull()?.let { d ->
                !d.isBefore(cutoff) && !d.isAfter(today)
            } == true
        }

        val medsById = medicines.associateBy { it.id }
        val missedByMed = recent
            .filter { it.status == "Missed" || it.status == "Skipped" }
            .groupBy { it.medicineId }
            .mapNotNull { (medId, logsForMed) ->
                medsById[medId]?.let { med -> med to logsForMed.size }
            }
            .filter { it.second > 0 }

        if (missedByMed.isEmpty()) return "No missed doses in the last $days days."

        return buildString {
            append("Missed/skipped doses in the last $days days:\n")
            missedByMed.sortedByDescending { it.second }.forEach { (med, count) ->
                append("- ${med.name} (${med.dosage}): $count missed\n")
            }
        }.trim()
    }

    class Factory(
        private val repository: HealthInsightsRepository = HealthInsightsRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HealthInsightsViewModel::class.java)) {
                "Unknown ViewModel class: $modelClass"
            }
            return HealthInsightsViewModel(repository) as T
        }
    }
}

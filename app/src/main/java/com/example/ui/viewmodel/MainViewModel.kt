package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.MedTrackApplication
import com.example.data.model.DoseLog
import com.example.data.model.Medicine
import com.example.data.model.Profile
import com.example.data.repository.MedRepository
import com.example.reminder.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class MainViewModel(
    application: Application,
    private val repository: MedRepository
) : AndroidViewModel(application) {

    // Onboarding status: simple state flow that checking if profiles exist
    private val _isOnboarded = MutableStateFlow<Boolean?>(null)
    val isOnboarded: StateFlow<Boolean?> = _isOnboarded.asStateFlow()

    // Active Profile
    private val _selectedProfile = MutableStateFlow<Profile?>(null)
    val selectedProfile: StateFlow<Profile?> = _selectedProfile.asStateFlow()

    // Active tracking date (defaults to today)
    private val _selectedDate = MutableStateFlow<LocalDate>(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    // All UI profiles list
    val profilesList: StateFlow<List<Profile>> = repository.allProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Medicines list for active profile
    @OptIn(ExperimentalCoroutinesApi::class)
    val medicinesList: StateFlow<List<Medicine>> = _selectedProfile
        .flatMapLatest { profile ->
            if (profile != null) repository.getAllMedicines(profile.id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dose logs for selected profile and date
    @OptIn(ExperimentalCoroutinesApi::class)
    val doseLogsList: StateFlow<List<DoseLog>> = combine(_selectedProfile, _selectedDate) { profile, date ->
        profile to date
    }.flatMapLatest { (profile, date) ->
        if (profile != null) {
            val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            repository.getLogsForProfileAndDate(profile.id, dateStr)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All logs for general statistics and calendars
    @OptIn(ExperimentalCoroutinesApi::class)
    val allProfileLogs: StateFlow<List<DoseLog>> = _selectedProfile
        .flatMapLatest { profile ->
            if (profile != null) repository.getLogsForProfile(profile.id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Automatically fetch files lists and determine onboarding criteria
        viewModelScope.launch {
            profilesList.collect { list ->
                if (list.isNotEmpty()) {
                    _isOnboarded.value = true
                    if (_selectedProfile.value == null) {
                        _selectedProfile.value = list.first()
                    }
                } else {
                    _isOnboarded.value = false
                }
            }
        }

        // Eagerly populate dose logs whenever the active profile or selected date changes.
        // This is kept separate from the doseLogsList flow so the population side-effect
        // never races with the Room query subscriber.
        viewModelScope.launch {
            combine(_selectedProfile, _selectedDate) { profile, date -> profile to date }
                .collect { (profile, date) ->
                    if (profile != null) {
                        repository.populateLogsForDate(profile.id, date)
                        if (date.isEqual(LocalDate.now())) {
                            repository.updateMissedLogsToday(profile.id)
                        }
                    }
                }
        }
    }

    fun selectProfile(profile: Profile) {
        _selectedProfile.value = profile
        _selectedDate.value = LocalDate.now()
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    // Profiles Actions
    fun createProfile(name: String, colorIndex: Int) {
        viewModelScope.launch {
            val profile = Profile(name = name, avatarColor = colorIndex)
            val id = repository.insertProfile(profile)
            _selectedProfile.value = profile.copy(id = id.toInt())
            _isOnboarded.value = true
        }
    }

    fun deleteProfile(profile: Profile) {
        viewModelScope.launch {
            repository.deleteProfile(profile)
            val remaining = repository.allProfiles.first()
            if (remaining.isNotEmpty()) {
                _selectedProfile.value = remaining.first()
            } else {
                _selectedProfile.value = null
                _isOnboarded.value = false
            }
        }
    }

    /**
     * Persist edits to the active profile (typically the caregiver name + phone).
     * The selected profile flow is updated immediately so Settings reflects the change.
     */
    fun updateProfile(profile: Profile) {
        viewModelScope.launch {
            repository.updateProfile(profile)
            if (_selectedProfile.value?.id == profile.id) {
                _selectedProfile.value = profile
            }
        }
    }

    // Medicines actions
    fun addMedicine(
        name: String,
        dosage: String,
        frequency: String,
        times: List<String>,
        startDate: LocalDate,
        endDate: LocalDate?,
        notes: String?,
        colorTag: Int,
        isReminderEnabled: Boolean,
        pillCount: Int? = null,
        pillsPerDose: Int = 1
    ) {
        val profile = _selectedProfile.value ?: return
        viewModelScope.launch {
            val timesCsv = times.joinToString(",")
            val medicine = Medicine(
                profileId = profile.id,
                name = name,
                dosage = dosage,
                frequency = frequency,
                timesCsv = timesCsv,
                startDate = startDate.toEpochDay(),
                endDate = endDate?.toEpochDay(),
                notes = notes,
                colorTag = colorTag,
                isReminderEnabled = isReminderEnabled,
                pillCount = pillCount,
                pillsPerDose = pillsPerDose
            )
            
            // Insert into local Room SQLite DB
            val medId = repository.insertMedicine(medicine)
            val insertedMed = medicine.copy(id = medId.toInt())

            // Schedule alarms with system AlarmManager
            ReminderScheduler.scheduleAlarms(getApplication(), insertedMed)

            // Trigger lazy-population for selectedDate to instantly show in lists
            repository.populateLogsForDate(profile.id, _selectedDate.value)
        }
    }

    fun updateMedicine(
        id: Int,
        name: String,
        dosage: String,
        frequency: String,
        times: List<String>,
        startDate: LocalDate,
        endDate: LocalDate?,
        notes: String?,
        colorTag: Int,
        isReminderEnabled: Boolean,
        pillCount: Int? = null,
        pillsPerDose: Int = 1
    ) {
        val profile = _selectedProfile.value ?: return
        viewModelScope.launch {
            val timesCsv = times.joinToString(",")
            val medicine = Medicine(
                id = id,
                profileId = profile.id,
                name = name,
                dosage = dosage,
                frequency = frequency,
                timesCsv = timesCsv,
                startDate = startDate.toEpochDay(),
                endDate = endDate?.toEpochDay(),
                notes = notes,
                colorTag = colorTag,
                isReminderEnabled = isReminderEnabled,
                pillCount = pillCount,
                pillsPerDose = pillsPerDose
            )

            // Update in Room database
            repository.updateMedicine(medicine)

            // Dynamic schedule alarms
            ReminderScheduler.scheduleAlarms(getApplication(), medicine)

            // Refresh today's logs dynamically to fit new scheduling times
            repository.populateLogsForDate(profile.id, _selectedDate.value)
        }
    }

    fun deleteMedicine(medicine: Medicine) {
        viewModelScope.launch {
            ReminderScheduler.cancelAlarms(getApplication(), medicine)
            repository.deleteMedicine(medicine)
        }
    }

    // Daily Logs Action Tracking
    fun markDoseStatus(log: DoseLog, status: String) {
        viewModelScope.launch {
            val previousStatus = log.status
            val updatedLog = log.copy(
                status = status,
                timestamp = if (status == "Taken" || status == "Skipped") System.currentTimeMillis() else null
            )
            repository.updateLog(updatedLog)

            // Refill bookkeeping: when transitioning INTO Taken from any other state,
            // decrement the medicine's pill count by its per-dose amount. Reverse the
            // transaction when the user undoes a "Taken" mark back to Pending.
            if (status == "Taken" && previousStatus != "Taken") {
                adjustPillCount(log.medicineId, delta = -1)
            } else if (previousStatus == "Taken" && status != "Taken") {
                adjustPillCount(log.medicineId, delta = +1)
            }
        }
    }

    private suspend fun adjustPillCount(medicineId: Int, delta: Int) {
        val med = repository.getMedicineById(medicineId) ?: return
        val current = med.pillCount ?: return
        val pillsPerDose = med.pillsPerDose.coerceAtLeast(1)
        val newCount = (current + delta * pillsPerDose).coerceAtLeast(0)
        repository.updateMedicine(med.copy(pillCount = newCount))
    }

    // Helper Factory Creator
    class Factory(private val application: Application, private val repository: MedRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

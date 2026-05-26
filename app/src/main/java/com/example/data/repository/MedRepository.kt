package com.example.data.repository

import com.example.data.local.DoseLogDao
import com.example.data.local.MedicineDao
import com.example.data.local.ProfileDao
import com.example.data.model.DoseLog
import com.example.data.model.Medicine
import com.example.data.model.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MedRepository(
    private val profileDao: ProfileDao,
    private val medicineDao: MedicineDao,
    private val doseLogDao: DoseLogDao
) {
    // Profiles
    val allProfiles: Flow<List<Profile>> = profileDao.getAllProfiles()

    suspend fun getProfileById(id: Int): Profile? = withContext(Dispatchers.IO) {
        profileDao.getProfileById(id)
    }

    suspend fun insertProfile(profile: Profile): Long = withContext(Dispatchers.IO) {
        profileDao.insertProfile(profile)
    }

    suspend fun updateProfile(profile: Profile) = withContext(Dispatchers.IO) {
        profileDao.updateProfile(profile)
    }

    suspend fun deleteProfile(profile: Profile) = withContext(Dispatchers.IO) {
        // Delete cascading items locally
        medicineDao.deleteMedicinesByProfile(profile.id)
        doseLogDao.deleteLogsForProfile(profile.id)
        profileDao.deleteProfile(profile)
    }

    // Medicines
    fun getAllMedicines(profileId: Int): Flow<List<Medicine>> = medicineDao.getAllMedicines(profileId)

    suspend fun getMedicineById(id: Int): Medicine? = withContext(Dispatchers.IO) {
        medicineDao.getMedicineById(id)
    }

    suspend fun insertMedicine(medicine: Medicine): Long = withContext(Dispatchers.IO) {
        medicineDao.insertMedicine(medicine)
    }

    suspend fun updateMedicine(medicine: Medicine) = withContext(Dispatchers.IO) {
        medicineDao.updateMedicine(medicine)
    }

    suspend fun deleteMedicine(medicine: Medicine) = withContext(Dispatchers.IO) {
        doseLogDao.deleteLogsForMedicine(medicine.id)
        medicineDao.deleteMedicine(medicine)
    }

    // Dose Logs
    fun getLogsForProfileAndDate(profileId: Int, dateStr: String): Flow<List<DoseLog>> =
        doseLogDao.getLogsForProfileAndDate(profileId, dateStr)

    fun getLogsForProfile(profileId: Int): Flow<List<DoseLog>> =
        doseLogDao.getLogsForProfile(profileId)

    suspend fun insertLog(doseLog: DoseLog): Long = withContext(Dispatchers.IO) {
        doseLogDao.insertLog(doseLog)
    }

    suspend fun updateLog(doseLog: DoseLog) = withContext(Dispatchers.IO) {
        doseLogDao.updateLog(doseLog)
    }

    suspend fun getLogById(id: Int): DoseLog? = withContext(Dispatchers.IO) {
        doseLogDao.getLogById(id)
    }

    /**
     * Lazy populate expected DoseLogs for a specific date if they don't exist yet.
     * This checks active medicines for the given profile and inserts log placeholders.
     */
    suspend fun populateLogsForDate(profileId: Int, date: LocalDate) = withContext(Dispatchers.IO) {
        val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val existingLogs = doseLogDao.getLogsForProfileAndDateSync(profileId, dateStr)
        val medicines = medicineDao.getAllMedicines(profileId).first()

        val epochDayStr = date.toEpochDay()
        // Filter medicines active on this day
        val activeMeds = medicines.filter { med ->
            med.startDate <= epochDayStr && (med.endDate == null || epochDayStr <= med.endDate)
        }

        val logsToInsert = mutableListOf<DoseLog>()

        for (med in activeMeds) {
            val times = med.getTimesList()
            for (time in times) {
                // Check if log already exists
                val exists = existingLogs.any { it.medicineId == med.id && it.scheduledTime == time }
                if (!exists) {
                    // Determine initial status:
                    // If target date is today and time is in the past, or target date is in the past, mark as Missed or Pending.
                    val isToday = date.isEqual(LocalDate.now())
                    val isPast = date.isBefore(LocalDate.now())
                    
                    val initialStatus = if (isPast) {
                        "Missed"
                    } else {
                        // For today (including past-time slots), always seed as Pending.
                        // updateMissedLogsToday() is responsible for flipping Pending → Missed
                        // after the grace window, so the user still has a chance to mark Taken.
                        "Pending"
                    }

                    logsToInsert.add(
                        DoseLog(
                            profileId = profileId,
                            medicineId = med.id,
                            medicineName = med.name,
                            dosage = med.dosage,
                            scheduledTime = time,
                            dateStr = dateStr,
                            status = initialStatus,
                            timestamp = null
                        )
                    )
                }
            }
        }

        if (logsToInsert.isNotEmpty()) {
            doseLogDao.insertLogs(logsToInsert)
        }
    }

    /**
     * Check and update all pending logs of today to "Missed" if their scheduled time has passed
     */
    suspend fun updateMissedLogsToday(profileId: Int) = withContext(Dispatchers.IO) {
        val todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val existingLogs = doseLogDao.getLogsForProfileAndDateSync(profileId, todayStr)
        val now = LocalTime.now()

        for (log in existingLogs) {
            if (log.status == "Pending") {
                val parsedTime = LocalTime.parse(log.scheduledTime)
                if (now.isAfter(parsedTime)) {
                    doseLogDao.updateLog(log.copy(status = "Missed"))
                }
            }
        }
    }
}

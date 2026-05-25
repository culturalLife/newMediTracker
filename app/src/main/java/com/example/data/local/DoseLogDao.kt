package com.example.data.local

import androidx.room.*
import com.example.data.model.DoseLog
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseLogDao {
    @Query("SELECT * FROM dose_logs WHERE profileId = :profileId AND dateStr = :dateStr")
    fun getLogsForProfileAndDate(profileId: Int, dateStr: String): Flow<List<DoseLog>>

    @Query("SELECT * FROM dose_logs WHERE profileId = :profileId AND dateStr = :dateStr")
    suspend fun getLogsForProfileAndDateSync(profileId: Int, dateStr: String): List<DoseLog>

    @Query("SELECT * FROM dose_logs WHERE profileId = :profileId")
    fun getLogsForProfile(profileId: Int): Flow<List<DoseLog>>

    @Query("SELECT * FROM dose_logs WHERE profileId = :profileId")
    suspend fun getLogsForProfileSync(profileId: Int): List<DoseLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(doseLog: DoseLog): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(doseLogs: List<DoseLog>)

    @Update
    suspend fun updateLog(doseLog: DoseLog)

    @Query("SELECT * FROM dose_logs WHERE id = :id")
    suspend fun getLogById(id: Int): DoseLog?

    @Query("DELETE FROM dose_logs WHERE medicineId = :medicineId")
    suspend fun deleteLogsForMedicine(medicineId: Int)

    @Query("DELETE FROM dose_logs WHERE profileId = :profileId")
    suspend fun deleteLogsForProfile(profileId: Int)
}

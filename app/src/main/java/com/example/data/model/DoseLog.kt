package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dose_logs")
data class DoseLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val profileId: Int,
    val medicineId: Int,
    val medicineName: String,
    val dosage: String,
    val scheduledTime: String, // "HH:mm"
    val dateStr: String, // "yyyy-MM-dd"
    val status: String, // "Pending", "Taken", "Skipped", "Missed"
    val timestamp: Long?, // Timestamp of confirmation
    val snoozeCount: Int = 0
)

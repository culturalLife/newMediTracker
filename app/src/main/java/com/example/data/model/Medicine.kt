package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medicines")
data class Medicine(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val profileId: Int,
    val name: String,
    val dosage: String,
    val frequency: String,
    val timesCsv: String, // Comma separated HH:mm values, e.g. "08:00,21:00"
    val startDate: Long, // Epoch day
    val endDate: Long?, // Epoch day
    val notes: String?,
    val colorTag: Int, // 0 to 5 for UI colors
    val isReminderEnabled: Boolean
) {
    // Helper to split the CSV times list
    fun getTimesList(): List<String> {
        if (timesCsv.isBlank()) return emptyList()
        return timesCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
}

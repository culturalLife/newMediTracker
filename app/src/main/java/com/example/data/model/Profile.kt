package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val avatarColor: Int,
    /** Optional caregiver name shown in Settings + the SMS deep-link. */
    val caregiverName: String? = null,
    /** Optional caregiver phone in any format the user wants — used to build smsto: intents. */
    val caregiverPhone: String? = null
)

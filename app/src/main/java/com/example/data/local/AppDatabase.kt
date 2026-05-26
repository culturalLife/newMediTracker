package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.DoseLog
import com.example.data.model.Medicine
import com.example.data.model.Profile

@Database(entities = [Profile::class, Medicine::class, DoseLog::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun medicineDao(): MedicineDao
    abstract fun doseLogDao(): DoseLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medtrack_database"
                )
                // NOTE: fallbackToDestructiveMigrationFrom(1) means only a v1→v2 upgrade
                // will wipe data. Future migrations (v2→v3+) MUST provide a Migration object
                // to avoid data loss. Replace with addMigrations(...) when adding new columns.
                .fallbackToDestructiveMigrationFrom(1)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

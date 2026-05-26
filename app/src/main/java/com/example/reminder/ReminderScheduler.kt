package com.example.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.data.model.Medicine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object ReminderScheduler {
    fun scheduleAlarms(context: Context, medicine: Medicine) {
        if (!medicine.isReminderEnabled) {
            cancelAlarms(context, medicine)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val times = medicine.getTimesList()

        for (index in times.indices) {
            val timeStr = times[index]
            try {
                val time = LocalTime.parse(timeStr)
                var alarmDateTime = LocalDateTime.of(LocalDate.now(), time)

                // If scheduled time has already passed today, schedule for tomorrow
                if (alarmDateTime.isBefore(LocalDateTime.now())) {
                    alarmDateTime = alarmDateTime.plusDays(1)
                }

                val epochMillis = alarmDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    putExtra("MEDICINE_ID", medicine.id)
                    putExtra("MEDICINE_NAME", medicine.name)
                    putExtra("MEDICINE_DOSAGE", medicine.dosage)
                    putExtra("PROFILE_ID", medicine.profileId)
                    putExtra("TIME_INDEX", index)
                    putExtra("TIME_STR", timeStr)
                }

                val requestCode = generateRequestCode(medicine.id, index)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                try {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        epochMillis,
                        pendingIntent
                    )
                } catch (e: SecurityException) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        epochMillis,
                        pendingIntent
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun cancelAlarms(context: Context, medicine: Medicine) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val times = medicine.getTimesList()

        for (index in times.indices) {
            val requestCode = generateRequestCode(medicine.id, index)
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    fun scheduleSnoozeAlarm(context: Context, profileId: Int, medicineId: Int, medicineName: String, dosage: String, snoozeMinutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val time = LocalDateTime.now().plusMinutes(snoozeMinutes.toLong())
        val epochMillis = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("MEDICINE_ID", medicineId)
            putExtra("MEDICINE_NAME", medicineName)
            putExtra("MEDICINE_DOSAGE", dosage)
            putExtra("PROFILE_ID", profileId)
            putExtra("IS_SNOOZE", true)
        }

        // Snooze alarms use a separate request-code namespace (500_000 + medicineId)
        // to guarantee they never clash with regular alarm codes (medicineId * 100 + timeIndex).
        val requestCode = 500_000 + medicineId
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                epochMillis,
                pendingIntent
            )
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                epochMillis,
                pendingIntent
            )
        }
    }

    private fun generateRequestCode(medicineId: Int, timeIndex: Int): Int {
        return medicineId * 100 + timeIndex
    }
}

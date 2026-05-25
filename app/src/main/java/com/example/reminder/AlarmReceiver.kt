package com.example.reminder

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.example.MedTrackApplication
import com.example.MainActivity
import com.example.data.model.Medicine
import com.example.reminder.ReminderOverlayActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medicineId = intent.getIntExtra("MEDICINE_ID", -1)
        val profileId = intent.getIntExtra("PROFILE_ID", -1)
        val medicineName = intent.getStringExtra("MEDICINE_NAME") ?: "Medicine"
        val dosage = intent.getStringExtra("MEDICINE_DOSAGE") ?: "Dose"
        val timeStr = intent.getStringExtra("TIME_STR") ?: ""
        val isSnooze = intent.getBooleanExtra("IS_SNOOZE", false)

        if (medicineId == -1) return

        // Launch full-screen overlay activity directly with FLAG_ACTIVITY_NEW_TASK
        val overlayIntent = Intent(context, ReminderOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("MEDICINE_ID", medicineId)
            putExtra("PROFILE_ID", profileId)
            putExtra("MEDICINE_NAME", medicineName)
            putExtra("MEDICINE_DOSAGE", dosage)
            putExtra("TIME_STR", timeStr)
            putExtra("IS_SNOOZE", isSnooze)
        }
        
        val pendingOverlayIntent = PendingIntent.getActivity(
            context,
            medicineId,
            overlayIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Show a high-importance notification with the full-screen intent
        val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, "medicine_reminders")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Medicine Reminder: $medicineName")
            .setContentText("It's time to take your dose: $dosage")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(notificationUri)
            .setFullScreenIntent(pendingOverlayIntent, true) // Crucial for overlaying when locked!
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(medicineId, builder.build())

        // Also launch overlay directly
        try {
            context.startActivity(overlayIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Reschedule alarm for next day if NOT snooze
        if (!isSnooze) {
            CoroutineScope(Dispatchers.IO).launch {
                val repo = MedTrackApplication.instance.repository
                val med = repo.getMedicineById(medicineId)
                if (med != null) {
                    ReminderScheduler.scheduleAlarms(context, med)
                }
            }
        }
    }
}

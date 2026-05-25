package com.example.caregiver

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.model.DoseLog
import com.example.data.model.Profile
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Builds and fires offline-first caregiver notifications.
 *
 * The app never needs SEND_SMS permission and never sends a message itself — it only
 * opens the user's preferred messaging app (SMS, WhatsApp, email...) with a prefilled
 * body via standard intents. The user reviews and sends.
 *
 * Two flavors:
 *   - [composeMissedDoseSms] for a single missed dose nudge (smsto: deep link).
 *   - [composeDailyShareIntent] for an ad-hoc share of today's adherence summary
 *     using ACTION_SEND so any installed app (WhatsApp, Gmail, Drive...) can receive it.
 */
object CaregiverNotifier {

    /**
     * Open the user's SMS app pre-populated with a polite missed-dose message.
     *
     * Returns false if the profile doesn't have a caregiver phone configured or no
     * SMS-capable app is installed; callers should surface a "Set up caregiver" hint.
     */
    fun composeMissedDoseSms(
        context: Context,
        profile: Profile,
        medicineName: String,
        scheduledTime: String
    ): Boolean {
        val phone = profile.caregiverPhone?.takeIf { it.isNotBlank() } ?: return false
        val displayTime = runCatching {
            LocalTime.parse(scheduledTime).format(DateTimeFormatter.ofPattern("h:mm a"))
        }.getOrDefault(scheduledTime)
        val caregiverName = profile.caregiverName?.takeIf { it.isNotBlank() } ?: "there"

        val body = "Hi $caregiverName — quick note from MediTracker: " +
            "${profile.name} hasn't logged $medicineName from the $displayTime dose yet. " +
            "Could you check in when you have a moment? Thank you."

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:${phone.replace(" ", "")}")
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /**
     * Build an ACTION_SEND intent containing today's adherence summary that the user
     * can route to any installed app via the system chooser.
     *
     * @param logs the dose logs for the period to summarize.
     * @param dateLabel a friendly date label, e.g. "today" or "this week".
     */
    fun composeDailyShareIntent(
        profile: Profile,
        logs: List<DoseLog>,
        dateLabel: String = "today"
    ): Intent {
        val summary = buildSummary(profile, logs, dateLabel)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "MediTracker — ${profile.name} ($dateLabel)")
            putExtra(Intent.EXTRA_TEXT, summary)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Public so callers can preview the text before launching the chooser, or
     * use it for unit tests.
     */
    fun buildSummary(profile: Profile, logs: List<DoseLog>, dateLabel: String): String {
        val taken = logs.count { it.status == "Taken" }
        val missed = logs.count { it.status == "Missed" }
        val skipped = logs.count { it.status == "Skipped" }
        val pending = logs.count { it.status == "Pending" }
        val total = logs.size
        val rate = if (total > 0) (taken.toDouble() / total * 100).toInt() else 0

        val builder = StringBuilder()
        builder.append("MediTracker update for ${profile.name} ($dateLabel)\n")
        builder.append("Adherence: $rate% ($taken of $total taken)\n")
        if (missed > 0) builder.append("Missed: $missed\n")
        if (skipped > 0) builder.append("Skipped: $skipped\n")
        if (pending > 0) builder.append("Still pending: $pending\n")

        val grouped = logs.sortedBy { it.scheduledTime }
        if (grouped.isNotEmpty()) {
            builder.append("\nSchedule:\n")
            grouped.forEach { log ->
                val displayTime = runCatching {
                    LocalTime.parse(log.scheduledTime).format(DateTimeFormatter.ofPattern("h:mm a"))
                }.getOrDefault(log.scheduledTime)
                val marker = when (log.status) {
                    "Taken" -> "[x]"
                    "Skipped" -> "[~]"
                    "Missed" -> "[!]"
                    else -> "[ ]"
                }
                builder.append("$marker $displayTime — ${log.medicineName} (${log.dosage})\n")
            }
        }

        builder.append("\nGenerated ${LocalDate.now().format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}.")
        return builder.toString()
    }
}

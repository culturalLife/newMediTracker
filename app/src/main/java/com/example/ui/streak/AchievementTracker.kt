package com.example.ui.streak

import android.content.Context

/**
 * Lightweight, on-device tracker for streak/achievement milestones.
 *
 * Persists the set of milestone day-counts the user has already been celebrated for,
 * so we don't re-trigger confetti every time the user reopens the app on the same
 * 7/30/100-day streak. Storage is plain SharedPreferences — no backend.
 */
class AchievementTracker(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Returns the next milestone the user should be celebrated for, given their current streak,
     * or null if no new milestone has just been crossed.
     *
     * Successful return marks the milestone as shown so subsequent recompositions don't loop.
     */
    fun consumeNewMilestone(currentStreak: Int): Milestone? {
        val crossed = MILESTONES.lastOrNull { it.days <= currentStreak } ?: return null
        val key = milestoneKey(crossed.days)
        if (prefs.getBoolean(key, false)) return null
        prefs.edit().putBoolean(key, true).apply()
        return crossed
    }

    /** Useful for testing or for users who want to "reset" their celebration history. */
    fun resetMilestones() {
        prefs.edit().clear().apply()
    }

    /** All milestones the user has unlocked so far, oldest first. */
    fun unlockedMilestones(): List<Milestone> =
        MILESTONES.filter { prefs.getBoolean(milestoneKey(it.days), false) }

    private fun milestoneKey(days: Int) = "milestone_$days"

    companion object {
        private const val PREFS = "meditracker_achievements"

        /** Milestone tiers, ascending. Add more here without changing call sites. */
        val MILESTONES: List<Milestone> = listOf(
            Milestone(days = 3, title = "Three-day Streak", emoji = "✨", subtitle = "You're building a habit."),
            Milestone(days = 7, title = "Perfect Week", emoji = "🌟", subtitle = "Seven flawless days in a row."),
            Milestone(days = 14, title = "Two Weeks Strong", emoji = "💪", subtitle = "Your routine is locked in."),
            Milestone(days = 30, title = "Perfect Month", emoji = "🏆", subtitle = "Thirty days of consistency."),
            Milestone(days = 60, title = "Sixty-day Streak", emoji = "🚀", subtitle = "Outstanding discipline."),
            Milestone(days = 100, title = "Centurion", emoji = "👑", subtitle = "100 perfect days. Legendary.")
        )
    }
}

data class Milestone(
    val days: Int,
    val title: String,
    val emoji: String,
    val subtitle: String
)

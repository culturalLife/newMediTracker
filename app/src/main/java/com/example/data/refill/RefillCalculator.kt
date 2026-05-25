package com.example.data.refill

import com.example.data.model.Medicine
import java.time.LocalDate

/**
 * Pure functions that turn a [Medicine]'s pill count + schedule into a refill forecast.
 *
 * The model is intentionally simple — we assume every scheduled dose is taken in
 * full going forward, so the prediction represents the *latest* date the user could
 * still be on schedule before running out. Consumed-vs-scheduled drift is already
 * tracked by the dose log status, so adherence shortfalls don't affect this number.
 */
object RefillCalculator {

    /** A computed forecast for a single medicine. */
    data class Forecast(
        val medicine: Medicine,
        /** Days of supply remaining at today's rate. Null when refill tracking is disabled. */
        val daysRemaining: Int?,
        /** Calendar date the user is projected to run out. Null when tracking is disabled. */
        val runOutDate: LocalDate?,
        /** True when [daysRemaining] is at or below [LOW_STOCK_DAYS]. */
        val isLowStock: Boolean
    )

    /** Threshold (inclusive) below which we surface the low-stock warning banner. */
    const val LOW_STOCK_DAYS: Int = 7

    /** Build a forecast for one medicine. Today's date is injected for testability. */
    fun forecast(medicine: Medicine, today: LocalDate = LocalDate.now()): Forecast {
        val pillCount = medicine.pillCount
        val perDose = medicine.pillsPerDose.coerceAtLeast(1)
        val dosesPerDay = medicine.getTimesList().size

        if (pillCount == null || dosesPerDay == 0) {
            return Forecast(medicine, daysRemaining = null, runOutDate = null, isLowStock = false)
        }

        val pillsPerDay = perDose * dosesPerDay
        val days = if (pillsPerDay > 0) pillCount / pillsPerDay else Int.MAX_VALUE
        val runOut = today.plusDays(days.toLong())
        return Forecast(
            medicine = medicine,
            daysRemaining = days,
            runOutDate = runOut,
            isLowStock = days <= LOW_STOCK_DAYS
        )
    }

    /** Forecasts for every medicine. Includes meds with no pillCount (daysRemaining = null). */
    fun forecastAll(medicines: List<Medicine>, today: LocalDate = LocalDate.now()): List<Forecast> =
        medicines.map { forecast(it, today) }

    /** Convenience subset: just the meds that need attention soon. */
    fun lowStock(medicines: List<Medicine>, today: LocalDate = LocalDate.now()): List<Forecast> =
        forecastAll(medicines, today).filter { it.isLowStock && it.daysRemaining != null }
}

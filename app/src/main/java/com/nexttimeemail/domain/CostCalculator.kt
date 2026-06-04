package com.nexttimeemail.domain

import com.nexttimeemail.data.Attendee
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

private const val MILLIS_PER_HOUR = 3_600_000.0

/**
 * Pure, side-effect-free cost maths. All attendees share one currency (set globally),
 * so amounts are plain numbers and only get a currency symbol at formatting time.
 */
object CostCalculator {

    /** Combined per-hour cost of the whole roster. */
    fun perHourTotal(attendees: List<Attendee>): Double =
        attendees.sumOf { it.hourlyRate.coerceAtLeast(0.0) }

    /** Cost accrued after [elapsedMillis] given a per-hour rate. */
    fun costAtElapsed(perHour: Double, elapsedMillis: Long): Double =
        perHour * (elapsedMillis / MILLIS_PER_HOUR)

    /** Formats an amount using the device locale and the given ISO currency code. */
    fun formatMoney(amount: Double, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        val format = NumberFormat.getCurrencyInstance(locale)
        runCatching { format.currency = Currency.getInstance(currencyCode) }
        return format.format(amount)
    }

    fun defaultCurrencyCode(locale: Locale = Locale.getDefault()): String =
        runCatching { Currency.getInstance(locale).currencyCode }.getOrDefault("USD")

    /**
     * How many whole [threshold] steps the cost has reached, used to drive the
     * reminder buzz (threshold 100 -> 1 at 100, 2 at 200, ...). Returns 0 when the
     * reminder is disabled ([threshold] <= 0).
     */
    fun reminderStep(cost: Double, threshold: Double): Int {
        if (threshold <= 0.0) return 0
        return (cost / threshold).toInt()
    }
}

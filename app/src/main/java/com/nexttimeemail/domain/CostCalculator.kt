package com.nexttimeemail.domain

import com.nexttimeemail.data.Attendee
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

private const val MILLIS_PER_HOUR = 3_600_000.0

/**
 * Pure, side-effect-free cost maths. Amounts are kept per currency because the app
 * intentionally never performs FX conversion between attendees.
 */
object CostCalculator {

    /** Combined per-hour cost of the roster, grouped by currency code. */
    fun perHourByCurrency(attendees: List<Attendee>): Map<String, Double> =
        attendees
            .filter { it.hourlyRate > 0.0 }
            .groupBy { it.currencyCode }
            .mapValues { (_, group) -> group.sumOf { it.hourlyRate } }

    /** Cost accrued after [elapsedMillis], grouped by currency code. */
    fun costAtElapsed(perHour: Map<String, Double>, elapsedMillis: Long): Map<String, Double> {
        val hours = elapsedMillis / MILLIS_PER_HOUR
        return perHour.mapValues { (_, rate) -> rate * hours }
    }

    /** Formats a single amount using the device locale and the given ISO currency code. */
    fun formatMoney(amount: Double, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        val format = NumberFormat.getCurrencyInstance(locale)
        runCatching { format.currency = Currency.getInstance(currencyCode) }
        return format.format(amount)
    }

    /**
     * Joins a per-currency cost map into one display string, e.g. "1,200 Kč + €300".
     * Returns a formatted zero in the locale currency when the map is empty.
     */
    fun formatTotals(
        costs: Map<String, Double>,
        locale: Locale = Locale.getDefault(),
    ): String {
        if (costs.isEmpty()) return formatMoney(0.0, defaultCurrencyCode(locale), locale)
        return costs.entries
            .sortedByDescending { it.value }
            .joinToString(" + ") { (code, amount) -> formatMoney(amount, code, locale) }
    }

    fun defaultCurrencyCode(locale: Locale = Locale.getDefault()): String =
        runCatching { Currency.getInstance(locale).currencyCode }.getOrDefault("USD")

    /**
     * How many whole [threshold] steps the cost has reached, used to drive the
     * reminder buzz (threshold 100 -> 1 at 100, 2 at 200, ...). With multiple
     * currencies the highest step reached by any single currency wins. Returns 0
     * when the reminder is disabled ([threshold] <= 0).
     */
    fun reminderStep(costs: Map<String, Double>, threshold: Double): Int {
        if (threshold <= 0.0) return 0
        return costs.values.maxOfOrNull { (it / threshold).toInt() } ?: 0
    }
}

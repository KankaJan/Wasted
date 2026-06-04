package com.nexttimeemail.domain

import com.nexttimeemail.data.Attendee
import com.nexttimeemail.data.RateType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.util.Locale
import org.junit.Test

class CostCalculatorTest {

    @Test
    fun mandayRateIsNormalisedToEightHours() {
        val attendee = Attendee(name = "A", rateType = RateType.MANDAY, rateValue = 800.0, currencyCode = "USD")
        assertEquals(100.0, attendee.hourlyRate, 0.0001)
    }

    @Test
    fun perHourGroupsByCurrency() {
        val attendees = listOf(
            Attendee(name = "A", rateType = RateType.HOURLY, rateValue = 50.0, currencyCode = "USD"),
            Attendee(name = "B", rateType = RateType.HOURLY, rateValue = 30.0, currencyCode = "USD"),
            Attendee(name = "C", rateType = RateType.MANDAY, rateValue = 800.0, currencyCode = "EUR"),
        )
        val perHour = CostCalculator.perHourByCurrency(attendees)
        assertEquals(80.0, perHour.getValue("USD"), 0.0001)
        assertEquals(100.0, perHour.getValue("EUR"), 0.0001)
    }

    @Test
    fun zeroRateAttendeesAreIgnored() {
        val attendees = listOf(
            Attendee(name = "A", rateType = RateType.HOURLY, rateValue = 0.0, currencyCode = "USD"),
        )
        assertTrue(CostCalculator.perHourByCurrency(attendees).isEmpty())
    }

    @Test
    fun costAtHalfHourIsHalfTheHourlyRate() {
        val perHour = mapOf("USD" to 120.0)
        val cost = CostCalculator.costAtElapsed(perHour, elapsedMillis = 1_800_000) // 30 min
        assertEquals(60.0, cost.getValue("USD"), 0.0001)
    }

    @Test
    fun formatMoneyUsesGivenCurrency() {
        val formatted = CostCalculator.formatMoney(1234.5, "USD", Locale.US)
        assertEquals("$1,234.50", formatted)
    }
}

package com.nexttimeemail.domain

import com.nexttimeemail.data.Attendee
import com.nexttimeemail.data.RateType
import org.junit.Assert.assertEquals
import java.util.Locale
import org.junit.Test

class CostCalculatorTest {

    @Test
    fun mandayRateIsNormalisedToEightHours() {
        val attendee = Attendee(name = "A", rateType = RateType.MANDAY, rateValue = 800.0)
        assertEquals(100.0, attendee.hourlyRate, 0.0001)
    }

    @Test
    fun perHourTotalSumsTheRoster() {
        val attendees = listOf(
            Attendee(name = "A", rateType = RateType.HOURLY, rateValue = 50.0),
            Attendee(name = "B", rateType = RateType.HOURLY, rateValue = 30.0),
            Attendee(name = "C", rateType = RateType.MANDAY, rateValue = 800.0), // -> 100/h
        )
        assertEquals(180.0, CostCalculator.perHourTotal(attendees), 0.0001)
    }

    @Test
    fun zeroRateAttendeesContributeNothing() {
        val attendees = listOf(
            Attendee(name = "A", rateType = RateType.HOURLY, rateValue = 0.0),
        )
        assertEquals(0.0, CostCalculator.perHourTotal(attendees), 0.0001)
    }

    @Test
    fun costAtHalfHourIsHalfTheHourlyRate() {
        val cost = CostCalculator.costAtElapsed(perHour = 120.0, elapsedMillis = 1_800_000) // 30 min
        assertEquals(60.0, cost, 0.0001)
    }

    @Test
    fun reminderStepCountsWholeThresholdsCrossed() {
        assertEquals(0, CostCalculator.reminderStep(cost = 99.0, threshold = 100.0))
        assertEquals(1, CostCalculator.reminderStep(cost = 100.0, threshold = 100.0))
        assertEquals(3, CostCalculator.reminderStep(cost = 350.0, threshold = 100.0))
    }

    @Test
    fun reminderStepIsDisabledWhenThresholdNotPositive() {
        assertEquals(0, CostCalculator.reminderStep(cost = 999.0, threshold = 0.0))
    }

    @Test
    fun formatMoneyUsesGivenCurrency() {
        val formatted = CostCalculator.formatMoney(1234.5, "USD", Locale.US)
        assertEquals("$1,234.50", formatted)
    }
}

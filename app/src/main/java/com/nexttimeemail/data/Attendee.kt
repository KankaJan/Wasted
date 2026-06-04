package com.nexttimeemail.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A meeting participant and their cost. [rateValue] is expressed in [currencyCode]
 * and interpreted according to [rateType].
 */
@Entity(tableName = "attendees")
data class Attendee(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val email: String? = null,
    val rateType: RateType = RateType.HOURLY,
    val rateValue: Double = 0.0,
    val currencyCode: String = "USD",
) {
    /** The participant's cost per hour, normalising manday rates to an hourly figure. */
    val hourlyRate: Double
        get() = when (rateType) {
            RateType.HOURLY -> rateValue
            RateType.MANDAY -> rateValue / RateType.HOURS_PER_MANDAY
        }
}

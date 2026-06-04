package com.nexttimeemail.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A meeting participant and their cost. [rateValue] is interpreted according to
 * [rateType]. The currency is a single global app setting, not stored per attendee.
 */
@Entity(tableName = "attendees")
data class Attendee(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val email: String? = null,
    val rateType: RateType = RateType.HOURLY,
    val rateValue: Double = 0.0,
) {
    /** The participant's cost per hour, normalising manday rates to an hourly figure. */
    val hourlyRate: Double
        get() = when (rateType) {
            RateType.HOURLY -> rateValue
            RateType.MANDAY -> rateValue / RateType.HOURS_PER_MANDAY
        }
}

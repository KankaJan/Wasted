package com.nexttimeemail.data

/**
 * How an attendee's pay is expressed. A manday is treated as a fixed number of
 * working hours so any rate can be reduced to a common per-hour figure.
 */
enum class RateType {
    HOURLY,
    MANDAY;

    companion object {
        /** Working hours in a single manday. */
        const val HOURS_PER_MANDAY = 8.0
    }
}

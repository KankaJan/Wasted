package com.nexttimeemail.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Formats an elapsed duration as HH:MM:SS. */
fun formatElapsed(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = TimeUnit.SECONDS.toHours(totalSeconds)
    val minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}

/** Localised date label for an epoch-millis timestamp, e.g. "Jun 4, 2026". */
fun formatDate(epochMillis: Long, locale: Locale = Locale.getDefault()): String {
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
}

/** Localised date + time label for an epoch-millis timestamp. */
fun formatDateTime(epochMillis: Long, locale: Locale = Locale.getDefault()): String {
    val dateTime = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    return dateTime.format(
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale),
    )
}

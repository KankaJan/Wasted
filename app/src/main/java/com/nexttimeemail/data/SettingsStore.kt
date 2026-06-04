package com.nexttimeemail.data

import android.content.Context

/**
 * Small persistent app settings backed by SharedPreferences.
 *
 * [reminderThreshold] is the cost step at which the app buzzes (e.g. 100 ->
 * buzz at 100, 200, 300...). A value of 0 means the reminder is off.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("nexttimeemail.settings", Context.MODE_PRIVATE)

    var reminderThreshold: Double
        get() = prefs.getFloat(KEY_REMINDER_THRESHOLD, 0f).toDouble()
        set(value) {
            prefs.edit().putFloat(KEY_REMINDER_THRESHOLD, value.toFloat().coerceAtLeast(0f)).apply()
        }

    private companion object {
        const val KEY_REMINDER_THRESHOLD = "reminder_threshold"
    }
}

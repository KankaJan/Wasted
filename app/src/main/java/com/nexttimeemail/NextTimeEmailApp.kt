package com.nexttimeemail

import android.app.Application
import com.nexttimeemail.data.AppDatabase
import com.nexttimeemail.data.MeetingRepository
import com.nexttimeemail.data.SettingsStore

/**
 * Holds the single repository and settings instances. Manual DI is enough for an
 * app this small — no Hilt/Dagger ceremony needed.
 */
class NextTimeEmailApp : Application() {

    val repository: MeetingRepository by lazy {
        val db = AppDatabase.get(this)
        MeetingRepository(db.attendeeDao(), db.meetingDao())
    }

    val settings: SettingsStore by lazy { SettingsStore(this) }
}

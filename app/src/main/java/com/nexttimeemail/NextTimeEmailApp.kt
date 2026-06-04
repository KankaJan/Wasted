package com.nexttimeemail

import android.app.Application
import com.nexttimeemail.data.AppDatabase
import com.nexttimeemail.data.MeetingRepository

/**
 * Holds the single repository instance. Manual DI is enough for an app this small —
 * no Hilt/Dagger ceremony needed.
 */
class NextTimeEmailApp : Application() {

    val repository: MeetingRepository by lazy {
        val db = AppDatabase.get(this)
        MeetingRepository(db.attendeeDao(), db.meetingDao())
    }
}

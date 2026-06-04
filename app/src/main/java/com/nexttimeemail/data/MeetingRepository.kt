package com.nexttimeemail.data

import kotlinx.coroutines.flow.Flow

/** Thin layer over the DAOs so view models depend on one type instead of two. */
class MeetingRepository(
    private val attendeeDao: AttendeeDao,
    private val meetingDao: MeetingDao,
) {
    val attendees: Flow<List<Attendee>> = attendeeDao.observeAll()
    val meetings: Flow<List<MeetingRecord>> = meetingDao.observeAll()

    suspend fun attendeesSnapshot(): List<Attendee> = attendeeDao.getAll()

    suspend fun upsertAttendee(attendee: Attendee) {
        if (attendee.id == 0L) attendeeDao.insert(attendee) else attendeeDao.update(attendee)
    }

    suspend fun deleteAttendee(attendee: Attendee) = attendeeDao.delete(attendee)

    suspend fun recordMeeting(record: MeetingRecord) {
        meetingDao.insert(record)
    }

    suspend fun clearHistory() = meetingDao.clear()
}

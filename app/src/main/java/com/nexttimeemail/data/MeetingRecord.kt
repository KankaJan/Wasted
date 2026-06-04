package com.nexttimeemail.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A finished meeting kept for the history list. [costSummary] is a ready-to-show,
 * already-formatted string (e.g. "1,200 Kč + €300") because a meeting can mix
 * currencies and we deliberately do not convert between them.
 */
@Entity(tableName = "meetings")
data class MeetingRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val durationMillis: Long,
    val attendeeCount: Int,
    val costSummary: String,
)

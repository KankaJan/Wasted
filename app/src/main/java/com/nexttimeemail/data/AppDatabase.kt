package com.nexttimeemail.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Attendee::class, MeetingRecord::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun attendeeDao(): AttendeeDao
    abstract fun meetingDao(): MeetingDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nexttimeemail.db",
                )
                    // Dropping the per-attendee currency column; no released data to preserve.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}

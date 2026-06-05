package com.nexttimeemail.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

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
                instance ?: run {
                    // Load SQLCipher's native library before opening the encrypted DB.
                    System.loadLibrary("sqlcipher")
                    Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "nexttimeemail.db",
                    )
                        // Encrypt the database on disk with SQLCipher, keyed by a random
                        // passphrase held in the Keystore-backed secure prefs. The roster
                        // holds names, emails and pay rates, so it must not sit in plaintext.
                        .openHelperFactory(
                            SupportOpenHelperFactory(SecureStorage.databasePassphrase(context)),
                        )
                        // Dropping the per-attendee currency column; no released data to preserve.
                        // (Switching to an encrypted DB also makes any pre-existing plaintext
                        // dev database unreadable, which destructive migration handles.)
                        .fallbackToDestructiveMigration()
                        .build().also { instance = it }
                }
            }
    }
}

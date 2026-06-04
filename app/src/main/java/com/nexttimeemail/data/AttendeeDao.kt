package com.nexttimeemail.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendeeDao {

    @Query("SELECT * FROM attendees ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Attendee>>

    @Query("SELECT * FROM attendees ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<Attendee>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attendee: Attendee): Long

    @Update
    suspend fun update(attendee: Attendee)

    @Delete
    suspend fun delete(attendee: Attendee)
}

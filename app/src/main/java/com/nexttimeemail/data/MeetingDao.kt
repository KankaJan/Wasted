package com.nexttimeemail.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MeetingDao {

    @Query("SELECT * FROM meetings ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<MeetingRecord>>

    @Insert
    suspend fun insert(record: MeetingRecord): Long

    @Query("DELETE FROM meetings")
    suspend fun clear()
}

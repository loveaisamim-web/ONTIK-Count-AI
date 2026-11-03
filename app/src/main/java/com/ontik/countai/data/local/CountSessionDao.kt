package com.ontik.countai.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CountSessionDao {

    @Query("SELECT * FROM count_sessions ORDER BY timestampMs DESC")
    fun observeAllSessions(): Flow<List<CountSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: CountSessionEntity): Long

    @Delete
    suspend fun deleteSession(session: CountSessionEntity)

    @Query("DELETE FROM count_sessions")
    suspend fun clearAllSessions()
}

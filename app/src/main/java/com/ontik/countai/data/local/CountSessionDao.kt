package com.ontik.countai.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CountSessionDao {

    @Query("SELECT * FROM count_sessions WHERE deleted = 0 ORDER BY timestampMs DESC")
    fun observeActiveSessions(): Flow<List<CountSessionEntity>>

    @Query("SELECT * FROM count_sessions WHERE syncStatus = 'PENDING_UPLOAD' OR syncStatus = 'FAILED'")
    suspend fun getUnsyncedSessions(): List<CountSessionEntity>

    @Query("SELECT * FROM count_sessions WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getSessionByRemoteId(remoteId: String): CountSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: CountSessionEntity): Long

    @Update
    suspend fun updateSession(session: CountSessionEntity)

    @Query("UPDATE count_sessions SET deleted = 1, updatedAtMs = :nowMs, syncStatus = 'PENDING_UPLOAD' WHERE id = :id")
    suspend fun softDeleteSession(id: Long, nowMs: Long)

    @Query("DELETE FROM count_sessions WHERE deleted = 1 AND updatedAtMs < :cutoffMs")
    suspend fun purgeDeletedOlderThan(cutoffMs: Long)

    @Query("DELETE FROM count_sessions")
    suspend fun clearAllSessionsHard()
}

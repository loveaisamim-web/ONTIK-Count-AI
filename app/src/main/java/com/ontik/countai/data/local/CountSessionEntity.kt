package com.ontik.countai.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ontik.countai.domain.model.SyncStatus
import java.util.UUID

@Entity(tableName = "count_sessions")
data class CountSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val remoteId: String = UUID.randomUUID().toString(),
    val timestampMs: Long,
    val updatedAtMs: Long = timestampMs,
    val label: String,
    val presetName: String,
    val finalCount: Int,
    val autoCount: Int,
    val manualDelta: Int,
    val direction: String,
    val durationSeconds: Long,
    val isTestModeSession: Boolean = false,
    val expectedTestCount: Int = 100,
    val accuracyErrorPercent: Float = 0f,
    val notes: String = "",
    val deleted: Boolean = false,
    val syncStatus: String = SyncStatus.PENDING_UPLOAD.name
)

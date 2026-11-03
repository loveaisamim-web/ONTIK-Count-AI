package com.ontik.countai.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "count_sessions")
data class CountSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestampMs: Long,
    val mode: String, // "LIVE" or "PHOTO"
    val finalCount: Int,
    val aiDetectedCount: Int,
    val manualDelta: Int,
    val direction: String,
    val confidenceThreshold: Float,
    val durationSeconds: Long,
    val modelName: String,
    val notes: String = ""
)

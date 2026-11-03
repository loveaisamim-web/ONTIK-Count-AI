package com.ontik.countai.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "count_sessions")
data class CountSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestampMs: Long,
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
    val notes: String = ""
)

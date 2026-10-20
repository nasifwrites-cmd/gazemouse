package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calibration_profiles")
data class CalibrationProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val gridPointsCount: Int = 9,
    val accuracyScore: Float = 0.0f,
    val matrixJson: String,
    val pointsDataJson: String,
    val isActive: Boolean = false
)

@Entity(tableName = "click_logs")
data class ClickLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val cursorX: Float,
    val cursorY: Float,
    val decibels: Float,
    val eventType: String // "SNAP_CLICK", "TARGET_HIT", "BUTTON_CLICK"
)

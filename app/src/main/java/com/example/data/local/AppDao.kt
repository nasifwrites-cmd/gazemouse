package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CalibrationDao {
    @Query("SELECT * FROM calibration_profiles ORDER BY createdAt DESC")
    fun getAllProfiles(): Flow<List<CalibrationProfileEntity>>

    @Query("SELECT * FROM calibration_profiles WHERE isActive = 1 LIMIT 1")
    fun getActiveProfile(): Flow<CalibrationProfileEntity?>

    @Query("SELECT * FROM calibration_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProfileOnce(): CalibrationProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: CalibrationProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: CalibrationProfileEntity)

    @Query("UPDATE calibration_profiles SET isActive = 0")
    suspend fun deactivateAllProfiles()

    @Query("UPDATE calibration_profiles SET isActive = 1 WHERE id = :profileId")
    suspend fun setActiveProfile(profileId: Long)

    @Delete
    suspend fun deleteProfile(profile: CalibrationProfileEntity)

    @Query("DELETE FROM calibration_profiles")
    suspend fun clearAll()
}

@Dao
interface ClickLogDao {
    @Query("SELECT * FROM click_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentLogs(): Flow<List<ClickLogEntity>>

    @Insert
    suspend fun insertLog(log: ClickLogEntity): Long

    @Query("DELETE FROM click_logs")
    suspend fun clearLogs()

    @Query("SELECT COUNT(*) FROM click_logs")
    fun getLogCount(): Flow<Int>
}

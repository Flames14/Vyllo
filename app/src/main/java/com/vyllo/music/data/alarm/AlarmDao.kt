package com.vyllo.music.data.alarm

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for alarm operations.
 */
@Dao
interface AlarmDao {

    @Query("SELECT * FROM alarms ORDER BY hour, minute ASC")
    fun getAllAlarms(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms ORDER BY hour, minute ASC")
    suspend fun getAllAlarmsList(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id LIMIT 1")
    suspend fun getAlarmById(id: Long): AlarmEntity?

    @Query("SELECT * FROM alarms WHERE isEnabled = 1 ORDER BY hour, minute ASC")
    fun getEnabledAlarms(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE isEnabled = 1 ORDER BY hour, minute ASC")
    suspend fun getEnabledAlarmsList(): List<AlarmEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: AlarmEntity): Long

    @Update
    suspend fun updateAlarm(alarm: AlarmEntity)

    @Delete
    suspend fun deleteAlarm(alarm: AlarmEntity)

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun deleteAlarmById(id: Long)

    @Query("UPDATE alarms SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun toggleAlarm(id: Long, isEnabled: Boolean)

    @Query("SELECT COUNT(*) FROM alarms")
    suspend fun getAlarmCount(): Int
}

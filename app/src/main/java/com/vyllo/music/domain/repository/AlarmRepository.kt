package com.vyllo.music.domain.repository

import com.vyllo.music.domain.model.AlarmModel
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for alarm operations.
 * Follows Clean Architecture - domain layer knows only about interfaces.
 */
interface AlarmRepository {

    /**
     * Observe all alarms as a Flow.
     */
    fun getAllAlarms(): Flow<List<AlarmModel>>

    /**
     * Get all alarms as a list (one-time fetch).
     */
    suspend fun getAllAlarmsList(): List<AlarmModel>

    /**
     * Observe only enabled alarms.
     */
    fun getEnabledAlarms(): Flow<List<AlarmModel>>

    /**
     * Get all enabled alarms as a list.
     */
    suspend fun getEnabledAlarmsList(): List<AlarmModel>

    /**
     * Get a specific alarm by ID.
     */
    suspend fun getAlarmById(id: Long): AlarmModel?

    /**
     * Create a new alarm.
     * @return The ID of the created alarm.
     */
    suspend fun createAlarm(alarm: AlarmModel): Long

    /**
     * Update an existing alarm.
     */
    suspend fun updateAlarm(alarm: AlarmModel)

    /**
     * Delete an alarm.
     */
    suspend fun deleteAlarm(alarm: AlarmModel)

    /**
     * Delete an alarm by ID.
     */
    suspend fun deleteAlarmById(id: Long)

    /**
     * Toggle alarm enabled state.
     */
    suspend fun toggleAlarm(id: Long, isEnabled: Boolean)

    /**
     * Get count of all alarms.
     */
    suspend fun getAlarmCount(): Int
}

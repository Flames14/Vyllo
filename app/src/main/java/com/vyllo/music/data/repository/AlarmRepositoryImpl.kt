package com.vyllo.music.data.repository

import com.vyllo.music.data.alarm.AlarmDao
import com.vyllo.music.data.alarm.AlarmEntity
import com.vyllo.music.domain.model.AlarmModel
import com.vyllo.music.domain.repository.AlarmRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of AlarmRepository.
 */
@Singleton
class AlarmRepositoryImpl @Inject constructor(
    private val alarmDao: AlarmDao
) : AlarmRepository {

    override fun getAllAlarms(): Flow<List<AlarmModel>> {
        return alarmDao.getAllAlarms()
            .map { entities -> entities.map { it.toAlarmModel() } }
            .distinctUntilChanged()
    }

    override suspend fun getAllAlarmsList(): List<AlarmModel> {
        return alarmDao.getAllAlarmsList().map { it.toAlarmModel() }
    }

    override fun getEnabledAlarms(): Flow<List<AlarmModel>> {
        return alarmDao.getEnabledAlarms()
            .map { entities -> entities.map { it.toAlarmModel() } }
            .distinctUntilChanged()
    }

    override suspend fun getEnabledAlarmsList(): List<AlarmModel> {
        return alarmDao.getEnabledAlarmsList().map { it.toAlarmModel() }
    }

    override suspend fun getAlarmById(id: Long): AlarmModel? {
        return alarmDao.getAlarmById(id)?.toAlarmModel()
    }

    override suspend fun createAlarm(alarm: AlarmModel): Long {
        val entity = AlarmEntity.fromAlarmModel(alarm)
        return alarmDao.insertAlarm(entity)
    }

    override suspend fun updateAlarm(alarm: AlarmModel) {
        val entity = AlarmEntity.fromAlarmModel(alarm)
        alarmDao.updateAlarm(entity)
    }

    override suspend fun deleteAlarm(alarm: AlarmModel) {
        val entity = AlarmEntity.fromAlarmModel(alarm)
        alarmDao.deleteAlarm(entity)
    }

    override suspend fun deleteAlarmById(id: Long) {
        alarmDao.deleteAlarmById(id)
    }

    override suspend fun toggleAlarm(id: Long, isEnabled: Boolean) {
        alarmDao.toggleAlarm(id, isEnabled)
    }

    override suspend fun getAlarmCount(): Int {
        return alarmDao.getAlarmCount()
    }
}

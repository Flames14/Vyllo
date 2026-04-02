package com.vyllo.music.domain.usecase

import com.vyllo.music.domain.model.AlarmModel
import com.vyllo.music.domain.repository.AlarmRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for observing all alarms.
 */
class GetAllAlarmsUseCase @Inject constructor(
    private val alarmRepository: AlarmRepository
) {
    operator fun invoke(): Flow<List<AlarmModel>> {
        return alarmRepository.getAllAlarms()
    }
}

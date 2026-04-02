package com.vyllo.music.domain.usecase

import com.vyllo.music.domain.model.AlarmModel
import com.vyllo.music.domain.repository.AlarmRepository
import javax.inject.Inject

/**
 * Use case for creating a new alarm.
 */
class CreateAlarmUseCase @Inject constructor(
    private val alarmRepository: AlarmRepository
) {
    suspend operator fun invoke(alarm: AlarmModel): Long {
        return alarmRepository.createAlarm(alarm)
    }
}

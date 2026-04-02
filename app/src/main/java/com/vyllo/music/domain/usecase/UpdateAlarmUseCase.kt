package com.vyllo.music.domain.usecase

import com.vyllo.music.domain.model.AlarmModel
import com.vyllo.music.domain.repository.AlarmRepository
import javax.inject.Inject

/**
 * Use case for updating an existing alarm.
 */
class UpdateAlarmUseCase @Inject constructor(
    private val alarmRepository: AlarmRepository
) {
    suspend operator fun invoke(alarm: AlarmModel) {
        alarmRepository.updateAlarm(alarm)
    }
}

package com.vyllo.music.domain.usecase

import com.vyllo.music.domain.repository.AlarmRepository
import javax.inject.Inject

/**
 * Use case for deleting an alarm.
 */
class DeleteAlarmUseCase @Inject constructor(
    private val alarmRepository: AlarmRepository
) {
    suspend operator fun invoke(alarmId: Long) {
        alarmRepository.deleteAlarmById(alarmId)
    }
}

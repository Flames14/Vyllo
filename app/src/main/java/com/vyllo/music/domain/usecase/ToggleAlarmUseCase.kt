package com.vyllo.music.domain.usecase

import com.vyllo.music.domain.repository.AlarmRepository
import javax.inject.Inject

/**
 * Use case for toggling alarm enabled state.
 */
class ToggleAlarmUseCase @Inject constructor(
    private val alarmRepository: AlarmRepository
) {
    suspend operator fun invoke(id: Long, isEnabled: Boolean) {
        alarmRepository.toggleAlarm(id, isEnabled)
    }
}

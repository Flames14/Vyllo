package com.vyllo.music.domain.manager.player

import com.vyllo.music.PlayerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns sleep-timer countdown logic extracted from PlayerViewModel.
 * No behavior change — verbatim logic moved here.
 */
class SleepTimerController(
    private val updateState: (((PlayerUiState) -> PlayerUiState)) -> Unit,
    private val scopeProvider: () -> CoroutineScope
) {
    private var sleepTimerJob: Job? = null

    fun setSleepTimer(minutes: Int, onTimerFinished: () -> Unit, onFadeVolume: ((Float) -> Unit)? = null) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            updateState { it.copy(sleepTimerRemainingSeconds = null, isSleepTimerActive = false) }
            onFadeVolume?.invoke(1.0f)
            return
        }

        val totalSeconds = (minutes * 60).toLong()
        updateState { it.copy(sleepTimerRemainingSeconds = totalSeconds, isSleepTimerActive = true) }
        sleepTimerJob = scopeProvider().launch {
            var remaining = totalSeconds
            val fadeWindowSeconds = 30L.coerceAtMost(totalSeconds)
            while (remaining > 0 && isActive) {
                delay(1000)
                remaining--
                updateState { it.copy(sleepTimerRemainingSeconds = remaining) }
                if (remaining <= fadeWindowSeconds && onFadeVolume != null) {
                    val fadeRatio = (remaining.toFloat() / fadeWindowSeconds).coerceIn(0.0f, 1.0f)
                    onFadeVolume(fadeRatio)
                }
            }
            updateState { it.copy(sleepTimerRemainingSeconds = null, isSleepTimerActive = false) }
            onTimerFinished()
            onFadeVolume?.invoke(1.0f)
        }
    }

    fun cancelSleepTimer(onResetVolume: (() -> Unit)? = null) {
        sleepTimerJob?.cancel()
        updateState { it.copy(sleepTimerRemainingSeconds = null, isSleepTimerActive = false) }
        onResetVolume?.invoke()
    }
}

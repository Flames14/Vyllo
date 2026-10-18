package com.vyllo.music.ui.alarm

import com.vyllo.music.domain.model.AlarmModel

/**
 * The countdown wording for the next alarm, as structured data.
 *
 * Extracted from [AlarmViewModel] so the "which unit do we show" decision can be
 * tested without Android string resources or a live alarm list.
 */
sealed interface AlarmCountdown {
    /** No enabled alarms, or none scheduled — nothing to show. */
    data object Idle : AlarmCountdown

    /** The next trigger time has already passed. */
    data object Ringing : AlarmCountdown

    data class HoursMinutes(val hours: Long, val minutes: Long) : AlarmCountdown
    data class MinutesSeconds(val minutes: Long, val seconds: Long) : AlarmCountdown
    data class Seconds(val seconds: Long) : AlarmCountdown
}

object AlarmCountdownFormatter {

    fun next(enabledAlarms: List<AlarmModel>, now: Long): AlarmCountdown {
        if (enabledAlarms.isEmpty()) return AlarmCountdown.Idle

        // Find the absolute earliest next trigger time
        val nextAlarmTime = enabledAlarms.map { it.calculateNextTriggerTime() }.minOrNull()
            ?: return AlarmCountdown.Idle

        val millisUntil = nextAlarmTime - now
        if (millisUntil <= 0) return AlarmCountdown.Ringing

        val hours = millisUntil / 3600000
        val minutes = (millisUntil % 3600000) / 60000
        val seconds = (millisUntil % 60000) / 1000

        return when {
            hours > 0 -> AlarmCountdown.HoursMinutes(hours, minutes)
            minutes > 0 -> AlarmCountdown.MinutesSeconds(minutes, seconds)
            else -> AlarmCountdown.Seconds(seconds)
        }
    }
}

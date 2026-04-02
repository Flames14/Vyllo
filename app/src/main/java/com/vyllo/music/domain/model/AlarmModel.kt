package com.vyllo.music.domain.model

/**
 * Represents an alarm configuration.
 * Immutable data class for thread safety.
 */
data class AlarmModel(
    val id: Long = 0L,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean = true,
    val label: String = "",
    val repeatDays: Set<DayOfWeek> = emptySet(),
    val soundType: SoundType = SoundType.DEFAULT,
    val downloadedSongUrl: String? = null,
    val downloadedSongTitle: String? = null,
    val volume: Int = 80,
    val gradualVolume: Boolean = true,
    val gradualVolumeDurationSecs: Int = 30,
    val snoozeDurationMins: Int = 5,
    val vibrationEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Get alarm time in minutes since midnight for easy comparison.
     */
    fun getMinutesSinceMidnight(): Int = hour * 60 + minute

    /**
     * Check if alarm should trigger on a specific day.
     */
    fun shouldTriggerOnDay(day: DayOfWeek): Boolean {
        return repeatDays.isEmpty() || repeatDays.contains(day)
    }

    /**
     * Get display time in 12-hour format (e.g., "7:00 AM").
     */
    fun getDisplayTime(): String {
        val amPm = if (hour < 12) "AM" else "PM"
        val displayHour = if (hour == 0 || hour == 12) 12 else hour % 12
        val displayMinute = if (minute < 10) "0$minute" else "$minute"
        return "$displayHour:$displayMinute $amPm"
    }

    /**
     * Get repeat days display string.
     */
    fun getRepeatDaysDisplay(): String {
        if (repeatDays.isEmpty()) return "Once"
        if (repeatDays.size == 7) return "Every day"
        
        val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, 
                            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        val weekends = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        
        return when {
            repeatDays == weekdays -> "Weekdays"
            repeatDays == weekends -> "Weekends"
            else -> repeatDays.joinToString(" ") { it.name.take(3) }
        }
    }

    /**
     * Calculate the next trigger time for this specific alarm.
     */
    fun calculateNextTriggerTime(): Long {
        val now = java.util.Calendar.getInstance()
        val alarmTime = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        // If no repeat days, it's a one-time alarm
        if (repeatDays.isEmpty()) {
            if (alarmTime.timeInMillis <= now.timeInMillis) {
                alarmTime.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            return alarmTime.timeInMillis
        }

        // Find the next day the alarm should trigger
        val currentDayIdx = (now.get(java.util.Calendar.DAY_OF_WEEK) - 1) // 0=Sunday
        val currentDay = DayOfWeek.values()[currentDayIdx]

        // Check if today is a trigger day and time hasn't passed
        if (repeatDays.contains(currentDay) && alarmTime.timeInMillis > now.timeInMillis) {
            return alarmTime.timeInMillis
        }

        // Check next 7 days
        for (i in 1..7) {
            val nextDayIdx = (currentDayIdx + i) % 7
            val nextDay = DayOfWeek.values()[nextDayIdx]
            if (repeatDays.contains(nextDay)) {
                alarmTime.add(java.util.Calendar.DAY_OF_YEAR, i)
                return alarmTime.timeInMillis
            }
        }

        return alarmTime.timeInMillis
    }
}

/**
 * Days of the week for alarm repeat configuration.
 */
enum class DayOfWeek {
    SUNDAY,
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY
}

/**
 * Type of sound to play for the alarm.
 */
enum class SoundType {
    DEFAULT,        // Built-in alarm sound
    DOWNLOADED_SONG // User's downloaded song
}

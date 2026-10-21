package com.vyllo.music.domain.model

/**
 * Recurring "pause playback at this time" schedule. Days use ISO numbering
 * (1 = Monday … 7 = Sunday) to match [java.util.Calendar.DAY_OF_WEEK] after
 * conversion, and the trigger only fires once per calendar day.
 */
data class SleepSchedule(
    val enabled: Boolean = false,
    val days: Set<Int> = emptySet(),
    val hour: Int = 23,
    val minute: Int = 0
) {
    val minuteOfDay: Int get() = hour * 60 + minute

    fun isValidDay(day: Int): Boolean = day in 1..7

    fun isDue(
        dayOfWeek: Int,
        minuteOfDay: Int,
        todayEpochDay: Long,
        lastFiredEpochDay: Long
    ): Boolean {
        if (!enabled) return false
        if (dayOfWeek !in days) return false
        if (minuteOfDay < this.minuteOfDay) return false
        return lastFiredEpochDay != todayEpochDay
    }

    companion object {
        /** Wire format: `enabled;1,2,3;23;30` — compact and easy to inspect. */
        fun encode(schedule: SleepSchedule): String =
            "${if (schedule.enabled) 1 else 0};" +
                schedule.days.sorted().joinToString(",") + ";" +
                "${schedule.hour};${schedule.minute}"

        fun decode(raw: String?): SleepSchedule {
            if (raw.isNullOrBlank()) return SleepSchedule()
            val parts = raw.split(';')
            if (parts.size != 4) return SleepSchedule()
            val days = parts[1].split(',')
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 1..7 }
                .toSet()
            val hour = parts[2].toIntOrNull()?.coerceIn(0, 23) ?: 23
            val minute = parts[3].toIntOrNull()?.coerceIn(0, 59) ?: 0
            return SleepSchedule(
                enabled = parts[0] == "1" && days.isNotEmpty(),
                days = days,
                hour = hour,
                minute = minute
            )
        }
    }
}

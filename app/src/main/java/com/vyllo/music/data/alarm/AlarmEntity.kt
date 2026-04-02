package com.vyllo.music.data.alarm

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.vyllo.music.domain.model.AlarmModel
import com.vyllo.music.domain.model.DayOfWeek
import com.vyllo.music.domain.model.SoundType

/**
 * Room entity representing an alarm stored in the database.
 */
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean = true,
    val label: String = "",
    val repeatDaysString: String = "", // Comma-separated day names
    val soundType: String = SoundType.DEFAULT.name,
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
     * Convert entity to domain model.
     */
    fun toAlarmModel(): AlarmModel {
        return AlarmModel(
            id = id,
            hour = hour,
            minute = minute,
            isEnabled = isEnabled,
            label = label,
            repeatDays = parseRepeatDays(repeatDaysString),
            soundType = SoundType.valueOf(soundType),
            downloadedSongUrl = downloadedSongUrl,
            downloadedSongTitle = downloadedSongTitle,
            volume = volume,
            gradualVolume = gradualVolume,
            gradualVolumeDurationSecs = gradualVolumeDurationSecs,
            snoozeDurationMins = snoozeDurationMins,
            vibrationEnabled = vibrationEnabled,
            createdAt = createdAt
        )
    }

    /**
     * Create entity from domain model.
     */
    companion object {
        fun fromAlarmModel(alarm: AlarmModel): AlarmEntity {
            return AlarmEntity(
                id = alarm.id,
                hour = alarm.hour,
                minute = alarm.minute,
                isEnabled = alarm.isEnabled,
                label = alarm.label,
                repeatDaysString = formatRepeatDays(alarm.repeatDays),
                soundType = alarm.soundType.name,
                downloadedSongUrl = alarm.downloadedSongUrl,
                downloadedSongTitle = alarm.downloadedSongTitle,
                volume = alarm.volume,
                gradualVolume = alarm.gradualVolume,
                gradualVolumeDurationSecs = alarm.gradualVolumeDurationSecs,
                snoozeDurationMins = alarm.snoozeDurationMins,
                vibrationEnabled = alarm.vibrationEnabled,
                createdAt = alarm.createdAt
            )
        }

        private fun formatRepeatDays(days: Set<DayOfWeek>): String {
            return days.joinToString(",") { it.name }
        }

        private fun parseRepeatDays(daysString: String): Set<DayOfWeek> {
            if (daysString.isBlank()) return emptySet()
            return daysString.split(",").map { DayOfWeek.valueOf(it.trim()) }.toSet()
        }
    }
}

package com.vyllo.music.data.download

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.vyllo.music.data.alarm.AlarmDao
import com.vyllo.music.data.alarm.AlarmEntity

/**
 * Room database for storing download metadata.
 */
@Database(
    entities = [
        DownloadEntity::class, 
        PlaylistEntity::class, 
        PlaylistSongEntity::class, 
        HistoryEntity::class,
        AlarmEntity::class
    ], 
    version = 4, 
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class DownloadDatabase : RoomDatabase() {

    abstract fun downloadDao(): DownloadDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyDao(): HistoryDao
    abstract fun alarmDao(): AlarmDao

    companion object {
        @Volatile
        private var INSTANCE: DownloadDatabase? = null
        
        fun getInstance(context: Context): DownloadDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DownloadDatabase::class.java,
                    "downloads_db"
                )
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
        }
    }
}

/**
 * Type converters for Room to handle enum types.
 */
class Converters {
    @androidx.room.TypeConverter
    fun fromStatus(status: DownloadStatus): String = status.name

    @androidx.room.TypeConverter
    fun toStatus(value: String): DownloadStatus = DownloadStatus.valueOf(value)

    @androidx.room.TypeConverter
    fun fromMusicItemType(type: com.vyllo.music.domain.model.MusicItemType): String = type.name

    @androidx.room.TypeConverter
    fun toMusicItemType(value: String): com.vyllo.music.domain.model.MusicItemType =
        com.vyllo.music.domain.model.MusicItemType.valueOf(value)

    @androidx.room.TypeConverter
    fun fromDayOfWeek(day: com.vyllo.music.domain.model.DayOfWeek): String = day.name

    @androidx.room.TypeConverter
    fun toDayOfWeek(value: String): com.vyllo.music.domain.model.DayOfWeek =
        com.vyllo.music.domain.model.DayOfWeek.valueOf(value)

    @androidx.room.TypeConverter
    fun fromSoundType(type: com.vyllo.music.domain.model.SoundType): String = type.name

    @androidx.room.TypeConverter
    fun toSoundType(value: String): com.vyllo.music.domain.model.SoundType =
        com.vyllo.music.domain.model.SoundType.valueOf(value)
}

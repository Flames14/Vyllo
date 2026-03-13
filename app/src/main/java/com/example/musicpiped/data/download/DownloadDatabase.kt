package com.example.musicpiped.data.download

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Room database for storing download metadata.
 */
@Database(entities = [DownloadEntity::class, PlaylistEntity::class, PlaylistSongEntity::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class DownloadDatabase : RoomDatabase() {
    
    abstract fun downloadDao(): DownloadDao
    abstract fun playlistDao(): PlaylistDao
    
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
}

package com.vyllo.music.data.download

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database migrations.
 * Each migration preserves existing user data when the schema changes.
 */

/**
 * Migration from v1 to v2: Added PlaylistEntity and PlaylistSongEntity tables.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `PlaylistEntity` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT NOT NULL, " +
            "`createdAt` INTEGER NOT NULL DEFAULT 0)"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `PlaylistSongEntity` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`playlistId` INTEGER NOT NULL, " +
            "`songUrl` TEXT NOT NULL, " +
            "`songTitle` TEXT NOT NULL, " +
            "`songArtist` TEXT NOT NULL, " +
            "`songThumbnailUrl` TEXT NOT NULL, " +
            "`addedAt` INTEGER NOT NULL DEFAULT 0, " +
            "FOREIGN KEY(`playlistId`) REFERENCES `PlaylistEntity`(`id`) ON DELETE CASCADE)"
        )
    }
}

/**
 * Migration from v2 to v3: Added HistoryEntity table.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `HistoryEntity` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`url` TEXT NOT NULL, " +
            "`title` TEXT NOT NULL, " +
            "`uploader` TEXT NOT NULL, " +
            "`thumbnailUrl` TEXT NOT NULL, " +
            "`listenedAt` INTEGER NOT NULL DEFAULT 0)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_HistoryEntity_listenedAt` ON `HistoryEntity`(`listenedAt`)"
        )
    }
}

/**
 * Migration from v3 to v4: Added AlarmEntity table.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `AlarmEntity` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`hour` INTEGER NOT NULL, " +
            "`minute` INTEGER NOT NULL, " +
            "`label` TEXT NOT NULL DEFAULT '', " +
            "`isEnabled` INTEGER NOT NULL DEFAULT 1, " +
            "`repeatDays` TEXT NOT NULL DEFAULT '', " +
            "`soundType` TEXT NOT NULL DEFAULT 'DEFAULT', " +
            "`downloadedSongUrl` TEXT, " +
            "`downloadedSongTitle` TEXT, " +
            "`volume` INTEGER NOT NULL DEFAULT 80, " +
            "`gradualVolume` INTEGER NOT NULL DEFAULT 0, " +
            "`vibrationEnabled` INTEGER NOT NULL DEFAULT 1)"
        )
    }
}

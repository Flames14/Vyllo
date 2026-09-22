package com.vyllo.music.data.download

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database migrations.
 * Each migration must produce a schema identical to app/schemas/.../<version>.json
 * (Room validates identity + table shape after migrating).
 */

/**
 * Migration from v1 to v2: Added playlists + playlist_songs tables.
 *
 * Table/column names MUST match PlaylistEntity ("playlists") and
 * PlaylistSongEntity ("playlist_songs") — including composite PK and index.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlists` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL)"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlist_songs` (" +
                "`playlistId` INTEGER NOT NULL, " +
                "`url` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`uploader` TEXT NOT NULL, " +
                "`thumbnailUrl` TEXT NOT NULL, " +
                "`addedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`playlistId`, `url`), " +
                "FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_playlist_songs_playlistId` " +
                "ON `playlist_songs` (`playlistId`)"
        )
    }
}

/**
 * Migration from v2 to v3: Added playback_history table.
 * Matches HistoryEntity / schema v3.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `playback_history` (" +
                "`url` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`uploader` TEXT NOT NULL, " +
                "`thumbnailUrl` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`lastPlayedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`url`))"
        )
    }
}

/**
 * Migration from v3 to v4: Added alarms table.
 * Matches AlarmEntity / schema 4.json exactly:
 * alarms(id, hour, minute, isEnabled, label, repeatDaysString, soundType,
 *        downloadedSongUrl, downloadedSongTitle, volume, gradualVolume,
 *        gradualVolumeDurationSecs, snoozeDurationMins, vibrationEnabled, createdAt)
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `alarms` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`hour` INTEGER NOT NULL, " +
                "`minute` INTEGER NOT NULL, " +
                "`isEnabled` INTEGER NOT NULL, " +
                "`label` TEXT NOT NULL, " +
                "`repeatDaysString` TEXT NOT NULL, " +
                "`soundType` TEXT NOT NULL, " +
                "`downloadedSongUrl` TEXT, " +
                "`downloadedSongTitle` TEXT, " +
                "`volume` INTEGER NOT NULL, " +
                "`gradualVolume` INTEGER NOT NULL, " +
                "`gradualVolumeDurationSecs` INTEGER NOT NULL, " +
                "`snoozeDurationMins` INTEGER NOT NULL, " +
                "`vibrationEnabled` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
    }
}

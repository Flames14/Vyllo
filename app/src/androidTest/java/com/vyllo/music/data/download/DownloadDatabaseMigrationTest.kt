package com.vyllo.music.data.download

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Instrumentation tests for Room migrations 1→4 against the exported schema.
 * Validates that each migration produces the exact table shape Room expects.
 */
class DownloadDatabaseMigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DownloadDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To4_createsAllTablesAndPreservesDownloads() {
        helper.createDatabase(TEST_DB, 1).apply {
            // v1 only had the downloads table
            execSQL(
                "INSERT INTO downloads (url, title, uploader, thumbnailUrl, filePath, fileSize, downloadedAt, status) " +
                    "VALUES ('u1', 'T', 'A', 'th', '/p', 1, 100, 'COMPLETED')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            4,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4
        )

        // Existing data survives
        db.query("SELECT url FROM downloads").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.getString(0) == "u1")
        }

        // New tables exist with expected columns
        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name IN " +
                "('playlists','playlist_songs','playback_history','alarms')"
        ).use { c ->
            val names = mutableSetOf<String>()
            while (c.moveToNext()) names.add(c.getString(0))
            assertTrue("playlists missing", names.contains("playlists"))
            assertTrue("playlist_songs missing", names.contains("playlist_songs"))
            assertTrue("playback_history missing", names.contains("playback_history"))
            assertTrue("alarms missing", names.contains("alarms"))
        }

        // alarms schema shape matches Migration 3→4 / schema 4.json
        db.query("PRAGMA table_info(alarms)").use { c ->
            val cols = mutableSetOf<String>()
            while (c.moveToNext()) cols.add(c.getString(1))
            assertTrue(cols.containsAll(listOf(
                "id", "hour", "minute", "isEnabled", "label", "repeatDaysString",
                "soundType", "downloadedSongUrl", "downloadedSongTitle", "volume",
                "gradualVolume", "gradualVolumeDurationSecs", "snoozeDurationMins",
                "vibrationEnabled", "createdAt"
            )))
        }

        // playlist_songs foreign key index present
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='index_playlist_songs_playlistId'"
        ).use { c ->
            assertTrue("index missing", c.moveToFirst())
        }

        db.close()
    }

    @Test
    fun migrate1To4_isIdempotentForCreateIfNotExists() {
        helper.createDatabase(TEST_DB, 1).close()
        helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).use {
            // Running the raw CREATE TABLE IF NOT EXISTS statements again must not throw
            MIGRATION_1_2.migrate(it)
            MIGRATION_2_3.migrate(it)
            MIGRATION_3_4.migrate(it)
        }
    }
}

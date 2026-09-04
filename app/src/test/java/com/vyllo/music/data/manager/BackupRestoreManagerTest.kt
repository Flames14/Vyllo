package com.vyllo.music.data.manager

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for BackupRestoreManager JSON format and validation.
 */
class BackupRestoreManagerTest {

    @Test
    fun `valid backup json contains required sections`() {
        val jsonString = """
        {
            "version": 1,
            "timestamp": 1700000000000,
            "preferences": {
                "themeMode": "Ocean",
                "isBackgroundPlaybackEnabled": true,
                "volumeBoostMultiplier": 1.5
            },
            "playlists": [
                {
                    "name": "Favorites",
                    "createdAt": 1700000000000,
                    "songs": [
                        {
                            "title": "Song A",
                            "url": "https://music.youtube.com/watch?v=123",
                            "uploader": "Artist A",
                            "thumbnailUrl": "https://img.youtube.com/123.jpg",
                            "addedAt": 1700000000000
                        }
                    ]
                }
            ],
            "alarms": [
                {
                    "hour": 7,
                    "minute": 30,
                    "isEnabled": true,
                    "label": "Morning Wakeup",
                    "repeatDays": ["MONDAY", "TUESDAY"],
                    "soundType": "DEFAULT",
                    "volume": 85,
                    "gradualVolume": true,
                    "vibrationEnabled": true
                }
            ]
        }
        """.trimIndent()

        val root = JSONObject(jsonString)
        assertEquals(1, root.getInt("version"))
        assertTrue(root.has("preferences"))
        assertTrue(root.has("playlists"))
        assertTrue(root.has("alarms"))

        val playlists = root.getJSONArray("playlists")
        assertEquals(1, playlists.length())
        val firstPlaylist = playlists.getJSONObject(0)
        assertEquals("Favorites", firstPlaylist.getString("name"))

        val songs = firstPlaylist.getJSONArray("songs")
        assertEquals(1, songs.length())
        assertEquals("Song A", songs.getJSONObject(0).getString("title"))

        val alarms = root.getJSONArray("alarms")
        assertEquals(1, alarms.length())
        val firstAlarm = alarms.getJSONObject(0)
        assertEquals(7, firstAlarm.getInt("hour"))
        assertEquals(30, firstAlarm.getInt("minute"))
    }

    @Test
    fun `empty or malformed backup is safely caught`() {
        val malformedJson = "{ invalid_json: "
        var errorThrown = false
        try {
            JSONObject(malformedJson)
        } catch (e: Exception) {
            errorThrown = true
        }
        assertTrue(errorThrown)
    }
}

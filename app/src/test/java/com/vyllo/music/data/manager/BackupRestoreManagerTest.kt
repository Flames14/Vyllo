package com.vyllo.music.data.manager

import com.vyllo.music.data.alarm.AlarmDao
import com.vyllo.music.data.alarm.AlarmEntity
import com.vyllo.music.data.download.FavoriteEntity
import com.vyllo.music.data.download.FavoritesDao
import com.vyllo.music.data.download.HistoryDao
import com.vyllo.music.data.download.HistoryEntity
import com.vyllo.music.data.download.PlaylistDao
import com.vyllo.music.domain.model.MusicItemType
import com.vyllo.music.domain.model.PlaylistEntity
import com.vyllo.music.domain.model.PlaylistSongEntity
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Exercises the real BackupRestoreManager.restoreBackupJson path with fakes
 * for the DAO layer, so JSON schema drift is caught.
 */
class BackupRestoreManagerTest {

    private lateinit var playlistDao: PlaylistDao
    private lateinit var alarmDao: AlarmDao
    private lateinit var historyDao: HistoryDao
    private lateinit var favoritesDao: FavoritesDao
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var manager: BackupRestoreManager

    private val sampleBackup = """
        {
            "version": 1,
            "timestamp": 1700000000000,
            "preferences": {
                "themeMode": "Ocean",
                "isBackgroundPlaybackEnabled": true,
                "volumeBoostMultiplier": 1.5,
                "isQueueSticky": false
            },
            "playlists": [
                {
                    "name": "Favorites",
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
            ],
            "history": [
                {
                    "url": "https://music.youtube.com/watch?v=123",
                    "title": "Song A",
                    "uploader": "Artist A",
                    "thumbnailUrl": "https://img.youtube.com/123.jpg",
                    "type": "SONG",
                    "lastPlayedAt": 1700000000000
                }
            ],
            "favorites": [
                {
                    "url": "https://music.youtube.com/watch?v=123",
                    "title": "Song A",
                    "uploader": "Artist A",
                    "thumbnailUrl": "https://img.youtube.com/123.jpg",
                    "type": "SONG",
                    "rating": 1,
                    "likedAt": 1700000000000
                },
                {
                    "url": "https://music.youtube.com/watch?v=999",
                    "title": "Bogus",
                    "rating": 7,
                    "likedAt": 1700000000000
                }
            ]
        }
    """.trimIndent()

    @Before
    fun setUp() {
        playlistDao = mock()
        alarmDao = mock()
        historyDao = mock()
        favoritesDao = mock()
        preferenceManager = mock()
        manager = BackupRestoreManager(playlistDao, alarmDao, historyDao, favoritesDao, preferenceManager)
    }

    @Test
    fun `restore valid backup applies preferences playlists and alarms`() = runTest {
        whenever(playlistDao.getPlaylistByName("Favorites")).thenReturn(null)
        whenever(playlistDao.insertPlaylist(PlaylistEntity(name = "Favorites"))).thenReturn(42L)

        val result = manager.restoreBackupJson(sampleBackup)

        assertTrue(result.isSuccess)
        verify(preferenceManager).themeMode = "Ocean"
        verify(preferenceManager).isBackgroundPlaybackEnabled = true
        verify(preferenceManager).volumeBoostMultiplier = 1.5f
        verify(preferenceManager).isQueueSticky = false

        val songsCaptor = argumentCaptor<List<PlaylistSongEntity>>()
        verify(playlistDao).insertSongsToPlaylist(songsCaptor.capture())
        val songs = songsCaptor.firstValue
        assertEquals(1, songs.size)
        assertEquals("Song A", songs[0].title)
        assertEquals(42L, songs[0].playlistId)

        val alarmCaptor = argumentCaptor<AlarmEntity>()
        verify(alarmDao).insertAlarm(alarmCaptor.capture())
        val alarm = alarmCaptor.firstValue
        assertEquals(7, alarm.hour)
        assertEquals(30, alarm.minute)
        assertEquals("Morning Wakeup", alarm.label)
        assertEquals("MONDAY,TUESDAY", alarm.repeatDaysString)
        assertEquals(85, alarm.volume)
        assertTrue(alarm.gradualVolume)
        assertTrue(alarm.vibrationEnabled)
    }

    @Test
    fun `restore applies playback and download settings`() = runTest {
        val json = """
            {
              "version": 1,
              "preferences": {
                "autoplayEnabled": false,
                "wifiOnlyDownloads": true,
                "downloadsRequireBatteryOk": true,
                "streamQuality": "Medium",
                "downloadQuality": "Low",
                "lyricsSourceOrder": "lrclib_search,lrclib_exact",
                "sleepSchedule": "1;1,5;22;15",
                "skipSilence": true,
                "pauseOnDeviceDisconnect": false,
                "resumeOnDeviceReconnect": true,
                "pauseWhenMuted": true,
                "audioOffloadEnabled": true,
                "loudnessPreset": "Balanced",
                "crossfadeSeconds": 5
              }
            }
        """.trimIndent()

        assertTrue(manager.restoreBackupJson(json).isSuccess)
        verify(preferenceManager).isAutoplayEnabled = false
        verify(preferenceManager).wifiOnlyDownloads = true
        verify(preferenceManager).downloadsRequireBatteryOk = true
        verify(preferenceManager).streamQuality = "Medium"
        verify(preferenceManager).downloadQuality = "Low"
        verify(preferenceManager).lyricsSourceOrder = "lrclib_search,lrclib_exact,paxsenix,better_lyrics,lyrics_plus,kugou,netease,lyrics_ovh,lrclib_title_only"
        verify(preferenceManager).sleepSchedule = com.vyllo.music.domain.model.SleepSchedule(
            enabled = true,
            days = setOf(1, 5),
            hour = 22,
            minute = 15
        )
        verify(preferenceManager).skipSilence = true
        verify(preferenceManager).pauseOnDeviceDisconnect = false
        verify(preferenceManager).resumeOnDeviceReconnect = true
        verify(preferenceManager).pauseWhenMuted = true
        verify(preferenceManager).audioOffloadEnabled = true
        verify(preferenceManager).loudnessPreset = "Balanced"
        verify(preferenceManager).crossfadeSeconds = 5
    }

    @Test
    fun `restore reapplies playback history`() = runTest {
        whenever(playlistDao.getPlaylistByName("Favorites")).thenReturn(null)
        whenever(playlistDao.insertPlaylist(PlaylistEntity(name = "Favorites"))).thenReturn(42L)

        assertTrue(manager.restoreBackupJson(sampleBackup).isSuccess)

        val historyCaptor = argumentCaptor<HistoryEntity>()
        verify(historyDao).insertHistory(historyCaptor.capture())
        val entry = historyCaptor.firstValue
        assertEquals("https://music.youtube.com/watch?v=123", entry.url)
        assertEquals("Song A", entry.title)
        assertEquals(MusicItemType.SONG, entry.type)
        assertEquals(1700000000000L, entry.lastPlayedAt)
    }

    @Test
    fun `restore drops favorite entries with an out of range rating`() = runTest {
        whenever(playlistDao.getPlaylistByName("Favorites")).thenReturn(null)
        whenever(playlistDao.insertPlaylist(PlaylistEntity(name = "Favorites"))).thenReturn(42L)

        assertTrue(manager.restoreBackupJson(sampleBackup).isSuccess)

        // The sample carries one valid rating (1) and one junk rating (7);
        // only the valid one may reach the database.
        val favCaptor = argumentCaptor<FavoriteEntity>()
        verify(favoritesDao, org.mockito.kotlin.times(1)).upsert(favCaptor.capture())
        val favorite = favCaptor.firstValue
        assertEquals(1, favorite.rating)
        assertEquals("https://music.youtube.com/watch?v=123", favorite.url)
    }

    @Test
    fun `restore falls back to SONG for an unparseable item type`() = runTest {
        val json = """
            {
              "version": 1,
              "history": [
                { "url": "u1", "title": "T", "type": "NOT_A_REAL_TYPE", "lastPlayedAt": 1 }
              ]
            }
        """.trimIndent()

        assertTrue(manager.restoreBackupJson(json).isSuccess)

        val historyCaptor = argumentCaptor<HistoryEntity>()
        verify(historyDao).insertHistory(historyCaptor.capture())
        assertEquals(MusicItemType.SONG, historyCaptor.firstValue.type)
    }

    @Test
    fun `restore rejects unsupported version`() = runTest {
        val result = manager.restoreBackupJson("""{"version":0}""")
        assertTrue(result.isFailure)
    }

    @Test
    fun `restore rejects malformed json`() = runTest {
        val result = manager.restoreBackupJson("{ invalid_json: ")
        assertTrue(result.isFailure)
    }

    @Test
    fun `restore empty object succeeds with no side effects`() = runTest {
        val result = manager.restoreBackupJson("{}")
        assertTrue(result.isSuccess)
        verify(preferenceManager, never()).themeMode = org.mockito.kotlin.any()
    }

    @Test
    fun `restore maps pinned playlist names back to their ids`() = runTest {
        val json = """
            {
              "version": 1,
              "preferences": { "pinnedPlaylistNames": ["Chill", "Favorites"] },
              "playlists": [
                { "name": "Favorites" },
                { "name": "Chill" }
              ]
            }
        """.trimIndent()
        whenever(playlistDao.getPlaylistByName("Favorites")).thenReturn(PlaylistEntity(id = 42, name = "Favorites"))
        whenever(playlistDao.getPlaylistByName("Chill")).thenReturn(PlaylistEntity(id = 7, name = "Chill"))

        assertTrue(manager.restoreBackupJson(json).isSuccess)

        // Order is preserved and the unknown/name-less entries are dropped.
        verify(preferenceManager).pinnedPlaylistIds = listOf(7L, 42L)
    }

    @Test
    fun `restore ignores pinned names that no playlist matches`() = runTest {
        val json = """
            {
              "version": 1,
              "preferences": { "pinnedPlaylistNames": ["Ghost"] },
              "playlists": [ { "name": "Favorites" } ]
            }
        """.trimIndent()
        whenever(playlistDao.getPlaylistByName("Favorites")).thenReturn(PlaylistEntity(id = 42, name = "Favorites"))

        assertTrue(manager.restoreBackupJson(json).isSuccess)

        verify(preferenceManager).pinnedPlaylistIds = emptyList<Long>()
    }

    @Test
    fun `export writes pinned playlists as names`() = runTest {
        whenever(playlistDao.getAllPlaylistsList()).thenReturn(
            listOf(
                PlaylistEntity(id = 42, name = "Favorites"),
                PlaylistEntity(id = 7, name = "Chill")
            )
        )
        whenever(playlistDao.getSongsByPlaylistList(42L)).thenReturn(emptyList())
        whenever(playlistDao.getSongsByPlaylistList(7L)).thenReturn(emptyList())
        whenever(alarmDao.getAllAlarmsList()).thenReturn(emptyList())
        whenever(historyDao.getRecentHistoryList(any())).thenReturn(emptyList())
        whenever(favoritesDao.getAllList(any())).thenReturn(emptyList())
        whenever(preferenceManager.pinnedPlaylistIds).thenReturn(listOf(7L, 42L))
        whenever(preferenceManager.sleepSchedule).thenReturn(com.vyllo.music.domain.model.SleepSchedule())
        whenever(preferenceManager.isAutoplayEnabled).thenReturn(false)
        whenever(preferenceManager.wifiOnlyDownloads).thenReturn(true)
        whenever(preferenceManager.streamQuality).thenReturn("Medium")

        val json = JSONObject(manager.exportBackupJson())
        val names = json.getJSONObject("preferences").getJSONArray("pinnedPlaylistNames")

        assertEquals(2, names.length())
        assertEquals("Chill", names.getString(0))
        assertEquals("Favorites", names.getString(1))
        val prefs = json.getJSONObject("preferences")
        assertFalse(prefs.getBoolean("autoplayEnabled"))
        assertTrue(prefs.getBoolean("wifiOnlyDownloads"))
        assertEquals("Medium", prefs.getString("streamQuality"))
        assertEquals("0;;23;0", prefs.getString("sleepSchedule"))
    }
}

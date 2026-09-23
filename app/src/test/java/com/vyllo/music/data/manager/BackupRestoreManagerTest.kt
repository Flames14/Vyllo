package com.vyllo.music.data.manager

import com.vyllo.music.data.alarm.AlarmDao
import com.vyllo.music.data.alarm.AlarmEntity
import com.vyllo.music.data.download.HistoryDao
import com.vyllo.music.data.download.HistoryEntity
import com.vyllo.music.data.download.PlaylistDao
import com.vyllo.music.domain.model.PlaylistEntity
import com.vyllo.music.domain.model.PlaylistSongEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
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
            ]
        }
    """.trimIndent()

    @Before
    fun setUp() {
        playlistDao = mock()
        alarmDao = mock()
        historyDao = mock()
        preferenceManager = mock()
        manager = BackupRestoreManager(playlistDao, alarmDao, historyDao, preferenceManager)
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
}

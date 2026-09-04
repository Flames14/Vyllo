package com.vyllo.music.data.manager

import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.data.alarm.AlarmDao
import com.vyllo.music.data.alarm.AlarmEntity
import com.vyllo.music.data.download.HistoryDao
import com.vyllo.music.data.download.HistoryEntity
import com.vyllo.music.data.download.PlaylistDao
import com.vyllo.music.data.download.PlaylistEntity
import com.vyllo.music.data.download.PlaylistSongEntity
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRestoreManager @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val alarmDao: AlarmDao,
    private val historyDao: HistoryDao,
    private val preferenceManager: PreferenceManager
) {
    suspend fun exportBackupJson(): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("timestamp", System.currentTimeMillis())

        // Preferences
        val prefs = JSONObject().apply {
            put("themeMode", preferenceManager.themeMode)
            put("isFloatingPlayerEnabled", preferenceManager.isFloatingPlayerEnabled)
            put("isBackgroundPlaybackEnabled", preferenceManager.isBackgroundPlaybackEnabled)
            put("isKeepAudioPlayingEnabled", preferenceManager.isKeepAudioPlayingEnabled)
            put("isLiquidScrollEnabled", preferenceManager.isLiquidScrollEnabled)
            put("isHighRefreshRateEnabled", preferenceManager.isHighRefreshRateEnabled)
            put("volumeBoostMultiplier", preferenceManager.volumeBoostMultiplier.toDouble())
            put("isQueueSticky", preferenceManager.isQueueSticky)
        }
        root.put("preferences", prefs)

        // Playlists
        val playlistsArray = JSONArray()
        val playlists = playlistDao.getAllPlaylistsList()
        for (playlist in playlists) {
            val playlistObj = JSONObject().apply {
                put("name", playlist.name)
                val songsArray = JSONArray()
                val songs = playlistDao.getSongsByPlaylistList(playlist.id)
                for (song in songs) {
                    val songObj = JSONObject().apply {
                        put("title", song.title)
                        put("url", song.url)
                        put("uploader", song.uploader)
                        put("thumbnailUrl", song.thumbnailUrl)
                        put("addedAt", song.addedAt)
                    }
                    songsArray.put(songObj)
                }
                put("songs", songsArray)
            }
            playlistsArray.put(playlistObj)
        }
        root.put("playlists", playlistsArray)

        // Alarms
        val alarmsArray = JSONArray()
        val alarms = alarmDao.getAllAlarmsList()
        for (alarm in alarms) {
            val alarmObj = JSONObject().apply {
                put("hour", alarm.hour)
                put("minute", alarm.minute)
                put("isEnabled", alarm.isEnabled)
                put("label", alarm.label)
                put("repeatDaysString", alarm.repeatDaysString)
                put("soundType", alarm.soundType)
                put("downloadedSongUrl", alarm.downloadedSongUrl)
                put("downloadedSongTitle", alarm.downloadedSongTitle)
                put("volume", alarm.volume)
                put("gradualVolume", alarm.gradualVolume)
                put("vibrationEnabled", alarm.vibrationEnabled)
            }
            alarmsArray.put(alarmObj)
        }
        root.put("alarms", alarmsArray)

        return root.toString(2)
    }

    suspend fun restoreBackupJson(jsonString: String): Result<Unit> {
        return runCatching {
            val root = JSONObject(jsonString)
            val version = root.optInt("version", 1)
            if (version < 1) {
                throw IllegalArgumentException("Unsupported backup version")
            }

            // Restore preferences
            if (root.has("preferences")) {
                val prefs = root.getJSONObject("preferences")
                if (prefs.has("themeMode")) preferenceManager.themeMode = prefs.getString("themeMode")
                if (prefs.has("isFloatingPlayerEnabled")) preferenceManager.isFloatingPlayerEnabled = prefs.getBoolean("isFloatingPlayerEnabled")
                if (prefs.has("isBackgroundPlaybackEnabled")) preferenceManager.isBackgroundPlaybackEnabled = prefs.getBoolean("isBackgroundPlaybackEnabled")
                if (prefs.has("isKeepAudioPlayingEnabled")) preferenceManager.isKeepAudioPlayingEnabled = prefs.getBoolean("isKeepAudioPlayingEnabled")
                if (prefs.has("isLiquidScrollEnabled")) preferenceManager.isLiquidScrollEnabled = prefs.getBoolean("isLiquidScrollEnabled")
                if (prefs.has("isHighRefreshRateEnabled")) preferenceManager.isHighRefreshRateEnabled = prefs.getBoolean("isHighRefreshRateEnabled")
                if (prefs.has("volumeBoostMultiplier")) preferenceManager.volumeBoostMultiplier = prefs.getDouble("volumeBoostMultiplier").toFloat()
                if (prefs.has("isQueueSticky")) preferenceManager.isQueueSticky = prefs.getBoolean("isQueueSticky")
            }

            // Restore playlists
            if (root.has("playlists")) {
                val playlistsArray = root.getJSONArray("playlists")
                for (i in 0 until playlistsArray.length()) {
                    val playlistObj = playlistsArray.getJSONObject(i)
                    val name = playlistObj.getString("name")

                    var playlist = playlistDao.getPlaylistByName(name)
                    val playlistId = if (playlist != null) {
                        playlist.id
                    } else {
                        playlistDao.insertPlaylist(PlaylistEntity(name = name))
                    }

                    if (playlistObj.has("songs")) {
                        val songsArray = playlistObj.getJSONArray("songs")
                        val songsList = mutableListOf<PlaylistSongEntity>()
                        for (j in 0 until songsArray.length()) {
                            val songObj = songsArray.getJSONObject(j)
                            songsList.add(
                                PlaylistSongEntity(
                                    playlistId = playlistId,
                                    title = songObj.getString("title"),
                                    url = songObj.getString("url"),
                                    uploader = songObj.optString("uploader", ""),
                                    thumbnailUrl = songObj.optString("thumbnailUrl", ""),
                                    addedAt = songObj.optLong("addedAt", System.currentTimeMillis())
                                )
                            )
                        }
                        if (songsList.isNotEmpty()) {
                            playlistDao.insertSongsToPlaylist(songsList)
                        }
                    }
                }
            }

            // Restore alarms
            if (root.has("alarms")) {
                val alarmsArray = root.getJSONArray("alarms")
                for (i in 0 until alarmsArray.length()) {
                    val alarmObj = alarmsArray.getJSONObject(i)
                    val repeatDaysStr = when {
                        alarmObj.has("repeatDaysString") -> alarmObj.getString("repeatDaysString")
                        alarmObj.has("repeatDays") -> {
                            val daysArray = alarmObj.getJSONArray("repeatDays")
                            val list = mutableListOf<String>()
                            for (k in 0 until daysArray.length()) {
                                list.add(daysArray.getString(k))
                            }
                            list.joinToString(",")
                        }
                        else -> ""
                    }

                    val soundType = alarmObj.optString("soundType", "DEFAULT")

                    val entity = AlarmEntity(
                        hour = alarmObj.getInt("hour"),
                        minute = alarmObj.getInt("minute"),
                        isEnabled = alarmObj.optBoolean("isEnabled", true),
                        label = alarmObj.optString("label", ""),
                        repeatDaysString = repeatDaysStr,
                        soundType = soundType,
                        downloadedSongUrl = alarmObj.optString("downloadedSongUrl").takeIf { it.isNotBlank() },
                        downloadedSongTitle = alarmObj.optString("downloadedSongTitle").takeIf { it.isNotBlank() },
                        volume = alarmObj.optInt("volume", 80),
                        gradualVolume = alarmObj.optBoolean("gradualVolume", true),
                        vibrationEnabled = alarmObj.optBoolean("vibrationEnabled", true),
                        createdAt = System.currentTimeMillis()
                    )
                    alarmDao.insertAlarm(entity)
                }
            }
            SecureLogger.d("BackupRestoreManager", "Backup restored successfully")
        }
    }
}

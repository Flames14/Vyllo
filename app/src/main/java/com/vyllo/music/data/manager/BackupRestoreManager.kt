package com.vyllo.music.data.manager

import com.vyllo.music.core.security.SecureLogger
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
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRestoreManager @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val alarmDao: AlarmDao,
    private val historyDao: HistoryDao,
    private val favoritesDao: FavoritesDao,
    private val preferenceManager: PreferenceManager
) {
    suspend fun exportBackupJson(): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("timestamp", System.currentTimeMillis())
        val playlists = playlistDao.getAllPlaylistsList()
        val playlistNamesById = playlists.associate { it.id to it.name }

        // Preferences
        val prefs = JSONObject().apply {
            put("themeMode", preferenceManager.themeMode)
            put("isFloatingPlayerEnabled", preferenceManager.isFloatingPlayerEnabled)
            put("isBackgroundPlaybackEnabled", preferenceManager.isBackgroundPlaybackEnabled)
            put("isKeepAudioPlayingEnabled", preferenceManager.isKeepAudioPlayingEnabled)
            put("isHighRefreshRateEnabled", preferenceManager.isHighRefreshRateEnabled)
            put("volumeBoostMultiplier", preferenceManager.volumeBoostMultiplier.toDouble())
            put("isQueueSticky", preferenceManager.isQueueSticky)
            put("upNextIncludeVariants", preferenceManager.upNextIncludeVariants)
            put("isBlockScreenshotsEnabled", preferenceManager.isBlockScreenshotsEnabled)
            put("pinnedPlaylistNames", JSONArray(
                preferenceManager.pinnedPlaylistIds.mapNotNull(playlistNamesById::get)
            ))
        }
        root.put("preferences", prefs)

        // Playlists
        val playlistsArray = JSONArray()
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

        // Playback history -- previously injected but never serialized, so a
        // restore silently lost it even though the DAO was right there.
        val historyArray = JSONArray()
        for (entry in historyDao.getRecentHistoryList(MAX_HISTORY_ENTRIES)) {
            historyArray.put(JSONObject().apply {
                put("url", entry.url)
                put("title", entry.title)
                put("uploader", entry.uploader)
                put("thumbnailUrl", entry.thumbnailUrl)
                put("type", entry.type.name)
                put("lastPlayedAt", entry.lastPlayedAt)
            })
        }
        root.put("history", historyArray)

        // Favorites / ratings
        val favoritesArray = JSONArray()
        for (entry in favoritesDao.getAllList(MAX_FAVORITE_ENTRIES)) {
            favoritesArray.put(JSONObject().apply {
                put("url", entry.url)
                put("title", entry.title)
                put("uploader", entry.uploader)
                put("thumbnailUrl", entry.thumbnailUrl)
                put("type", entry.type.name)
                put("rating", entry.rating)
                put("likedAt", entry.likedAt)
            })
        }
        root.put("favorites", favoritesArray)

        return root.toString(2)
    }

    suspend fun restoreBackupJson(jsonString: String): Result<Unit> {
        return runCatching {
            if (jsonString.length > 10 * 1024 * 1024) {
                throw IllegalArgumentException("Backup file too large")
            }
            val root = JSONObject(jsonString)
            val version = root.optInt("version", 1)
            if (version < 1) {
                throw IllegalArgumentException("Unsupported backup version")
            }

            // Restore preferences
            var pinnedPlaylistNames: List<String> = emptyList()
            if (root.has("preferences")) {
                val prefs = root.getJSONObject("preferences")
                if (prefs.has("themeMode")) preferenceManager.themeMode = prefs.getString("themeMode")
                if (prefs.has("isFloatingPlayerEnabled")) preferenceManager.isFloatingPlayerEnabled = prefs.getBoolean("isFloatingPlayerEnabled")
                if (prefs.has("isBackgroundPlaybackEnabled")) preferenceManager.isBackgroundPlaybackEnabled = prefs.getBoolean("isBackgroundPlaybackEnabled")
                if (prefs.has("isKeepAudioPlayingEnabled")) preferenceManager.isKeepAudioPlayingEnabled = prefs.getBoolean("isKeepAudioPlayingEnabled")
                if (prefs.has("isHighRefreshRateEnabled")) preferenceManager.isHighRefreshRateEnabled = prefs.getBoolean("isHighRefreshRateEnabled")
                if (prefs.has("volumeBoostMultiplier")) preferenceManager.volumeBoostMultiplier = prefs.getDouble("volumeBoostMultiplier").toFloat()
                if (prefs.has("isQueueSticky")) preferenceManager.isQueueSticky = prefs.getBoolean("isQueueSticky")
                if (prefs.has("upNextIncludeVariants")) preferenceManager.upNextIncludeVariants = prefs.getBoolean("upNextIncludeVariants")
                if (prefs.has("isBlockScreenshotsEnabled")) preferenceManager.isBlockScreenshotsEnabled = prefs.getBoolean("isBlockScreenshotsEnabled")
                if (prefs.has("pinnedPlaylistNames")) {
                    val namesArray = prefs.getJSONArray("pinnedPlaylistNames")
                    pinnedPlaylistNames = (0 until minOf(namesArray.length(), 100))
                        .mapNotNull { namesArray.optString(it, "").takeIf { n -> n.isNotBlank() } }
                }
            }

            // Restore playlists (capped: a crafted file must not OOM the restore)
            val restoredPlaylistIdsByName = mutableMapOf<String, Long>()
            if (root.has("playlists")) {
                val playlistsArray = root.getJSONArray("playlists")
                for (i in 0 until minOf(playlistsArray.length(), 200)) {
                    val playlistObj = playlistsArray.getJSONObject(i)
                    val name = playlistObj.optString("name", "").take(120)
                    if (name.isBlank()) continue

                    val playlist = playlistDao.getPlaylistByName(name)
                    val playlistId = if (playlist != null) {
                        playlist.id
                    } else {
                        playlistDao.insertPlaylist(PlaylistEntity(name = name))
                    }
                    restoredPlaylistIdsByName[name] = playlistId

                    if (playlistObj.has("songs")) {
                        val songsArray = playlistObj.getJSONArray("songs")
                        val songsList = mutableListOf<PlaylistSongEntity>()
                        for (j in 0 until minOf(songsArray.length(), 500)) {
                            val songObj = songsArray.getJSONObject(j)
                            val title = songObj.optString("title", "").take(300)
                            val url = songObj.optString("url", "")
                            if (title.isBlank() || url.isBlank()) continue
                            songsList.add(
                                PlaylistSongEntity(
                                    playlistId = playlistId,
                                    title = title,
                                    url = url,
                                    uploader = songObj.optString("uploader", "").take(200),
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

            // Pinned playlists travel by name so they survive a device change
            if (pinnedPlaylistNames.isNotEmpty()) {
                val pinnedIds = pinnedPlaylistNames
                    .mapNotNull { restoredPlaylistIdsByName[it] }
                    .distinct()
                preferenceManager.pinnedPlaylistIds = pinnedIds
            }

            // Restore alarms (capped + range-checked)
            if (root.has("alarms")) {
                val alarmsArray = root.getJSONArray("alarms")
                for (i in 0 until minOf(alarmsArray.length(), 100)) {
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
                        hour = alarmObj.optInt("hour", 7).coerceIn(0, 23),
                        minute = alarmObj.optInt("minute", 0).coerceIn(0, 59),
                        isEnabled = alarmObj.optBoolean("isEnabled", true),
                        label = alarmObj.optString("label", "").take(120),
                        repeatDaysString = repeatDaysStr,
                        soundType = soundType,
                        downloadedSongUrl = alarmObj.optString("downloadedSongUrl").takeIf { it.isNotBlank() },
                        downloadedSongTitle = alarmObj.optString("downloadedSongTitle").takeIf { it.isNotBlank() },
                        volume = alarmObj.optInt("volume", 80).coerceIn(0, 100),
                        gradualVolume = alarmObj.optBoolean("gradualVolume", true),
                        vibrationEnabled = alarmObj.optBoolean("vibrationEnabled", true),
                        createdAt = System.currentTimeMillis()
                    )
                    alarmDao.insertAlarm(entity)
                }
            }
            // Restore playback history (capped: a crafted file must not OOM the restore)
            if (root.has("history")) {
                val historyArray = root.getJSONArray("history")
                for (i in 0 until minOf(historyArray.length(), MAX_HISTORY_ENTRIES)) {
                    val obj = historyArray.getJSONObject(i)
                    val url = obj.optString("url", "")
                    val title = obj.optString("title", "").take(300)
                    if (url.isBlank() || title.isBlank()) continue
                    historyDao.insertHistory(
                        HistoryEntity(
                            url = url,
                            title = title,
                            uploader = obj.optString("uploader", "").take(200),
                            thumbnailUrl = obj.optString("thumbnailUrl", ""),
                            type = parseMusicItemType(obj.optString("type", "")),
                            lastPlayedAt = obj.optLong("lastPlayedAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            // Restore favorites / ratings
            if (root.has("favorites")) {
                val favoritesArray = root.getJSONArray("favorites")
                for (i in 0 until minOf(favoritesArray.length(), MAX_FAVORITE_ENTRIES)) {
                    val obj = favoritesArray.getJSONObject(i)
                    val url = obj.optString("url", "")
                    val title = obj.optString("title", "").take(300)
                    val rating = obj.optInt("rating", 0)
                    // Only accept the two valid signed ratings; anything else
                    // in a hand-edited file is discarded rather than stored.
                    if (url.isBlank() || title.isBlank() || rating !in setOf(1, -1)) continue
                    favoritesDao.upsert(
                        FavoriteEntity(
                            url = url,
                            title = title,
                            uploader = obj.optString("uploader", "").take(200),
                            thumbnailUrl = obj.optString("thumbnailUrl", ""),
                            type = parseMusicItemType(obj.optString("type", "")),
                            rating = rating,
                            likedAt = obj.optLong("likedAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            SecureLogger.d("BackupRestoreManager", "Backup restored successfully")
        }
    }

    private fun parseMusicItemType(raw: String): MusicItemType =
        runCatching { MusicItemType.valueOf(raw) }.getOrDefault(MusicItemType.SONG)

    private companion object {
        const val MAX_HISTORY_ENTRIES = 2000
        const val MAX_FAVORITE_ENTRIES = 2000
    }
}

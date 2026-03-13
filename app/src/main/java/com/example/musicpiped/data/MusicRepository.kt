package com.example.musicpiped.data

import com.example.musicpiped.network.OkHttpDownloader
import com.example.musicpiped.data.download.PlaylistEntity
import com.example.musicpiped.data.download.PlaylistSongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.musicpiped.data.download.DownloadDatabase
import com.example.musicpiped.data.download.DownloadEntity
import com.example.musicpiped.data.download.DownloadStatus
import com.example.musicpiped.service.DownloadWorker
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import java.net.URLEncoder

object MusicRepository {
    
    @Volatile
    private var isInitialized = false
    private var preferences: android.content.SharedPreferences? = null
    
    private var currentExtractor: org.schabi.newpipe.extractor.ListExtractor<*>? = null
    private var nextSearchPage: Page? = null
    
    // --- GLOBAL PLAYBACK QUEUE ---
    var currentQueue = mutableListOf<MusicItem>()
    var currentIndex = -1
    
    // Cache for current song to avoid double extraction (getStreamUrl + getRelatedSongs)
    private var currentStreamUrl: String? = null
    private var currentStreamInfo: StreamInfo? = null

    // Client for direct suggestion fetching - Optimized configuration
    private val client = OkHttpClient.Builder()
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // Chipset-aware Optimization: Specialized dispatcher for heavy extraction work
    // Single thread (limit concurrency) + NORMAL priority (prevent Doze mode starvation)
    // Note: Thread.priority uses Java range 1-10 (Thread.NORM_PRIORITY=5), NOT Android Process priorities
    private val extractorDispatcher = java.util.concurrent.Executors.newFixedThreadPool(1) { r ->
        Thread(r).apply {
            priority = Thread.NORM_PRIORITY  // Java Thread priority 5 (normal)
        }
    }.asCoroutineDispatcher()

    fun init(context: android.content.Context) {
        if (!isInitialized) {
            synchronized(this) {
                if (!isInitialized) {
                    preferences = context.applicationContext.getSharedPreferences("music_prefs", android.content.Context.MODE_PRIVATE)
                    NewPipe.init(
                        OkHttpDownloader(),
                        Localization.DEFAULT,
                        ContentCountry.DEFAULT
                    )
                    isInitialized = true
                }
            }
        }
    }

    // --- FIX 1: WARM UP EXTRACTOR ---
    fun warmUp() {
        // Execute a dummy request to prime the JS engine and network stack
        kotlinx.coroutines.CoroutineScope(extractorDispatcher).launch {
            try {
                if (!isInitialized) return@launch
                // Just fetching the search page primes the localized strings and basic JS logic
                ServiceList.YouTube.getSearchExtractor("music", listOf("videos"), "").fetchPage()
            } catch (_: Exception) {
                // Ignore errors during warm-up
            }
        }
    }

    // --- FEATURE 1: REAL SEARCH SUGGESTIONS ---
    suspend fun getSuggestions(query: String): List<String> = withContext(extractorDispatcher) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://suggestqueries.google.com/complete/search?client=youtube&ds=yt&client=firefox&q=$encodedQuery"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val jsonString = response.body?.string() ?: return@withContext emptyList()
            
            // Robust parsing for ["query", ["sug1", "sug2", ...]]
            val startIndex = jsonString.indexOf("[", jsonString.indexOf("[") + 1)
            val endIndex = jsonString.lastIndexOf("]")
            
            if (startIndex == -1 || endIndex == -1) return@withContext emptyList()
            
            var arrayContent = jsonString.substring(startIndex, endIndex + 1)
            arrayContent = arrayContent.removePrefix("[").removeSuffix("]")
            
            // Split by comma, but be careful with commas inside quotes (simple regex split)
            val items = arrayContent.split("\",\"").map { 
                it.replace("\"", "").replace("[", "").replace("]", "").trim() 
            }
            
            return@withContext items.filter { it.isNotEmpty() && it != "s" }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
    }

    // --- FEATURE 2: PATTERN ANALYSIS & SUGGESTIONS ---
    fun recordListen(item: MusicItem) {
        val prefs = preferences ?: return
        val artist = item.uploader
        val currentCount = prefs.getInt("artist_$artist", 0)
        prefs.edit().putInt("artist_$artist", currentCount + 1).apply()
    }

    suspend fun getPatternSuggestions(): List<MusicItem> = withContext(extractorDispatcher) {
        val prefs = preferences ?: return@withContext emptyList()
        val allEntries = prefs.all
        
        // Find top 5 artists to add variety
        val topArtists = allEntries.filterKeys { it.startsWith("artist_") }
            .entries
            .sortedByDescending { it.value as? Int ?: 0 }
            .take(5)
            .map { it.key.removePrefix("artist_") }

        if (topArtists.isNotEmpty()) {
            val seedArtist = topArtists.random()
            return@withContext searchMusic("Mix of $seedArtist")
        } else {
            return@withContext searchMusic("Top latest music videos")
        }
    }

    // --- CORE SEARCH ---
    suspend fun searchMusic(query: String, maintainSession: Boolean = true): List<MusicItem> = withContext(extractorDispatcher) {
        try {
            if (!isInitialized) return@withContext emptyList()
            val service = ServiceList.YouTube
            
            val searchExtractor = service.getSearchExtractor(query, listOf("videos"), "")
            searchExtractor.fetchPage()
            
            if (maintainSession) {
                nextSearchPage = null
                currentExtractor = searchExtractor
                nextSearchPage = searchExtractor.initialPage.nextPage
            }

            return@withContext mapItems(searchExtractor.initialPage.items)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
    }

    suspend fun loadMoreResults(): List<MusicItem> = withContext(extractorDispatcher) {
        try {
            val extractor = currentExtractor ?: return@withContext emptyList()
            val page = nextSearchPage ?: return@withContext emptyList()
            
            if (page.url == null && page.ids == null) return@withContext emptyList()

            val nextPage = extractor.getPage(page)
            nextSearchPage = nextPage.nextPage

            return@withContext mapItems(nextPage.items)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
    }

    private fun mapItems(items: List<org.schabi.newpipe.extractor.InfoItem>): List<MusicItem> {
        return items.mapNotNull { item ->
            when (item) {
                is StreamInfoItem -> {
                    val thumbUrl = try { item.thumbnails?.firstOrNull()?.url ?: "" } catch (e: Exception) { "" }
                    MusicItem(item.name ?: "Unknown", item.url ?: "", item.uploaderName ?: "Unknown", thumbUrl, MusicItemType.SONG)
                }
                is PlaylistInfoItem -> {
                    val thumbUrl = try { item.thumbnails?.firstOrNull()?.url ?: "" } catch (e: Exception) { "" }
                    MusicItem(item.name ?: "Unknown", item.url ?: "", item.uploaderName ?: "YouTube Music", thumbUrl, MusicItemType.PLAYLIST)
                }
                else -> null
            }
        }
    }

    // --- FEATURE: TRENDING MUSIC ---
    suspend fun getTrendingMusic(): List<MusicItem> = withContext(extractorDispatcher) {
        try {
            val youtube = ServiceList.YouTube
            val kioskList = youtube.kioskList
            
            val extractor = try {
                kioskList.javaClass.getMethod("getExtractorById", String::class.java, Page::class.java).invoke(kioskList, "trending_music", null) as? org.schabi.newpipe.extractor.ListExtractor<org.schabi.newpipe.extractor.InfoItem>
            } catch (e: Exception) {
                null
            }

            if (extractor != null) {
                extractor.fetchPage()
                val items = extractor.initialPage.items
                return@withContext mapItems(items)
            }
            return@withContext emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
    }

    // Helper to get StreamInfo with Caching and Retry Logic
    private suspend fun getOrFetchStreamInfo(url: String, force: Boolean): StreamInfo {
        // If not forced, check cache first
        if (!force) {
            synchronized(this) {
                if (url == currentStreamUrl && currentStreamInfo != null) {
                    return currentStreamInfo!!
                }
            }
        } else {
             // If forced, clear cache immediately
             synchronized(this) {
                if (url == currentStreamUrl) {
                    currentStreamUrl = null
                    currentStreamInfo = null
                }
             }
        }

        var attempt = 0
        var lastException: Exception? = null
        
        while (attempt < 3) {
            try {
                // Ensure we are not on main thread for network calls
                val extractor = ServiceList.YouTube.getStreamExtractor(url)
                // Support cancellation: abort if user skips
                kotlinx.coroutines.runInterruptible(extractorDispatcher) {
                    extractor.fetchPage()
                }
                val info = StreamInfo.getInfo(extractor)
                
                synchronized(this) {
                    currentStreamUrl = url
                    currentStreamInfo = info
                }
                return info
            } catch (e: Exception) {
                lastException = e
                attempt++
                
                // Clear cache on failure to ensure clean slate for next attempt
                synchronized(this) {
                    currentStreamUrl = null
                    currentStreamInfo = null
                }

                if (attempt < 3) {
                    // Backoff: 400ms, 800ms
                    kotlinx.coroutines.delay(400L * attempt)
                }
            }
        }
        throw lastException ?: Exception("Failed to fetch stream info after 3 attempts")
    }

    suspend fun getStreamUrl(url: String, force: Boolean = false): String? = withContext(extractorDispatcher) {
        try {
            if (!isInitialized) return@withContext null
            val streamInfo = getOrFetchStreamInfo(url, force)
            
            // Priority: M4A > MP3 > Opus, then bitrate
            var audioStream = streamInfo.audioStreams
                .filter { 
                    val fmt = it.format?.toString()?.uppercase() ?: ""
                    fmt.contains("M4A") || fmt.contains("MP3")
                }
                .maxByOrNull { it.averageBitrate } 
            
            // Fallback 1: Any audio stream (including Opus) with highest bitrate
            if (audioStream == null) {
                audioStream = streamInfo.audioStreams.maxByOrNull { it.averageBitrate }
            }
            
            // Fallback 2 (Last Resort): If no "audio-only" stream, try to extract audio from video stream (Muxed)
            // Note: NewPipe Extractor separates them, but sometimes high quality audio is only in muxed streams.
            // For now, we stick to audioStreams to avoid downloading video data.
            
            return@withContext audioStream?.url
        } catch (e: Exception) {
            // Invalidate cache on error
            synchronized(this) {
                if (currentStreamUrl == url) {
                    currentStreamUrl = null
                    currentStreamInfo = null
                }
            }
            return@withContext null
        }
    }

    suspend fun getRelatedSongs(url: String): List<MusicItem> = withContext(extractorDispatcher) {
        try {
            if (!isInitialized) return@withContext emptyList()
            // Reuse cached info if available (force=false)
            val streamInfo = getOrFetchStreamInfo(url, false)
            return@withContext mapItems(streamInfo.relatedItems)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
    }

    // --- FEATURE: FLOATING PLAYER PREFERENCE ---
    var isFloatingPlayerEnabled: Boolean
        get() = preferences?.getBoolean("floating_player_enabled", false) ?: false
        set(value) {
            preferences?.edit()?.putBoolean("floating_player_enabled", value)?.apply()
        }
    
    // --- PERSIST FLOATING POSITION ---
    var floatingPlayerX: Int
        get() = preferences?.getInt("floating_player_x", 0) ?: 0
        set(value) {
            preferences?.edit()?.putInt("floating_player_x", value)?.apply()
        }
        
    var floatingPlayerY: Int
        get() = preferences?.getInt("floating_player_y", 100) ?: 100
        set(value) {
            preferences?.edit()?.putInt("floating_player_y", value)?.apply()
        }

    // --- FEATURE: BACKGROUND PLAYBACK ---
    var isBackgroundPlaybackEnabled: Boolean
        get() = preferences?.getBoolean("background_playback_enabled", true) ?: true
        set(value) {
            preferences?.edit()?.putBoolean("background_playback_enabled", value)?.apply()
        }
    
    // --- FEATURE: THEME MODE PREFERENCE ---
    var themeMode: String
        get() = preferences?.getString("theme_mode", "System") ?: "System"
        set(value) {
            preferences?.edit()?.putString("theme_mode", value)?.apply()
        }

    // --- FEATURE: KEEP AUDIO PLAYING ---
    var isKeepAudioPlayingEnabled: Boolean
        get() = preferences?.getBoolean("keep_audio_playing_enabled", false) ?: false
        set(value) {
            preferences?.edit()?.putBoolean("keep_audio_playing_enabled", value)?.apply()
        }

    // --- FEATURE: LIQUID SMOOTH SCROLL ---
    var isLiquidScrollEnabled: Boolean
        get() = preferences?.getBoolean("liquid_scroll_enabled", false) ?: false
        set(value) {
            preferences?.edit()?.putBoolean("liquid_scroll_enabled", value)?.apply()
        }

    // --- FEATURE: SEARCH HISTORY ---
    fun saveSearchQuery(query: String) {
        if (query.isBlank()) return
        val prefs = preferences ?: return
        val history = loadSearchHistory().toMutableList()
        history.remove(query)
        history.add(0, query)
        val limitedHistory = history.take(10)
        prefs.edit().putString("search_history", limitedHistory.joinToString("|||")).apply()
    }

    fun loadSearchHistory(): List<String> {
        val prefs = preferences ?: return emptyList()
        val historyString = prefs.getString("search_history", "") ?: ""
        return if (historyString.isBlank()) emptyList() else historyString.split("|||")
    }

    fun clearSearchHistory() {
        preferences?.edit()?.remove("search_history")?.apply()
    }

    // --- FEATURE: HISTORY PERSISTENCE ---
    fun saveHistory(items: List<MusicItem>) {
        val prefs = preferences ?: return
        try {
            val jsonArray = org.json.JSONArray()
            for (item in items) {
                val jsonObject = org.json.JSONObject()
                jsonObject.put("title", item.title)
                jsonObject.put("url", item.url)
                jsonObject.put("uploader", item.uploader)
                jsonObject.put("thumbnailUrl", item.thumbnailUrl)
                jsonObject.put("type", item.type.name)
                jsonArray.put(jsonObject)
            }
            prefs.edit().putString("recent_history", jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadHistory(): List<MusicItem> {
        val prefs = preferences ?: return emptyList()
        val jsonString = prefs.getString("recent_history", null) ?: return emptyList()
        val items = mutableListOf<MusicItem>()
        try {
            val jsonArray = org.json.JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val typeStr = obj.optString("type", "SONG")
                val type = try { MusicItemType.valueOf(typeStr) } catch(e: Exception) { MusicItemType.SONG }
                items.add(MusicItem(
                    title = obj.getString("title"),
                    url = obj.getString("url"),
                    uploader = obj.getString("uploader"),
                    thumbnailUrl = obj.getString("thumbnailUrl"),
                    type = type
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return items
    }

    // --- PLAYLIST FEATURE METHODS ---

    fun getPlaylistDao(context: android.content.Context) = 
        DownloadDatabase.getInstance(context).playlistDao()

    fun getAllPlaylists(context: android.content.Context) = 
        getPlaylistDao(context).getAllPlaylists()

    suspend fun createPlaylist(context: android.content.Context, name: String) {
        getPlaylistDao(context).insertPlaylist(PlaylistEntity(name = name))
    }

    suspend fun deletePlaylist(context: android.content.Context, playlist: PlaylistEntity) {
        getPlaylistDao(context).deletePlaylist(playlist)
    }

    suspend fun addSongToPlaylist(context: android.content.Context, playlistId: Long, item: MusicItem) {
        getPlaylistDao(context).insertSongToPlaylist(
            PlaylistSongEntity(
                playlistId = playlistId,
                url = item.url,
                title = item.title,
                uploader = item.uploader,
                thumbnailUrl = item.thumbnailUrl
            )
        )
    }

    suspend fun removeSongFromPlaylist(context: android.content.Context, playlistId: Long, url: String) {
        getPlaylistDao(context).removeSongFromPlaylist(playlistId, url)
    }

    fun getSongsInPlaylist(context: android.content.Context, playlistId: Long) = 
        getPlaylistDao(context).getSongsByPlaylist(playlistId)

    // --- DOWNLOAD FEATURE METHODS ---

    fun getDownloadDao(context: android.content.Context) = 
        DownloadDatabase.getInstance(context).downloadDao()

    fun getAllDownloads(context: android.content.Context): Flow<List<DownloadEntity>> {
        return getDownloadDao(context).getAllDownloads()
    }

    suspend fun isDownloaded(context: android.content.Context, url: String): Boolean {
        return getDownloadDao(context).isDownloaded(url)
    }

    fun downloadSong(context: android.content.Context, item: MusicItem) {
        try {
            val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(
                    "url" to item.url,
                    "title" to item.title,
                    "uploader" to item.uploader,
                    "thumbnailUrl" to item.thumbnailUrl
                ))
                .addTag("download_${item.url}")
                .build()
            
            WorkManager.getInstance(context).enqueue(workRequest)
            
            // Mark as pending in DB immediately
            kotlinx.coroutines.CoroutineScope(extractorDispatcher).launch {
                getDownloadDao(context).insertDownload(DownloadEntity(
                    url = item.url,
                    title = item.title,
                    uploader = item.uploader,
                    thumbnailUrl = item.thumbnailUrl,
                    filePath = "",
                    status = DownloadStatus.PENDING
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("MusicRepository", "Failed to enqueue download", e)
        }
    }

    suspend fun deleteDownload(context: android.content.Context, url: String) = withContext(Dispatchers.IO) {
        val dao = getDownloadDao(context)
        val download = dao.getDownloadByUrl(url)
        if (download != null) {
            val file = File(download.filePath)
            if (file.exists()) file.delete()
            dao.deleteByUrl(url)
        }
    }

    suspend fun getLocalStreamUrl(context: android.content.Context, url: String): String? {
        val dao = getDownloadDao(context)
        
        // 1. Try exact match
        var download = dao.getDownloadByUrl(url)
        
        // 2. If not found, try matching by Video ID
        if (download == null) {
            val videoId = extractVideoId(url)
            if (videoId != null) {
                // We need to scan all downloads to find matching ID (inefficient but safe for now)
                // Ideally, we should migrate DB to use VideoID as primary key or add it as indexed column
                val allDownloads = dao.getAllDownloadsList() 
                download = allDownloads.find { extractVideoId(it.url) == videoId }
            }
        }

        return if (download != null && download.status == DownloadStatus.COMPLETED) {
            val file = File(download.filePath)
            if (file.exists()) {
                android.util.Log.d("MusicRepository", "Found local file for $url at ${download.filePath}")
                "file://${download.filePath}"
            } else {
                android.util.Log.e("MusicRepository", "Local file missing for $url at ${download.filePath}")
                null
            }
        } else {
            android.util.Log.d("MusicRepository", "No local download found for $url")
            null
        }
    }

    // --- SMART LYRICS ENGINE ---
    // Logic moved to LyricsEngine.kt

    // --- Lyrics Preference Persistence ---

    private fun lyricsPreferenceKey(videoUrl: String): String {
        val videoId = extractVideoId(videoUrl) ?: videoUrl.hashCode().toString()
        return "lyrics_pref_$videoId"
    }

    fun saveLyricsPreference(videoUrl: String, lrcId: Long) {
        preferences?.edit()?.putLong(lyricsPreferenceKey(videoUrl), lrcId)?.apply()
        android.util.Log.d("MusicRepository", "Saved lyrics preference: ${lyricsPreferenceKey(videoUrl)} -> $lrcId")
    }

    fun getSavedLyricsId(videoUrl: String): Long? {
        val key = lyricsPreferenceKey(videoUrl)
        val id = preferences?.getLong(key, -1L) ?: -1L
        return if (id > 0) id else null
    }

    fun clearLyricsPreference(videoUrl: String) {
        preferences?.edit()?.remove(lyricsPreferenceKey(videoUrl))?.apply()
    }

    suspend fun getLyrics(title: String, artist: String, durationSecs: Long, videoUrl: String = ""): com.example.musicpiped.data.LyricsResponse? = withContext(Dispatchers.IO) {
        // Check for saved lyrics preference first
        if (videoUrl.isNotBlank()) {
            val savedId = getSavedLyricsId(videoUrl)
            if (savedId != null) {
                android.util.Log.d("MusicRepository", "Found saved lyrics preference: ID=$savedId")
                val savedResult = LyricsEngine.getLyricsById(savedId)
                if (savedResult != null) {
                    val parsedLines = LyricsEngine.parseSyncedLyrics(savedResult.syncedLyrics)
                    val detectText = savedResult.syncedLyrics ?: savedResult.plainLyrics ?: ""
                    return@withContext LyricsResponse(
                        success = true,
                        strategy = "SAVED_PREFERENCE",
                        result = savedResult,
                        results = listOf(savedResult),
                        plainLyrics = savedResult.plainLyrics,
                        syncedLines = parsedLines.takeIf { it.isNotEmpty() },
                        languages = LyricsEngine.detectLanguage(detectText),
                        lyricsStatus = com.example.musicpiped.data.LyricsStatus(
                            hasPlain = savedResult.plainLyrics != null,
                            hasSynced = savedResult.syncedLyrics != null,
                            isInstrumental = savedResult.instrumental
                        )
                    )
                }
                // Saved ID failed, clear it and fall through to normal search
                android.util.Log.w("MusicRepository", "Saved lyrics ID=$savedId failed, clearing preference")
                clearLyricsPreference(videoUrl)
            }
            
            // Phase 1: Try perfectly synced YouTube Subtitles first
            try {
                val streamInfo = getOrFetchStreamInfo(videoUrl, false)
                val subtitles = streamInfo.subtitles
                if (!subtitles.isNullOrEmpty()) {
                    val targetSub = subtitles.find { 
                        it.locale?.language?.contains("en", ignoreCase = true) == true 
                    } ?: subtitles.first()
                    
                    val subUrl = try { targetSub.content } catch (_: Exception) { null }
                        ?: try { targetSub.url } catch (_: Exception) { null }
                    
                    if (subUrl != null) {
                        android.util.Log.d("MusicRepository", "Found YouTube subtitles: ${targetSub.locale?.language} - $subUrl")
                        
                        val request = Request.Builder().url(subUrl).build()
                        val response = client.newCall(request).execute()
                        
                        if (response.isSuccessful) {
                            response.body?.string()?.let { vttBody ->
                                val syncedLines = parseVttToSyncedLyrics(vttBody)
                                if (syncedLines.isNotEmpty()) {
                                    android.util.Log.d("MusicRepository", "Successfully parsed ${syncedLines.size} perfectly synced lines from YouTube CC")
                                    return@withContext LyricsResponse(
                                        success = true,
                                        strategy = "YOUTUBE_CAPTIONS",
                                        syncedLines = syncedLines,
                                        plainLyrics = syncedLines.joinToString("\n") { it.content },
                                        languages = listOf(targetSub.locale?.language ?: "unknown"),
                                        lyricsStatus = com.example.musicpiped.data.LyricsStatus(
                                            hasPlain = true,
                                            hasSynced = true,
                                            isInstrumental = false
                                        ),
                                        result = LyricsResult(
                                            id = -2L,
                                            trackName = title,
                                            artistName = artist,
                                            albumName = null,
                                            duration = durationSecs,
                                            instrumental = false,
                                            plainLyrics = syncedLines.joinToString("\n") { it.content },
                                            syncedLyrics = null
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("MusicRepository", "YouTube CC extraction failed: ${e.message}")
            }
        }

        // Phase 2: Fall back to LRCLIB Universal Search
        return@withContext LyricsEngine.findLyricsUniversal(
            rawTitle = title,
            rawArtist = artist,
            durationSecs = durationSecs
        )
    }

    /**
     * Parses standard WebVTT or simple SRT-style millisecond timestamps into SyncedLyricLine.
     * NewPipe returns `.vtt` format for subtitles.
     */
    private fun parseVttToSyncedLyrics(vttContent: String): List<com.example.musicpiped.data.SyncedLyricLine> {
        val lines = vttContent.lines()
        val syncedLyrics = mutableListOf<com.example.musicpiped.data.SyncedLyricLine>()
        
        // Regex for WebVTT timestamp: 00:00:15.500 --> 00:00:17.340  OR  00:15.500 --> 00:17.340
        val timeRegex = Regex("(\\d{2}:)?(\\d{2}:\\d{2})[\\.,](\\d{3})\\s*-->\\s*.*")
        
        var currentStartTime: Long? = null
        val currentText = StringBuilder()
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.uppercase().startsWith("WEBVTT") || trimmed.uppercase() == "NOTE") {
                continue
            }
            
            val match = timeRegex.find(trimmed)
            if (match != null) {
                // If we had a previous block running, save it before starting this new one
                if (currentStartTime != null && currentText.isNotEmpty()) {
                    val cleanText = currentText.toString().replace(Regex("<[^>]*>"), "").trim() // Strip HTML/VTT tags
                    if (cleanText.isNotBlank()) {
                        syncedLyrics.add(com.example.musicpiped.data.SyncedLyricLine(currentStartTime, cleanText))
                    }
                    currentText.clear()
                }
                
                // Parse new start time
                try {
                    val hoursStr = match.groupValues[1]
                    val minSecStr = match.groupValues[2] // "MM:SS"
                    val msStr = match.groupValues[3] // "MMM"
                    
                    val hours = if (hoursStr.isNotBlank()) hoursStr.replace(":", "").toLong() else 0L
                    val minSecParts = minSecStr.split(":")
                    val minutes = minSecParts[0].toLong()
                    val seconds = minSecParts[1].toLong()
                    val ms = msStr.toLong()
                    
                    currentStartTime = (hours * 3600000L) + (minutes * 60000L) + (seconds * 1000L) + ms
                } catch (e: Exception) {
                    currentStartTime = null
                }
            } else if (currentStartTime != null && !trimmed.all { it.isDigit() }) {
                // Ignore plain digit lines (often block IDs in SRT/VTT)
                if (currentText.isNotEmpty()) currentText.append("\n")
                currentText.append(trimmed)
            }
        }
        
        // Add final block
        if (currentStartTime != null && currentText.isNotEmpty()) {
            val cleanText = currentText.toString().replace(Regex("<[^>]*>"), "").trim()
            if (cleanText.isNotBlank()) {
                syncedLyrics.add(com.example.musicpiped.data.SyncedLyricLine(currentStartTime, cleanText))
            }
        }
        
        return syncedLyrics.sortedBy { it.startTimeMs }
    }

    private fun extractVideoId(url: String): String? {
        return try {
            if (url.contains("v=")) {
                url.substringAfter("v=").substringBefore("&")
            } else if (url.contains("youtu.be/")) {
                url.substringAfter("youtu.be/").substringBefore("?")
            } else {
                null
            }
        } catch (e: Exception) { null }
    }
}

enum class MusicItemType {
    SONG, PLAYLIST
}

// Mark as Immutable to enable Compose stability optimizations
@androidx.compose.runtime.Immutable
data class MusicItem(
    val title: String,
    val url: String,
    val uploader: String,
    val thumbnailUrl: String,
    val type: MusicItemType = MusicItemType.SONG
)

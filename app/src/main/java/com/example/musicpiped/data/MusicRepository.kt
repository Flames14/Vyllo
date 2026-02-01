package com.example.musicpiped.data

import com.example.musicpiped.network.OkHttpDownloader
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
    
    // Cache for current song to avoid double extraction (getStreamUrl + getRelatedSongs)
    private var currentStreamUrl: String? = null
    private var currentStreamInfo: StreamInfo? = null

    // Client for direct suggestion fetching - Optimized configuration
    private val client = OkHttpClient.Builder()
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // Chipset-aware Optimization: Specialized dispatcher for heavy extraction work
    // Single thread (limit concurrency) + Background priority (keep big cores for UI)
    private val extractorDispatcher = java.util.concurrent.Executors.newFixedThreadPool(1) { r ->
        Thread(r).apply {
            priority = android.os.Process.THREAD_PRIORITY_BACKGROUND
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
            return@withContext getTrendingPlaylists()
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

    suspend fun getTrendingPlaylists(): List<MusicItem> = withContext(extractorDispatcher) {
        try {
            if (!isInitialized) return@withContext emptyList()
            val service = ServiceList.YouTube
            val kiosk = service.getKioskList().getExtractorById("Music", null)
            kiosk.fetchPage()
            
            // Allow pagination for trending too
            currentExtractor = kiosk
            nextSearchPage = kiosk.initialPage.nextPage
            
            return@withContext mapItems(kiosk.initialPage.items)
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
            
            // Priority: Opus > M4A > MP3, then bitrate
            var audioStream = streamInfo.audioStreams
                .filter { 
                    val fmt = it.format?.toString()?.uppercase() ?: ""
                    fmt.contains("OPUS") || fmt.contains("M4A")
                }
                .maxByOrNull { it.averageBitrate } 
            
            // Fallback 1: Any audio stream with highest bitrate
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
    
    // --- FEATURE: THEME MODE PREFERENCE ---
    var themeMode: String
        get() = preferences?.getString("theme_mode", "System") ?: "System"
        set(value) {
            preferences?.edit()?.putString("theme_mode", value)?.apply()
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

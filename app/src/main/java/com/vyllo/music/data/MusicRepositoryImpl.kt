package com.vyllo.music.data

import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.LyricsResponse
import com.vyllo.music.data.download.*
import com.vyllo.music.data.manager.PlaybackQueueManager
import com.vyllo.music.data.manager.PreferenceManager
import com.vyllo.music.data.network.YouTubeDataSource
import com.vyllo.music.data.network.SuggestionDataSource
import com.vyllo.music.data.repository.PlaylistRepository
import com.vyllo.music.data.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepositoryImpl @Inject constructor(
    private val youtubeDataSource: YouTubeDataSource,
    private val suggestionDataSource: SuggestionDataSource,
    private val playlistRepository: PlaylistRepository,
    private val downloadRepository: DownloadRepository,
    private val historyDao: HistoryDao,
    private val preferenceManager: PreferenceManager,
    private val playbackQueueManager: PlaybackQueueManager,
    private val lyricsEngine: LyricsEngine
) : IMusicRepository {
    private val streamProbeClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun getSuggestions(query: String): List<String> = suggestionDataSource.getSuggestions(query)

    override suspend fun searchMusic(query: String, maintainSession: Boolean): List<MusicItem> =
        youtubeDataSource.searchMusic(query, maintainSession)


    override suspend fun loadMoreResults(): List<MusicItem> =
        youtubeDataSource.loadMoreResults()

    override suspend fun getTrendingMusic(): List<MusicItem> =
        youtubeDataSource.getTrendingMusic()

    override suspend fun getStreamUrl(url: String, force: Boolean, isVideo: Boolean): String? {
        if (!isVideo) {
            val localUrl = downloadRepository.getLocalStreamUrl(url)
            if (localUrl != null) {
                SecureLogger.d("MusicRepositoryImpl") { "Using local download: $url" }
                return localUrl
            }
        } else {
            SecureLogger.d("MusicRepositoryImpl") { "Video mode requested, skipping local check" }
        }

        return try {
            val info = youtubeDataSource.getOrFetchStreamInfo(url, force)
            if (isVideo) {
                val videoUrl = withContext(Dispatchers.IO) {
                    selectPlayableVideoUrl(info.videoStreams)
                }
                SecureLogger.d("MusicRepositoryImpl") { "Video stream selected: ${videoUrl != null}" }
                videoUrl
            } else {
                var audioUrl = withContext(Dispatchers.IO) {
                    selectPlayableAudioUrl(info.audioStreams)
                }
                if (audioUrl == null) {
                    SecureLogger.w("MusicRepositoryImpl", "No playable audio stream found, falling back to low-resolution video stream for audio playback")
                    audioUrl = withContext(Dispatchers.IO) {
                        selectPlayableVideoUrlForAudio(info.videoStreams)
                    }
                }
                SecureLogger.d("MusicRepositoryImpl") { "Audio stream selected: ${audioUrl != null}" }
                audioUrl
            }
        } catch (e: Exception) {
            SecureLogger.e("MusicRepositoryImpl", "Failed to get stream URL", e)
            null
        }
    }

    private fun selectPlayableAudioUrl(streams: List<AudioStream>): String? {
        streams.forEach { stream ->
            SecureLogger.d("MusicRepositoryImpl") { "Available stream: format=${stream.format}, bitrate=${stream.averageBitrate}, url=${stream.url}" }
        }
        return streams
            .asSequence()
            .filter { it.isUrl && !it.url.isNullOrBlank() }
            .sortedByDescending { it.averageBitrate }
            .mapNotNull { it.url }
            .firstOrNull(::isPlayableStreamUrl)
    }

    private fun selectPlayableVideoUrl(streams: List<VideoStream>): String? {
        streams.forEach { stream ->
            SecureLogger.d("MusicRepositoryImpl") { "Available video stream: format=${stream.format}, bitrate=${stream.bitrate}, url=${stream.url}" }
        }
        return streams
            .asSequence()
            .filter { it.isUrl && !it.url.isNullOrBlank() }
            .sortedByDescending { it.bitrate }
            .mapNotNull { it.url }
            .firstOrNull(::isPlayableStreamUrl)
    }

    private fun selectPlayableVideoUrlForAudio(streams: List<VideoStream>): String? {
        streams.forEach { stream ->
            SecureLogger.d("MusicRepositoryImpl") { "Available video stream for audio: format=${stream.format}, bitrate=${stream.bitrate}, url=${stream.url}" }
        }
        return streams
            .asSequence()
            .filter { it.isUrl && !it.url.isNullOrBlank() }
            // Sort by ascending bitrate to get the lowest-resolution video stream
            // to save bandwidth, while maintaining standard audio quality.
            .sortedBy { it.bitrate }
            .mapNotNull { it.url }
            .firstOrNull(::isPlayableStreamUrl)
    }

    private fun isPlayableStreamUrl(streamUrl: String): Boolean {
        return try {
            val ua = userAgentForStreamUrl(streamUrl)
            val request = Request.Builder()
                .url(streamUrl)
                .header("Range", "bytes=0-1")
                .header("User-Agent", ua)
                .build()

            streamProbeClient.newCall(request).execute().use { response ->
                val playable = response.isSuccessful || response.code == 206
                SecureLogger.d("MusicRepositoryImpl") {
                    "Probe result: http=${response.code}, playable=$playable, client=${clientNameFromUrl(streamUrl)}, UA=$ua"
                }
                if (!playable) {
                    SecureLogger.w(
                        "MusicRepositoryImpl",
                        "Rejected stream candidate: http=${response.code}, client=${clientNameFromUrl(streamUrl)}"
                    )
                }
                playable
            }
        } catch (e: Exception) {
            SecureLogger.w(
                "MusicRepositoryImpl",
                "Rejected stream candidate: ${e.javaClass.simpleName}, client=${clientNameFromUrl(streamUrl)}",
                e
            )
            false
        }
    }

    private fun userAgentForStreamUrl(streamUrl: String): String {
        return when {
            streamUrl.contains("c=IOS", ignoreCase = true) ->
                "com.google.ios.youtube/21.03.2(iPhone16,2; U; CPU iOS 18_7_2 like Mac OS X; US)"
            streamUrl.contains("c=ANDROID", ignoreCase = true) ->
                "com.google.android.youtube/21.03.36 (Linux; U; Android 15; US) gzip"
            else ->
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        }
    }

    private fun clientNameFromUrl(streamUrl: String): String {
        return when {
            streamUrl.contains("c=IOS", ignoreCase = true) -> "IOS"
            streamUrl.contains("c=ANDROID", ignoreCase = true) -> "ANDROID"
            streamUrl.contains("c=WEB", ignoreCase = true) -> "WEB"
            else -> "UNKNOWN"
        }
    }

    override suspend fun getLocalStreamUrl(url: String): String? = downloadRepository.getLocalStreamUrl(url)

    override suspend fun getRelatedSongs(url: String): List<MusicItem> {
        return try {
            val info = youtubeDataSource.getOrFetchStreamInfo(url, false)
            info.relatedItems.mapNotNull { item ->
                if (item is org.schabi.newpipe.extractor.stream.StreamInfoItem) {
                    MusicItem(item.name ?: "", item.url ?: "", item.uploaderName ?: "", item.thumbnails?.firstOrNull()?.url ?: "")
                } else null
            }
        } catch (e: Exception) { emptyList() }
    }

    // Playlist Methods
    override fun getAllPlaylists(): Flow<List<PlaylistEntity>> = playlistRepository.getAllPlaylists()
    override suspend fun createPlaylist(name: String) = playlistRepository.createPlaylist(name)
    override suspend fun deletePlaylist(playlist: PlaylistEntity) = playlistRepository.deletePlaylist(playlist)
    override suspend fun addSongToPlaylist(playlistId: Long, item: MusicItem) = playlistRepository.addSongToPlaylist(playlistId, item)
    override suspend fun removeSongFromPlaylist(playlistId: Long, url: String) = playlistRepository.removeSongFromPlaylist(playlistId, url)
    override fun getSongsInPlaylist(playlistId: Long): Flow<List<PlaylistSongEntity>> = playlistRepository.getSongsInPlaylist(playlistId)
    
    // Download Methods
    override fun getAllDownloads(): Flow<List<DownloadEntity>> = downloadRepository.getAllDownloads()
    override fun downloadSong(item: MusicItem) = downloadRepository.downloadSong(item)
    override fun cancelDownload(url: String) = downloadRepository.cancelDownload(url)
    override suspend fun deleteDownload(url: String) = downloadRepository.deleteDownload(url)
    override suspend fun isDownloaded(url: String) = downloadRepository.isDownloaded(url)

    // History Methods
    override fun getRecentHistory(limit: Int): Flow<List<MusicItem>> =
        historyDao.getRecentHistory(limit)
            .map { entities -> entities.map { it.toMusicItem() } }
            .distinctUntilChanged()

    override suspend fun recordListen(item: MusicItem) {
        historyDao.insertHistory(item.toHistoryEntity())
        
        // Also keep the old pattern analysis logic for now
        val artist = item.uploader
        val currentCount = preferenceManager.preferences.getInt("artist_$artist", 0)
        preferenceManager.preferences.edit().putInt("artist_$artist", currentCount + 1).apply()
    }

    override suspend fun clearHistory() = historyDao.clearHistory()

    override suspend fun getPatternSuggestions(): List<MusicItem> {
        val allEntries = preferenceManager.preferences.all
        val topArtists = allEntries
            .filterKeys { it.startsWith("artist_") }
            .mapNotNull { (key, value) -> (value as? Int)?.let { key to it } }
            .sortedByDescending { it.second }
            .take(5)
            .map { it.first.removePrefix("artist_") }

        val allItems = mutableListOf<MusicItem>()

        // Fetch Quick Picks based on multiple top artists (8 items)
        if (topArtists.isNotEmpty()) {
            val artist1 = topArtists.random()
            allItems.addAll(youtubeDataSource.searchMusic("Mix of $artist1", maintainSession = false).take(8))
        } else {
            allItems.addAll(youtubeDataSource.getTrendingMusic().take(8))
        }

        // Fetch Mixed For You from another random top artist (6 items)
        if (topArtists.size > 1) {
            val otherArtists = topArtists.filter { name -> allItems.none { it.uploader.contains(name, ignoreCase = true) } }
            val artist2 = if (otherArtists.isNotEmpty()) otherArtists.random() else topArtists.first()
            allItems.addAll(youtubeDataSource.searchMusic("Recommended for $artist2", maintainSession = false).take(6))
        } else {
            allItems.addAll(youtubeDataSource.searchMusic("New suggested music", maintainSession = false).take(6))
        }

        // New Releases (8 items)
        allItems.addAll(youtubeDataSource.searchMusic("Official new music releases", maintainSession = false).take(8))

        // Trending Now (8 items)
        allItems.addAll(youtubeDataSource.getTrendingMusic().take(8))

        // Ensure we have a distinct list and enough items for pagination
        val finalItems = allItems.distinctBy { it.url }.toMutableList()

        // Finally, for "load more" pagination, fetch items from a general or first artist recommendation
        val paginationQuery = if (topArtists.isNotEmpty()) "Recommended music mix ${topArtists.first()}" else "Top latest music videos"
        val paginatedResults = youtubeDataSource.fetchItemsWithPagination(paginationQuery)

        finalItems.addAll(paginatedResults)
        return finalItems.distinctBy { it.url }
    }

    override suspend fun loadMoreRecommendations(): List<MusicItem> =
        youtubeDataSource.loadMoreItems()

    // Preference Methods
    override fun loadSearchHistory() = preferenceManager.loadSearchHistory()
    override fun saveSearchQuery(query: String) = preferenceManager.saveSearchQuery(query)
    override fun clearSearchHistory() = preferenceManager.clearSearchHistory()
    
    override fun saveLyricsPreference(videoUrl: String, lrcId: Long) = preferenceManager.saveLyricsPreference(videoUrl, lrcId.toString())
    override fun getSavedLyricsId(videoUrl: String): Long? = preferenceManager.loadLyricsPreference(videoUrl)?.toLongOrNull()
    override fun isLiquidScrollEnabled(): Boolean = preferenceManager.isLiquidScrollEnabled

    override suspend fun getLyrics(title: String, artist: String, duration: Long, url: String): LyricsResponse? {
        return lyricsEngine.findLyricsUniversal(title, artist, duration)
    }
}

package com.vyllo.music.data

import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.data.download.*
import com.vyllo.music.data.manager.PlaybackQueueManager
import com.vyllo.music.data.manager.PreferenceManager
import com.vyllo.music.data.network.YouTubeDataSource
import com.vyllo.music.data.network.SuggestionDataSource
import com.vyllo.music.data.repository.PlaylistRepository
import com.vyllo.music.data.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
    private val playbackQueueManager: PlaybackQueueManager
) : IMusicRepository {

    override suspend fun getSuggestions(query: String): List<String> = suggestionDataSource.getSuggestions(query)

    override suspend fun searchMusic(query: String, maintainSession: Boolean): List<MusicItem> =
        youtubeDataSource.searchMusic(query, maintainSession)

    override suspend fun loadMoreResults(): List<MusicItem> =
        youtubeDataSource.loadMoreResults()

    override suspend fun getTrendingMusic(): List<MusicItem> =
        youtubeDataSource.getTrendingMusic()

    override suspend fun getStreamUrl(url: String, force: Boolean, isVideo: Boolean): String? {
        // For video mode, always fetch from network since downloads are audio-only
        if (!isVideo) {
            val localUrl = downloadRepository.getLocalStreamUrl(url)
            if (localUrl != null) {
                android.util.Log.d("MusicRepositoryImpl", "Using local download: $url")
                return localUrl
            }
        } else {
            android.util.Log.d("MusicRepositoryImpl", "Video mode requested, skipping local check: $url")
        }

        return try {
            val info = youtubeDataSource.getOrFetchStreamInfo(url, force)
            if (isVideo) {
                // Get the best video stream that has both audio and video (Muxed)
                // or just pick the best video stream (NewPipe often provides separate streams,
                // but ExoPlayer handles merging in some cases, however for simplicity we want a playable URL)
                val videoUrl = info.videoStreams.maxByOrNull { it.bitrate }?.url
                android.util.Log.d("MusicRepositoryImpl", "Video stream selected: ${videoUrl?.take(50)}...")
                videoUrl
            } else {
                val audioUrl = info.audioStreams.maxByOrNull { it.averageBitrate }?.url
                android.util.Log.d("MusicRepositoryImpl", "Audio stream selected: ${audioUrl?.take(50)}...")
                audioUrl
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicRepositoryImpl", "Failed to get stream URL", e)
            null
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
        historyDao.getRecentHistory(limit).map { entities -> entities.map { it.toMusicItem() } }

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
        val topArtists = allEntries.filterKeys { it.startsWith("artist_") }
            .entries.sortedByDescending { it.value as? Int ?: 0 }.take(5).map { it.key.removePrefix("artist_") }

        val allItems = mutableListOf<MusicItem>()

        // 1. Fetch Quick Picks based on multiple top artists (8 items)
        if (topArtists.isNotEmpty()) {
            val artist1 = topArtists.random()
            allItems.addAll(youtubeDataSource.searchMusic("Mix of $artist1", maintainSession = false).take(8))
        } else {
            allItems.addAll(youtubeDataSource.getTrendingMusic().take(8))
        }

        // 2. Fetch Mixed For You from another random top artist (6 items)
        if (topArtists.size > 1) {
            val otherArtists = topArtists.filter { name -> allItems.none { it.uploader.contains(name, ignoreCase = true) } }
            val artist2 = if (otherArtists.isNotEmpty()) otherArtists.random() else topArtists.random()
            allItems.addAll(youtubeDataSource.searchMusic("Recommended for $artist2", maintainSession = false).take(6))
        } else {
            allItems.addAll(youtubeDataSource.searchMusic("New suggested music", maintainSession = false).take(6))
        }

        // 3. New Releases (8 items)
        allItems.addAll(youtubeDataSource.searchMusic("Official new music releases", maintainSession = false).take(8))

        // 4. Trending Now (8 items)
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
        return LyricsEngine.findLyricsUniversal(title, artist, duration)
    }
}

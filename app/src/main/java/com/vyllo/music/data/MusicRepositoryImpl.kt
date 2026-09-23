package com.vyllo.music.data

import com.vyllo.music.domain.repository.IMusicRepository
import com.vyllo.music.domain.repository.VideoStats

import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.LyricsResponse
import com.vyllo.music.domain.model.DownloadEntity
import com.vyllo.music.domain.model.PlaylistEntity
import com.vyllo.music.domain.model.PlaylistSongEntity
import com.vyllo.music.data.download.HistoryDao
import com.vyllo.music.data.download.toHistoryEntity
import com.vyllo.music.data.download.toMusicItem
import com.vyllo.music.domain.manager.PlaybackQueueManager
import com.vyllo.music.data.manager.PreferenceManager
import com.vyllo.music.data.network.YouTubeDataSource
import com.vyllo.music.data.network.SuggestionDataSource
import com.vyllo.music.data.repository.PlaylistRepository
import com.vyllo.music.data.repository.DownloadRepository
import com.vyllo.music.data.repository.delegates.DescriptionMetadataParser
import com.vyllo.music.data.repository.delegates.RelatedPoolManager
import com.vyllo.music.data.repository.delegates.StreamSelector
import com.vyllo.music.data.repository.delegates.SubtitleLyricsDelegate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val lyricsEngine: LyricsEngine,
    private val streamSelector: StreamSelector,
    private val relatedPoolManager: RelatedPoolManager,
    private val subtitleLyricsDelegate: SubtitleLyricsDelegate,
    private val descriptionMetadataParser: DescriptionMetadataParser
) : IMusicRepository {

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
                    streamSelector.selectPlayableVideoUrl(info.videoStreams)
                }
                SecureLogger.d("MusicRepositoryImpl") { "Video stream selected: ${videoUrl != null}" }
                videoUrl
            } else {
                var audioUrl = withContext(Dispatchers.IO) {
                    streamSelector.selectPlayableAudioUrl(info.audioStreams)
                }
                if (audioUrl == null) {
                    SecureLogger.w("MusicRepositoryImpl", "No playable audio stream found, falling back to low-resolution video stream for audio playback")
                    audioUrl = withContext(Dispatchers.IO) {
                        streamSelector.selectPlayableVideoUrlForAudio(info.videoStreams)
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

    override suspend fun getLocalStreamUrl(url: String): String? = downloadRepository.getLocalStreamUrl(url)

    override suspend fun getRelatedSongs(url: String, force: Boolean): List<MusicItem> =
        relatedPoolManager.getRelatedSongs(url, force)

    override suspend fun getMoreRelatedSongs(url: String): List<MusicItem> =
        relatedPoolManager.getMoreRelatedSongs(url)

    override suspend fun getArtistSongs(artist: String): List<MusicItem> =
        relatedPoolManager.getArtistSongs(artist)

    override suspend fun getDiscoverSimilarSongs(title: String, artist: String): List<MusicItem> =
        relatedPoolManager.getDiscoverSimilarSongs(title, artist)

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
            .take(10)
            .map { it.first.removePrefix("artist_") }
            .shuffled() // Shuffle so user sees variety across refreshes

        val allItems = mutableListOf<MusicItem>()

        // Dynamic time-of-day contextual music seed
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val contextualSeed = when (hour) {
            in 5..11 -> listOf("morning acoustic songs", "fresh morning pop hits")
            in 12..17 -> listOf("feel good upbeat music", "lofi chill beats")
            in 18..22 -> listOf("evening vibes chill music", "top hits official audio")
            else -> listOf("late night drive songs", "deep sleep relaxing music")
        }.random()

        // 1. Personalized Artist Mix (Quick Picks)
        if (topArtists.isNotEmpty()) {
            val selectedArtist = topArtists.first()
            allItems.addAll(relatedPoolManager.cleanSongList(youtubeDataSource.searchMusic("$selectedArtist radio", maintainSession = false)).take(12))
        } else {
            allItems.addAll(relatedPoolManager.cleanSongList(youtubeDataSource.searchMusic("top music hits 2026", maintainSession = false)).take(12))
        }

        // 2. Mixed For You (Secondary Artist or Genre Discovery)
        if (topArtists.size > 1) {
            val secondArtist = topArtists[1]
            allItems.addAll(relatedPoolManager.cleanSongList(youtubeDataSource.searchMusic("$secondArtist mix", maintainSession = false)).take(10))
        } else {
            allItems.addAll(relatedPoolManager.cleanSongList(youtubeDataSource.searchMusic(contextualSeed, maintainSession = false)).take(10))
        }

        // 3. Time-of-Day Contextual Discovery
        allItems.addAll(relatedPoolManager.cleanSongList(youtubeDataSource.searchMusic(contextualSeed, maintainSession = false)).take(10))

        // 4. Global Trending Music
        allItems.addAll(relatedPoolManager.cleanSongList(youtubeDataSource.getTrendingMusic()).take(12))

        val finalItems = allItems.distinctBy { it.url }.toMutableList()

        // 5. Pagination Seed
        val paginationQuery = if (topArtists.isNotEmpty()) "songs like ${topArtists.first()}" else "global top 50 music"
        val paginatedResults = relatedPoolManager.cleanSongList(youtubeDataSource.fetchItemsWithPagination(paginationQuery))
        finalItems.addAll(paginatedResults)

        return finalItems.distinctBy { it.url }.let { relatedPoolManager.capPerArtist(it, 2) }
    }

    override suspend fun loadMoreRecommendations(): List<MusicItem> =
        youtubeDataSource.loadMoreItems()

    // Preference Methods
    override fun loadSearchHistory() = preferenceManager.loadSearchHistory()
    override fun saveSearchQuery(query: String) = preferenceManager.saveSearchQuery(query)
    override fun clearSearchHistory() = preferenceManager.clearSearchHistory()

    override fun saveLyricsPreference(videoUrl: String, lrcId: Long) = preferenceManager.saveLyricsPreference(videoUrl, lrcId.toString())
    override fun getSavedLyricsId(videoUrl: String): Long? = preferenceManager.loadLyricsPreference(videoUrl)?.toLongOrNull()

    override suspend fun getLyrics(title: String, artist: String, duration: Long, url: String): LyricsResponse? {
        var extTitle: String? = null
        var extArtist: String? = null
        var extAlbum: String? = null
        var extLanguage: String? = null
        var extMusic: String? = null
        var subtitleResponse: LyricsResponse? = null

        if (url.isNotBlank()) {
            getSavedLyricsId(url)?.let { savedId ->
                lyricsEngine.getLyricsById(savedId)?.let { saved ->
                    val syncedSource = saved.syncedLyrics ?: saved.plainLyrics
                    val syncedLines = LyricsEngine.parseSyncedLyrics(syncedSource)
                    val plainLyrics = saved.plainLyrics ?: syncedLines.joinToString("\n") { it.content }.takeIf { it.isNotBlank() }
                    return LyricsResponse(
                        success = true,
                        strategy = "LRCLIB_SAVED",
                        result = saved,
                        results = listOf(saved),
                        plainLyrics = plainLyrics,
                        syncedLines = syncedLines.takeIf { it.isNotEmpty() },
                        languages = LyricsEngine.detectLanguage(syncedSource ?: plainLyrics ?: ""),
                        lyricsStatus = com.vyllo.music.domain.model.LyricsStatus(
                            hasPlain = !plainLyrics.isNullOrBlank(),
                            hasSynced = syncedLines.isNotEmpty(),
                            isInstrumental = saved.instrumental
                        )
                    )
                }
            }
        }

        val quickLrclib = lyricsEngine.findLyricsUniversal(
            rawTitle = title,
            rawArtist = artist,
            durationSecs = duration
        )
        if (quickLrclib.success) {
            return quickLrclib
        }

        try {
            if (url.isNotBlank()) {
                val info = youtubeDataSource.getOrFetchStreamInfo(url, false)
                val description = info.description?.content ?: ""
                val tags = info.tags?.map { it.toString() } ?: emptyList()

                val topicMeta = descriptionMetadataParser.parseProvidedToYouTubeDescription(description)
                val meta = topicMeta ?: descriptionMetadataParser.parseDescriptionMetadata(description)
                extTitle = meta.title
                extArtist = meta.singers
                extAlbum = meta.movie
                extLanguage = meta.language ?: descriptionMetadataParser.detectLanguageFromTagsOrTitle(title, tags) ?: descriptionMetadataParser.detectLanguageFromText(description)
                extMusic = meta.music

                SecureLogger.d("MusicRepositoryImpl") {
                    "Parsed metadata from StreamInfo: title=$extTitle, artist=$extArtist, album=$extAlbum, lang=$extLanguage, music=$extMusic"
                }

                // Try to extract and download subtitles
                val bestSubStream = subtitleLyricsDelegate.selectBestSubtitles(info.subtitles, extLanguage)
                if (bestSubStream != null) {
                    SecureLogger.d("MusicRepositoryImpl") {
                        "Found subtitle stream: lang=${bestSubStream.displayLanguageName}, format=${bestSubStream.format}, url=${bestSubStream.url}"
                    }
                    val subUrl = bestSubStream.url
                    val subContent = if (subUrl.isNullOrBlank()) {
                        SecureLogger.w("MusicRepositoryImpl", "Subtitle stream has no URL; skipping")
                        null
                    } else {
                        subtitleLyricsDelegate.downloadSubtitleContent(subUrl)
                    }

                    if (!subContent.isNullOrBlank()) {
                        val syncedLines = subtitleLyricsDelegate.parseSubtitles(subContent)
                        if (syncedLines.isNotEmpty()) {
                            subtitleResponse = subtitleLyricsDelegate.buildSubtitleResponse(
                                subContent = subContent,
                                syncedLines = syncedLines,
                                title = title,
                                artist = artist,
                                duration = duration,
                                extTitle = extTitle,
                                extArtist = extArtist,
                                extAlbum = extAlbum,
                                extLanguage = extLanguage
                            )
                            SecureLogger.d("MusicRepositoryImpl") { "Successfully loaded lyrics from YouTube subtitles!" }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            SecureLogger.e("MusicRepositoryImpl", { "Failed to extract StreamInfo metadata for lyrics: ${e.message}" }, e)
        }

        if (subtitleResponse != null) {
            return subtitleResponse
        }

        return lyricsEngine.findLyricsUniversal(
            rawTitle = title,
            rawArtist = artist,
            durationSecs = duration,
            extractedTitle = extTitle,
            extractedArtist = extArtist,
            extractedAlbum = extAlbum,
            extractedLanguage = extLanguage,
            extractedMusic = extMusic
        )
    }

    override suspend fun getVideoStats(url: String): VideoStats? = withContext(Dispatchers.IO) {
        try {
            val info = youtubeDataSource.getOrFetchStreamInfo(url, false)
            val likeCount = info.likeCount
            val viewCount = info.viewCount
            var commentCount = -1L
            try {
                val commentsInfo = org.schabi.newpipe.extractor.comments.CommentsInfo.getInfo(
                    org.schabi.newpipe.extractor.ServiceList.YouTube,
                    if (url.startsWith("http")) url else "https://www.youtube.com/watch?v=$url"
                )
                commentCount = commentsInfo.commentsCount.toLong()
            } catch (e: Exception) {
                // Comments are optional enrichment; keep like/view stats either way.
                SecureLogger.d("MusicRepositoryImpl", "Comment count unavailable: ${e.message}")
            }

            VideoStats(likeCount = likeCount, viewCount = viewCount, commentCount = commentCount)
        } catch (e: Exception) {
            SecureLogger.w("MusicRepositoryImpl", "Failed to fetch video stats: ${e.message}")
            null
        }
    }
}

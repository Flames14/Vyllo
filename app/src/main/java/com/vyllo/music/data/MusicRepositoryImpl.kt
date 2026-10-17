package com.vyllo.music.data

import com.vyllo.music.domain.repository.IMusicRepository
import com.vyllo.music.domain.repository.VideoStats

import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.LyricsResponse
import com.vyllo.music.domain.model.DownloadEntity
import com.vyllo.music.domain.model.PlaylistEntity
import com.vyllo.music.domain.model.PlaylistSongEntity
import com.vyllo.music.data.download.FavoriteEntity
import com.vyllo.music.data.download.FavoritesDao
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    private val favoritesDao: FavoritesDao,
    private val preferenceManager: PreferenceManager,
    private val playbackQueueManager: PlaybackQueueManager,
    private val lyricsEngine: LyricsEngine,
    private val streamSelector: StreamSelector,
    private val relatedPoolManager: RelatedPoolManager,
    private val subtitleLyricsDelegate: SubtitleLyricsDelegate,
    private val descriptionMetadataParser: DescriptionMetadataParser
) : IMusicRepository {

    private companion object {
        const val PRIMARY_BATCH = 12
        const val SECONDARY_BATCH = 10
        const val TRENDING_BATCH = 12
        const val MAX_PER_ARTIST_HOME = 3
    }

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
                // force == true means the previously handed-out URL was rejected by
                // googlevideo, so it is now worth a round trip to probe alternatives
                // (concurrently) instead of re-serving the same dead link.
                val candidates = streamSelector.videoCandidates(info.videoStreams)
                val videoUrl = if (force) {
                    streamSelector.firstPlayableStreamUrl(candidates)
                } else {
                    candidates.firstOrNull()
                }
                SecureLogger.d("MusicRepositoryImpl") { "Video stream selected: ${videoUrl != null}" }
                videoUrl
            } else {
                var audioCandidates = streamSelector.audioCandidates(info.audioStreams)
                var audioUrl: String? = if (force) {
                    streamSelector.firstPlayableStreamUrl(audioCandidates)
                } else {
                    // Happy path: no probe at all. ExoPlayer surfaces a 403 through
                    // PlayerErrorListener, which comes back here with force = true.
                    audioCandidates.firstOrNull()
                }
                if (audioUrl == null) {
                    // Either no audio-only stream existed, or (force) every audio
                    // candidate was rejected. Fall back to the cheapest video stream.
                    SecureLogger.w("MusicRepositoryImpl", "No usable audio stream, falling back to low-resolution video stream for audio")
                    val videoFallback = streamSelector.videoCandidatesForAudio(info.videoStreams)
                    audioUrl = if (force) {
                        streamSelector.firstPlayableStreamUrl(videoFallback)
                    } else {
                        videoFallback.firstOrNull()
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

    override suspend fun getArtistSongs(artist: String, excludeUrls: Set<String>): List<MusicItem> =
        relatedPoolManager.getArtistSongs(artist, excludeUrls)

    override suspend fun getDiscoverSimilarSongs(
        title: String,
        artist: String,
        excludeUrls: Set<String>,
        excludeUploaders: Set<String>
    ): List<MusicItem> = relatedPoolManager.getDiscoverSimilarSongs(
        title = title,
        artist = artist,
        excludeUrls = excludeUrls,
        excludeUploaders = excludeUploaders
    )

    override suspend fun getRelatedDiscoveryOrder(
        url: String,
        excludeUrls: Set<String>,
        limit: Int
    ): List<MusicItem> = relatedPoolManager.getDiscoveryOrder(url, excludeUrls, limit)

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

        // Typed accessor - no raw key strings outside PreferenceManager.
        preferenceManager.recordArtistListen(item.uploader)
    }

    override suspend fun clearHistory() = historyDao.clearHistory()

    override suspend fun removeFromHistory(url: String) = historyDao.deleteFromHistory(url)

    // Favorites / Ratings Methods
    override suspend fun getRating(url: String): Int =
        favoritesDao.getRating(url) ?: 0

    override fun observeLikedSongs(): Flow<List<MusicItem>> =
        favoritesDao.observeLiked().map { entities -> entities.map { it.toMusicItem() } }

    override fun observeDislikedSongs(): Flow<List<MusicItem>> =
        favoritesDao.observeDisliked().map { entities -> entities.map { it.toMusicItem() } }

    override fun observeLikedCount(): Flow<Int> = favoritesDao.observeLikedCount()

    override suspend fun setRating(item: MusicItem, rating: Int): Boolean {
        // Guard against blank keys: a track with no URL cannot be re-rated later.
        if (item.url.isBlank()) return false
        return try {
            when (rating) {
                1, -1 -> favoritesDao.upsert(
                    FavoriteEntity(
                        url = item.url,
                        title = item.title.take(300),
                        uploader = item.uploader.take(200),
                        thumbnailUrl = item.thumbnailUrl,
                        type = item.type,
                        rating = rating
                    )
                )
                // Treat any out-of-range value as "clear" rather than storing junk.
                else -> favoritesDao.deleteByUrl(item.url)
            }
            // A like is an explicit signal of taste; feed artist affinity with it.
            if (rating == 1) {
                preferenceManager.recordArtistListen(item.uploader)
            }
            true
        } catch (e: Exception) {
            SecureLogger.e("MusicRepositoryImpl", "Failed to persist rating for ${item.url}: ${e.message}")
            false
        }
    }

    override suspend fun clearAllRatings() = favoritesDao.clear()

    /**
 * Home feed seed.
 *
 * Previously six strictly sequential network round trips, and Home awaited the
 * whole thing before rendering anything — the single largest cause of the slow
 * cold start. The independent branches now run concurrently; only the pagination
 * seed stays serial because it must claim [YouTubeDataSource]'s session slot.
 */
override suspend fun getPatternSuggestions(): List<MusicItem> = coroutineScope {
        val topArtists = preferenceManager.getTopArtists(10)

        // Dynamic time-of-day contextual music seed
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val contextualSeed = when (hour) {
            in 5..11 -> listOf("morning acoustic songs", "fresh morning pop hits")
            in 12..17 -> listOf("feel good upbeat music", "lofi chill beats")
            in 18..22 -> listOf("evening vibes chill music", "top hits official audio")
            else -> listOf("late night drive songs", "deep sleep relaxing music")
        }.random()

        // Four independent branches. topArtists is not shuffled: a stable top
        // artist keeps the feed consistent between refreshes, and variety already
        // comes from the contextual seed plus the interleaving downstream.
        val primaryArtist = topArtists.firstOrNull()
        val secondaryArtist = topArtists.getOrNull(1)

        val primaryDeferred = async {
            val query = if (primaryArtist != null) "$primaryArtist radio" else "top music hits 2026"
            relatedPoolManager.cleanSongList(youtubeDataSource.searchMusic(query, maintainSession = false))
                .take(PRIMARY_BATCH)
        }
        val secondaryDeferred = async {
            val query = secondaryArtist?.let { "$it mix" } ?: contextualSeed
            relatedPoolManager.cleanSongList(youtubeDataSource.searchMusic(query, maintainSession = false))
                .take(SECONDARY_BATCH)
        }
        val contextualDeferred = async {
            relatedPoolManager.cleanSongList(youtubeDataSource.searchMusic(contextualSeed, maintainSession = false))
                .take(SECONDARY_BATCH)
        }
        val trendingDeferred = async {
            relatedPoolManager.cleanSongList(getTrendingMusic()).take(TRENDING_BATCH)
        }

        val combined = buildList {
            addAll(primaryDeferred.await())
            addAll(secondaryDeferred.await())
            addAll(contextualDeferred.await())
            addAll(trendingDeferred.await())
        }.distinctBy { it.url }

        // Pagination seed runs last: fetchItemsWithPagination claims the shared
        // session slot, so it must not race the parallel searches above.
        val paginationQuery = if (primaryArtist != null) "songs like $primaryArtist" else "global top 50 music"
        val withPagination = combined + relatedPoolManager.cleanSongList(
            youtubeDataSource.fetchItemsWithPagination(paginationQuery)
        )

        // Cap at 3 per artist rather than 2: at 2 the feed could be squeezed down to
        // a handful of rows whenever one artist's searches dominated the results.
        relatedPoolManager.capPerArtist(withPagination.distinctBy { it.url }, MAX_PER_ARTIST_HOME)
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
        var extLanguage: String?
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

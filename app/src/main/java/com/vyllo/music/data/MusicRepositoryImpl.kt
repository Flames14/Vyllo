package com.vyllo.music.data

import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.MusicItemType
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import com.vyllo.music.domain.model.LyricsResult
import com.vyllo.music.domain.model.LyricsStatus
import com.vyllo.music.domain.model.SyncedLyricLine
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

    /**
     * Cached suggestion pool per anchor track so repeated calls (prefetch, autoplay,
     * Up Next UI) share the same result instead of refetching everything each time.
     */
    private val relatedPoolCache = ConcurrentHashMap<String, RelatedPool>()

    private val relatedDemotePattern = Regex(
        "\\b(lyrics|live|cover|remix|sped|slowed|reverb|karaoke|instrumental|mashup|reaction|loop|\\d+ hour|with lyrics|official lyric)\\b",
        RegexOption.IGNORE_CASE
    )

    private class RelatedPool(
        val anchorUrl: String,
        val items: MutableList<MusicItem>,
        val seenKeys: MutableSet<String>,
        val fetchedAt: Long = System.currentTimeMillis(),
        var expansionRound: Int = 0
    )

    companion object {
        private const val RELATED_POOL_TTL_MS = 15 * 60 * 1000L
        private const val RELATED_MAX_ITEMS = 200
        private const val EXPANSION_QUERIES_PER_ROUND = 2
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

    override suspend fun getRelatedSongs(url: String, force: Boolean): List<MusicItem> = withContext(Dispatchers.IO) {
        try {
            if (force) {
                relatedPoolCache.remove(url)
            } else {
                relatedPoolCache[url]?.let { pool ->
                    if (System.currentTimeMillis() - pool.fetchedAt < RELATED_POOL_TTL_MS) {
                        return@withContext synchronized(pool) { pool.items.toList() }
                    }
                    relatedPoolCache.remove(url)
                }
            }

            val info = youtubeDataSource.getOrFetchStreamInfo(url, false)
            val currentTitle = info.name?.trim().orEmpty()
            val currentUploader = info.uploaderName?.trim().orEmpty()
            val anchorDurationSecs = info.duration

            // 1) YouTube's own related items (auto-mix, similar videos, etc.)
            val relatedItems = info.relatedItems.mapNotNull { item ->
                if (item is org.schabi.newpipe.extractor.stream.StreamInfoItem) {
                    val bestThumbnail = item.thumbnails?.maxByOrNull { it.width * it.height }?.url
                        ?: item.thumbnails?.lastOrNull()?.url
                        ?: item.thumbnails?.firstOrNull()?.url
                        ?: ""
                    MusicItem(
                        title = item.name ?: "",
                        url = item.url ?: "",
                        uploader = item.uploaderName ?: "",
                        thumbnailUrl = bestThumbnail,
                        type = MusicItemType.SONG,
                        durationSecs = item.duration
                    )
                } else null
            }

            // 2) Targeted searches built from the current track's artist/style so the
            //    suggestions actually match the user's listening taste.
            val searchResults = fetchRelatedSearchResults(
                initialRelatedQueries(currentUploader, currentTitle)
            )

            val pool = buildRelatedPool(
                anchorUrl = url,
                currentTitle = currentTitle,
                currentUploader = currentUploader,
                anchorDurationSecs = anchorDurationSecs,
                searchResults = searchResults,
                relatedItems = relatedItems
            )
            relatedPoolCache[url] = pool

            SecureLogger.d("MusicRepositoryImpl") {
                "Related songs: ${pool.items.size} (search=${searchResults.size}, youtube=${relatedItems.size}) for url=$url"
            }
            pool.items.toList()
        } catch (e: Exception) {
            SecureLogger.e("MusicRepositoryImpl", "getRelatedSongs failed", e)
            emptyList()
        }
    }

    override suspend fun getMoreRelatedSongs(url: String): List<MusicItem> = withContext(Dispatchers.IO) {
        val pool = relatedPoolCache[url]
        if (pool == null || System.currentTimeMillis() - pool.fetchedAt >= RELATED_POOL_TTL_MS) {
            // Pool missing or stale — rebuild it from scratch.
            return@withContext getRelatedSongs(url)
        }
        try {
            val info = youtubeDataSource.getOrFetchStreamInfo(url, false)
            val currentTitle = info.name?.trim().orEmpty()
            val currentUploader = info.uploaderName?.trim().orEmpty()
            val anchorDurationSecs = info.duration

            val queries = synchronized(pool) {
                val q = expansionQueries(currentUploader, currentTitle, pool.expansionRound)
                if (q.isNotEmpty()) pool.expansionRound++
                q
            }
            if (queries.isEmpty()) {
                SecureLogger.d("MusicRepositoryImpl") { "Related pool fully expanded for $url" }
                return@withContext emptyList()
            }

            val newResults = fetchRelatedSearchResults(queries)
            val added = mutableListOf<MusicItem>()
            synchronized(pool) {
                newResults.forEach { item ->
                    if (isGoodCandidate(item, currentTitle, currentUploader, url) &&
                        pool.seenKeys.add(normalizedTrackKey(item))
                    ) {
                        val score = scoreCandidate(item, currentUploader, anchorDurationSecs)
                        added.add(item)
                        val insertAt = pool.items.indexOfFirst {
                            scoreCandidate(it, currentUploader, anchorDurationSecs) < score
                        }
                        if (insertAt == -1) pool.items.add(item) else pool.items.add(insertAt, item)
                    }
                }
                if (pool.items.size > RELATED_MAX_ITEMS) {
                    pool.items.subList(RELATED_MAX_ITEMS, pool.items.size).clear()
                }
            }
            SecureLogger.d("MusicRepositoryImpl") {
                "Related expansion: +${added.size} for $url (total ${pool.items.size})"
            }
            added
        } catch (e: Exception) {
            SecureLogger.e("MusicRepositoryImpl", "getMoreRelatedSongs failed", e)
            emptyList()
        }
    }

    override suspend fun getArtistSongs(artist: String): List<MusicItem> = withContext(Dispatchers.IO) {
        val cleanArtist = artist.replace(" - Topic", "").trim()
        if (cleanArtist.isBlank()) return@withContext emptyList()
        try {
            val results = youtubeDataSource.searchMusic("$cleanArtist top songs tracks", maintainSession = false)
            results.filter { it.type == MusicItemType.SONG }
        } catch (e: Exception) {
            SecureLogger.w("MusicRepositoryImpl", "getArtistSongs failed for $cleanArtist", e)
            emptyList()
        }
    }

    override suspend fun getDiscoverSimilarSongs(title: String, artist: String): List<MusicItem> = withContext(Dispatchers.IO) {
        val cleanArtist = artist.replace(" - Topic", "").trim()
        try {
            val queries = listOf(
                "songs like $title",
                "artists similar to $cleanArtist music",
                "trending music similar to $cleanArtist"
            )
            val results = fetchRelatedSearchResults(queries)
            val cleanLower = cleanArtist.lowercase()
            results.filter { it.type == MusicItemType.SONG && !it.uploader.lowercase().contains(cleanLower) }
        } catch (e: Exception) {
            SecureLogger.w("MusicRepositoryImpl", "getDiscoverSimilarSongs failed", e)
            emptyList()
        }
    }

    private suspend fun fetchRelatedSearchResults(queries: List<String>): List<MusicItem> = coroutineScope {
        queries.map { query ->
            async {
                try {
                    youtubeDataSource.searchMusic(query, maintainSession = false)
                } catch (e: Exception) {
                    SecureLogger.w("MusicRepositoryImpl", "Related search failed: $query", e)
                    emptyList()
                }
            }
        }.awaitAll().flatten()
    }

    private fun initialRelatedQueries(uploader: String, title: String): List<String> = buildList {
        val cleanUploader = uploader.replace(" - Topic", "").trim()
        if (cleanUploader.isNotBlank()) {
            add("songs like $cleanUploader hits")
            add("artists similar to $cleanUploader music")
            add("$cleanUploader radio mix")
            add("best of $cleanUploader and similar")
        }
        if (title.isNotBlank()) {
            add("$title song mix")
            add("music like $title")
            add("$title radio playlist")
        }
    }.shuffled().take(4)

    private fun expansionQueries(uploader: String, title: String, round: Int): List<String> {
        val cleanUploader = uploader.replace(" - Topic", "").trim()
        val templates = buildList {
            if (cleanUploader.isNotBlank()) {
                add("similar to $cleanUploader radio")
                add("best songs like $cleanUploader")
                add("$cleanUploader playlist mix")
            }
            if (title.isNotBlank()) {
                add("$title similar songs")
                add("$title playlist mix")
            }
        }
        if (templates.isEmpty()) return emptyList()
        val start = (round * EXPANSION_QUERIES_PER_ROUND).coerceAtMost(templates.size)
        return templates.drop(start).take(EXPANSION_QUERIES_PER_ROUND)
    }

    private fun buildRelatedPool(
        anchorUrl: String,
        currentTitle: String,
        currentUploader: String,
        anchorDurationSecs: Long,
        searchResults: List<MusicItem>,
        relatedItems: List<MusicItem>
    ): RelatedPool {
        val cleanAnchorUploader = currentUploader.replace(" - Topic", "").trim().lowercase()

        val sameArtistList = mutableListOf<MusicItem>()
        val relatedArtistList = mutableListOf<MusicItem>()
        val seenTrackKeys = mutableSetOf<String>()

        // Combine YouTube's dynamic related items and search recommendations
        val allCandidates = (relatedItems + searchResults).filter {
            isGoodCandidate(it, currentTitle, currentUploader, anchorUrl)
        }

        allCandidates.forEach { item ->
            val key = normalizedTrackKey(item)
            if (seenTrackKeys.add(key)) {
                val itemUploader = item.uploader.replace(" - Topic", "").trim().lowercase()
                val isSameArtist = cleanAnchorUploader.isNotBlank() && (
                    itemUploader == cleanAnchorUploader ||
                    itemUploader.contains(cleanAnchorUploader) ||
                    item.title.lowercase().contains(cleanAnchorUploader)
                )

                if (isSameArtist) {
                    sameArtistList.add(item)
                } else {
                    relatedArtistList.add(item)
                }
            }
        }

        // Interleave for rich variety with dynamic shuffle so each generated mix is fresh and non-repetitive:
        val shuffledSameArtist = sameArtistList.shuffled()
        val shuffledRelatedArtist = relatedArtistList.shuffled()

        val pool = RelatedPool(anchorUrl = anchorUrl, items = mutableListOf(), seenKeys = seenTrackKeys)
        var sameIdx = 0
        var relIdx = 0

        while (sameIdx < shuffledSameArtist.size || relIdx < shuffledRelatedArtist.size) {
            // Add 1 from same artist
            if (sameIdx < shuffledSameArtist.size) {
                pool.items.add(shuffledSameArtist[sameIdx++])
            }
            // Add up to 3 from related artists for rich diversity
            for (i in 0 until 3) {
                if (relIdx < shuffledRelatedArtist.size) {
                    pool.items.add(shuffledRelatedArtist[relIdx++])
                }
            }
        }

        return pool
    }

    private fun isGoodCandidate(
        item: MusicItem,
        currentTitle: String,
        currentUploader: String,
        anchorUrl: String
    ): Boolean {
        if (item.type != MusicItemType.SONG) return false
        if (item.url.isBlank() || item.title.isBlank()) return false
        if (item.url == anchorUrl) return false
        val isSameTrack = item.title.equals(currentTitle, ignoreCase = true) &&
            item.uploader.equals(currentUploader, ignoreCase = true)
        if (isSameTrack) return false
        // Skip Shorts and long-form (podcasts/livestreams); keep unknown durations.
        return item.durationSecs == 0L || item.durationSecs in 30..900
    }

    /**
     * Heuristic score: strong boost for same artist, mild boost for similar
     * duration, and demotion for variant uploads (lyrics/live/cover/remix...).
     */
    private fun scoreCandidate(
        item: MusicItem,
        currentUploader: String,
        anchorDurationSecs: Long
    ): Double {
        var score = 0.0
        val titleLower = item.title.lowercase()
        val uploaderLower = item.uploader.lowercase()
        val anchorUploader = currentUploader.lowercase()

        if (uploaderLower == anchorUploader) {
            score += 4.0
        } else if (anchorUploader.isNotBlank() && uploaderLower.contains(anchorUploader)) {
            score += 3.0
        } else if (anchorUploader.isNotBlank() && titleLower.contains(anchorUploader)) {
            score += 2.0
        }

        if (anchorDurationSecs in 60..600 && item.durationSecs in 60..600) {
            val diff = kotlin.math.abs(item.durationSecs - anchorDurationSecs)
            if (diff < 30) score += 1.5
            else if (diff < 90) score += 0.75
        }

        if (relatedDemotePattern.containsMatchIn(item.title)) {
            score -= 1.0
        }
        return score
    }

    /**
     * Normalized (title, uploader) key so different uploads of the same song
     * (official audio, lyric video, live, sped-up...) collapse into one entry.
     */
    private fun normalizedTrackKey(item: MusicItem): String {
        val cleanTitle = item.title.lowercase()
            .replace(Regex("[(\\[][^)\\]}]*[)\\]}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val cleanUploader = item.uploader.lowercase()
            .replace(" - topic", "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return "$cleanTitle|$cleanUploader"
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
            allItems.addAll(cleanSongList(youtubeDataSource.searchMusic("$selectedArtist radio", maintainSession = false)).take(12))
        } else {
            allItems.addAll(cleanSongList(youtubeDataSource.searchMusic("top music hits 2026", maintainSession = false)).take(12))
        }

        // 2. Mixed For You (Secondary Artist or Genre Discovery)
        if (topArtists.size > 1) {
            val secondArtist = topArtists[1]
            allItems.addAll(cleanSongList(youtubeDataSource.searchMusic("$secondArtist mix", maintainSession = false)).take(10))
        } else {
            allItems.addAll(cleanSongList(youtubeDataSource.searchMusic(contextualSeed, maintainSession = false)).take(10))
        }

        // 3. Time-of-Day Contextual Discovery
        allItems.addAll(cleanSongList(youtubeDataSource.searchMusic(contextualSeed, maintainSession = false)).take(10))

        // 4. Global Trending Music
        allItems.addAll(cleanSongList(youtubeDataSource.getTrendingMusic()).take(12))

        val finalItems = allItems.distinctBy { it.url }.toMutableList()

        // 5. Pagination Seed
        val paginationQuery = if (topArtists.isNotEmpty()) "songs like ${topArtists.first()}" else "global top 50 music"
        val paginatedResults = cleanSongList(youtubeDataSource.fetchItemsWithPagination(paginationQuery))
        finalItems.addAll(paginatedResults)

        return finalItems.distinctBy { it.url }.let { capPerArtist(it, 2) }
    }

    /**
     * Caps how many suggestions a single artist may occupy so the home feed
     * stays diverse instead of being flooded by one artist's search results.
     */
    private fun capPerArtist(items: List<MusicItem>, maxPerArtist: Int): List<MusicItem> {
        val counts = HashMap<String, Int>()
        return items.filter { item ->
            val key = item.uploader.lowercase().replace(" - topic", "").trim()
            val current = counts[key] ?: 0
            if (current < maxPerArtist) {
                counts[key] = current + 1
                true
            } else {
                false
            }
        }
    }

    private fun cleanSongList(items: List<MusicItem>): List<MusicItem> {
        return items
            .filter {
                it.type == MusicItemType.SONG &&
                    it.url.isNotBlank() &&
                    it.title.isNotBlank() &&
                    (it.durationSecs == 0L || it.durationSecs in 30..900)
            }
            .distinctBy { it.url }
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

    private data class DescriptionMetadata(
        val title: String?,
        val singers: String?,
        val music: String?,
        val movie: String?,
        val language: String?
    )

    private fun parseProvidedToYouTubeDescription(description: String): DescriptionMetadata? {
        val lines = description.lines().map { it.trim() }
        val startIndex = lines.indexOfFirst { it.contains("Provided to YouTube by", ignoreCase = true) }
        if (startIndex == -1) return null

        var songLineIndex = -1
        for (i in (startIndex + 1) until lines.size) {
            if (lines[i].isNotBlank()) {
                songLineIndex = i
                break
            }
        }
        if (songLineIndex == -1) return null

        val songLine = lines[songLineIndex]
        val songParts = songLine.split(Regex("[·•]")).map { it.trim() }
        if (songParts.isEmpty()) return null

        val title = songParts[0]
        val singers = if (songParts.size > 1) {
            songParts.subList(1, songParts.size).joinToString(", ")
        } else {
            null
        }

        var albumLineIndex = -1
        for (i in (songLineIndex + 1) until lines.size) {
            if (lines[i].isNotBlank()) {
                albumLineIndex = i
                break
            }
        }
        val album = if (albumLineIndex != -1) {
            val candidate = lines[albumLineIndex]
            if (candidate.startsWith("℗") || candidate.startsWith("Released on:", ignoreCase = true) || candidate.contains("Auto-generated", ignoreCase = true)) {
                null
            } else {
                candidate
            }
        } else {
            null
        }

        return DescriptionMetadata(
            title = title,
            singers = singers,
            music = null,
            movie = album,
            language = null
        )
    }

    private fun parseDescriptionMetadata(description: String): DescriptionMetadata {
        var title: String? = null
        var singers: String? = null
        var music: String? = null
        var movie: String? = null
        var language: String? = null

        description.lines().forEach { line ->
            val trimmed = line.trim()
            if (title == null) {
                val match = Regex("(?i)^(?:\\s*[-–—•*]*\\s*)?(?:Song|Track|Title)\\s*[:\\-–—]\\s*(.+)").find(trimmed)
                if (match != null) title = match.groupValues[1].trim()
            }
            if (singers == null) {
                val match = Regex("(?i)^(?:\\s*[-–—•*]*\\s*)?(?:Singer|Singers|Artist|Artists|Vocals|Sung by|Singers/Vocals|Singer/Vocals)\\s*[:\\-–—]\\s*(.+)").find(trimmed)
                if (match != null) singers = match.groupValues[1].trim()
            }
            if (music == null) {
                val match = Regex("(?i)^(?:\\s*[-–—•*]*\\s*)?(?:Music|Music Director|Composer|Composed by|Music Composer)\\s*[:\\-–—]\\s*(.+)").find(trimmed)
                if (match != null) music = match.groupValues[1].trim()
            }
            if (movie == null) {
                val match = Regex("(?i)^(?:\\s*[-–—•*]*\\s*)?(?:Movie|Album|Film|Movie Name|Film Name)\\s*[:\\-–—]\\s*(.+)").find(trimmed)
                if (match != null) movie = match.groupValues[1].trim()
            }
            if (language == null) {
                val match = Regex("(?i)^(?:\\s*[-–—•*]*\\s*)?(?:Language|Lang)\\s*[:\\-–—]\\s*(.+)").find(trimmed)
                if (match != null) language = match.groupValues[1].trim()
            }
        }

        return DescriptionMetadata(title, singers, music, movie, language)
    }

    private fun detectLanguageFromTagsOrTitle(title: String, tags: List<String>): String? {
        val languages = listOf("Tamil", "Telugu", "Hindi", "Malayalam", "Kannada", "Punjabi", "Bengali", "English", "Spanish")
        for (lang in languages) {
            if (title.contains(lang, ignoreCase = true)) return lang
            if (tags.any { it.contains(lang, ignoreCase = true) }) return lang
        }
        return null
    }

    private fun detectLanguageFromText(text: String): String? {
        val languages = listOf("Tamil", "Telugu", "Hindi", "Malayalam", "Kannada", "Punjabi", "Bengali", "English", "Spanish")
        for (lang in languages) {
            if (text.contains(lang, ignoreCase = true)) return lang
        }
        return null
    }

    private fun selectBestSubtitles(streams: List<SubtitlesStream>, targetLanguage: String?): SubtitlesStream? {
        if (streams.isEmpty()) return null
        
        val supportedStreams = streams.filter { 
            (it.format == org.schabi.newpipe.extractor.MediaFormat.VTT || 
            it.format == org.schabi.newpipe.extractor.MediaFormat.SRT) &&
            !it.url.isNullOrBlank()
        }
        
        if (supportedStreams.isEmpty()) return null

        val langLower = targetLanguage?.lowercase()

        if (!langLower.isNullOrBlank()) {
            supportedStreams.firstOrNull { 
                !it.isAutoGenerated && 
                (it.languageTag.lowercase().contains(langLower) || it.displayLanguageName.lowercase().contains(langLower)) 
            }?.let { return it }
        }

        supportedStreams.firstOrNull { 
            !it.isAutoGenerated && 
            (it.languageTag.lowercase().startsWith("en") || it.displayLanguageName.lowercase().contains("english")) 
        }?.let { return it }

        if (!langLower.isNullOrBlank()) {
            supportedStreams.firstOrNull { 
                it.isAutoGenerated && 
                (it.languageTag.lowercase().contains(langLower) || it.displayLanguageName.lowercase().contains(langLower)) 
            }?.let { return it }
        }

        supportedStreams.firstOrNull { 
            it.isAutoGenerated && 
            (it.languageTag.lowercase().startsWith("en") || it.displayLanguageName.lowercase().contains("english")) 
        }?.let { return it }

        supportedStreams.firstOrNull { !it.isAutoGenerated }?.let { return it }

        return supportedStreams.firstOrNull()
    }

    private fun parseSubtitleTimestamp(timeStr: String): Long? {
        val clean = timeStr.trim().replace(',', '.')
        val parts = clean.split('.')
        val timeParts = parts[0].split(':')
        if (timeParts.size < 2) return null
        val ms = parts.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLongOrNull() ?: 0L
        val secs = timeParts.last().toLongOrNull() ?: return null
        val mins = timeParts[timeParts.size - 2].toLongOrNull() ?: return null
        val hrs = if (timeParts.size > 2) timeParts[timeParts.size - 3].toLongOrNull() ?: 0L else 0L
        return hrs * 3600000L + mins * 60000L + secs * 1000L + ms
    }

    private fun parseSubtitles(content: String): List<SyncedLyricLine> {
        val lines = content.lines()
        val list = mutableListOf<SyncedLyricLine>()
        var currentTimestamp: Long? = null
        val currentText = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.contains("-->")) {
                if (currentTimestamp != null && currentText.isNotEmpty()) {
                    val text = cleanSubtitleText(currentText.toString())
                    if (text.isNotBlank()) {
                        list.add(SyncedLyricLine(currentTimestamp, text))
                    }
                    currentText.clear()
                }
                val parts = trimmed.split("-->")
                if (parts.isNotEmpty()) {
                    currentTimestamp = parseSubtitleTimestamp(parts[0])
                }
            } else if (trimmed.isBlank()) {
                if (currentTimestamp != null && currentText.isNotEmpty()) {
                    val text = cleanSubtitleText(currentText.toString())
                    if (text.isNotBlank()) {
                        list.add(SyncedLyricLine(currentTimestamp, text))
                    }
                    currentText.clear()
                }
                currentTimestamp = null
            } else if (trimmed.toLongOrNull() != null) {
                continue
            } else if (trimmed.equals("WEBVTT", ignoreCase = true) || trimmed.startsWith("NOTE")) {
                continue
            } else {
                if (currentTimestamp != null) {
                    if (currentText.isNotEmpty()) currentText.append(" ")
                    currentText.append(trimmed)
                }
            }
        }
        if (currentTimestamp != null && currentText.isNotEmpty()) {
            val text = cleanSubtitleText(currentText.toString())
            if (text.isNotBlank()) {
                list.add(SyncedLyricLine(currentTimestamp, text))
            }
        }
        return list
    }

    private fun cleanSubtitleText(text: String): String {
        return text.replace(Regex("<[^>]*>"), "").trim()
    }

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
                        lyricsStatus = LyricsStatus(
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
                
                val topicMeta = parseProvidedToYouTubeDescription(description)
                val meta = topicMeta ?: parseDescriptionMetadata(description)
                extTitle = meta.title
                extArtist = meta.singers
                extAlbum = meta.movie
                extLanguage = meta.language ?: detectLanguageFromTagsOrTitle(title, tags) ?: detectLanguageFromText(description)
                extMusic = meta.music
                
                SecureLogger.d("MusicRepositoryImpl") {
                    "Parsed metadata from StreamInfo: title=$extTitle, artist=$extArtist, album=$extAlbum, lang=$extLanguage, music=$extMusic"
                }

                // Try to extract and download subtitles
                val bestSubStream = selectBestSubtitles(info.subtitles, extLanguage)
                if (bestSubStream != null) {
                    SecureLogger.d("MusicRepositoryImpl") {
                        "Found subtitle stream: lang=${bestSubStream.displayLanguageName}, format=${bestSubStream.format}, url=${bestSubStream.url}"
                    }
                    val request = Request.Builder()
                        .url(bestSubStream.url!!)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .build()
                    
                    val subContent = withContext(Dispatchers.IO) {
                        try {
                            streamProbeClient.newCall(request).execute().use { response ->
                                if (response.isSuccessful) response.body?.string() else null
                            }
                        } catch (e: Exception) {
                            SecureLogger.e("MusicRepositoryImpl", "Failed to download subtitles: ${e.message}", e)
                            null
                        }
                    }

                    if (!subContent.isNullOrBlank()) {
                        val syncedLines = parseSubtitles(subContent)
                        if (syncedLines.isNotEmpty()) {
                            val plainText = syncedLines.joinToString("\n") { it.content }
                            val detectText = plainText
                            val detectedLangs = LyricsEngine.detectLanguage(detectText)
                            
                            val lyricsResult = LyricsResult(
                                id = -1L,
                                trackName = extTitle ?: title,
                                artistName = extArtist ?: artist,
                                albumName = extAlbum,
                                duration = duration,
                                instrumental = false,
                                plainLyrics = plainText,
                                syncedLyrics = subContent
                            )
                            
                            subtitleResponse = LyricsResponse(
                                success = true,
                                strategy = "YOUTUBE_SUBTITLES",
                                result = lyricsResult,
                                results = listOf(lyricsResult),
                                plainLyrics = plainText,
                                syncedLines = syncedLines,
                                languages = if (extLanguage != null) listOf(extLanguage.lowercase()) else detectedLangs,
                                lyricsStatus = LyricsStatus(
                                    hasPlain = true,
                                    hasSynced = true,
                                    isInstrumental = false
                                )
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
            } catch (_: Exception) {}

            VideoStats(likeCount = likeCount, viewCount = viewCount, commentCount = commentCount)
        } catch (e: Exception) {
            SecureLogger.w("MusicRepositoryImpl", "Failed to fetch video stats: ${e.message}")
            null
        }
    }
}

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
}

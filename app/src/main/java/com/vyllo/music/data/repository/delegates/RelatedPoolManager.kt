package com.vyllo.music.data.repository.delegates

import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.data.network.YouTubeDataSource
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.MusicItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

class RelatedPool(
    val anchorUrl: String,
    val items: MutableList<MusicItem>,
    val seenKeys: MutableSet<String>,
    val fetchedAt: Long = System.currentTimeMillis(),
    var expansionRound: Int = 0
)

@Singleton
class RelatedPoolManager @Inject constructor(
    private val youtubeDataSource: YouTubeDataSource
) {
    /**
     * Cached suggestion pool per anchor track so repeated calls (prefetch, autoplay,
     * Up Next UI) share the same result instead of refetching everything each time.
     */
    private val relatedPoolCache = ConcurrentHashMap<String, RelatedPool>()

    private val relatedDemotePattern = Regex(
        "\\b(lyrics|live|cover|remix|sped|slowed|reverb|karaoke|instrumental|mashup|reaction|loop|\\d+ hour|with lyrics|official lyric)\\b",
        RegexOption.IGNORE_CASE
    )

    companion object {
        private const val RELATED_POOL_TTL_MS = 15 * 60 * 1000L
        private const val RELATED_MAX_ITEMS = 200
        private const val EXPANSION_QUERIES_PER_ROUND = 2
    }

    suspend fun getRelatedSongs(url: String, force: Boolean): List<MusicItem> = withContext(Dispatchers.IO) {
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

    suspend fun getMoreRelatedSongs(url: String): List<MusicItem> = withContext(Dispatchers.IO) {
        val pool = relatedPoolCache[url]
        if (pool == null || System.currentTimeMillis() - pool.fetchedAt >= RELATED_POOL_TTL_MS) {
            // Pool missing or stale — rebuild it from scratch.
            return@withContext getRelatedSongs(url, false)
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

    suspend fun getArtistSongs(artist: String): List<MusicItem> = withContext(Dispatchers.IO) {
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

    suspend fun getDiscoverSimilarSongs(title: String, artist: String): List<MusicItem> = withContext(Dispatchers.IO) {
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

    suspend fun fetchRelatedSearchResults(queries: List<String>): List<MusicItem> = coroutineScope {
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

    fun initialRelatedQueries(uploader: String, title: String): List<String> = buildList {
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

    fun expansionQueries(uploader: String, title: String, round: Int): List<String> {
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

    fun buildRelatedPool(
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

    fun isGoodCandidate(
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
    fun scoreCandidate(
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
    fun normalizedTrackKey(item: MusicItem): String {
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

    /**
     * Caps how many suggestions a single artist may occupy so the home feed
     * stays diverse instead of being flooded by one artist's search results.
     */
    fun capPerArtist(items: List<MusicItem>, maxPerArtist: Int): List<MusicItem> {
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

    fun cleanSongList(items: List<MusicItem>): List<MusicItem> {
        return items
            .filter {
                it.type == MusicItemType.SONG &&
                    it.url.isNotBlank() &&
                    it.title.isNotBlank() &&
                    (it.durationSecs == 0L || it.durationSecs in 30..900)
            }
            .distinctBy { it.url }
    }
}

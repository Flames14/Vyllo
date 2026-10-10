package com.vyllo.music.data.repository.delegates

import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.data.download.HistoryDao
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

/**
 * A ranked, pre-filtered candidate pool for one anchor track.
 *
 * [ranked] is kept sorted by score descending at all times, so expanding the
 * pool is a single linear merge rather than a re-score-everything pass.
 */
class RelatedPool(
    val anchorUrl: String,
    val anchor: RecommendationAnchor,
    val ranked: MutableList<ScoredCandidate> = mutableListOf(),
    val seenKeys: MutableSet<String> = mutableSetOf(),
    val fetchedAt: Long = System.currentTimeMillis(),
    var expansionRound: Int = 0,
    /** Ordered query ladder for this anchor, reused by expansion rounds. */
    val ladder: List<WeightedQuery> = emptyList()
)

@Singleton
class RelatedPoolManager @Inject constructor(
    private val youtubeDataSource: YouTubeDataSource,
    private val historyDao: HistoryDao,
    private val trackMetadataResolver: TrackMetadataResolver
) {

    companion object {
        private const val RELATED_POOL_TTL_MS = 15 * 60 * 1000L
        private const val RELATED_MAX_ITEMS = 200
        private const val MAX_CACHED_ANCHORS = 5

        /** Rows of play history used for the taste signal. */
        private const val TASTE_HISTORY_LIMIT = 50

        /** Up Next continuity: 2 same/similar-artist tracks per discovery track. */
        const val UPNE_PRIMARY_RUN = 2
        const val UPNE_DISCOVERY_RUN = 1

        /** Related tab: discovery-leaning, 1 same/similar-artist per 3 others. */
        const val RELATED_PRIMARY_RUN = 1
        const val RELATED_DISCOVERY_RUN = 3

        const val DISCOVERY_ORDER_LIMIT = 25
        const val ARTIST_SECTION_LIMIT = 15
        const val SIMILAR_SECTION_LIMIT = 20

        private const val MAX_PER_ARTIST_SECTION = 3
    }

    // ---------------------------------------------------------------------
    // Pool cache (access-ordered LRU over anchors)
    // ---------------------------------------------------------------------

    private val relatedPoolCache: MutableMap<String, RelatedPool> =
        object : LinkedHashMap<String, RelatedPool>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RelatedPool>): Boolean =
                size > MAX_CACHED_ANCHORS
        }

    private fun getPool(url: String): RelatedPool? = synchronized(relatedPoolCache) { relatedPoolCache[url] }

    private fun putPool(url: String, pool: RelatedPool) = synchronized(relatedPoolCache) { relatedPoolCache[url] = pool }

    private fun removePool(url: String) = synchronized(relatedPoolCache) { relatedPoolCache.remove(url) }

    private fun isFresh(pool: RelatedPool): Boolean =
        System.currentTimeMillis() - pool.fetchedAt < RELATED_POOL_TTL_MS

    // ---------------------------------------------------------------------
    // Listener taste + recent history snapshot
    // ---------------------------------------------------------------------

    /**
     * How often each uploader appears in recent play history, plus the tracks seen
     * there.
     *
     * Uploaders bias ordering; track keys are what let the Related tab skip what
     * the listener just played. Up Next deliberately still allows a repeat after a
     * long gap — blocking it would starve the queue near the end of a session.
     */
    private val userTaste = ConcurrentHashMap<String, Int>()
    private val recentTrackKeys = ConcurrentHashMap.newKeySet<String>()
    private val recentUrls = ConcurrentHashMap.newKeySet<String>()

    private suspend fun refreshUserTaste() {
        try {
            val counts = HashMap<String, Int>()
            val keys = HashSet<String>()
            val urls = HashSet<String>()
            historyDao.getRecentHistoryList(limit = TASTE_HISTORY_LIMIT).forEach { item ->
                val uploaderKey = RelatedRanking.sameArtistKey(item.uploader)
                if (uploaderKey.isNotBlank()) counts[uploaderKey] = (counts[uploaderKey] ?: 0) + 1
                keys += RelatedRanking.normalizedTrackKey(item.title, item.uploader)
                if (item.url.isNotBlank()) urls += item.url
            }
            userTaste.clear()
            userTaste.putAll(counts)
            recentTrackKeys.clear()
            recentTrackKeys.addAll(keys)
            recentUrls.clear()
            recentUrls.addAll(urls)
        } catch (e: Exception) {
            SecureLogger.d("RelatedPoolManager") { "taste refresh skipped: ${e.message}" }
        }
    }

    private fun tasteCount(uploader: String): Int =
        userTaste[RelatedRanking.sameArtistKey(uploader)] ?: 0

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /**
     * Builds (or returns the cached) ranked pool for [url] and returns it in
     * **continuity order** — this is what seeds Up Next.
     */
    suspend fun getRelatedSongs(url: String, force: Boolean): List<MusicItem> = withContext(Dispatchers.IO) {
        try {
            val cached = if (force) {
                removePool(url)
                null
            } else {
                getPool(url)?.takeIf { isFresh(it) }
            }
            if (cached != null) return@withContext continuityOrder(cached)

            val pool = buildPool(url)
            putPool(url, pool)
            SecureLogger.d("RelatedPoolManager") {
                "pool=${pool.ranked.size} queries=${pool.ladder.size} url=$url"
            }
            continuityOrder(pool)
        } catch (e: Exception) {
            SecureLogger.e("RelatedPoolManager", "getRelatedSongs failed", e)
            emptyList()
        }
    }

    /** Expands the pool with another round of the query ladder; returns only the new tracks. */
    suspend fun getMoreRelatedSongs(url: String): List<MusicItem> = withContext(Dispatchers.IO) {
        val pool = getPool(url)?.takeIf { isFresh(it) }
            ?: return@withContext getRelatedSongs(url, force = false)

        try {
            val batch = RelatedRanking.buildExpansionLadder(pool.ladder, pool.expansionRound)
            if (batch.isEmpty()) {
                SecureLogger.d("RelatedPoolManager") { "pool fully expanded for $url" }
                return@withContext emptyList()
            }
            synchronized(pool) { pool.expansionRound++ }

            val results = fetchRelatedSearchResults(batch)
            val added = mergeIntoPool(pool, results)
            SecureLogger.d("RelatedPoolManager") {
                "expansion: +${added.size} total=${pool.ranked.size} url=$url"
            }
            added
        } catch (e: Exception) {
            SecureLogger.e("RelatedPoolManager", "getMoreRelatedSongs failed", e)
            emptyList()
        }
    }

    /**
     * Discovery ordering for the Related tab. Same pool as Up Next, re-weighted so
     * new artists surface, with recently played tracks removed.
     *
     * Runs synchronously against the cached pool, so the Related tab's third
     * section costs nothing extra — the pool is already built for Up Next.
     */
    fun getDiscoveryOrder(
        url: String,
        excludeUrls: Set<String> = emptySet(),
        limit: Int = DISCOVERY_ORDER_LIMIT
    ): List<MusicItem> {
        val pool = getPool(url) ?: return emptyList()
        val primary = ArrayList<MusicItem>(limit)
        val discovery = ArrayList<MusicItem>(limit)
        synchronized(pool) {
            for (scored in pool.ranked) {
                val item = scored.item
                if (excludeUrls.contains(item.url)) continue
                if (scored.tier == AffinityTier.DISCOVERY) {
                    // Related is the "show me something new" surface.
                    if (recentTrackKeys.contains(RelatedRanking.normalizedTrackKey(item))) continue
                    discovery += item
                } else {
                    primary += item
                }
            }
        }
        return RelatedRanking.interleave(
            primary = primary,
            discovery = discovery,
            primaryRun = RELATED_PRIMARY_RUN,
            discoveryRun = RELATED_DISCOVERY_RUN,
            limit = limit
        )
    }

    /**
     * "More from {artist}". Goes through the same acceptance gate as the rest of
     * the recommender — the previous version returned the raw search results, so
     * the section could show Shorts, live/cover variants and the current track.
     */
    suspend fun getArtistSongs(
        artist: String,
        excludeUrls: Set<String> = emptySet(),
        limit: Int = ARTIST_SECTION_LIMIT
    ): List<MusicItem> = withContext(Dispatchers.IO) {
        val cleanArtist = RelatedRanking.cleanArtist(artist).trim()
        if (cleanArtist.isBlank()) return@withContext emptyList()
        try {
            refreshUserTaste()
            val results = fetchRelatedSearchResults(
                RelatedRanking.buildArtistLadder(cleanArtist).take(2)
            )
            acceptPlain(
                items = results.map { it.item },
                excludeUrls = excludeUrls
            ).take(limit)
        } catch (e: Exception) {
            SecureLogger.w("RelatedPoolManager", "getArtistSongs failed for $cleanArtist", e)
            emptyList()
        }
    }

    /**
     * "You Might Also Like" — artist-anchored discovery.
     *
     * The previous version filtered on `!uploader.contains(artist)`, which
     * emptied the entire section whenever the uploader name was blank, and it
     * seeded itself with `"songs like <title>"` and
     * `"trending music similar to <artist>"` — neither of which is a query shape
     * YouTube understands, so the section filled with unrelated mainstream hits.
     */
    suspend fun getDiscoverSimilarSongs(
        title: String,
        artist: String,
        excludeUrls: Set<String> = emptySet(),
        excludeUploaders: Set<String> = emptySet(),
        limit: Int = SIMILAR_SECTION_LIMIT
    ): List<MusicItem> = withContext(Dispatchers.IO) {
        try {
            refreshUserTaste()
            val ladder = RelatedRanking.buildSimilarArtistsLadder(
                title = title,
                artist = RelatedRanking.cleanArtist(artist)
            )
            if (ladder.isEmpty()) return@withContext emptyList()

            val results = fetchRelatedSearchResults(ladder.take(3))
            val accepted = acceptPlain(items = results.map { it.item }, excludeUrls = excludeUrls)
            val blocked = excludeUploaders.mapTo(HashSet()) { RelatedRanking.sameArtistKey(it) }
            RelatedRanking.capPerArtist(accepted, MAX_PER_ARTIST_SECTION)
                .filter { RelatedRanking.sameArtistKey(it.uploader) !in blocked }
                .take(limit)
        } catch (e: Exception) {
            SecureLogger.w("RelatedPoolManager", "getDiscoverSimilarSongs failed", e)
            emptyList()
        }
    }

    /** Clean, deduplicated song list used by the home feed. */
    fun cleanSongList(items: List<MusicItem>): List<MusicItem> =
        items.filter {
            it.type == MusicItemType.SONG &&
                it.url.isNotBlank() &&
                it.title.isNotBlank() &&
                RelatedRanking.isAcceptableDuration(it.durationSecs) &&
                !RelatedRanking.isShortsUrl(it.url)
        }.distinctBy { it.url }

    fun capPerArtist(items: List<MusicItem>, maxPerArtist: Int): List<MusicItem> =
        RelatedRanking.capPerArtist(items, maxPerArtist)

    // ---------------------------------------------------------------------
    // Pool construction
    // ---------------------------------------------------------------------

    private suspend fun buildPool(url: String): RelatedPool {
        refreshUserTaste()

        val info = youtubeDataSource.getOrFetchStreamInfo(url, force = false)
        val metadata = trackMetadataResolver.extract(info)
        val anchorTitle = info.name?.trim().orEmpty().ifBlank { metadata.title.orEmpty() }
        val anchorUploader = info.uploaderName?.trim().orEmpty().ifBlank { metadata.artist.orEmpty() }
        val anchor = RecommendationAnchor(
            title = anchorTitle,
            uploader = anchorUploader,
            durationSecs = info.duration,
            language = metadata.language,
            movie = metadata.movie
        )

        val languageFragment = trackMetadataResolver.languageQueryFragment(metadata.language)
        val genres = trackMetadataResolver.extractGenres(
            tags = info.tags?.map { it.toString() }.orEmpty(),
            title = anchorTitle
        )
        val ladder = RelatedRanking.buildQueryLadder(
            artist = anchorUploader,
            language = metadata.language,
            movie = metadata.movie,
            isSoundtrack = metadata.isSoundtrack,
            genres = genres,
            languageFragment = languageFragment
        )
        val batch = ladder.take(RelatedRanking.INITIAL_QUERY_COUNT)

        // YouTube's own related items ride along on the StreamInfo we already hold.
        val relatedItems = mapRelatedItems(info.relatedItems).map { TaggedItem(it, null) }
        val searchResults = fetchRelatedSearchResults(batch)

        val pool = RelatedPool(anchorUrl = url, anchor = anchor, ladder = ladder)
        val scored = scoreCandidates(items = relatedItems + searchResults, anchor = anchor, anchorUrl = url)
        pool.ranked += scored
        pool.seenKeys += scored.mapTo(HashSet()) { RelatedRanking.normalizedTrackKey(it.item) }
        pool.ranked.sortByDescending { it.score }
        trimToLimit(pool)
        return pool
    }

    /**
     * Scores each new candidate exactly once, then merges into the already-sorted
     * pool in a single linear pass.
     */
    private fun mergeIntoPool(pool: RelatedPool, results: List<TaggedItem>): List<MusicItem> {
        val fresh = scoreCandidates(
            items = results,
            anchor = pool.anchor,
            anchorUrl = pool.anchorUrl,
            seenKeys = pool.seenKeys
        )
        if (fresh.isEmpty()) return emptyList()

        synchronized(pool) {
            val knownUrls = pool.ranked.mapTo(HashSet()) { it.item.url }
            val actuallyNew = fresh.filter { knownUrls.add(it.item.url) }
            if (actuallyNew.isEmpty()) return emptyList()

            val merged = RelatedRanking.mergeSortedDesc(
                existing = pool.ranked,
                incoming = actuallyNew,
                limit = RELATED_MAX_ITEMS,
                scoreOf = { it.score }
            )
            pool.ranked.clear()
            pool.ranked += merged
            pool.seenKeys += fresh.mapTo(HashSet()) { RelatedRanking.normalizedTrackKey(it.item) }
            return actuallyNew.sortedByDescending { it.score }.map { it.item }
        }
    }

    private fun trimToLimit(pool: RelatedPool) {
        if (pool.ranked.size > RELATED_MAX_ITEMS) {
            pool.ranked.subList(RELATED_MAX_ITEMS, pool.ranked.size).clear()
        }
    }

    private fun continuityOrder(pool: RelatedPool): List<MusicItem> {
        val primary = ArrayList<MusicItem>(pool.ranked.size)
        val discovery = ArrayList<MusicItem>(pool.ranked.size)
        synchronized(pool) {
            for (scored in pool.ranked) {
                if (scored.tier == AffinityTier.DISCOVERY) discovery += scored.item else primary += scored.item
            }
        }
        return RelatedRanking.interleave(
            primary = primary,
            discovery = discovery,
            primaryRun = UPNE_PRIMARY_RUN,
            discoveryRun = UPNE_DISCOVERY_RUN,
            limit = RELATED_MAX_ITEMS
        )
    }

    // ---------------------------------------------------------------------
    // Candidate acceptance + scoring
    // ---------------------------------------------------------------------

    /** A candidate plus the language implied by the query that surfaced it. */
    private data class TaggedItem(val item: MusicItem, val language: String?)

    /**
     * Scores candidates once each. When [seenKeys] is supplied, already-known
     * tracks are dropped (used by expansion so a track never appears twice).
     */
    private fun scoreCandidates(
        items: List<TaggedItem>,
        anchor: RecommendationAnchor,
        anchorUrl: String,
        seenKeys: MutableSet<String>? = null
    ): List<ScoredCandidate> {
        val out = ArrayList<ScoredCandidate>(items.size)
        val batchSeen = HashSet<String>()
        for (tagged in items) {
            val item = tagged.item
            if (item.type != MusicItemType.SONG) continue
            if (!RelatedRanking.isAcceptable(item, anchor.title, anchor.uploader, anchorUrl)) continue
            if (item.url == anchorUrl) continue

            val key = RelatedRanking.normalizedTrackKey(item)
            if (seenKeys != null) {
                if (seenKeys.contains(key)) continue
            } else if (!batchSeen.add(key)) {
                continue
            }

            out += RelatedRanking.toCandidate(
                item = item,
                anchor = anchor,
                itemLanguage = tagged.language,
                tasteCount = tasteCount(item.uploader)
            )
        }
        return out
    }

    /**
     * Acceptance gate for the standalone sections (artist / similar), which are not
     * scored — just filtered, deduped and capped.
     */
    private fun acceptPlain(items: List<MusicItem>, excludeUrls: Set<String>): List<MusicItem> {
        val out = ArrayList<MusicItem>(items.size)
        val seenKeys = HashSet<String>()
        val seenUrls = HashSet<String>()
        for (item in items) {
            if (item.type != MusicItemType.SONG) continue
            if (item.url.isBlank() || item.title.isBlank()) continue
            if (excludeUrls.contains(item.url)) continue
            if (!seenUrls.add(item.url)) continue
            if (RelatedRanking.isShortsUrl(item.url)) continue
            if (!RelatedRanking.isAcceptableDuration(item.durationSecs)) continue
            if (!seenKeys.add(RelatedRanking.normalizedTrackKey(item))) continue
            out += item
        }
        return out
    }

    private fun mapRelatedItems(items: List<org.schabi.newpipe.extractor.InfoItem>): List<MusicItem> =
        items.mapNotNull { infoItem ->
            if (infoItem !is org.schabi.newpipe.extractor.stream.StreamInfoItem) return@mapNotNull null
            MusicItem(
                title = infoItem.name ?: "",
                url = infoItem.url ?: "",
                uploader = infoItem.uploaderName ?: "",
                thumbnailUrl = infoItem.thumbnails?.maxByOrNull { it.width * it.height }?.url
                    ?: infoItem.thumbnails?.lastOrNull()?.url
                    ?: "",
                type = MusicItemType.SONG,
                durationSecs = infoItem.duration
            )
        }

    // ---------------------------------------------------------------------
    // Query execution
    // ---------------------------------------------------------------------

    /**
     * Runs the weighted queries in parallel, tagging each result with the language
     * its query implies — a candidate's language for free, since we cannot afford a
     * StreamInfo fetch per suggestion.
     */
    private suspend fun fetchRelatedSearchResults(queries: List<WeightedQuery>): List<TaggedItem> = coroutineScope {
        if (queries.isEmpty()) return@coroutineScope emptyList()
        queries.map { weighted ->
            async {
                try {
                    youtubeDataSource.searchMusic(weighted.query, maintainSession = false)
                        .filter { it.type == MusicItemType.SONG }
                        .map { TaggedItem(it, weighted.impliedLanguage) }
                } catch (e: Exception) {
                    SecureLogger.w("RelatedPoolManager", "search failed: ${weighted.query}", e)
                    emptyList()
                }
            }
        }.awaitAll().flatten()
    }
}
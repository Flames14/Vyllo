package com.vyllo.music.data.network

import android.content.Context
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.MusicItemType
import com.vyllo.music.network.OkHttpDownloader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import com.vyllo.music.data.network.potoken.PoTokenProviderImpl
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var currentSearchExtractor: org.schabi.newpipe.extractor.ListExtractor<*>? = null
    private var nextSearchPage: Page? = null

    private var currentListExtractor: org.schabi.newpipe.extractor.ListExtractor<*>? = null
    private var nextListPage: Page? = null

    /**
     * Feed lane: browsing work (home, search, trending, recommendation queries).
     * Deliberately small — these are bulk, latency-tolerant requests that should
     * never be able to starve playback.
     */
    private val feedDispatcher = Executors.newFixedThreadPool(FEED_LANE_THREADS) { r ->
        Thread(r).apply { name = "vyllo-feed"; priority = Thread.NORM_PRIORITY }
    }.asCoroutineDispatcher()

    /**
     * Playback lane: stream extraction only.
     *
     * This is the fix for slow taps. Previously *every* extractor call shared one
     * three-thread pool, so while Home was loading six-plus searches a tap sat in
     * the executor queue and the user waited for it. A tap now has its own lane and
     * never queues behind feed work.
     */
    private val playbackDispatcher = Executors.newFixedThreadPool(PLAYBACK_LANE_THREADS) { r ->
        Thread(r).apply { name = "vyllo-playback"; priority = Thread.NORM_PRIORITY }
    }.asCoroutineDispatcher()

    private data class CachedStreamInfo(
        val info: StreamInfo,
        val fetchedAt: Long = System.currentTimeMillis()
    )

    private val streamInfoCacheLock = Any()
    private val streamInfoCache = object : LinkedHashMap<String, CachedStreamInfo>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedStreamInfo>?): Boolean {
            return size > 50
        }
    }

    init {
        ensureNewPipeInitialized(context)
    }

    companion object {
        private const val CACHE_TTL_MS = 10 * 60 * 1000L

        /**
         * Feed lane size. Kept at the original three: these are the browsing
         * requests that used to saturate the pool and make taps slow.
         */
        private const val FEED_LANE_THREADS = 3

        /**
         * Playback lane size. Stream extraction is what a tap blocks on, and the
         * lookahead prefetches one extra track at a time, so two is enough to keep a
         * resolve running while a lookahead resolve is in flight.
         */
        private const val PLAYBACK_LANE_THREADS = 2

        /** Valid YouTube kiosk id in newpipeextractor 0.26.x. */
        private const val TRENDING_MUSIC_KIOSK_ID = "trending_music"

        /** Used when the charts kiosk is unavailable or region-restricted. */
        private const val TRENDING_FALLBACK_QUERY = "trending music hits"

        @Volatile
        private var newPipeReady = false

        /**
         * Idempotent, thread-safe NewPipe + PoToken bootstrap.
         *
         * Called from the Application before any extractor work so the first
         * search/play does not pay for static initialisation mid-gesture, and
         * again from this data source's constructor as a safety net (unit
         * tests and non-app entry points construct it directly).
         */
        fun ensureNewPipeInitialized(context: Context) {
            if (newPipeReady) return
            synchronized(YouTubeDataSource::class.java) {
                if (newPipeReady) return
                NewPipe.init(OkHttpDownloader(), Localization.DEFAULT, ContentCountry.DEFAULT)
                YoutubeStreamExtractor.setFetchIosClient(false)
                PoTokenProviderImpl.init(context.applicationContext)
                YoutubeStreamExtractor.setPoTokenProvider(PoTokenProviderImpl)
                newPipeReady = true
            }
        }

        /**
         * Boots NewPipe and then pre-warms the poToken generator off the main
         * thread, so the first user-initiated play skips the cold cost.
         */
        fun warmUpPoToken(context: Context) {
            ensureNewPipeInitialized(context)
            PoTokenProviderImpl.warmUpAsync()
        }
    }

    suspend fun searchMusic(query: String, maintainSession: Boolean = true): List<MusicItem> = withContext(feedDispatcher) {
        searchMusicRaw(query, maintainSession)
    }

    /**
     * Search body without a dispatcher hop.
     *
     * Split out so callers already running on [feedDispatcher] can reuse it. Calling
     * the public searchMusic from inside a feedDispatcher block would re-enter the
     * same bounded pool and can deadlock once all three threads are occupied.
     */
    private suspend fun searchMusicRaw(query: String, maintainSession: Boolean): List<MusicItem> {
        return try {
            val service = ServiceList.YouTube
            val searchExtractor = service.getSearchExtractor(query, emptyList(), "")
            searchExtractor.fetchPage()

            if (maintainSession) {
                currentSearchExtractor = searchExtractor
                nextSearchPage = searchExtractor.initialPage.nextPage
            }
            return mapItems(searchExtractor.initialPage.items)
        } catch (e: Exception) {
            SecureLogger.e("YouTubeDataSource", "Search failed for query: $query", e)
            return emptyList()
        }
    }

    suspend fun loadMoreResults(): List<MusicItem> = withContext(feedDispatcher) {
        try {
            val extractor = currentSearchExtractor ?: return@withContext emptyList()
            val page = nextSearchPage ?: return@withContext emptyList()
            if (page.url == null && page.ids == null) return@withContext emptyList()

            val nextPage = extractor.getPage(page)
            nextSearchPage = nextPage.nextPage
            return@withContext mapItems(nextPage.items)
        } catch (e: Exception) {
            SecureLogger.e("YouTubeDataSource", "Load more results failed", e)
            return@withContext emptyList()
        }
    }

    suspend fun fetchItemsWithPagination(query: String): List<MusicItem> = withContext(feedDispatcher) {
        try {
            val service = ServiceList.YouTube
            val extractor = service.getSearchExtractor(query, emptyList(), "")
            extractor.fetchPage()

            currentListExtractor = extractor
            nextListPage = extractor.initialPage.nextPage

            return@withContext mapItems(extractor.initialPage.items)
        } catch (e: Exception) {
            SecureLogger.e("YouTubeDataSource", "Fetch items with pagination failed", e)
            return@withContext emptyList()
        }
    }

    suspend fun loadMoreItems(): List<MusicItem> = withContext(feedDispatcher) {
        try {
            val extractor = currentListExtractor ?: return@withContext emptyList()
            val page = nextListPage ?: return@withContext emptyList()
            if (page.url == null && page.ids == null) return@withContext emptyList()

            val nextPage = extractor.getPage(page)
            nextListPage = nextPage.nextPage
            return@withContext mapItems(nextPage.items)
        } catch (e: Exception) {
            SecureLogger.e("YouTubeDataSource", "Load more items failed", e)
            return@withContext emptyList()
        }
    }

    /**
 * YouTube Music charts.
 *
 * This used to reach the kiosk extractor through reflection and swallow every
 * failure, so any breakage left the Home feed's trending section silently empty —
 * indistinguishable from "still loading" in the UI. Three changes:
 *
 * 1. Typed API instead of reflection (`kioskList.getExtractorById`), which is not
 *    signature-fragile across NewPipe versions.
 * 2. A failure here is logged, not swallowed.
 * 3. A search fallback, so the section always has *something* rather than being
 *    blank. YouTube Music charts are also region/format restricted and can return
 *    zero items on their own, which the fallback covers too.
 */
suspend fun getTrendingMusic(): List<MusicItem> = withContext(feedDispatcher) {
    val kiosk = try {
        ServiceList.YouTube.kioskList.getExtractorById(TRENDING_MUSIC_KIOSK_ID, null)
    } catch (e: Exception) {
        SecureLogger.w("YouTubeDataSource", "Trending kiosk unavailable, using search fallback", e)
        null
    }

    if (kiosk != null) {
        val fromKiosk = try {
            kiosk.fetchPage()
            mapItems(kiosk.initialPage.items)
        } catch (e: Exception) {
            SecureLogger.w("YouTubeDataSource", "Trending kiosk fetch failed, using search fallback", e)
            emptyList()
        }
        if (fromKiosk.isNotEmpty()) return@withContext fromKiosk
        SecureLogger.d("YouTubeDataSource") { "Trending kiosk returned no items, using search fallback" }
    }

    // Runs on this same thread — never re-enters the bounded feed pool.
    searchMusicRaw(TRENDING_FALLBACK_QUERY, maintainSession = false)
}

    suspend fun getOrFetchStreamInfo(url: String, force: Boolean): StreamInfo = withContext(playbackDispatcher) {
        if (!force) {
            val cached = synchronized(streamInfoCacheLock) {
                streamInfoCache[url]?.takeIf { (System.currentTimeMillis() - it.fetchedAt) < CACHE_TTL_MS }
            }
            if (cached != null) {
                return@withContext cached.info
            }
        }

        var attempt = 0
        var lastException: Exception? = null
        while (attempt < 3) {
            try {
                val extractor = ServiceList.YouTube.getStreamExtractor(url)
                runInterruptible { extractor.fetchPage() }
                val info = StreamInfo.getInfo(extractor)
                synchronized(streamInfoCacheLock) {
                    streamInfoCache[url] = CachedStreamInfo(info)
                }
                return@withContext info
            } catch (e: Exception) {
                lastException = e
                attempt++
                if (attempt < 3) {
                    // Exponential backoff with jitter to avoid thundering herd
                    val baseDelay = 200L * attempt
                    val jitter = (Math.random() * 100L).toLong()
                    delay(baseDelay + jitter)
                }
            }
        }
        throw lastException ?: Exception("Fetch failed after 3 attempts")
    }

    private fun mapItems(items: List<org.schabi.newpipe.extractor.InfoItem>): List<MusicItem> {
        return items.mapNotNull { item ->
            when (item) {
                is StreamInfoItem -> {
                    val bestThumbnail = item.thumbnails?.maxByOrNull { it.width * it.height }?.url
                        ?: item.thumbnails?.lastOrNull()?.url
                        ?: item.thumbnails?.firstOrNull()?.url
                        ?: ""
                    MusicItem(
                        title = item.name ?: "Unknown",
                        url = item.url ?: "",
                        uploader = item.uploaderName ?: "Unknown",
                        thumbnailUrl = bestThumbnail,
                        type = MusicItemType.SONG,
                        durationSecs = item.duration
                    )
                }
                is PlaylistInfoItem -> {
                    val bestThumbnail = item.thumbnails?.maxByOrNull { it.width * it.height }?.url
                        ?: item.thumbnails?.lastOrNull()?.url
                        ?: item.thumbnails?.firstOrNull()?.url
                        ?: ""
                    MusicItem(
                        title = item.name ?: "Unknown",
                        url = item.url ?: "",
                        uploader = item.uploaderName ?: "YouTube Music",
                        thumbnailUrl = bestThumbnail,
                        type = MusicItemType.PLAYLIST
                    )
                }
                else -> null
            }
        }
    }
}

package com.vyllo.music.data.network

import android.content.Context
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
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var isInitialized = false
    
    // Search session state - needed for pagination
    private var currentSearchExtractor: org.schabi.newpipe.extractor.ListExtractor<*>? = null
    private var nextSearchPage: Page? = null
    
    private var currentListExtractor: org.schabi.newpipe.extractor.ListExtractor<*>? = null
    private var nextListPage: Page? = null

    // Cache the last fetched stream info to avoid redundant network hits
    private var currentStreamUrl: String? = null
    private var currentStreamInfo: StreamInfo? = null

    // Single thread dispatcher for NewPipe extractors because they aren't always thread-safe
    private val extractorDispatcher = Executors.newFixedThreadPool(1) { r ->
        Thread(r).apply { priority = Thread.NORM_PRIORITY }
    }.asCoroutineDispatcher()

    init {
        initNewPipe()
    }

    private fun initNewPipe() {
        if (!isInitialized) {
            synchronized(this) {
                if (!isInitialized) {
                    // TODO: Move these to a config file or preferences
                    NewPipe.init(OkHttpDownloader(), Localization.DEFAULT, ContentCountry.DEFAULT)
                    isInitialized = true
                }
            }
        }
    }

    suspend fun searchMusic(query: String, maintainSession: Boolean = true): List<MusicItem> = withContext(extractorDispatcher) {
        try {
            val service = ServiceList.YouTube
            val searchExtractor = service.getSearchExtractor(query, listOf("videos"), "")
            searchExtractor.fetchPage()
            
            if (maintainSession) {
                currentSearchExtractor = searchExtractor
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
            val extractor = currentSearchExtractor ?: return@withContext emptyList()
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

    suspend fun fetchItemsWithPagination(query: String): List<MusicItem> = withContext(extractorDispatcher) {
        try {
            val service = ServiceList.YouTube
            val extractor = service.getSearchExtractor(query, listOf("videos"), "")
            extractor.fetchPage()
            
            currentListExtractor = extractor
            nextListPage = extractor.initialPage.nextPage
            
            return@withContext mapItems(extractor.initialPage.items)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
    }

    suspend fun loadMoreItems(): List<MusicItem> = withContext(extractorDispatcher) {
        try {
            val extractor = currentListExtractor ?: return@withContext emptyList()
            val page = nextListPage ?: return@withContext emptyList()
            if (page.url == null && page.ids == null) return@withContext emptyList()

            val nextPage = extractor.getPage(page)
            nextListPage = nextPage.nextPage
            return@withContext mapItems(nextPage.items)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
    }

    suspend fun getTrendingMusic(): List<MusicItem> = withContext(extractorDispatcher) {
        try {
            val youtube = ServiceList.YouTube
            val kioskList = youtube.kioskList
            
            // Using reflection here because the 'trending' ID isn't directly exposed in all NewPipe versions
            val extractor = findKioskExtractor(kioskList, "trending_music")

            if (extractor != null) {
                extractor.fetchPage()
                return@withContext mapItems(extractor.initialPage.items)
            }
            return@withContext emptyList()
        } catch (e: Exception) {
            android.util.Log.e("YouTubeDataSource", "Trending fetch failed", e)
            return@withContext emptyList()
        }
    }

    /**
     * Helper to find a kiosk extractor by ID using reflection. 
     * This is a bit of a hack but it works across different library versions.
     */
    private fun findKioskExtractor(
        kioskList: org.schabi.newpipe.extractor.kiosk.KioskList, 
        id: String
    ): org.schabi.newpipe.extractor.ListExtractor<org.schabi.newpipe.extractor.InfoItem>? {
        return try {
            val method = kioskList.javaClass.getMethod("getExtractorById", String::class.java, Page::class.java)
            @Suppress("UNCHECKED_CAST")
            method.invoke(kioskList, id, null) as? org.schabi.newpipe.extractor.ListExtractor<org.schabi.newpipe.extractor.InfoItem>
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getOrFetchStreamInfo(url: String, force: Boolean): StreamInfo = withContext(extractorDispatcher) {
        if (!force && url == currentStreamUrl && currentStreamInfo != null) {
            return@withContext currentStreamInfo!!
        }

        // Retry logic for flaky connections
        var attempt = 0
        var lastException: Exception? = null
        while (attempt < 3) {
            try {
                val extractor = ServiceList.YouTube.getStreamExtractor(url)
                runInterruptible { extractor.fetchPage() }
                val info = StreamInfo.getInfo(extractor)
                synchronized(this) {
                    currentStreamUrl = url
                    currentStreamInfo = info
                }
                return@withContext info
            } catch (e: Exception) {
                lastException = e
                attempt++
                if (attempt < 3) delay(400L * attempt)
            }
        }
        throw lastException ?: Exception("Fetch failed after 3 attempts")
    }

    private fun mapItems(items: List<org.schabi.newpipe.extractor.InfoItem>): List<MusicItem> {
        return items.mapNotNull { item ->
            when (item) {
                is StreamInfoItem -> MusicItem(
                    title = item.name ?: "Unknown",
                    url = item.url ?: "",
                    uploader = item.uploaderName ?: "Unknown",
                    thumbnailUrl = item.thumbnails?.firstOrNull()?.url ?: "",
                    type = MusicItemType.SONG,
                    durationSecs = item.duration
                )
                is PlaylistInfoItem -> MusicItem(
                    title = item.name ?: "Unknown",
                    url = item.url ?: "",
                    uploader = item.uploaderName ?: "YouTube Music",
                    thumbnailUrl = item.thumbnails?.firstOrNull()?.url ?: "",
                    type = MusicItemType.PLAYLIST
                )
                else -> null
            }
        }
    }
}

package com.vyllo.music.domain.manager.player

import com.vyllo.music.PlayerUiState
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.repository.IMusicRepository
import com.vyllo.music.domain.manager.PlaybackQueueManager
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.data.repository.delegates.RelatedRanking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Owns related-songs / load-more / force-refresh / autoplay logic.
 *
 * Scheduling matters here: every method ultimately needs the extractor pool, and
 * the previous version fired three jobs at once (up to nine concurrent searches)
 * *plus* a fresh pool rebuild on every track change. That starved the stream
 * resolve a tap needs, which is the same latency the play path was fighting.
 *
 * Now: the pool first (Up Next and the Related tab's discovery section both
 * depend on it), then the two standalone sections together.
 */
class RelatedController(
    private val repository: IMusicRepository,
    private val playbackQueueManager: PlaybackQueueManager,
    private val getState: () -> PlayerUiState,
    private val updateState: (((PlayerUiState) -> PlayerUiState)) -> Unit,
    private val scopeProvider: () -> CoroutineScope
) {
    /** Anchor track the current related-songs pool was built for. */
    private var relatedAnchorUrl: String? = null

    /** Incremented per load so a superseded load cannot overwrite a newer one. */
    private var loadSeq: Long = 0L

    fun loadRelatedSongs(item: MusicItem, force: Boolean = false) {
        val anchorChanged = relatedAnchorUrl != item.url

        // A cached pool for the same anchor is still valid — rebuilding it throws
        // away a good ranking for no reason. Only a genuine anchor change or an
        // explicit refresh forces a re-fetch.
        if (!force && !anchorChanged && getState().relatedSongs.isNotEmpty()) return

        relatedAnchorUrl = item.url
        val seq = ++loadSeq

        scopeProvider().launch {
            updateState {
                it.copy(
                    isLoadingPool = true,
                    isLoadingArtistSongs = true,
                    isLoadingSimilarSongs = true,
                    isLoadingRelatedTab = true
                )
            }

            // ---- Stage 1: the ranked pool -------------------------------------
            val pool: List<MusicItem> = try {
                repository.getRelatedSongs(item.url, force = force || anchorChanged)
            } catch (e: Exception) {
                SecureLogger.w(TAG, "related pool load failed", e)
                emptyList()
            }
            if (seq != loadSeq) return@launch

            val filtered = distinctTracks(pool.filter { it.url != item.url })
            playbackQueueManager.replaceUpcomingItems(filtered)
            val upcoming = playbackQueueManager.getUpcomingSnapshot()

            val discovery: List<MusicItem> = try {
                distinctTracks(repository.getRelatedDiscoveryOrder(
                    url = item.url,
                    excludeUrls = setOf(item.url) + filtered.map { it.url },
                    limit = DISCOVERY_SECTION_LIMIT
                ).filter { it.url != item.url })
            } catch (e: Exception) {
                SecureLogger.w(TAG, "discovery order failed", e)
                emptyList()
            }

            updateState {
                it.copy(
                    relatedSongs = upcoming,
                    discoverySongs = discovery,
                    isLoadingPool = false
                )
            }

            // ---- Stage 2: the two standalone sections, now that we know ----
            // which URLs and uploaders are already on screen.
            if (seq != loadSeq) return@launch

            val poolUrls = filtered.mapTo(HashSet()) { it.url }
            val poolUploaders = filtered.mapTo(HashSet()) { it.normalizedArtistKey }
            val poolTrackKeys = filtered.mapTo(HashSet()) { RelatedRanking.normalizedTrackKey(it) }

            // Fetch independent sections concurrently, then deduplicate against
            // the actual results. The old code read artist rows from UI state
            // before the parallel request finished, so exclusions were stale.
            val (rawArtistTracks, rawSimilarTracks) = coroutineScope {
                val artistRequest = async {
                    try {
                        repository.getArtistSongs(
                            artist = item.uploader,
                            excludeUrls = poolUrls + setOf(item.url)
                        )
                    } catch (e: Exception) {
                        SecureLogger.w(TAG, "artist songs load failed", e)
                        emptyList()
                    }
                }
                val similarRequest = async {
                    try {
                        repository.getDiscoverSimilarSongs(
                            title = item.title,
                            artist = item.uploader,
                            excludeUrls = poolUrls + setOf(item.url),
                            excludeUploaders = poolUploaders
                        )
                    } catch (e: Exception) {
                        SecureLogger.w(TAG, "similar songs load failed", e)
                        emptyList()
                    }
                }
                artistRequest.await() to similarRequest.await()
            }
            val artistTracks = distinctTracks(rawArtistTracks.filter { it.url != item.url }).filter {
                RelatedRanking.normalizedTrackKey(it) !in poolTrackKeys
            }
            if (seq != loadSeq) return@launch
            updateState { it.copy(artistSongs = artistTracks, isLoadingArtistSongs = false) }

            val excludedTracks = poolTrackKeys + artistTracks.map { RelatedRanking.normalizedTrackKey(it) }
            val discoveries = distinctTracks(rawSimilarTracks.filter { it.url != item.url }).filter {
                RelatedRanking.normalizedTrackKey(it) !in excludedTracks
            }
            if (seq == loadSeq) {
                updateState {
                    it.copy(discoverSimilarSongs = discoveries, isLoadingSimilarSongs = false)
                }
            }

            if (seq == loadSeq) {
                updateState { it.copy(isLoadingRelatedTab = false) }
            }
        }
    }

    /**
     * Fetches the next batch of suggestions for the current track so Up Next keeps
     * growing as the user scrolls.
     */
    fun loadMoreRelatedSongs() {
        val anchor = relatedAnchorUrl ?: return
        if (getState().isLoadingMoreRelated) return
        scopeProvider().launch {
            updateState { it.copy(isLoadingMoreRelated = true) }
            try {
                val more = distinctTracks(repository.getMoreRelatedSongs(anchor))
                val existingKeys = (playbackQueueManager.getUpcomingSnapshot() + listOfNotNull(getState().currentPlayingItem))
                    .mapTo(HashSet()) { RelatedRanking.normalizedTrackKey(it) }
                playbackQueueManager.appendDistinct(more.filter {
                    RelatedRanking.normalizedTrackKey(it) !in existingKeys
                })
                val upcoming = playbackQueueManager.getUpcomingSnapshot()
                updateState { state ->
                    val currentUrl = state.currentPlayingItem?.url
                    val merged = distinctTracks(state.relatedSongs + upcoming)
                        .filter { it.url != currentUrl }
                    state.copy(relatedSongs = merged)
                }
            } catch (e: Exception) {
                SecureLogger.w(TAG, "load more related songs failed", e)
            } finally {
                updateState { it.copy(isLoadingMoreRelated = false) }
            }
        }
    }

    fun forceRefreshRelatedSongs(currentItem: MusicItem?) {
        val item = currentItem ?: return
        loadRelatedSongs(item, force = true)
    }

    fun getNextAutoplayItem(autoplayEnabled: Boolean): MusicItem? {
        if (!autoplayEnabled) return null
        return playbackQueueManager.nextItem
    }

    private fun distinctTracks(items: List<MusicItem>): List<MusicItem> {
        val seen = HashSet<String>()
        return items.filter { item ->
            item.url.isNotBlank() && seen.add(RelatedRanking.normalizedTrackKey(item))
        }
    }


    private companion object {
        const val TAG = "RelatedController"
        const val DISCOVERY_SECTION_LIMIT = 25
    }
}

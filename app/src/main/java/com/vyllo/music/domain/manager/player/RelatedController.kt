package com.vyllo.music.domain.manager.player

import com.vyllo.music.PlayerUiState
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.repository.IMusicRepository
import com.vyllo.music.domain.manager.PlaybackQueueManager
import com.vyllo.music.domain.model.MusicItem
import kotlinx.coroutines.CoroutineScope
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

            val filtered = pool.filter { it.url != item.url }
            playbackQueueManager.replaceUpcomingItems(filtered)
            val upcoming = playbackQueueManager.getUpcomingSnapshot()

            val discovery: List<MusicItem> = try {
                repository.getRelatedDiscoveryOrder(
                    url = item.url,
                    excludeUrls = setOf(item.url),
                    limit = DISCOVERY_SECTION_LIMIT
                ).filter { it.url != item.url }
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

            // coroutineScope so these two are children of *this* load and are
            // joined before isLoadingRelatedTab clears — otherwise the Up Next
            // spinner stops while the Related tab is still empty.
            coroutineScope {
                launch {
                    val artistTracks = try {
                        repository.getArtistSongs(
                            artist = item.uploader,
                            excludeUrls = poolUrls
                        ).filter { it.url != item.url }
                    } catch (e: Exception) {
                        SecureLogger.w(TAG, "artist songs load failed", e)
                        emptyList()
                    }
                    if (seq == loadSeq) {
                        updateState { it.copy(artistSongs = artistTracks, isLoadingArtistSongs = false) }
                    }
                }

                launch {
                    val alreadyShown = getState().artistSongs.mapTo(HashSet()) { it.url } + poolUrls
                    val discoveries = try {
                        repository.getDiscoverSimilarSongs(
                            title = item.title,
                            artist = item.uploader,
                            excludeUrls = alreadyShown,
                            excludeUploaders = poolUploaders
                        ).filter { it.url != item.url }
                    } catch (e: Exception) {
                        SecureLogger.w(TAG, "similar songs load failed", e)
                        emptyList()
                    }
                    if (seq == loadSeq) {
                        updateState {
                            it.copy(
                                discoverSimilarSongs = discoveries,
                                isLoadingSimilarSongs = false
                            )
                        }
                    }
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
            val more = repository.getMoreRelatedSongs(anchor)
            playbackQueueManager.appendDistinct(more)
            val upcoming = playbackQueueManager.getUpcomingSnapshot()
            updateState { state ->
                val currentUrl = state.currentPlayingItem?.url
                val merged = (state.relatedSongs + upcoming)
                    .distinctBy { it.url }
                    .filter { it.url != currentUrl }
                state.copy(relatedSongs = merged, isLoadingMoreRelated = false)
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

    private companion object {
        const val TAG = "RelatedController"
        const val DISCOVERY_SECTION_LIMIT = 25
    }
}
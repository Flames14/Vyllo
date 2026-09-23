package com.vyllo.music.domain.manager.player

import com.vyllo.music.PlayerUiState
import com.vyllo.music.domain.repository.IMusicRepository
import com.vyllo.music.domain.manager.PlaybackQueueManager
import com.vyllo.music.domain.model.MusicItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Owns related-songs / load-more / force-refresh / autoplay logic
 * extracted from PlayerViewModel. No behavior change — verbatim logic moved here.
 */
class RelatedController(
    private val repository: IMusicRepository,
    private val playbackQueueManager: PlaybackQueueManager,
    private val getState: () -> PlayerUiState,
    private val updateState: (((PlayerUiState) -> PlayerUiState)) -> Unit,
    private val scopeProvider: () -> CoroutineScope
) {
    /** Anchor track that the current related-songs pool was built for. */
    private var relatedAnchorUrl: String? = null

    fun loadRelatedSongs(item: MusicItem, force: Boolean = false) {
        relatedAnchorUrl = item.url
        scopeProvider().launch {
            updateState { it.copy(isLoadingRelatedTab = true) }
            val relatedJob = launch {
                val related = repository.getRelatedSongs(item.url, force = force)
                val filtered = related.filter { it.url != item.url }
                playbackQueueManager.replaceUpcomingItems(filtered)
                val upcoming = playbackQueueManager.getUpcomingSnapshot()
                updateState { it.copy(relatedSongs = upcoming, isLoadingMoreRelated = false) }
            }
            val artistJob = launch {
                val artistTracks = repository.getArtistSongs(item.uploader)
                updateState { it.copy(artistSongs = artistTracks) }
            }
            val similarJob = launch {
                val discoveries = repository.getDiscoverSimilarSongs(item.title, item.uploader)
                updateState { it.copy(discoverSimilarSongs = discoveries) }
            }
            relatedJob.join()
            artistJob.join()
            similarJob.join()
            updateState { it.copy(isLoadingRelatedTab = false) }
        }
    }

    /**
     * Fetches the next batch of suggestions for the current track so the
     * Up Next list keeps growing as the user scrolls.
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
                state.copy(relatedSongs = upcoming, isLoadingMoreRelated = false)
            }
        }
    }

    fun forceRefreshRelatedSongs(currentItem: MusicItem?) {
        val item = currentItem ?: return
        relatedAnchorUrl = null
        loadRelatedSongs(item, force = true)
    }

    fun getNextAutoplayItem(autoplayEnabled: Boolean): MusicItem? {
        if (!autoplayEnabled) return null
        return playbackQueueManager.nextItem
    }
}

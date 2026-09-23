package com.vyllo.music.service.queue

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.repository.IMusicRepository
import com.vyllo.music.domain.manager.PlaybackQueueManager
import com.vyllo.music.data.manager.WakeLockManager
import com.vyllo.music.domain.manager.StreamUrlCache
import com.vyllo.music.service.WakeLockHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Queue lookahead / transitions / error-recovery operations.
 * Extracted from PlaybackQueueOrchestrator — no behavior change.
 */
class QueueOperations(
    private val repository: IMusicRepository,
    private val playbackQueueManager: PlaybackQueueManager,
    private val wakeLockManager: WakeLockManager,
    private val wakeLockHelper: WakeLockHelper,
    private val streamUrlCache: StreamUrlCache,
    private val state: QueueState
) {
    /**
     * Warms the stream-URL caches for the upcoming track and, when possible,
     * enqueues it behind the current item so the handoff is seamless.
     * Safe to call from anywhere; no-ops when there is nothing to do.
     */
    fun prefetchUpcomingStreams(scope: CoroutineScope, player: ExoPlayer?) {
        scope.launch {
            ensureNextEnqueued(player)
        }
    }

    /**
     * Called from the service's onMediaItemTransition after the queue index has
     * been synced: trims already-played items and tops up the lookahead.
     */
    fun onPlayerAdvancedToNewItem(scope: CoroutineScope, player: ExoPlayer?) {
        val p = player ?: return
        scope.launch {
            try {
                // Re-read everything inside the guard: the playlist may have been
                // replaced (user tapped another track) between event and execution.
                val count = p.mediaItemCount
                val currentIndex = p.currentMediaItemIndex
                if (currentIndex > 0 && currentIndex < count && count > 1) {
                    p.removeMediaItems(0, currentIndex)
                }
            } catch (e: Exception) {
                SecureLogger.w("MusicService", "Failed to trim played items: ${e.message}")
            }
            ensureNextEnqueued(p)
        }
    }

    suspend fun ensureNextEnqueued(player: ExoPlayer?) {
        val p = player ?: return
        try {
            // REPEAT_MODE_ONE intentionally replays the current item — no lookahead.
            // REPEAT_MODE_ALL still needs the next queue item enqueued so ExoPlayer
            // can advance (and wrap) instead of looping a single media item forever.
            if (p.repeatMode == Player.REPEAT_MODE_ONE) return
        } catch (e: Exception) {
            SecureLogger.w("MusicService", "Player gone while ensuring lookahead: ${e.message}")
            return
        }

        // PlaybackQueueManager is the source of truth for queue order. The UI
        // ("Up Next") renders getUpcomingSnapshot() from the same manager, so
        // ExoPlayer's lookahead must match manager.nextItem exactly — otherwise
        // seekToNextMediaItem plays a stale entry the user never saw.
        val expectedNext = playbackQueueManager.nextItem
        pruneLookaheadToMatchQueue(p, expectedNext?.url) ?: return

        if (expectedNext == null) {
            // Queue exhausted — warm autoplay discovery in the background so a
            // cold network fetch is not needed the moment the track ends.
            state.warmAutoplayDiscovery()
            return
        }

        wakeLockManager.withLocks {
            // Re-check after acquiring: a concurrent transition may have changed things.
            if (state.isMediaIdEnqueued(p, expectedNext.url)) return@withLocks
            if (playbackQueueManager.nextItem?.url != expectedNext.url) return@withLocks
            val resolved = state.resolveStreamUrl(expectedNext.url)
            if (resolved != null &&
                playbackQueueManager.nextItem?.url == expectedNext.url &&
                p.currentMediaItem?.mediaId != expectedNext.url
            ) {
                try {
                    with(state) { p.addMediaItem(expectedNext.toMediaItem(resolved)) }
                    SecureLogger.d("MusicService") { "Lookahead enqueued: ${expectedNext.title}" }
                } catch (e: Exception) {
                    SecureLogger.w("MusicService", "Failed to enqueue lookahead: ${e.message}")
                }
            }
        }
    }

    /**
     * Drops every playlist entry after the current one unless it is exactly the
     * manager's next item. Returns null when the player is unusable so callers
     * can bail out.
     */
    private fun pruneLookaheadToMatchQueue(player: ExoPlayer, expectedNextUrl: String?): Boolean {
        return try {
            val currentIdx = player.currentMediaItemIndex
            if (currentIdx < 0) return true
            val firstLookahead = currentIdx + 1
            if (firstLookahead >= player.mediaItemCount) return true

            val lookaheadIds = (firstLookahead until player.mediaItemCount)
                .map { player.getMediaItemAt(it).mediaId }
            val matchesQueue = expectedNextUrl != null &&
                lookaheadIds.size == 1 &&
                lookaheadIds[0] == expectedNextUrl
            if (matchesQueue) {
                true
            } else {
                player.removeMediaItems(firstLookahead, player.mediaItemCount)
                SecureLogger.d("MusicService") {
                    "Pruned stale lookahead (expected=$expectedNextUrl, was=$lookaheadIds)"
                }
                true
            }
        } catch (e: Exception) {
            SecureLogger.w("MusicService", "Player gone while pruning lookahead: ${e.message}")
            false
        }
    }

    /**
     * Advances to the next track in the queue.
     * Fast path: the lookahead item is already buffered — seek to it with no
     * network at all, which works even in deep Doze. Slow path: resolve and
     * play manually, fully under wake locks. At queue end, discovers and
     * appends fresh autoplay recommendations.
     */
    fun playNextTrack(scope: CoroutineScope, player: ExoPlayer?) {
        val mySeq = state.transitionSeq.incrementAndGet()
        scope.launch {
            val p = player
            if (p != null &&
                p.repeatMode == Player.REPEAT_MODE_OFF &&
                p.hasNextMediaItem() &&
                lookaheadMatchesQueue(p)
            ) {
                try {
                    p.seekToNextMediaItem()
                    p.playWhenReady = true
                    p.play()
                    SecureLogger.d("MusicService", "Advanced to buffered lookahead item")
                } catch (e: Exception) {
                    SecureLogger.w("MusicService", "Buffered advance failed, falling back: ${e.message}")
                    playTrackAtIndexInternal(p, playbackQueueManager.currentIndex + 1, mySeq)
                }
                return@launch
            }
            advanceQueueManually(p, mySeq)
        }
    }

    /**
     * True only when ExoPlayer's next entry is the same track the Up Next UI
     * shows (PlaybackQueueManager.nextItem). A stale lookahead must never be
     * seeked into — fall back to the manager-index path instead.
     */
    private fun lookaheadMatchesQueue(player: ExoPlayer): Boolean {
        return try {
            val expected = playbackQueueManager.nextItem?.url ?: return false
            val nextIdx = player.currentMediaItemIndex + 1
            nextIdx < player.mediaItemCount && player.getMediaItemAt(nextIdx).mediaId == expected
        } catch (e: Exception) {
            SecureLogger.w("MusicService", "Lookahead check failed: ${e.message}")
            false
        }
    }

    suspend fun advanceQueueManually(player: ExoPlayer?, mySeq: Long) {
        val queue = playbackQueueManager.getQueueSnapshot()
        val nextIndex = playbackQueueManager.currentIndex + 1
        if (nextIndex in queue.indices) {
            playTrackAtIndexInternal(player, nextIndex, mySeq)
            return
        }

        // Repeat-all: wrap to the start of the queue instead of treating end as exhaustion.
        if (queue.isNotEmpty() && player?.repeatMode == Player.REPEAT_MODE_ALL) {
            playTrackAtIndexInternal(player, 0, mySeq)
            return
        }

        val currentUrl = queue.getOrNull(playbackQueueManager.currentIndex)?.url ?: return
        wakeLockManager.withLocks {
            try {
                var related = repository.getMoreRelatedSongs(currentUrl)
                if (related.isEmpty()) {
                    related = repository.getRelatedSongs(currentUrl)
                }
                val added = playbackQueueManager.appendDistinct(related)
                state.lastAutoplayWarmUpMs = System.currentTimeMillis()
                if (added.isNotEmpty()) {
                    if (mySeq != state.transitionSeq.get()) return@withLocks
                    playTrackAtIndexInternal(player, playbackQueueManager.currentIndex + 1, mySeq)
                }
            } catch (e: Exception) {
                SecureLogger.e("MusicService", "Autoplay discovery failed", e)
            }
        }
    }

    /**
     * Plays the previous track in the active queue if available.
     */
    fun playPreviousTrack(scope: CoroutineScope, player: ExoPlayer?) {
        val mySeq = state.transitionSeq.incrementAndGet()
        scope.launch {
            val prevIndex = playbackQueueManager.currentIndex - 1
            val queue = playbackQueueManager.getQueueSnapshot()
            if (prevIndex in queue.indices) {
                playTrackAtIndexInternal(player, prevIndex, mySeq)
            } else {
                player?.seekToPreviousMediaItem()
            }
        }
    }

    /**
     * Loads and starts playing the track at the specified index in the queue.
     * Updates PlaybackQueueManager immediately so UI states (title, artist,
     * artwork) transition in exact lockstep with audio playback.
     *
     * The whole gap — resolve, set, prepare, wait-for-start — runs under CPU +
     * WiFi locks so a locked screen cannot stall it.
     */
    fun playTrackAtIndex(scope: CoroutineScope, player: ExoPlayer?, index: Int) {
        val mySeq = state.transitionSeq.incrementAndGet()
        scope.launch {
            playTrackAtIndexInternal(player, index, mySeq)
        }
    }

    suspend fun playTrackAtIndexInternal(player: ExoPlayer?, index: Int, mySeq: Long) {
        val queue = playbackQueueManager.getQueueSnapshot()
        if (index !in queue.indices) {
            SecureLogger.w("MusicService", "Index $index out of bounds (size: ${queue.size})")
            return
        }

        val track = queue[index]
        playbackQueueManager.setCurrentIndexSafe(index)

        wakeLockManager.withLocks {
            try {
                if (mySeq != state.transitionSeq.get()) {
                    SecureLogger.d("MusicService", "Stale transition aborted for ${track.title}")
                    return@withLocks
                }
                player?.pause()
                // Hold the locks across the resolve so screen-off/Doze cannot
                // stall the transition mid-network-request.
                val streamUrl = state.resolveStreamUrl(track.url)
                if (mySeq != state.transitionSeq.get()) {
                    SecureLogger.d("MusicService", "Stale transition aborted after resolve")
                    return@withLocks
                }
                if (streamUrl != null) {
                    val p = player
                    if (p == null) return@withLocks
                    with(state) {
                        val mediaItem = track.toMediaItem(streamUrl)
                        p.setMediaItem(mediaItem)
                    }
                    p.prepare()
                    p.playWhenReady = true
                    // Hold the locks until playback verifiably started. Releasing
                    // earlier lets Doze suspend the service while still buffering.
                    val started = wakeLockHelper.keepWakeLockUntilPlaying(p)
                    if (!started) {
                        SecureLogger.w("MusicService", "Playback did not confirm start for ${track.title}")
                    }
                    if (mySeq == state.transitionSeq.get()) {
                        ensureNextEnqueued(p)
                    }
                } else {
                    SecureLogger.w("MusicService", "Stream URL null for ${track.title}, falling back to next track")
                    val nextIdx = index + 1
                    if (nextIdx < playbackQueueManager.getQueueSnapshot().size &&
                        mySeq == state.transitionSeq.get()
                    ) {
                        playTrackAtIndexInternal(player, nextIdx, mySeq)
                    }
                }
            } catch (e: Exception) {
                SecureLogger.e("MusicService", "playTrackAtIndex failed for ${track.title}", e)
            }
        }
    }

    /**
     * Recovers from a playback error with a freshly resolved URL.
     * Must be called instead of blindly re-preparing: cached googlevideo URLs
     * expire, and re-preparing the same dead URL loops forever.
     */
    fun retryCurrentWithFreshUrl(scope: CoroutineScope, player: ExoPlayer?, delayMs: Long) {
        val mySeq = state.transitionSeq.incrementAndGet()
        scope.launch {
            wakeLockManager.withLocks {
                try {
                    delay(delayMs)
                    val p = player ?: return@withLocks
                    if (mySeq != state.transitionSeq.get()) return@withLocks

                    if (p.isPlaying || (p.playWhenReady && p.playbackState == Player.STATE_READY)) {
                        // Current item is healthy; the failure came from a
                        // pre-buffered lookahead item. Drop lookahead items past
                        // the current one, evict their cached URLs, and re-enqueue.
                        refreshLookaheadAfterError(p)
                        return@withLocks
                    }

                    val queueIndex = playbackQueueManager.currentIndex
                    val track = playbackQueueManager.getQueueSnapshot().getOrNull(queueIndex)
                        ?: return@withLocks
                    streamUrlCache.remove(track.url, false)
                    val freshUrl = state.resolveStreamUrl(track.url, force = true)
                    if (mySeq != state.transitionSeq.get()) return@withLocks
                    if (freshUrl != null) {
                        with(state) {
                            p.setMediaItem(track.toMediaItem(freshUrl))
                        }
                        p.prepare()
                        p.playWhenReady = true
                        wakeLockHelper.keepWakeLockUntilPlaying(p)
                        if (mySeq == state.transitionSeq.get()) {
                            ensureNextEnqueued(p)
                        }
                    } else {
                        SecureLogger.w("MusicService", "Fresh resolve failed after error; trying next track")
                        advanceQueueManually(p, mySeq)
                    }
                } catch (e: Exception) {
                    SecureLogger.e("MusicService", "Error recovery failed", e)
                }
            }
        }
    }

    private suspend fun refreshLookaheadAfterError(player: ExoPlayer) {
        try {
            val currentIndex = player.currentMediaItemIndex
            for (i in player.mediaItemCount - 1 downTo currentIndex + 1) {
                val staleId = player.getMediaItemAt(i).mediaId
                if (staleId.isNotBlank()) {
                    streamUrlCache.remove(staleId, false)
                }
                player.removeMediaItem(i)
            }
        } catch (e: Exception) {
            SecureLogger.w("MusicService", "Failed to drop stale lookahead: ${e.message}")
        }
        ensureNextEnqueued(player)
    }
}

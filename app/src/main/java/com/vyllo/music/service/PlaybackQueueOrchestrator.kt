package com.vyllo.music.service

import androidx.media3.exoplayer.ExoPlayer
import com.vyllo.music.data.IMusicRepository
import com.vyllo.music.data.manager.PlaybackQueueManager
import com.vyllo.music.data.manager.WakeLockManager
import com.vyllo.music.domain.manager.StreamUrlCache
import com.vyllo.music.service.queue.QueueOperations
import com.vyllo.music.service.queue.QueueState
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns everything the queue needs to keep playing with the screen off.
 *
 * The previous design resolved the next track's stream URL only AFTER the
 * current track ended (STATE_ENDED): a multi-second network job running in a
 * gap where neither ExoPlayer's internal wake lock nor the foreground service
 * priority is active. In Doze that gap work is suspended and only resumes when
 * the user turns the screen on — the reported "up next plays only on unlock".
 *
 * This implementation instead keeps a one-track lookahead inside ExoPlayer's
 * own playlist ([current, next]):
 * - the next URL is resolved and enqueued WHILE the current track is still
 *   playing (wake locks + foreground priority + network all active), so
 *   ExoPlayer pre-buffers it and the handoff needs zero network;
 * - every gap path (auto-advance, media-button next, error retry) runs under
 *   [WakeLockManager] locks with a generous timeout instead of the old 3s hop.
 *
 * Threading: all [ExoPlayer] calls happen on the caller's [CoroutineScope]
 * (the service's Main scope). Network resolution suspends to Dispatchers.IO
 * internally, so the main thread is never blocked.
 *
 * Thin orchestrator — state lives in QueueState, operations in QueueOperations.
 * No behavior change.
 */
@Singleton
class PlaybackQueueOrchestrator @Inject constructor(
    private val repository: IMusicRepository,
    private val playbackQueueManager: PlaybackQueueManager,
    private val wakeLockManager: WakeLockManager,
    private val wakeLockHelper: WakeLockHelper,
    private val streamUrlCache: StreamUrlCache
) {

    private val state = QueueState(
        repository = repository,
        playbackQueueManager = playbackQueueManager,
        wakeLockManager = wakeLockManager,
        streamUrlCache = streamUrlCache
    )

    private val operations = QueueOperations(
        repository = repository,
        playbackQueueManager = playbackQueueManager,
        wakeLockManager = wakeLockManager,
        wakeLockHelper = wakeLockHelper,
        streamUrlCache = streamUrlCache,
        state = state
    )

    // ------------------------------------------------------------------
    // Lookahead: keep [current, next] in the player playlist
    // ------------------------------------------------------------------

    /**
     * Warms the stream-URL caches for the upcoming track and, when possible,
     * enqueues it behind the current item so the handoff is seamless.
     * Safe to call from anywhere; no-ops when there is nothing to do.
     */
    fun prefetchUpcomingStreams(scope: CoroutineScope, player: ExoPlayer?) {
        operations.prefetchUpcomingStreams(scope, player)
    }

    /**
     * Called from the service's onMediaItemTransition after the queue index has
     * been synced: trims already-played items and tops up the lookahead.
     */
    fun onPlayerAdvancedToNewItem(scope: CoroutineScope, player: ExoPlayer?) {
        operations.onPlayerAdvancedToNewItem(scope, player)
    }

    // ------------------------------------------------------------------
    // Transitions
    // ------------------------------------------------------------------

    /**
     * Advances to the next track in the queue.
     * Fast path: the lookahead item is already buffered — seek to it with no
     * network at all, which works even in deep Doze. Slow path: resolve and
     * play manually, fully under wake locks. At queue end, discovers and
     * appends fresh autoplay recommendations.
     */
    fun playNextTrack(scope: CoroutineScope, player: ExoPlayer?) {
        operations.playNextTrack(scope, player)
    }

    /**
     * Plays the previous track in the active queue if available.
     */
    fun playPreviousTrack(scope: CoroutineScope, player: ExoPlayer?) {
        operations.playPreviousTrack(scope, player)
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
        operations.playTrackAtIndex(scope, player, index)
    }

    // ------------------------------------------------------------------
    // Error recovery (Doze-proof: coroutine delay under locks, not Handler)
    // ------------------------------------------------------------------

    /**
     * Recovers from a playback error with a freshly resolved URL.
     * Must be called instead of blindly re-preparing: cached googlevideo URLs
     * expire, and re-preparing the same dead URL loops forever.
     */
    fun retryCurrentWithFreshUrl(scope: CoroutineScope, player: ExoPlayer?, delayMs: Long) {
        operations.retryCurrentWithFreshUrl(scope, player, delayMs)
    }
}

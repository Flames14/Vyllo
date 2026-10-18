package com.vyllo.music.service.audio

import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.data.manager.PreferenceManager
import com.vyllo.music.domain.manager.PlaybackQueueManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Owns the queue's lifecycle outside playback itself: restoring the last session
 * on startup and persisting structural edits back to storage.
 *
 * Extracted from MusicService — no behavior change. Both jobs are launched on the
 * caller's scope so they inherit its failure handler and cancellation.
 */
class QueuePersistenceCoordinator(
    private val queueManager: PlaybackQueueManager,
    private val preferenceManager: PreferenceManager
) {

    /** Starts queue restore-then-persist. Idempotent per scope. */
    fun start(scope: CoroutineScope) {
        scope.launch { restoreIfEmpty() }
        scope.launch { persistOnChanges() }
    }

    /**
     * Restores the last session's queue after process death / reboot. No autoplay:
     * items come back for Up Next continuity only, and only when nothing is queued
     * (so a live user action is never clobbered).
     */
    suspend fun restoreIfEmpty() {
        try {
            if (queueManager.getQueueSnapshot().isEmpty()) {
                preferenceManager.restoreQueue()?.let { (items, index) ->
                    queueManager.replaceQueue(items, index)
                }
            }
        } catch (e: Exception) {
            SecureLogger.w("MusicService", "queue restore failed", e)
        }
    }

    /**
     * Persists the queue on every structural change. Debounced because saves are
     * plain SharedPreferences writes and structure can change in bursts (e.g. a
     * shuffle swap); the collector guarantees only the final state is written.
     */
    @OptIn(FlowPreview::class)
    suspend fun persistOnChanges() {
        try {
            queueManager.queueVersion
                .debounce(2000L)
                .collectLatest {
                    preferenceManager.saveQueue(
                        queueManager.getQueueSnapshot(),
                        queueManager.currentIndex
                    )
                }
        } catch (e: Exception) {
            SecureLogger.w("MusicService", "queue save failed", e)
        }
    }
}

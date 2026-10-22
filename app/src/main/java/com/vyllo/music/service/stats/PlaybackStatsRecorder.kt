package com.vyllo.music.service.stats

import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.repository.IMusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Feeds listening statistics from the playback session.
 *
 * The service is the only place that knows a track really started and how long
 * it played, so it owns the counters: UI-triggered "add to history" calls only
 * keep the recent list fresh and must never double count. Starts are guarded by
 * media id because Media3 can report the same item more than once (playlist
 * rebuild, resumption, session restore).
 */
class PlaybackStatsRecorder(
    private val repository: IMusicRepository,
    private val scope: CoroutineScope
) {
    private var countedMediaId: String? = null

    /** Called whenever the player lands on a track that may now be playing. */
    fun onTrackStarted(item: MusicItem?) {
        val mediaId = item?.url?.takeIf { it.isNotBlank() } ?: return
        if (mediaId == countedMediaId) return
        countedMediaId = mediaId
        scope.launch {
            runCatching { repository.recordPlayStart(item) }
                .onFailure { SecureLogger.w("PlaybackStats", "play start failed: ${it.message}") }
        }
    }

    /**
     * Called when a track is left with the position it reached. Small scraps
     * (skips in the first few seconds) are ignored so skipped tracks do not
     * accumulate listening time.
     */
    fun onTrackEnded(item: MusicItem?, positionMs: Long) {
        val url = item?.url?.takeIf { it.isNotBlank() } ?: return
        if (positionMs <= MIN_LISTEN_MS) return
        scope.launch {
            runCatching { repository.recordListenedTime(url, positionMs) }
                .onFailure { SecureLogger.w("PlaybackStats", "listen time failed: ${it.message}") }
        }
    }

    /** Forget the dedupe guard when playback restarts from scratch. */
    fun reset() {
        countedMediaId = null
    }

    companion object {
        const val MIN_LISTEN_MS = 3_000L
    }
}

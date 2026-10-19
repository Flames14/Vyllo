package com.vyllo.music.service.audio

import androidx.media3.common.Player
import com.vyllo.music.data.manager.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Persists "where was I" so process death does not lose the playhead.
 *
 * Samples every [intervalMs] while playing, and is also called explicitly on
 * pause / track change / teardown, so the stored position is never more than
 * one sample behind the real one.
 */
class PlayheadPersistence(
    private val preferenceManager: PreferenceManager,
    private val intervalMs: Long = DEFAULT_INTERVAL_MS
) {

    companion object {
        const val DEFAULT_INTERVAL_MS = 5_000L
    }

    fun start(scope: CoroutineScope, playerProvider: () -> Player?) {
        scope.launch {
            while (isActive) {
                delay(intervalMs)
                val player = playerProvider() ?: continue
                if (player.isPlaying) persist(player)
            }
        }
    }

    /** Writes the current item's logical URL (mediaId) and position. */
    fun persist(player: Player?) {
        if (player == null) return
        val mediaId = player.currentMediaItem?.mediaId ?: return
        if (mediaId.isBlank()) return
        preferenceManager.savePlaybackPosition(mediaId, player.currentPosition)
    }

    /** The track finished: replaying it should start at zero, not at the last second. */
    fun clear() {
        preferenceManager.clearPlaybackPosition()
    }
}

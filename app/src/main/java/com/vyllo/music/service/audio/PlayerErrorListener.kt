package com.vyllo.music.service.audio

import android.media.AudioManager
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.data.manager.PlaybackAudioEffectsManager
import com.vyllo.music.data.manager.PlaybackQueueManager
import com.vyllo.music.domain.manager.PlaybackErrorHandler
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.service.PlaybackQueueOrchestrator
import kotlinx.coroutines.CoroutineScope

/**
 * Player.Listener extracted from MusicService.
 * Service-managed retry holds CPU+WiFi locks across backoff — no behavior change.
 */
class PlayerErrorListener(
    private val playerProvider: () -> ExoPlayer?,
    private val serviceScope: CoroutineScope,
    private val playbackErrorHandler: PlaybackErrorHandler,
    private val playbackQueueOrchestrator: PlaybackQueueOrchestrator,
    private val playbackQueueManager: PlaybackQueueManager,
    private val playbackAudioEffectsManager: PlaybackAudioEffectsManager
) : Player.Listener {

    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        // Service-managed retry: holds CPU+WiFi locks across the backoff
        // delay and re-resolves a FRESH stream URL (cached googlevideo
        // URLs expire). A bare Handler.postDelayed retry would be
        // deferred by Doze until the screen turns on.
        val retryDelayMs = playbackErrorHandler.registerErrorAndGetDelay(error)
        if (retryDelayMs != null) {
            playbackQueueOrchestrator.retryCurrentWithFreshUrl(serviceScope, playerProvider(), retryDelayMs)
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_READY -> {
                SecureLogger.d("MusicService", "Playback STATE_READY")
                playbackErrorHandler.resetOnSuccess()
            }
            Player.STATE_ENDED -> {
                SecureLogger.d("MusicService", "Playback STATE_ENDED reached")
                playbackErrorHandler.resetOnSuccess()
                // Keep the locks held across the gap while the next track is
                // resolved. Releasing here lets Doze suspend the service before
                // playNextTrack() can run its locked network request.
                val currentRepeatMode = playerProvider()?.repeatMode ?: Player.REPEAT_MODE_OFF
                if (currentRepeatMode == Player.REPEAT_MODE_OFF) {
                    playbackQueueOrchestrator.playNextTrack(serviceScope, playerProvider())
                }
            }
            Player.STATE_IDLE -> {
                SecureLogger.d("MusicService", "Playback STATE_IDLE")
            }
            Player.STATE_BUFFERING -> {
                SecureLogger.d("MusicService", "Playback STATE_BUFFERING")
            }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        // Handled natively by ExoPlayer with WAKE_MODE_NETWORK.
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        SecureLogger.d("MusicService", "MediaItem transition: reason=$reason, mediaId=${mediaItem?.mediaId}")
        val currentMediaId = mediaItem?.mediaId
        if (!currentMediaId.isNullOrBlank()) {
            val queue = playbackQueueManager.getQueueSnapshot()
            val currentIndex = queue.indexOfFirst { it.url == currentMediaId }
            if (currentIndex >= 0) {
                playbackQueueManager.setCurrentIndexSafe(currentIndex)
            } else {
                val meta = mediaItem.mediaMetadata
                val directItem = MusicItem(
                    title = meta.title?.toString() ?: "Unknown",
                    uploader = meta.artist?.toString() ?: "Unknown",
                    thumbnailUrl = meta.artworkUri?.toString() ?: "",
                    url = currentMediaId
                )
                playbackQueueManager.setCurrentPlayingItemDirectly(directItem)
            }
        }
        // Whatever drove the transition (user tap, media button, or an
        // automatic advance to the pre-buffered lookahead item), trim
        // played items and top up the lookahead so the FOLLOWING
        // handoff is also seamless with the screen off.
        playbackErrorHandler.resetOnSuccess()
        playbackQueueOrchestrator.onPlayerAdvancedToNewItem(serviceScope, playerProvider())
    }

    override fun onAudioSessionIdChanged(audioSessionId: Int) {
        if (audioSessionId == AudioManager.ERROR) return
        playbackAudioEffectsManager.attachToAudioSession(audioSessionId)
    }
}

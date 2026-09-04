package com.vyllo.music.service

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.data.IMusicRepository
import com.vyllo.music.data.manager.PlaybackQueueManager
import com.vyllo.music.data.manager.WakeLockManager
import com.vyllo.music.domain.model.MusicItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class PlaybackQueueOrchestrator @Inject constructor(
    private val repository: IMusicRepository,
    private val playbackQueueManager: PlaybackQueueManager,
    private val wakeLockManager: WakeLockManager
) {

    /**
     * Warms up the stream URL cache for upcoming tracks in the queue so
     * track transitions happen with near-instant buffering.
     * Crucially, this does NOT mutate ExoPlayer's internal playlist directly,
     * maintaining PlaybackQueueManager as the single source of truth.
     */
    fun prefetchUpcomingStreams(scope: CoroutineScope) {
        scope.launch {
            val upcoming = playbackQueueManager.getUpcomingSnapshot()
            val nextTrack = upcoming.firstOrNull() ?: return@launch
            try {
                wakeLockManager.acquire()
                repository.getStreamUrl(nextTrack.url, force = false)
            } catch (e: Exception) {
                SecureLogger.d("MusicService", "Upcoming stream prefetch ignored: ${e.message}")
            } finally {
                wakeLockManager.release()
            }
        }
    }

    /**
     * Advances to the next track in the queue.
     * If the end of the queue has been reached, automatically discovers and appends
     * fresh autoplay recommendations to keep playback running seamlessly.
     */
    fun playNextTrack(scope: CoroutineScope, player: ExoPlayer?) {
        val queue = playbackQueueManager.getQueueSnapshot()
        val nextIndex = playbackQueueManager.currentIndex + 1
        if (nextIndex in queue.indices) {
            playTrackAtIndex(scope, player, nextIndex)
            return
        }

        val currentUrl = queue.getOrNull(playbackQueueManager.currentIndex)?.url ?: return
        scope.launch {
            try {
                wakeLockManager.acquire()
                var related = repository.getMoreRelatedSongs(currentUrl)
                if (related.isEmpty()) {
                    related = repository.getRelatedSongs(currentUrl)
                }
                val added = playbackQueueManager.appendDistinct(related)
                if (added.isNotEmpty()) {
                    playTrackAtIndex(scope, player, playbackQueueManager.currentIndex + 1)
                }
            } catch (e: Exception) {
                SecureLogger.e("MusicService", "Autoplay discovery failed", e)
            } finally {
                wakeLockManager.release()
            }
        }
    }

    /**
     * Plays the previous track in the active queue if available.
     */
    fun playPreviousTrack(scope: CoroutineScope, player: ExoPlayer?) {
        val prevIndex = playbackQueueManager.currentIndex - 1
        val queue = playbackQueueManager.getQueueSnapshot()
        if (prevIndex in queue.indices) {
            playTrackAtIndex(scope, player, prevIndex)
        }
    }

    /**
     * Loads and starts playing the track at the specified index in the queue.
     * Updates PlaybackQueueManager immediately so UI states (title, artist, artwork)
     * transition in exact lockstep with audio playback.
     */
    fun playTrackAtIndex(scope: CoroutineScope, player: ExoPlayer?, index: Int) {
        val queue = playbackQueueManager.getQueueSnapshot()
        if (index !in queue.indices) {
            SecureLogger.w("MusicService", "Index $index out of bounds (size: ${queue.size})")
            return
        }

        val track = queue[index]
        playbackQueueManager.setCurrentIndexSafe(index)

        scope.launch {
            player?.pause()
            try {
                wakeLockManager.acquire()
                val streamUrl = repository.getStreamUrl(track.url, force = false)
                if (streamUrl != null) {
                    val mediaItem = track.toMediaItem(streamUrl)
                    player?.let { exoPlayer ->
                        exoPlayer.setMediaItem(mediaItem)
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = true
                    }
                } else {
                    SecureLogger.w("MusicService", "Stream URL null for ${track.title}, falling back to next track")
                    val nextIdx = index + 1
                    if (nextIdx < playbackQueueManager.getQueueSnapshot().size) {
                        playTrackAtIndex(scope, player, nextIdx)
                    }
                }
            } catch (e: Exception) {
                SecureLogger.e("MusicService", "playTrackAtIndex failed for: ${track.title}", e)
            } finally {
                wakeLockManager.release()
            }
        }
    }

    private fun MusicItem.toMediaItem(streamUrl: String): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(uploader)
            .setArtworkUri(android.net.Uri.parse(thumbnailUrl))
            .build()
        return MediaItem.Builder()
            .setMediaId(url)
            .setUri(streamUrl)
            .setMediaMetadata(metadata)
            .build()
    }
}

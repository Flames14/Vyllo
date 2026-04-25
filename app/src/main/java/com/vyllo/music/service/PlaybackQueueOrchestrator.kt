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
    fun enqueueNextTrackIfNeeded(scope: CoroutineScope, player: ExoPlayer, currentMediaItem: MediaItem?) {
        val currentMediaId = currentMediaItem?.mediaId ?: return
        scope.launch {
            val queue = playbackQueueManager.getQueueSnapshot()
            val currentIndex = queue.indexOfFirst { it.url == currentMediaId }
            if (currentIndex == -1) {
                SecureLogger.w("MusicService", "Current media ID not found in queue: $currentMediaId")
                return@launch
            }

            playbackQueueManager.setCurrentIndexSafe(currentIndex)

            val tracksToPrefetch = 3
            val currentPlaylistIndex = player.currentMediaItemIndex
            val itemsAfterCurrent = player.mediaItemCount - currentPlaylistIndex - 1
            if (itemsAfterCurrent >= tracksToPrefetch) return@launch

            val tracksNeeded = tracksToPrefetch - itemsAfterCurrent
            val nextIndex = currentIndex + 1

            for (i in 0 until tracksNeeded) {
                val prefetchIndex = nextIndex + i
                if (prefetchIndex >= queue.size) {
                    fetchAndAddRelatedSongs(queue)
                    continue
                }

                val nextTrack = queue[prefetchIndex]
                val alreadyQueued = (0 until player.mediaItemCount).any { idx ->
                    player.getMediaItemAt(idx).mediaId == nextTrack.url
                }
                if (alreadyQueued) continue

                try {
                    wakeLockManager.acquire()
                    val streamUrl = repository.getStreamUrl(nextTrack.url, isVideo = false)
                    if (streamUrl != null) {
                        player.addMediaItem(nextTrack.toMediaItem(streamUrl))
                    }
                } catch (e: Exception) {
                    SecureLogger.e("MusicService", "Prefetch failed for: ${nextTrack.title}", e)
                } finally {
                    wakeLockManager.release()
                }
            }
        }
    }

    fun playNextTrack(scope: CoroutineScope, player: ExoPlayer?) {
        val nextIndex = playbackQueueManager.currentIndex + 1
        if (nextIndex < playbackQueueManager.currentQueue.size) {
            playTrackAtIndex(scope, player, nextIndex)
            return
        }

        val currentUrl = playbackQueueManager.currentQueue.getOrNull(playbackQueueManager.currentIndex)?.url ?: return
        scope.launch {
            try {
                wakeLockManager.acquire()
                val related = repository.getRelatedSongs(currentUrl)
                if (related.isNotEmpty()) {
                    val addedSongs = playbackQueueManager.appendDistinct(related)
                    if (addedSongs.isEmpty()) {
                        playbackQueueManager.addAll(related.take(5))
                    }
                    playTrackAtIndex(scope, player, playbackQueueManager.currentIndex + 1)
                }
            } catch (e: Exception) {
                SecureLogger.e("MusicService", "Autoplay discovery failed", e)
            } finally {
                wakeLockManager.release()
            }
        }
    }

    fun playTrackAtIndex(scope: CoroutineScope, player: ExoPlayer?, index: Int) {
        if (index >= playbackQueueManager.currentQueue.size) {
            SecureLogger.w("MusicService", "Index $index out of bounds (size: ${playbackQueueManager.currentQueue.size})")
            return
        }

        val track = playbackQueueManager.currentQueue[index]
        playbackQueueManager.setCurrentIndexSafe(index)
        scope.launch {
            try {
                wakeLockManager.acquire()
                val streamUrl = repository.getStreamUrl(track.url, force = false)
                if (streamUrl != null) {
                    val mediaItem = track.toMediaItem(streamUrl)
                    player?.let { exoPlayer ->
                        val existingIndex = (0 until exoPlayer.mediaItemCount).indexOfFirst { idx ->
                            exoPlayer.getMediaItemAt(idx).mediaId == track.url
                        }

                        if (existingIndex >= 0) {
                            exoPlayer.seekToDefaultPosition(existingIndex)
                        } else {
                            exoPlayer.addMediaItem(mediaItem)
                            exoPlayer.seekToDefaultPosition(exoPlayer.mediaItemCount - 1)
                        }
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = true
                    }
                }
            } catch (e: Exception) {
                SecureLogger.e("MusicService", "playTrackAtIndex failed for: ${track.title}", e)
            } finally {
                wakeLockManager.release()
            }
        }
    }

    private suspend fun fetchAndAddRelatedSongs(queue: List<MusicItem>) {
        try {
            wakeLockManager.acquire()
            val currentUrl = queue.lastOrNull()?.url ?: return
            val related = repository.getRelatedSongs(currentUrl)
            if (related.isNotEmpty()) {
                val addedSongs = playbackQueueManager.appendDistinct(related)
                if (addedSongs.isEmpty()) {
                    playbackQueueManager.addAll(related.take(5))
                }
            }
        } catch (e: Exception) {
            SecureLogger.e("MusicService", "Related songs fetch failed", e)
        } finally {
            wakeLockManager.release()
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

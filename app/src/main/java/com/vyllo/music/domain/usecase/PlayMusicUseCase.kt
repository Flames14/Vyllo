package com.vyllo.music.domain.usecase

import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.data.manager.PlaybackQueueManager
import com.vyllo.music.domain.manager.PlaybackManager
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.PlayResult
import javax.inject.Inject

/**
 * Resolves a stream URL and starts playback through the PlaybackManager.
 * Encapsulates the play flow that was previously scattered across MainActivity.
 */
class PlayMusicUseCase @Inject constructor(
    private val getStreamUrlUseCase: GetStreamUrlUseCase,
    private val playbackManager: PlaybackManager,
    private val playbackQueueManager: PlaybackQueueManager
) {

    private companion object {
        private const val TAG = "PlayMusicUseCase"
    }

    /**
     * Resolves the stream URL and starts playback.
     * @return PlayResult indicating success or failure with error message.
     */
    suspend fun execute(item: MusicItem, isVideo: Boolean = false, keepQueue: Boolean = false): PlayResult {
        // Ensure the MediaController is connected before mutating playback state.
        try {
            playbackManager.awaitConnection()
        } catch (e: Exception) {
            SecureLogger.w(TAG, "Controller not ready: ${e.message}")
            return PlayResult.Failure("Playback service unavailable")
        }

        // Stop current playback immediately so the user hears the change instantly
        playbackManager.stop()

        SecureLogger.d(TAG) { "Playing: ${item.title}, isVideo=$isVideo, keepQueue=$keepQueue" }

        // Only replace queue if not explicitly instructed to keep existing queue
        if (!keepQueue) {
            playbackQueueManager.replaceQueue(listOf(item), 0)
        } else {
            val idx = playbackQueueManager.indexOf(item)
            if (idx >= 0) {
                playbackQueueManager.setCurrentIndexSafe(idx)
            } else {
                playbackQueueManager.addItem(item)
                playbackQueueManager.setCurrentIndexSafe(playbackQueueManager.size - 1)
            }
        }

        // The repository/datasource already has internal retries, so we remove the second layer of delay here
        val streamUrl = getStreamUrlUseCase(item.url, isVideo = isVideo)
            ?: return PlayResult.Failure("Unable to resolve stream URL")

        val result = playbackManager.playMusic(item, streamUrl, isVideo = isVideo)

        return if (result.isSuccess) {
            PlayResult.Success
        } else {
            val message = result.exceptionOrNull()?.message ?: "Unknown playback error"
            SecureLogger.w(TAG, "Playback failed: $message")
            PlayResult.Failure(message)
        }
    }
}

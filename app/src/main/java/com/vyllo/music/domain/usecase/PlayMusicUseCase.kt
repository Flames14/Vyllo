package com.vyllo.music.domain.usecase

import com.vyllo.music.core.security.SecureLogger
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
) {

    private companion object {
        private const val TAG = "PlayMusicUseCase"
    }

    /**
     * Resolves the stream URL and starts playback.
     * @return PlayResult indicating success or failure with error message.
     */
    suspend fun execute(item: MusicItem, isVideo: Boolean = false): PlayResult {
        SecureLogger.d(TAG) { "Playing: ${item.title}, isVideo=$isVideo" }

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

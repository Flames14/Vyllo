package com.vyllo.music.service.audio

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Fades the outgoing track out and the incoming track in over the configured
 * window. This is a single-player fade-through, not an overlapping mix: the
 * overlap case needs two synchronized players and would break the
 * MediaSession/notification ownership the service relies on.
 *
 * With a 0 ms window the controller never touches volume, so ExoPlayer's
 * codec-level gapless path (MP3/AAC/Opus) is left intact.
 */
class CrossfadeController(
    private val playerProvider: () -> ExoPlayer?,
    private val crossfadeMillis: () -> Long,
    private val scope: CoroutineScope,
    private val tickMs: Long = 100L
) {
    private var fadeOutJob: Job? = null
    private var fadeInJob: Job? = null

    fun start() {
        stop()
        fadeOutJob = scope.launch {
            while (isActive) {
                delay(tickMs)
                applyFadeOut()
            }
        }
    }

    fun stop() {
        fadeOutJob?.cancel()
        fadeOutJob = null
        fadeInJob?.cancel()
        fadeInJob = null
        playerProvider()?.volume = 1f
    }

    /** Only automatic advances fade in; a seek or a manual skip stays instant. */
    fun onMediaItemTransition(reason: Int) {
        if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) return
        val fadeMs = crossfadeMillis()
        if (fadeMs <= 0L) return
        val player = playerProvider() ?: return

        fadeInJob?.cancel()
        player.volume = 0f
        val steps = (fadeMs / tickMs).toInt().coerceAtLeast(1)
        fadeInJob = scope.launch {
            for (step in 1..steps) {
                if (!isActive) return@launch
                delay(tickMs)
                player.volume = (step.toFloat() / steps).coerceIn(0f, 1f)
            }
        }
    }

    private fun applyFadeOut() {
        val player = playerProvider() ?: return
        // While the incoming track ramps up its own gain, the two ramps would
        // fight over the same volume property.
        if (fadeInJob?.isActive == true) return

        val fadeMs = crossfadeMillis()
        if (fadeMs <= 0L) {
            if (player.volume != 1f) player.volume = 1f
            return
        }

        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0L) return
        if (!player.isPlaying) return

        val target = FadeMath.gain(duration - player.currentPosition, fadeMs)
        if (abs(player.volume - target) > 0.01f) {
            player.volume = target
        }
    }
}

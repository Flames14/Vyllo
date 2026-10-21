package com.vyllo.music.service.audio

/**
 * Gain ramp used by the crossfade engine. Pure so the boundaries (which are
 * where audible glitches live) are unit-testable.
 */
object FadeMath {

    /**
     * Gain in 0..1 for a track with [remainingMs] left when the fade window is
     * [fadeMs]. Outside the window the gain is untouched at 1.0; a non-positive
     * window means "no crossfade" rather than silence.
     */
    fun gain(remainingMs: Long, fadeMs: Long): Float {
        if (fadeMs <= 0L) return 1f
        if (remainingMs <= 0L) return 0f
        if (remainingMs >= fadeMs) return 1f
        return remainingMs.toFloat() / fadeMs.toFloat()
    }
}

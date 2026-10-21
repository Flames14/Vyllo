package com.vyllo.music.core.utils

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Labels for the playback-rate controls. Pure so the rounding rules (which are
 * what users actually see) are unit-testable without Compose.
 */
object PlaybackRateFormat {

    /** "1x", "0.75x", "1.25x" — trailing zeros trimmed, dot as separator. */
    fun speedLabel(speed: Float): String {
        val safe = if (speed.isFinite() && speed > 0f) speed else 1f
        val text = if (safe == safe.roundToInt().toFloat()) {
            safe.roundToInt().toString()
        } else {
            String.format(Locale.US, "%.2f", safe).trimEnd('0').trimEnd('.')
        }
        return "${text}x"
    }

    /** Pitch as a percentage of the original: 1.0f -> "100%". */
    fun pitchLabel(pitch: Float): String {
        val safe = if (pitch.isFinite() && pitch > 0f) pitch else 1f
        return "${(safe * 100).roundToInt()}%"
    }

    /** Snap to the 5%-of-original grid the slider uses, keeping 0.5x–2.0x. */
    fun snapPitch(pitch: Float): Float {
        val safe = if (pitch.isFinite()) pitch else 1f
        return ((safe * 20).roundToInt() / 20f).coerceIn(0.5f, 2.0f)
    }
}

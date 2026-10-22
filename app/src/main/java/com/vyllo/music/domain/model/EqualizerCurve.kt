package com.vyllo.music.domain.model

/**
 * Geometry for the equalizer response curve.
 *
 * Kept out of the Canvas so the mapping (dB → normalized height, band spacing,
 * edge clamping) can be unit-tested without rendering.
 */
object EqualizerCurve {

    /**
     * Curve samples across the band strip. Values are normalized to `-1f..1f`
     * where positive is above the center line (boost) and negative is a cut.
     *
     * @param levels band levels in millibels (`-1000..1000`).
     * @param samples how many points to produce; at least 2.
     */
    fun levels(levels: List<Int>, samples: Int = 96): List<Float> {
        if (levels.isEmpty()) return List(samples.coerceAtLeast(2)) { 0f }
        val count = samples.coerceAtLeast(2)
        val slots = levels.size - 1
        return List(count) { index ->
            val position = index.toFloat() / (count - 1) * slots
            val lower = position.toInt().coerceIn(0, slots)
            val upper = (lower + 1).coerceAtMost(slots)
            val fraction = position - lower
            val from = normalize(levels[lower])
            val to = normalize(levels[upper])
            if (slots == 0) from else from + (to - from) * fraction
        }
    }

    /** Band level in millibels mapped to `-1f..1f` and clamped. */
    fun normalize(levelMillibels: Int): Float {
        val clamped = levelMillibels.coerceIn(EqualizerSettings.BAND_LEVEL_MIN, EqualizerSettings.BAND_LEVEL_MAX)
        return clamped / EqualizerSettings.BAND_LEVEL_MAX.toFloat()
    }
}

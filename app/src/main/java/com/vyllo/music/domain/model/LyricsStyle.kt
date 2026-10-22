package com.vyllo.music.domain.model

/**
 * User-facing lyrics typography. Values are plain numbers so the sheet can be a
 * slider and the preference layer does not need Compose types.
 */
data class LyricsStyle(
    /** Multiplier on the base lyric font size. */
    val scale: Float = 1f,
    /** Multiplier on line height, applied on top of [scale]. */
    val lineSpacing: Float = 1f,
    /** Left-align lines instead of centering them (better for long lines). */
    val alignStart: Boolean = false,
    /** Draw a soft accent glow behind the active line. */
    val glow: Boolean = true,
    /** Animate transitions between lines; off is calmer and cheaper. */
    val animate: Boolean = true
) {
    companion object {
        const val MIN_SCALE = 0.8f
        const val MAX_SCALE = 1.6f
        const val MIN_SPACING = 0.8f
        const val MAX_SPACING = 1.8f

        fun scaleFromPercent(percent: Int): Float =
            (percent.coerceIn(80, 160) / 100f).coerceIn(MIN_SCALE, MAX_SCALE)

        fun spacingFromPercent(percent: Int): Float =
            (percent.coerceIn(80, 180) / 100f).coerceIn(MIN_SPACING, MAX_SPACING)

        fun percentOf(value: Float): Int = Math.round(value * 100f)
    }
}

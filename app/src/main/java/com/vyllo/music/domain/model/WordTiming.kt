package com.vyllo.music.domain.model

/**
 * Word-progress math for karaoke-style highlighting.
 *
 * Pure and index-based so the UI can build spans without re-deriving timing,
 * and so the edge cases (before the line, between words, after the line) are
 * testable without a player.
 */
object WordTiming {

    /**
     * Number of words whose end has been reached at [positionMs]. Words are
     * expected to be ordered; the count therefore grows monotonically.
     */
    fun sungWordCount(words: List<LyricWord>, positionMs: Long): Int {
        if (words.isEmpty()) return 0
        return words.count { positionMs >= it.endTimeMs }
    }

    /**
     * Words started but not finished at [positionMs] — the word being sung right
     * now. Empty when playback sits in a gap or outside the line.
     */
    fun activeWordIndex(words: List<LyricWord>, positionMs: Long): Int? {
        if (words.isEmpty()) return null
        val index = words.indexOfFirst { positionMs < it.endTimeMs }
        if (index < 0) return null
        return index.takeIf { positionMs >= words[it].startTimeMs }
    }

    /** True when the line has enough timing data to highlight word by word. */
    fun isWordTimed(words: List<LyricWord>): Boolean = words.size >= 2
}

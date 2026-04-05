package com.vyllo.music.data.lyrics

import com.vyllo.music.domain.model.SyncedLyricLine

/**
 * Utilities for parsing and working with LRC-formatted synced lyrics.
 */
object LrcParser {

    /**
     * Parses LRC-formatted string into a list of timestamped lyric lines.
     * Supports [mm:ss.xx], [mm:ss:xx], [mm:ss.xxx], [mm:ss] formats.
     */
    fun parseSyncedLyrics(lrcString: String?): List<SyncedLyricLine> {
        if (lrcString.isNullOrBlank()) return emptyList()
        val lines = mutableListOf<SyncedLyricLine>()
        val regex = Regex("\\[(\\d{1,3}):(\\d{2})(?:[\\.\\:](\\d{2,3}))?\\]\\s*(.*)")

        lrcString.lines().forEach { line ->
            val matchResult = regex.find(line)
            if (matchResult != null) {
                try {
                    val minutes = matchResult.groupValues[1].toLong()
                    val seconds = matchResult.groupValues[2].toLong()
                    val fractionStr = matchResult.groupValues[3]
                    val fractionMs = when (fractionStr.length) {
                        2 -> fractionStr.toLong() * 10
                        3 -> fractionStr.toLong()
                        0 -> 0L
                        else -> fractionStr.take(3).toLong()
                    }
                    val text = matchResult.groupValues[4].trim()
                    if (text.isNotBlank() || lines.isNotEmpty()) {
                        val timeMs = (minutes * 60 + seconds) * 1000 + fractionMs
                        lines.add(SyncedLyricLine(timeMs, text))
                    }
                } catch (_: Exception) {
                    // Skip malformed lines
                }
            }
        }
        return lines.sortedBy { it.startTimeMs }
    }

    /**
     * Returns the index of the lyric line that should be active at the given time.
     */
    fun getCurrentLyricLine(syncedLyrics: List<SyncedLyricLine>?, currentTimeMs: Long): Int {
        if (syncedLyrics.isNullOrEmpty()) return -1
        for (i in syncedLyrics.indices.reversed()) {
            if (syncedLyrics[i].startTimeMs <= currentTimeMs + 50) {
                return i
            }
        }
        return -1
    }

    /**
     * Detects the language(s) of a lyrics text based on Unicode ranges.
     * Returns a list of detected languages, or ["english"] if none found.
     */
    fun detectLanguage(text: String): List<String> {
        if (text.isBlank()) return listOf("english")
        val detected = mutableListOf<String>()
        for ((language, pattern) in LANGUAGE_PATTERNS) {
            if (pattern.containsMatchIn(text)) {
                detected.add(language)
            }
        }
        return if (detected.isNotEmpty()) detected else listOf("english")
    }

    private val LANGUAGE_PATTERNS = mapOf(
        "tamil" to Regex("[\\u0B80-\\u0BFF]"),
        "hindi" to Regex("[\\u0900-\\u097F]"),
        "telugu" to Regex("[\\u0C00-\\u0C7F]"),
        "malayalam" to Regex("[\\u0D00-\\u0D7F]"),
        "korean" to Regex("[\\uAC00-\\uD7AF]"),
        "japanese" to Regex("[\\u3040-\\u30FF]"),
        "chinese" to Regex("[\\u4E00-\\u9FFF]"),
        "arabic" to Regex("[\\u0600-\\u06FF]"),
        "russian" to Regex("[\\u0400-\\u04FF]")
    )
}

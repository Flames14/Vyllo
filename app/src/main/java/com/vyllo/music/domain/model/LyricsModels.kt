package com.vyllo.music.domain.model

/**
 * Represents a synced lyric line with timestamp.
 *
 * [words] is only populated by providers that publish word timings (currently
 * the TTML-backed ones). An empty list means line-level lyrics: the UI must not
 * fake word progress by interpolating.
 */
data class SyncedLyricLine(
    val startTimeMs: Long,
    val content: String,
    val words: List<LyricWord> = emptyList()
)

/** A single sung word inside a [SyncedLyricLine]. */
data class LyricWord(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String
)

/**
 * Represents a lyrics search result from external sources.
 */
data class LyricsResult(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val duration: Long,
    val instrumental: Boolean,
    val plainLyrics: String?,
    val syncedLyrics: String?
)

/**
 * Status of lyrics availability.
 */
data class LyricsStatus(
    val hasPlain: Boolean,
    val hasSynced: Boolean,
    val isInstrumental: Boolean
)

/**
 * Complete lyrics response from the lyrics engine.
 */
data class LyricsResponse(
    val success: Boolean,
    val result: LyricsResult? = null,
    val results: List<LyricsResult> = emptyList(),
    val strategy: String = "",
    val plainLyrics: String? = null,
    val syncedLines: List<SyncedLyricLine>? = null,
    val languages: List<String>? = null,
    val lyricsStatus: LyricsStatus? = null,
    val error: String? = null
)

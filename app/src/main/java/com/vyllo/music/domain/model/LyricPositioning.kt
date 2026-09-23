package com.vyllo.music.domain.model

/**
 * Returns the index of the lyric line that should be active at the given time.
 * Pure domain logic shared by playback UI and parsers.
 */
object LyricPositioning {
    fun getCurrentLine(syncedLyrics: List<SyncedLyricLine>?, currentTimeMs: Long): Int {
        if (syncedLyrics.isNullOrEmpty()) return -1
        for (i in syncedLyrics.indices.reversed()) {
            if (syncedLyrics[i].startTimeMs <= currentTimeMs + 50) {
                return i
            }
        }
        return -1
    }
}

package com.vyllo.music.domain.model

/** One row of the "most played" list. */
data class TopTrack(
    val item: MusicItem,
    val playCount: Int,
    val listenedMs: Long
)

/** One row of the "most played artists" list. */
data class TopArtist(
    val name: String,
    val plays: Int,
    val listenedMs: Long
)

/**
 * Everything the stats screen shows, derived from playback history. Kept as one
 * immutable snapshot so the UI never renders mismatched totals and lists.
 */
data class ListeningStats(
    val totalPlays: Int = 0,
    val totalListenedMs: Long = 0L,
    val totalTracks: Int = 0,
    val topTracks: List<TopTrack> = emptyList(),
    val topArtists: List<TopArtist> = emptyList()
) {
    val hasData: Boolean get() = totalTracks > 0 && (totalPlays > 0 || totalListenedMs > 0L)

    /** Average play length, used for the "average listen" summary. */
    val averageListenMs: Long
        get() = if (totalPlays > 0) totalListenedMs / totalPlays else 0L
}

/**
 * Human label for a listening duration. Pure so it can be tested without a
 * Context; the UI formats the numbers, never the raw milliseconds.
 */
object ListeningTimeFormat {
    fun format(totalMs: Long): String {
        if (totalMs <= 0L) return "0 min"
        val totalMinutes = totalMs / 60_000L
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "$hours h $minutes min"
            hours > 0 -> "$hours h"
            else -> "$minutes min"
        }
    }
}

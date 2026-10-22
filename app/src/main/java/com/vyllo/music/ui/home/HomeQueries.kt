package com.vyllo.music.ui.home

/**
 * The search terms behind the home feed's fixed rows.
 *
 * Extracted from [com.vyllo.music.HomeViewModel] so the catalogue of queries —
 * which are effectively product copy — can be reviewed in one place.
 */
object HomeQueries {

    const val NEW_RELEASES = "new music releases"
    const val CHILL = "chill lofi music"
    const val WORKOUT = "workout gym music"
    const val FOCUS = "focus study music"

    /** Search term for an Explore category card. */
    fun exploreQuery(category: String): String = when (category) {
        "New releases" -> "new music releases official"
        "Charts" -> "official global music charts top songs"
        "Top music videos" -> "top official music videos this week"
        "Moods & genres" -> "moods and genres music mix"
        "Pop" -> "official pop hits music videos"
        "Hip-hop" -> "official hip hop rap music videos"
        "Rock" -> "official rock music videos"
        "R&B" -> "official r&b soul music videos"
        "Latin" -> "official latin music videos"
        "K-pop" -> "official k-pop music videos"
        "Afrobeats" -> "official afrobeats music videos"
        "Jazz" -> "jazz music official live sessions"
        "Classical" -> "classical music performances"
        "Lofi" -> "lofi beats study chill mix"
        "Party" -> "party playlist official"
        "Romance" -> "romantic love songs playlist"
        "Sleep" -> "sleep relaxing music"
        "Workout" -> "workout gym motivation playlist"
        else -> "$category playlist"
    }

    /** Search term for a mood chip on the home feed. */
    fun chipQuery(chip: String): String = when (chip) {
        "Relax" -> "relaxing chill music"
        "Energize" -> "energetic upbeat music"
        "Workout" -> "workout gym motivation music"
        "Focus" -> "focus concentration study music"
        "Commute" -> "driving road trip music"
        else -> "trending music"
    }
}

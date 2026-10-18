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
        "Charts" -> "top music charts global official"
        "Moods & genres" -> "moods and genres music mix"
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

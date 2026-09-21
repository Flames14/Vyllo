package com.vyllo.music.data.lyrics.engine

import com.vyllo.music.core.security.SecureLogger

/**
 * Title / artist cleaning utilities extracted from LyricsEngine without behavior change.
 */
object TitleArtistCleaner {

    private const val TAG = "LyricsEngine"

    fun isRecordLabel(name: String): Boolean {
        val labelKeywords = listOf("music", "records", "series", "company", "entertainment", "channel", "official", "tv", "indie", "south", "india")
        val lower = name.lowercase()
        return labelKeywords.any { lower.contains(it) }
    }

    fun cleanTitleAndArtist(rawTitle: String, rawArtist: String): Pair<String, String> {
        var title = rawTitle.trim()
        var artist = rawArtist.trim()

        // Step 1: Clean suffixes and garbage words first
        val suffixPatterns = listOf(
            "(?i)\\(Official (?:Music )?Video\\)",
            "(?i)\\[Official (?:Music )?Video\\]",
            "(?i)\\(Official Audio\\)",
            "(?i)\\[Official Audio\\]",
            "(?i)\\(Lyric(?:s)? Video\\)",
            "(?i)\\[Lyric(?:s)?\\]",
            "(?i)\\(Audio\\)",
            "(?i)\\[Audio\\]",
            "(?i)\\([vV]isualizer\\)",
            "(?i)\\[[vV]isualizer\\]",
            "(?i)\\(Music Video\\)",
            "(?i)\\[Music Video\\]",
            "(?i)\\(4K Video\\)",
            "(?i)\\[4K\\]",
            "(?i)\\(HD\\)",
            "(?i)\\[HD\\]"
        )
        for (pattern in suffixPatterns) {
            title = title.replace(Regex(pattern), "").trim()
        }

        val garbageWords = listOf(
            "official music video", "official video", "official audio", "video song",
            "lyrical video", "lyric video", "audio song", "full song", "music video",
            "full video", "hd video", "4k video", "lyric", "lyrics"
        )
        for (garbage in garbageWords) {
            title = title.replace(Regex("(?i)\\b$garbage\\b"), "").trim()
        }

        // Clean up empty dashes followed by pipes
        title = title.replace(Regex("\\s*[-\u2013\u2014]+\\s*(?=\\||$)"), " ").trim()
        title = title.replace(Regex("\\s*[-\u2013\u2014|]+\\s*$"), "").trim()
        title = title.replace(Regex("\\s+"), " ").trim()

        // Step 2: Handle "Artist - Title" format in title
        val dashes = listOf(" - ", " \u2013 ", " \u2014 ")
        var hasDashArtist = false
        for (dash in dashes) {
            if (title.contains(dash)) {
                val parts = title.split(dash, limit = 2)
                if (parts.size == 2) {
                    val potentialArtist = parts[0].trim()
                    val potentialTitle = parts[1].trim()

                    if (potentialArtist.length < 50 && potentialArtist.split(Regex("\\s+")).size <= 4) {
                        if (!isRecordLabel(potentialArtist)) {
                            artist = potentialArtist
                            title = potentialTitle
                            hasDashArtist = true
                            break
                        }
                    }
                }
            }
        }

        // Step 3: Handle " | " separator in title
        if (title.contains(" | ")) {
            val parts = title.split(" | ")
            val firstPart = parts[0].trim()
            val secondPart = if (parts.size > 1) parts[1].trim() else ""

            if (secondPart.isNotBlank() && !isRecordLabel(secondPart) && secondPart.split(Regex("\\s+")).size <= 3) {
                title = "$firstPart $secondPart"
            } else {
                title = firstPart
            }

            title = title.replace(Regex("\\s* - \\s*$"), "").trim()
        }

        title = title.replace(Regex("\\s*[-\u2013\u2014|]+\\s*$"), "").trim()

        artist = artist
            .replace(Regex("(?i)(-\\s*)?Topic"), "")
            .replace(Regex("(?i)VEVO"), "")
            .replace(Regex("(?i)(-\\s*)?Official(?:\\s+Channel)?"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (artist.startsWith("@")) {
            artist = artist.removePrefix("@")
        }

        val featMatch = Regex("(?i)\\(?\\s*(?:ft|feat|featuring|\\&|x)\\s+([^)\\]]+)").find(title)
        if (featMatch != null) {
            title = title.replace(featMatch.value, "").trim().trim(',', '&').trim()
            val featuredArtist = featMatch.groupValues[1].trim().trim(',', '&').trim()
            if (artist.isBlank() || isRecordLabel(artist)) {
                artist = featuredArtist
            } else if (!artist.contains(featuredArtist, ignoreCase = true)) {
                artist = "$artist feat. $featuredArtist"
            }
        }

        title = title.replace(Regex("\\(\\s*\\)"), "")
        title = title.replace(Regex("\\[\\s*\\]"), "")
        title = title.replace(Regex("\\s+"), " ").trim()

        SecureLogger.d(TAG, "  Cleaned: title='$title' artist='$artist'")
        return Pair(title, artist)
    }

    /**
     * Word-level Jaccard similarity (0.0 to 1.0).
     */
    fun wordJaccard(a: String, b: String): Double {
        if (a.isBlank() || b.isBlank()) return 0.0
        if (a == b) return 1.0
        val aSet = a.split("\\s+".toRegex()).filter { it.length > 1 }.toSet()
        val bSet = b.split("\\s+".toRegex()).filter { it.length > 1 }.toSet()
        if (aSet.isEmpty() || bSet.isEmpty()) return 0.0
        val intersection = aSet.intersect(bSet).size.toDouble()
        val union = aSet.union(bSet).size.toDouble()
        return intersection / union
    }

    fun extractArtistAndTitleFromQuery(query: String): Pair<String, String>? {
        val separators = listOf(" - ", " – ", " — ")
        for (separator in separators) {
            val parts = query.split(separator, limit = 2)
            if (parts.size == 2) {
                val artist = parts[0].trim()
                val title = parts[1].trim()
                if (artist.isNotBlank() && title.isNotBlank()) {
                    return artist to title
                }
            }
        }
        return null
    }
}

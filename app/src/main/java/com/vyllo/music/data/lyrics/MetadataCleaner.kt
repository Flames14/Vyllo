package com.vyllo.music.data.lyrics

/**
 * Holds cleaned metadata with extracted featured artists and movie/album context.
 */
data class CleanedMetadata(
    val cleanText: String,
    val featuredArtists: List<String> = emptyList(),
    val movieOrAlbum: String? = null
)

/**
 * Cleans YouTube-style song metadata by removing video tags, extracting
 * featured artists, and detecting movie/album names.
 */
object MetadataCleaner {

    /**
     * Cleans metadata text, extracting featured artists and movie/album names.
     */
    fun cleanMetadataAdvanced(text: String): CleanedMetadata {
        var working = text
        val featuredArtists = mutableListOf<String>()
        var movieOrAlbum: String? = null

        // Extract featured artists BEFORE removing them
        val featRegex = Regex("(?i)(?:ft\\.?|feat\\.?)\\s*(.+?)(?:\\s*[\\(\\[\\|]|$)")
        featRegex.find(working)?.let { match ->
            val featArtist = match.groupValues[1].trim()
            if (featArtist.isNotBlank()) {
                featuredArtists.add(featArtist)
            }
        }

        // Extract movie/album name
        val movieRegex = Regex("(?i)\\(?(?:from|ost|soundtrack)\\s*[\"']?([^\"'\\)\\]]+)[\"']?\\)?")
        movieRegex.find(working)?.let { match ->
            val name = match.groupValues[1].trim()
            if (name.isNotBlank() && name.length > 2) {
                movieOrAlbum = name
            }
        }

        // Clean the text
        working = working
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace(Regex("(?i)\\(.*?official.*?video.*?\\)"), "")
            .replace(Regex("(?i)\\[.*?official.*?video.*?\\]"), "")
            .replace(Regex("(?i)\\(.*?lyric.*?video.*?\\)"), "")
            .replace(Regex("(?i)\\[.*?lyric.*?video.*?\\]"), "")
            .replace(Regex("(?i)\\(.*?official.*?audio.*?\\)"), "")
            .replace(Regex("(?i)\\[.*?official.*?audio.*?\\]"), "")
            .replace(Regex("(?i)\\(.*?full.*?audio.*?\\)"), "")
            .replace(Regex("(?i)\\[.*?full.*?audio.*?\\]"), "")
            .replace(Regex("(?i)\\(.*?official.*?\\)"), "")
            .replace(Regex("(?i)\\[.*?official.*?\\]"), "")
            .replace(Regex("(?i)\\(.*?4k.*?\\)"), "")
            .replace(Regex("(?i)\\[.*?4k.*?\\]"), "")
            .replace(Regex("(?i)\\(?.*?remastered.*?\\)?"), "")
            .replace(Regex("(?i)\\(.*?(?:from|ost|soundtrack).*?\\)"), "")
            .replace(Regex("(?i)\\[.*?(?:from|ost|soundtrack).*?\\]"), "")
            .replace(Regex("(?i)\\s*(?:ft\\.?|feat\\.?)\\s*.+"), "")
            .replace(Regex("(?i)VEVO"), "")
            .replace(Regex("(?i)- Topic"), "")
            .replace(Regex("(?i)\\(Lyrics\\)"), "")
            .replace(Regex("(?i)\\[Lyrics\\]"), "")
            .replace(Regex("(?i)\\(Audio\\)"), "")
            .replace(Regex("(?i)\\[Audio\\]"), "")
            .replace(Regex("(?i)\\(Video\\)"), "")
            .replace(Regex("(?i)\\[Video\\]"), "")
            .replace(Regex("(?i)\\(HD\\)"), "")
            .replace(Regex("(?i)\\[HD\\]"), "")
            .replace("&", "and")
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return CleanedMetadata(working, featuredArtists, movieOrAlbum)
    }
}

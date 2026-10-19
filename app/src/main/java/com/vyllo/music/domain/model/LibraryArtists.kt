package com.vyllo.music.domain.model

import androidx.compose.runtime.Immutable

/**
 * A locally-derived artist entry. Vyllo stores no artist catalogue, so an
 * artist is simply the set of known songs sharing one normalised uploader.
 */
@Immutable
data class ArtistSummary(
    val key: String,
    val name: String,
    val thumbnailUrl: String,
    val songCount: Int
)

/**
 * Groups library songs into artists using [normalizeArtistKey], so
 * "Arijit Singh - Topic", "Arijit Singh" and "arijit  singh" collapse into
 * one entry instead of three.
 *
 * Pure: the library UI and its tests share exactly the same derivation.
 */
object LibraryArtists {

    private val TOPIC_SUFFIX = Regex("\\s*-\\s*topic\\s*$", RegexOption.IGNORE_CASE)
    private val WHITESPACE = Regex("\\s+")

    /** Human-readable artist name: drops YouTube's " - Topic" suffix and collapses whitespace. */
    fun displayName(uploader: String): String =
        uploader.replace(TOPIC_SUFFIX, "")
            .replace(WHITESPACE, " ")
            .trim()

    /**
     * Derives artists from [songs], newest information winning ties for the
     * display name and artwork. Entries with a blank uploader are skipped:
     * an "Unknown" tile would be noise, not a library.
     */
    fun fromSongs(songs: List<MusicItem>): List<ArtistSummary> {
        class Bucket {
            val urls = LinkedHashSet<String>()
            val names = HashMap<String, Int>()
            val thumbnails = HashMap<String, Int>()
        }

        val buckets = HashMap<String, Bucket>()
        for (song in songs) {
            val key = song.normalizedArtistKey
            val name = displayName(song.uploader)
            if (key.isBlank() || name.isBlank()) continue

            val bucket = buckets.getOrPut(key) { Bucket() }
            if (song.url.isNotBlank()) bucket.urls.add(song.url)
            bucket.names[name] = (bucket.names[name] ?: 0) + 1
            if (song.thumbnailUrl.isNotBlank()) {
                bucket.thumbnails[song.thumbnailUrl] = (bucket.thumbnails[song.thumbnailUrl] ?: 0) + 1
            }
        }

        return buckets.map { (key, bucket) ->
            ArtistSummary(
                key = key,
                name = bucket.names.maxByOrNull { it.value }?.key ?: key,
                thumbnailUrl = bucket.thumbnails.maxByOrNull { it.value }?.key.orEmpty(),
                songCount = bucket.urls.size
            )
        }.sortedWith(
            compareByDescending<ArtistSummary> { it.songCount }
                .thenBy { it.name.lowercase() }
                .thenBy { it.key }
        )
    }
}

package com.vyllo.music.data.repository.delegates

import org.schabi.newpipe.extractor.stream.StreamInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Track metadata we already pay for: the description of the anchor track is
 * fetched for lyrics, and it also tells us the language, film/album and
 * composer. Reusing it costs no extra network round trip and is what lets
 * "Up Next" stay in the same language / movie as the current track.
 */
data class TrackMetadata(
    val title: String? = null,
    val artist: String? = null,
    val movie: String? = null,
    val music: String? = null,
    /** Canonical lowercase language tag, e.g. "tamil". Null when unknown. */
    val language: String? = null,
    /** True when [movie] looks like a film / soundtrack rather than a plain album. */
    val isSoundtrack: Boolean = false
) {
    val hasLanguage: Boolean get() = !language.isNullOrBlank()
}

@Singleton
class TrackMetadataResolver @Inject constructor(
    private val descriptionParser: DescriptionMetadataParser
) {

    companion object {
        /**
         * Canonical language tag -> spellings that identify it.
         *
         * Kept deliberately ordered: longer/more specific spellings first so a
         * title containing "malayalam" is not swallowed by a shorter match.
         */
        private val LANGUAGE_HINTS: Map<String, List<String>> = linkedMapOf(
            "malayalam" to listOf("malayalam"),
            "tamil" to listOf("tamil", "tamilsong", "tamil song"),
            "telugu" to listOf("telugu", "telugusong"),
            "kannada" to listOf("kannada"),
            "hindi" to listOf("hindi", "bollywood", "hindustani"),
            "punjabi" to listOf("punjabi"),
            "bengali" to listOf("bengali", "bangla"),
            "marathi" to listOf("marathi"),
            "gujarati" to listOf("gujarati"),
            "oriya" to listOf("oriya", "odia"),
            "assamese" to listOf("assamese"),
            "nepali" to listOf("nepali"),
            "sinhala" to listOf("sinhala", "sinhalese"),
            "urdu" to listOf("urdu"),
            "english" to listOf("english"),
            "spanish" to listOf("spanish", "espanol"),
            "french" to listOf("french", "francais"),
            "german" to listOf("german", "deutsch"),
            "portuguese" to listOf("portuguese", "portugues"),
            "russian" to listOf("russian"),
            "korean" to listOf("korean", "kpop"),
            "japanese" to listOf("japanese", "anime"),
            "chinese" to listOf("chinese", "mandarin", "cantonese"),
            "arabic" to listOf("arabic"),
            "turkish" to listOf("turkish"),
            "indonesian" to listOf("indonesian"),
            "thai" to listOf("thai"),
            "vietnamese" to listOf("vietnamese"),
            "polish" to listOf("polish"),
            "dutch" to listOf("dutch"),
            "swedish" to listOf("swedish")
        )

        /** Words that mark a description line as a film/TV tie-in rather than an album. */
        private val FILM_MARKERS = listOf(
            "soundtrack", "original motion picture", "motion picture", "movie",
            "film", "ost", "bgm", "theme", "title track", "telefilm", "tv series"
        )

        /** Uploader names that are channels, not performers. */
        private val CHANNEL_SUFFIXES = listOf(
            "vevo", "records", "music", "official", "tv", "channel", "media", "audio",
            "lyrics", "entertainment", "productions", "studio", "songs", "hub"
        )

        /**
         * Genre / mood markers worth carrying into a follow-up query. Ordered by
         * specificity so "lo-fi" wins over "pop".
         */
        private val GENRE_HINTS: Map<String, List<String>> = linkedMapOf(
            "lofi" to listOf("lo-fi", "lofi", "chillhop"),
            "bollywood" to listOf("bollywood"),
            "hip hop" to listOf("hip hop", "hip-hop", "rap", "trap"),
            "rnb" to listOf("r&b", "rnb"),
            "rock" to listOf("rock", "metal", "punk"),
            "jazz" to listOf("jazz", "blues"),
            "classical" to listOf("classical", "orchestra", "instrumental"),
            "pop" to listOf("pop"),
            "folk" to listOf("folk", "acoustic"),
            "dance" to listOf("edm", "dance", "house music", "techno"),
            "romance" to listOf("love song", "romantic", "ballad"),
            "party" to listOf("party", "club", "dance"),
            "devotional" to listOf("devotional", "bhajan", "psalm", "worship", "sacred"),
            "workout" to listOf("workout", "gym", "fitness"),
            "sleep" to listOf("sleep", "relax", "calm", "ambient"),
            "focus" to listOf("focus", "study", "concentration")
        )

        const val MAX_GENRE_TOKENS = 2
    }

    /**
     * Genre tokens for a follow-up query, read from the real tag list rather than
     * guessed. Returns up to [MAX_GENRE_TOKENS] tokens, most specific first.
     */
    fun extractGenres(tags: List<String>, title: String = "", maxTokens: Int = MAX_GENRE_TOKENS): List<String> {
        val haystack = (tags + listOf(title)).joinToString(" ").lowercase()
        if (haystack.isBlank()) return emptyList()
        val found = ArrayList<String>(maxTokens)
        for ((genre, needles) in GENRE_HINTS) {
            if (found.size >= maxTokens) break
            if (needles.any { haystack.contains(it) }) found += genre
        }
        return found
    }

    /**
     * Single entry point shared by the lyrics path and the recommender so both
     * agree on what the track's title / artist / film / language actually are.
     */
    fun extract(info: StreamInfo, fallbackTitle: String = "", fallbackUploader: String = ""): TrackMetadata {
        val description = info.description?.content.orEmpty()
        val tags = info.tags?.map { it.toString() }.orEmpty()

        val provided = descriptionParser.parseProvidedToYouTubeDescription(description)
        val generic = descriptionParser.parseDescriptionMetadata(description)
        val picked = provided ?: generic

        val title = picked?.title?.takeIf { it.isNotBlank() } ?: info.name?.trim()
            ?: fallbackTitle.takeIf { it.isNotBlank() }
        val artist = picked?.singers?.takeIf { it.isNotBlank() } ?: info.uploaderName?.trim()
            ?: fallbackUploader.takeIf { it.isNotBlank() }

        val rawMovie = picked?.movie?.trim()
        val movie = rawMovie?.takeIf { it.isNotBlank() && !it.isPureBoilerplate() }
        val music = picked?.music?.trim()?.takeIf { it.isNotBlank() }

        val language = resolveLanguage(
            declared = picked?.language,
            title = title ?: fallbackTitle,
            tags = tags,
            uploader = artist ?: fallbackUploader,
            movie = movie
        )

        return TrackMetadata(
            title = title,
            artist = artist,
            movie = movie,
            music = music,
            language = language,
            isSoundtrack = movie != null && looksLikeFilm(movie)
        )
    }

    /**
     * Resolves a canonical lowercase language tag, or null when we genuinely do
     * not know. Never guesses: an invented language produces worse ordering than
     * admitting ignorance.
     */
    fun resolveLanguage(
        declared: String?,
        title: String = "",
        tags: List<String> = emptyList(),
        uploader: String = "",
        movie: String? = null
    ): String? {
        declared?.let { explicit ->
            normalizeLanguage(explicit)?.let { return it }
        }
        // A "Topic" uploader is the artist's official channel — its name is not a
        // language signal, so only consider channel-style names that are not.
        val haystacks = buildList {
            add(title)
            tags.forEach(::add)
            movie?.let(::add)
            if (uploader.isNotBlank() && !uploader.endsWith(" - Topic", ignoreCase = true)) add(uploader)
        }
        for (hint in LANGUAGE_HINTS) {
            val needles = hint.value
            if (haystacks.any { hay -> needles.any { needle -> hay.contains(needle, ignoreCase = true) } }) {
                return hint.key
            }
        }
        return null
    }

    /** Maps a loose language name ("Hindi", "hindi", "HINDI ") onto its canonical tag. */
    fun normalizeLanguage(raw: String): String? {
        val needle = raw.trim().lowercase()
        if (needle.isBlank()) return null
        for ((tag, hints) in LANGUAGE_HINTS) {
            if (hints.any { it == needle } || tag == needle) return tag
        }
        // "Hindi Language" / "Tamil Audio" style values.
        return LANGUAGE_HINTS.keys.firstOrNull { tag -> needle.startsWith(tag) }
    }

    fun looksLikeFilm(movie: String): Boolean {
        val lower = movie.lowercase()
        return FILM_MARKERS.any { lower.contains(it) }
    }

    /**
     * Metadata parsed from a raw description string, for callers that already have
     * the text in hand (e.g. the lyrics path).
     */
    fun resolveMetadata(description: String): TrackMetadata {
        val provided = descriptionParser.parseProvidedToYouTubeDescription(description)
        val generic = descriptionParser.parseDescriptionMetadata(description)
        val picked = provided ?: generic
        val rawMovie = picked?.movie?.trim()
        val movie = rawMovie?.takeIf { it.isNotBlank() && !it.isPureBoilerplate() }
        return TrackMetadata(
            title = picked?.title?.trim()?.takeIf { it.isNotBlank() },
            artist = picked?.singers?.trim()?.takeIf { it.isNotBlank() },
            movie = movie,
            music = picked?.music?.trim()?.takeIf { it.isNotBlank() },
            language = resolveLanguage(
                declared = picked?.language,
                title = picked?.title.orEmpty(),
                tags = emptyList(),
                movie = movie
            ),
            isSoundtrack = movie != null && looksLikeFilm(movie)
        )
    }

    /**
     * True for uploader names that are channels/brands rather than artists.
     * Used to decide whether a query is safe to build around a name.
     */
    fun isLikelyChannelName(uploader: String): Boolean {
        val lower = uploader.lowercase()
        return CHANNEL_SUFFIXES.any { lower.contains(it) }
    }

    /**
     * Query fragment that biases a search toward one language, e.g. "tamil".
     * Returns null when the language is unknown so we never inject noise.
     */
    fun languageQueryFragment(language: String?): String? {
        val canonical = normalizeLanguage(language ?: "") ?: return null
        // "english" is the YouTube default; adding it narrows results for no gain.
        return canonical.takeIf { it != "english" }
    }

    private fun String.isPureBoilerplate(): Boolean {
        val lower = lowercase()
        return lower.startsWith("℗") ||
            lower.startsWith("provided to youtube") ||
            lower.startsWith("auto-generated") ||
            lower.startsWith("released on") ||
            lower.startsWith("this song") ||
            lower.contains("auto-generated by youtube")
    }
}
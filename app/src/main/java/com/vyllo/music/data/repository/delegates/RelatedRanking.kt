package com.vyllo.music.data.repository.delegates

import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.normalizeArtistKey

/**
 * How strongly a candidate relates to the anchor track.
 *
 * The three tiers exist so Up Next (continuity) and the Related tab (discovery)
 * can order the SAME candidate pool differently instead of each surface running
 * its own unfiltered queries.
 */
enum class AffinityTier {
    /** Same performing artist. */
    SAME,

    /** Different artist that still matches the anchor's language, film or listener taste. */
    SIMILAR,

    /** Everything else — kept for exploration, deprioritised for the queue. */
    DISCOVERY
}

data class ScoredCandidate(
    val item: MusicItem,
    val score: Double,
    val tier: AffinityTier
)

/**
 * A search query plus the language it implies.
 *
 * The implied language is the cheap way to tag candidates: we cannot afford a
 * StreamInfo fetch per suggestion, but a `"tamil songs like X"` query returning
 * Tamil tracks is a language signal we already paid for.
 */
data class WeightedQuery(
    val query: String,
    val weight: Int,
    val impliedLanguage: String? = null
)

/**
 * Everything the scorer needs to know about the track the recommendations are
 * anchored on. [language] and [movie] come from the description metadata we
 * already parse for lyrics, so they cost no extra network round trip.
 */
data class RecommendationAnchor(
    val title: String,
    val uploader: String,
    val durationSecs: Long,
    val language: String? = null,
    val movie: String? = null
)

/**
 * Pure scoring / ordering logic for "similar songs".
 *
 * Deliberately free of Android, coroutines and the network so the ranking rules
 * can be unit tested directly.
 */
object RelatedRanking {

    /**
     * Taste bonus is applied *after* relevance and capped below the same-artist
     * bonus, so a heavily played but unrelated artist can never outrank the
     * current artist's own songs. See TASTE_BONUS_CAP below.
     */
    const val TASTE_BONUS_PER_PLAY = 0.05
    const val TASTE_PLAY_CAP = 20
    const val TASTE_BONUS_CAP = TASTE_BONUS_PER_PLAY * TASTE_PLAY_CAP

    // Relevance band. The lowest same-artist contribution (TITLE_MATCH) is 2.0,
    // which still exceeds TASTE_BONUS_CAP (1.0) — that is what keeps taste from
    // ever inverting relevance.
    const val SCORE_SAME_ARTIST = 4.0
    const val SCORE_PARTIAL_ARTIST = 3.0
    const val SCORE_TITLE_MATCH = 2.0
    const val SCORE_LANGUAGE_MATCH = 2.0
    const val SCORE_SOUNDTRACK_MATCH = 2.5
    const val SCORE_DURATION_CLOSE = 1.5
    const val SCORE_DURATION_NEAR = 0.75
    const val SCORE_VARIANT_DEMOTE = -1.0
    const val SCORE_LANGUAGE_MISMATCH = -1.5

    private val VARIANT_PATTERN = Regex(
        "\\b(lyrics?|lyric video|visualizer|official video|audio only|" +
            "live|cover|remix|sped|slowed|reverb|karaoke|instrumental|mashup|" +
            "reaction|karaoke|nightcore|8d audio|\\btabs\\b|tutorial|lesson)\\b",
        RegexOption.IGNORE_CASE
    )

    private val BRACKET_CONTENT_REGEX = Regex("[(\\[][^)\\]}]*[)\\]}]")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val SHORTS_PATH_REGEX = Regex("/(shorts|embed)/", RegexOption.IGNORE_CASE)
    private val TOPIC_SUFFIX_REGEX = Regex("\\s*-\\s*topic\\s*$", RegexOption.IGNORE_CASE)

    /** Variant words stripped when building a dedup key (bare or bracketed). */
    private val VARIANT_TOKENS_REGEX = Regex(
        "\\b(lyrics?|lyric\\s*video|visualizer|official\\s*(music\\s*)?(audio|video)|" +
            "audio\\s*only|official\\s*version|full\\s*audio|hd|hq|4k|remaster(ed)?|" +
            "live|cover|remix|sped(\\s*up)?|slowed(?:\\s*\\+reverb)?|reverb|nightcore|" +
            "karaoke|instrumental|mashup|reaction|8d\\s*audio)\\b",
        RegexOption.IGNORE_CASE
    )

    private val PUNCTUATION_REGEX = Regex("[^\\p{L}\\p{N}]+")

    /** Long-form uploads (podcasts, livestreams, mixes) and Shorts are not songs. */
    fun isAcceptableDuration(durationSecs: Long): Boolean =
        durationSecs == 0L || durationSecs in 30..900

    fun isShortsUrl(url: String): Boolean = SHORTS_PATH_REGEX.containsMatchIn(url)

    /** "ArtistVEVO - Topic" / "Artist - Topic" -> "ArtistVEVO". */
    fun cleanArtist(uploader: String): String =
        uploader.replace(TOPIC_SUFFIX_REGEX, "").trim()

    fun isVariantUpload(title: String): Boolean = VARIANT_PATTERN.containsMatchIn(title)

    /**
     * Normalized (title, uploader) key so different uploads of the same song
     * (official audio, lyric video, live, sped-up...) collapse into one entry.
     */
    fun normalizedTrackKey(item: MusicItem): String = normalizedTrackKey(item.title, item.uploader)

    /**
     * Same key from raw fields, for callers holding history rows rather than models.
     *
     * Both bracketed noise ("(Official Audio)") *and* bare variant words ("Lyrics",
     * "Live", "Remix") are stripped — uploader titles are inconsistent about which
     * form they use, and without both forms the same song appears twice in a
     * suggestion list under two keys.
     */
    fun normalizedTrackKey(title: String, uploader: String): String {
        val cleanTitle = VARIANT_TOKENS_REGEX
            .replace(title.lowercase().replace(BRACKET_CONTENT_REGEX, " "), " ")
            .replace(PUNCTUATION_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
        val cleanUploader = normalizeArtistKey(uploader)
            .replace(PUNCTUATION_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
        return "$cleanTitle|$cleanUploader"
    }

    fun sameArtistKey(uploader: String): String = normalizeArtistKey(uploader)

    /**
     * Candidate acceptance gate. Rejects playlists, the anchor itself, Shorts,
     * long-form uploads and variant uploads of the anchor track.
     */
    fun isAcceptable(
        item: MusicItem,
        anchorTitle: String,
        anchorUploader: String,
        anchorUrl: String
    ): Boolean {
        if (item.url.isBlank() || item.title.isBlank()) return false
        if (item.url == anchorUrl) return false
        if (isShortsUrl(item.url)) return false
        if (!isAcceptableDuration(item.durationSecs)) return false
        val isSameTrack = item.title.equals(anchorTitle, ignoreCase = true) &&
            item.uploader.equals(anchorUploader, ignoreCase = true)
        return !isSameTrack
    }

    /**
     * Relevance + capped taste. Call once per candidate — the pool merge is a
     * single linear pass, so no scoring is repeated.
     *
     * @param itemLanguage language implied for this candidate (by the query that
     *   surfaced it, or a learned uploader -> language mapping). Null when unknown.
     */
    fun score(
        item: MusicItem,
        anchor: RecommendationAnchor,
        itemLanguage: String?,
        tasteCount: Int
    ): Double {
        val uploaderKey = sameArtistKey(item.uploader)
        val anchorUploaderKey = sameArtistKey(anchor.uploader)
        val titleLower = item.title.lowercase()

        var relevance = 0.0

        // Artist affinity.
        if (anchorUploaderKey.isNotBlank() && uploaderKey == anchorUploaderKey) {
            relevance += SCORE_SAME_ARTIST
        } else if (anchorUploaderKey.isNotBlank() && uploaderKey.contains(anchorUploaderKey)) {
            relevance += SCORE_PARTIAL_ARTIST
        } else if (anchorUploaderKey.isNotBlank() && titleLower.contains(anchorUploaderKey)) {
            relevance += SCORE_TITLE_MATCH
        }

        // Language affinity — only meaningful when both sides are known.
        val anchorLang = anchor.language?.takeIf { it.isNotBlank() }
        val candidateLang = itemLanguage?.takeIf { it.isNotBlank() }
        if (anchorLang != null && candidateLang != null) {
            relevance += if (anchorLang == candidateLang) SCORE_LANGUAGE_MATCH else SCORE_LANGUAGE_MISMATCH
        }

        // Film / soundtrack affinity: for a Bollywood or film track, other songs
        // from the same movie are exactly what the listener wants next.
        val anchorMovie = anchor.movie?.takeIf { it.length >= MIN_MOVIE_LENGTH }
        if (anchorMovie != null) {
            val needle = anchorMovie.lowercase()
            if (titleLower.contains(needle) || item.uploader.lowercase().contains(needle)) {
                relevance += SCORE_SOUNDTRACK_MATCH
            }
        }

        // Duration proximity.
        if (anchor.durationSecs in 60..600 && item.durationSecs in 60..600) {
            val diff = kotlin.math.abs(item.durationSecs - anchor.durationSecs)
            if (diff < 30) relevance += SCORE_DURATION_CLOSE
            else if (diff < 90) relevance += SCORE_DURATION_NEAR
        }

        // Variant uploads (live/cover/remix/lyrics) are almost never the track
        // the listener wants queued next.
        if (isVariantUpload(item.title)) relevance += SCORE_VARIANT_DEMOTE

        // Taste last, and capped so it can only ever break ties inside a
        // relevance band — never outrank an actual same-artist match.
        val tasteBonus = (tasteCount.coerceIn(0, TASTE_PLAY_CAP) * TASTE_BONUS_PER_PLAY)
            .coerceAtMost(TASTE_BONUS_CAP)

        return relevance + tasteBonus
    }

    /**
     * Same artist -> SAME. Different artist that still shares the anchor's
     * language or film, or that the listener actually plays, -> SIMILAR.
     */
    fun tierOf(
        item: MusicItem,
        anchor: RecommendationAnchor,
        itemLanguage: String?,
        tasteCount: Int,
        score: Double
    ): AffinityTier {
        val anchorUploaderKey = sameArtistKey(anchor.uploader)
        val uploaderKey = sameArtistKey(item.uploader)
        val sameArtist = anchorUploaderKey.isNotBlank() && (
            uploaderKey == anchorUploaderKey ||
                uploaderKey.contains(anchorUploaderKey) ||
                item.title.lowercase().contains(anchorUploaderKey)
            )
        if (sameArtist) return AffinityTier.SAME

        val sharesLanguage = anchor.language != null && itemLanguage != null &&
            anchor.language == itemLanguage
        val sharesFilm = score >= SCORE_SOUNDTRACK_MATCH
        val isKnownArtist = tasteCount > 0

        return if (sharesLanguage || sharesFilm || isKnownArtist) {
            AffinityTier.SIMILAR
        } else {
            AffinityTier.DISCOVERY
        }
    }

    fun toCandidate(
        item: MusicItem,
        anchor: RecommendationAnchor,
        itemLanguage: String?,
        tasteCount: Int
    ): ScoredCandidate {
        val score = score(item, anchor, itemLanguage, tasteCount)
        return ScoredCandidate(
            item = item,
            score = score,
            tier = tierOf(item, anchor, itemLanguage, tasteCount, score)
        )
    }

    /**
     * Weaves [primary] and [discovery] together at a fixed ratio.
     *
     * Up Next uses (2, 1) so the queue stays inside the same language and artist;
     * the Related tab uses (1, 3) to surface new artists. Position 0 is always
     * from [primary] whenever it is non-empty.
     */
    fun interleave(
        primary: List<MusicItem>,
        discovery: List<MusicItem>,
        primaryRun: Int,
        discoveryRun: Int,
        limit: Int
    ): List<MusicItem> {
        if (limit <= 0 || (primary.isEmpty() && discovery.isEmpty())) return emptyList()
        val out = ArrayList<MusicItem>(minOf(limit, primary.size + discovery.size))
        var p = 0
        var d = 0
        while (out.size < limit && (p < primary.size || d < discovery.size)) {
            repeat(primaryRun.coerceAtLeast(0)) {
                if (out.size < limit && p < primary.size) out.add(primary[p++])
            }
            repeat(discoveryRun.coerceAtLeast(0)) {
                if (out.size < limit && d < discovery.size) out.add(discovery[d++])
            }
        }
        return out
    }

    /**
     * Single-pass merge of two score-descending lists.
     *
     * The pool stays sorted by score at all times, so expanding it is O(n) — the
     * previous implementation rescored the whole pool for every new candidate
     * (quadratic, with a regex run per comparison) on the queue-exhausted
     * autoplay path.
     */
    fun <T> mergeSortedDesc(
        existing: List<T>,
        incoming: List<T>,
        limit: Int,
        scoreOf: (T) -> Double
    ): List<T> {
        if (limit <= 0) return emptyList()
        if (incoming.isEmpty()) return existing.take(limit)
        if (existing.isEmpty()) return incoming.sortedByDescending(scoreOf).take(limit)

        val sortedIncoming = incoming.sortedByDescending(scoreOf)
        val out = ArrayList<T>(minOf(limit, existing.size + sortedIncoming.size))
        var i = 0
        var j = 0
        while (out.size < limit && (i < existing.size || j < sortedIncoming.size)) {
            val takeExisting = when {
                j >= sortedIncoming.size -> true
                i >= existing.size -> false
                else -> scoreOf(existing[i]) >= scoreOf(sortedIncoming[j])
            }
            if (takeExisting) out.add(existing[i++]) else out.add(sortedIncoming[j++])
        }
        return out
    }

    /**
     * Caps how many suggestions a single artist may occupy so a feed stays
     * diverse instead of being flooded by one artist's search results.
     */
    fun capPerArtist(items: List<MusicItem>, maxPerArtist: Int): List<MusicItem> {
        if (maxPerArtist <= 0) return emptyList()
        val counts = HashMap<String, Int>()
        return items.filter { item ->
            val key = sameArtistKey(item.uploader)
            val current = counts[key] ?: 0
            if (current < maxPerArtist) {
                counts[key] = current + 1
                true
            } else {
                false
            }
        }
    }

    private const val MIN_MOVIE_LENGTH = 3

    // ---------------------------------------------------------------------
    // Query ladder
    // ---------------------------------------------------------------------

    /** Number of queries fired for a freshly anchored pool. */
    const val INITIAL_QUERY_COUNT = 4

    /** Extra queries fired per expansion round. */
    const val EXPANSION_QUERIES_PER_ROUND = 2

    /**
     * Deterministic, ranked ladder of searches for a track.
     *
     * Deliberately not shuffled: the previous implementation picked a random 4
     * of 7 candidates and then cached the result for 15 minutes, so the same
     * track produced a different mix every session. Only the pool *ordering*
     * varies (via the taste bonus), never the query set.
     *
     * Note the deliberate absence of "songs like \<title\>": YouTube's
     * "songs like" semantic resolves artist names, not song titles — feeding it
     * a title returns covers and compilation uploads.
     */
    fun buildQueryLadder(
        artist: String,
        language: String? = null,
        movie: String? = null,
        isSoundtrack: Boolean = false,
        genres: List<String> = emptyList(),
        languageFragment: String? = null
    ): List<WeightedQuery> {
        val cleanArtist = cleanArtist(artist).trim()
        val cleanMovie = movie?.trim()?.takeIf { it.isNotBlank() && !isTooGeneric(it) }
        // Two separate things, deliberately not interchangeable:
        //  - [language] is the canonical tag, always attached as `impliedLanguage`
        //    so the scorer can compare languages.
        //  - [languageFragment] is only present when the language actually narrows a
        //    query (see TrackMetadataResolver.languageQueryFragment, which drops
        //    "english" as the YouTube default). Never build query text from [language].
        val lang = language
        val frag = languageFragment
        val out = ArrayList<WeightedQuery>(8)

        if (isSoundtrack && cleanMovie != null) {
            // Above the generic "<artist> radio" query: for a film track, other songs
            // from the same movie are a stronger match than the artist's catalogue.
            out += WeightedQuery("$cleanMovie soundtrack songs", weight = 104, impliedLanguage = lang)
            out += WeightedQuery("$cleanMovie all songs", weight = 90, impliedLanguage = lang)
        }

        if (cleanArtist.isNotBlank()) {
            out += WeightedQuery("$cleanArtist radio", weight = 100, impliedLanguage = lang)
            out += WeightedQuery("$cleanArtist top songs", weight = 92, impliedLanguage = lang)
            out += WeightedQuery("artists similar to $cleanArtist", weight = 86, impliedLanguage = lang)
            out += WeightedQuery("songs like $cleanArtist", weight = 80, impliedLanguage = lang)
            if (frag != null) {
                out += WeightedQuery("$cleanArtist $frag songs", weight = 78, impliedLanguage = lang)
            }
            genres.firstOrNull()?.let { genre ->
                out += WeightedQuery("$cleanArtist $genre songs", weight = 74, impliedLanguage = lang)
            }
            out += WeightedQuery("best of $cleanArtist and similar artists", weight = 70, impliedLanguage = lang)
        }

        if (cleanMovie != null && !isSoundtrack) {
            out += WeightedQuery("$cleanMovie songs", weight = 66, impliedLanguage = lang)
        }
        if (frag != null && cleanArtist.isBlank()) {
            out += WeightedQuery("$frag songs popular", weight = 60, impliedLanguage = lang)
        }
        if (out.isEmpty()) {
            out += WeightedQuery("trending music", weight = 10)
        }
        return out.sortedByDescending { it.weight }
    }

    /** Queries for expansion rounds, drawn from the tail of the same ladder. */
    fun buildExpansionLadder(
        ladder: List<WeightedQuery>,
        round: Int,
        perRound: Int = EXPANSION_QUERIES_PER_ROUND
    ): List<WeightedQuery> {
        if (ladder.isEmpty()) return emptyList()
        val start = (round * perRound).coerceAtMost(ladder.size)
        return ladder.drop(start).take(perRound)
    }

    /** Query set for the "More from {artist}" section. */
    fun buildArtistLadder(
        artist: String,
        language: String? = null,
        languageFragment: String? = null,
        genres: List<String> = emptyList()
    ): List<WeightedQuery> {
        val cleanArtist = cleanArtist(artist).trim()
        if (cleanArtist.isBlank()) return emptyList()
        val lang = language
        val out = ArrayList<WeightedQuery>(3)
        out += WeightedQuery("$cleanArtist songs", weight = 100, impliedLanguage = lang)
        out += WeightedQuery("$cleanArtist top songs tracks", weight = 90, impliedLanguage = lang)
        genres.firstOrNull()?.let { out += WeightedQuery("$cleanArtist $it songs", weight = 80, impliedLanguage = lang) }
        return out
    }

    /**
     * Query set for "You Might Also Like".
     *
     * Every query is artist-anchored on purpose. A track *title* is not a useful
     * seed for discovery, and "trending music similar to X" is not a query shape
     * YouTube understands — it returns unrelated mainstream hits, which is the
     * opposite of what a "similar to this track" section should show.
     */
    fun buildSimilarArtistsLadder(
        title: String,
        artist: String,
        language: String? = null,
        movie: String? = null,
        isSoundtrack: Boolean = false,
        genres: List<String> = emptyList(),
        languageFragment: String? = null
    ): List<WeightedQuery> {
        val cleanArtist = cleanArtist(artist).trim()
        val cleanMovie = movie?.trim()?.takeIf { it.isNotBlank() && !isTooGeneric(it) }
        val lang = language
        val frag = languageFragment
        val out = ArrayList<WeightedQuery>(5)

        if (cleanArtist.isNotBlank()) {
            out += WeightedQuery("artists similar to $cleanArtist", weight = 100, impliedLanguage = lang)
            out += WeightedQuery("songs like $cleanArtist", weight = 92, impliedLanguage = lang)
            out += WeightedQuery("$cleanArtist similar artists music", weight = 84, impliedLanguage = lang)
            if (frag != null) {
                out += WeightedQuery("$frag songs like $cleanArtist", weight = 82, impliedLanguage = lang)
            }
            genres.firstOrNull()?.let { out += WeightedQuery("$cleanArtist $it similar songs", weight = 76, impliedLanguage = lang) }
        }
        if (isSoundtrack && cleanMovie != null) {
            out += WeightedQuery("$cleanMovie soundtrack other songs", weight = 90, impliedLanguage = lang)
        }
        if (out.isEmpty()) {
            // No usable artist name: fall back to the title, but as a plain
            // search rather than the broken "songs like <title>" shape.
            val cleanTitle = title.trim()
            if (cleanTitle.isNotBlank()) {
                out += WeightedQuery(cleanTitle, weight = 40)
            }
        }
        return out.sortedByDescending { it.weight }
    }

    /** Rejects album/company names that would make a useless query. */
    private fun isTooGeneric(movie: String): Boolean {
        val lower = movie.lowercase()
        return lower.length < 4 ||
            lower == "various" ||
            lower.contains("official audio") ||
            lower.contains("single") ||
            lower.contains("album version")
    }
}
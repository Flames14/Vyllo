package com.vyllo.music.recognition.domain

import com.vyllo.music.domain.model.MusicItem

/**
 * Ranks YouTube search candidates against a Shazam recognition result.
 *
 * Blindly playing the top search hit is wrong surprisingly often — especially
 * for regional catalogues where reactions, covers and lyric videos outrank
 * the official upload. Token overlap on title+artist picks the real track;
 * known-derivative markers only ever subtract.
 */
object CandidateScorer {

    private val stopwords = setOf(
        "the", "a", "an", "official", "video", "audio", "song", "music",
        "lyrics", "lyric", "hd", "hq", "4k", "ft", "feat", "featuring",
        "prod", "presents", "original", "version", "full", "new"
    )

    /** Derivative markers and their score penalties. */
    private val penalties = mapOf(
        "reaction" to 30, "reacts" to 30, "react" to 25,
        "cover" to 25, "covered" to 25, "karaoke" to 30,
        "sped up" to 20, "spedup" to 20, "slowed" to 20, "reverb" to 15,
        "nightcore" to 25, "8d audio" to 15, "8d" to 10,
        "1 hour" to 30, "1hour" to 30, "loop" to 15, "looped" to 15,
        "mashup" to 25, "parody" to 30, "tiktok" to 10, "shorts" to 10,
        "trailer" to 20, "teaser" to 15, "interview" to 20,
        "instrumental" to 10, "acapella" to 10
    )

    private const val TITLE_HIT = 10
    private const val ARTIST_HIT = 15
    private const val OFFICIAL_BONUS = 8

    fun normalize(text: String): Set<String> {
        return text.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 1 && it !in stopwords }
            .toSet()
    }

    fun score(candidate: MusicItem, title: String, artist: String): Int {
        val titleTokens = normalize(title)
        val artistTokens = normalize(artist)
        val candidateTokens = normalize(candidate.title)
        val candidateUploader = candidate.uploader.lowercase()

        var points = 0
        points += titleTokens.intersect(candidateTokens).size * TITLE_HIT
        points += artistTokens.count { candidateUploader.contains(it) } * ARTIST_HIT

        val lowered = "${candidate.title.lowercase()} ${candidate.uploader.lowercase()}"
        if ("official" in lowered) points += OFFICIAL_BONUS
        for ((word, penalty) in penalties) {
            if (word in lowered) points -= penalty
        }
        return points
    }

    /**
     * Returns the best-matching candidate, or null when the list is empty.
     * Ties keep the earliest (highest-ranked) candidate.
     */
    fun best(candidates: List<MusicItem>, title: String, artist: String): MusicItem? {
        if (candidates.isEmpty()) return null
        var winner = candidates[0]
        var winnerScore = score(winner, title, artist)
        for (i in 1 until candidates.size) {
            val s = score(candidates[i], title, artist)
            if (s > winnerScore) {
                winner = candidates[i]
                winnerScore = s
            }
        }
        return winner
    }
}

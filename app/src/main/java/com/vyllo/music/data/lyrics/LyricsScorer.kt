package com.vyllo.music.data.lyrics

import com.vyllo.music.domain.model.LyricsResult

/**
 * Calculates string similarity metrics between two strings.
 * Uses Levenshtein distance (2-row optimization), token sort, and token set ratios.
 */
object LyricsScorer {

    /**
     * Normalized Levenshtein similarity (0.0 to 1.0).
     * Uses only 2 rows to minimize allocations.
     */
    fun levenshteinSimilarity(a: String, b: String): Double {
        val s1 = a.lowercase().trim()
        val s2 = b.lowercase().trim()
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        val maxLen = maxOf(s1.length, s2.length)
        if (kotlin.math.abs(s1.length - s2.length) > maxLen * 0.5) return 0.0
        var prev = IntArray(s2.length + 1) { it }
        var curr = IntArray(s2.length + 1)
        for (i in 1..s1.length) {
            curr[0] = i
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
            }
            prev = curr.also { curr = prev }
        }
        return 1.0 - prev[s2.length].toDouble() / maxLen.toDouble()
    }

    /**
     * Token Sort Ratio: sorts tokens alphabetically then compares.
     * Handles reordered words: "A ft B" vs "B feat A".
     */
    fun tokenSortRatio(a: String, b: String): Double {
        val s1 = tokenizeAndSort(a)
        val s2 = tokenizeAndSort(b)
        return levenshteinSimilarity(s1, s2)
    }

    /**
     * Token Set Ratio: robust to substrings and extra words.
     * Inspired by FuzzyWuzzy's token_set_ratio.
     */
    fun tokenSetRatio(a: String, b: String): Double {
        val tokensA = tokenize(a)
        val tokensB = tokenize(b)
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0

        val intersection = tokensA.intersect(tokensB).sorted().joinToString(" ")
        val diffA = (tokensA - tokensB).sorted().joinToString(" ")
        val diffB = (tokensB - tokensA).sorted().joinToString(" ")

        if (diffA.isEmpty() && diffB.isEmpty() && intersection.isNotEmpty()) return 1.0

        val combined1 = if (intersection.isNotEmpty() && diffA.isNotEmpty()) "$intersection $diffA" else (intersection.ifEmpty { diffA })
        val combined2 = if (intersection.isNotEmpty() && diffB.isNotEmpty()) "$intersection $diffB" else (intersection.ifEmpty { diffB })

        val scores = mutableListOf<Double>()
        if (intersection.isNotEmpty()) {
            scores.add(levenshteinSimilarity(intersection, combined1))
            scores.add(levenshteinSimilarity(intersection, combined2))
        }
        scores.add(levenshteinSimilarity(combined1, combined2))
        return scores.maxOrNull() ?: 0.0
    }

    private fun tokenize(text: String): Set<String> =
        text.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .toSet()

    private fun tokenizeAndSort(text: String): String =
        text.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .sorted()
            .joinToString(" ")

    /**
     * Extracts meaningful keywords (words longer than 2 chars).
     */
    fun extractKeywords(text: String): Set<String> =
        text.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 }
            .toSet()

    /**
     * Extracts core title by stripping parenthetical/bracketed suffixes.
     * E.g. "Monica (From \"Coolie\") (Tamil)" → "Monica"
     */
    fun extractCoreTitle(trackName: String): String =
        trackName.replace(Regex("\\s*[\\(\\[].*"), "").trim()

    /**
     * Full multi-factor score for a candidate.
     * Returns 0.0 to 1.0.
     */
    fun scoreCandidate(
        candidate: LyricsResult,
        inputTitle: String,
        titleKeywords: Set<String>,
        inputArtist: String,
        artistKeywords: Set<String>,
        featuredArtists: List<String>,
        durationSecs: Long,
        movieOrAlbum: String? = null
    ): Double {
        val titleScore = scoreTitleMatch(candidate.trackName, inputTitle, titleKeywords)
        val artistScore = scoreArtistMatch(candidate.artistName, inputArtist, artistKeywords, featuredArtists)
        val durationScore = scoreDuration(candidate.duration, durationSecs)
        val lyricsScore = scoreLyricsAvailability(candidate)
        val ctxBonus = contextBonus(candidate, inputArtist, movieOrAlbum)

        val score = if (artistScore < 0.15 && titleScore >= 0.6) {
            // Regional music: shift weight to title + context
            (titleScore * 0.50) + (artistScore * 0.05) + (durationScore * 0.25) + (lyricsScore * 0.10) + ctxBonus
        } else {
            (titleScore * 0.40) + (artistScore * 0.25) + (durationScore * 0.25) + (lyricsScore * 0.10) + ctxBonus
        }
        return minOf(score, 1.0)
    }

    private fun scoreTitleMatch(candidateTrackName: String, inputTitle: String, titleKeywords: Set<String>): Double {
        val candLower = candidateTrackName.lowercase().trim()
        val inputLower = inputTitle.lowercase().trim()
        if (candLower == inputLower) return 1.0

        val coreTitle = extractCoreTitle(candLower)
        val coreMatch = if (coreTitle.isNotBlank() && coreTitle == inputLower) 0.95 else 0.0
        val tokenSetScore = tokenSetRatio(coreTitle, inputLower)
        val tokenSortScore = tokenSortRatio(coreTitle, inputLower)
        val setScoreRaw = tokenSetRatio(candLower, inputLower)
        val sortScoreRaw = tokenSortRatio(candLower, inputLower)

        return maxOf(coreMatch, tokenSetScore, tokenSortScore, setScoreRaw, sortScoreRaw)
    }

    private fun scoreArtistMatch(
        candidateArtistName: String,
        inputArtist: String,
        artistKeywords: Set<String>,
        featuredArtists: List<String>
    ): Double {
        val candLower = candidateArtistName.lowercase().trim()
        val inputLower = inputArtist.lowercase().trim()
        if (candLower == inputLower) return 1.0

        val tokenSetScore = tokenSetRatio(candLower, inputLower)
        val tokenSortScore = tokenSortRatio(candLower, inputLower)

        var featScore = 0.0
        if (featuredArtists.isNotEmpty()) {
            val allArtists = (listOf(inputArtist) + featuredArtists).joinToString(" ")
            featScore = maxOf(
                tokenSetRatio(candLower, allArtists.lowercase()),
                tokenSortRatio(candLower, allArtists.lowercase())
            )
        }
        return maxOf(tokenSetScore, tokenSortScore, featScore)
    }

    private fun scoreDuration(candidateDuration: Long, inputDurationSecs: Long): Double {
        if (inputDurationSecs <= 0 || candidateDuration <= 0) return 0.3
        val diff = kotlin.math.abs(candidateDuration - inputDurationSecs)
        return when {
            diff <= 2  -> 1.0
            diff <= 5  -> 0.9
            diff <= 10 -> 0.7
            diff <= 20 -> 0.4
            diff <= 30 -> 0.2
            else       -> 0.0
        }
    }

    private fun scoreLyricsAvailability(candidate: LyricsResult): Double = when {
        candidate.syncedLyrics != null -> 1.0
        candidate.plainLyrics != null  -> 0.5
        candidate.instrumental         -> 0.1
        else                           -> 0.0
    }

    private fun contextBonus(candidate: LyricsResult, inputArtist: String, movieOrAlbum: String?): Double {
        val artistLower = inputArtist.lowercase().trim()
        val albumLower = (candidate.albumName ?: "").lowercase().trim()
        val trackLower = candidate.trackName.lowercase().trim()
        var bonus = 0.0

        if (artistLower.isNotBlank() && artistLower.length > 2) {
            if (albumLower.contains(artistLower) || artistLower.contains(albumLower)) {
                bonus += 0.20
            } else if (trackLower.contains(artistLower)) {
                bonus += 0.15
            }
        }

        if (movieOrAlbum != null) {
            val movieLower = movieOrAlbum.lowercase().trim()
            if (movieLower.isNotBlank()) {
                if (albumLower.contains(movieLower) || movieLower.contains(albumLower)) {
                    bonus += 0.15
                } else if (trackLower.contains(movieLower)) {
                    bonus += 0.10
                }
            }
        }
        return minOf(bonus, 0.25)
    }
}

package com.vyllo.music.data

import com.vyllo.music.BuildConfig
import com.vyllo.music.core.security.SecureLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

// --- Data Models ---

data class SyncedLyricLine(
    val startTimeMs: Long,
    val content: String
)

data class LyricsResult(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val duration: Long,
    val instrumental: Boolean,
    val plainLyrics: String?,
    val syncedLyrics: String?
)

data class LyricsStatus(
    val hasPlain: Boolean,
    val hasSynced: Boolean,
    val isInstrumental: Boolean
)

data class LyricsResponse(
    val success: Boolean,
    val result: LyricsResult? = null,
    val results: List<LyricsResult> = emptyList(),
    val strategy: String = "",
    val plainLyrics: String? = null,
    val syncedLines: List<SyncedLyricLine>? = null,
    val languages: List<String>? = null,
    val lyricsStatus: LyricsStatus? = null,
    val error: String? = null
)

object LyricsEngine {

    private const val TAG = "LyricsEngine"

    private val client = OkHttpClient.Builder()
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // Security: Use BuildConfig for API URLs (not hardcoded in source)
    private const val API_BASE = BuildConfig.LYRICS_API_BASE
    private const val NETEASE_SEARCH_API = BuildConfig.NETEASE_SEARCH_API
    private const val NETEASE_LYRIC_API = BuildConfig.NETEASE_LYRIC_API
    private const val CLIENT_HEADER = "Vyllo-Universal"

    // Minimum score to accept a candidate as valid.
    // Lower threshold to accommodate regional music where artist metadata rarely matches.
    private const val MIN_CONFIDENCE_SCORE = 0.25

    // If a candidate scores at or above this, stop searching immediately
    private const val HIGH_CONFIDENCE_THRESHOLD = 0.85

    // --- Language Detection ---

    private val LANGUAGE_PATTERNS = mapOf(
        "tamil" to Regex("[\\u0B80-\\u0BFF]"),
        "hindi" to Regex("[\\u0900-\\u097F]"),
        "telugu" to Regex("[\\u0C00-\\u0C7F]"),
        "malayalam" to Regex("[\\u0D00-\\u0D7F]"),
        "korean" to Regex("[\\uAC00-\\uD7AF]"),
        "japanese" to Regex("[\\u3040-\\u30FF]"),
        "chinese" to Regex("[\\u4E00-\\u9FFF]"),
        "arabic" to Regex("[\\u0600-\\u06FF]"),
        "russian" to Regex("[\\u0400-\\u04FF]")
    )

    fun detectLanguage(text: String): List<String> {
        if (text.isBlank()) return listOf("english")
        val detected = mutableListOf<String>()
        for ((language, pattern) in LANGUAGE_PATTERNS) {
            if (pattern.containsMatchIn(text)) {
                detected.add(language)
            }
        }
        return if (detected.isNotEmpty()) detected else listOf("english")
    }

    // --- LRC Parsing ---

    fun parseSyncedLyrics(lrcString: String?): List<SyncedLyricLine> {
        if (lrcString.isNullOrBlank()) return emptyList()
        val lines = mutableListOf<SyncedLyricLine>()
        // More robust regex for various LRC formats
        // [mm:ss.xx], [mm:ss:xx], [mm:ss.xxx], [mm:ss]
        val regex = Regex("\\[(\\d{1,3}):(\\d{2})(?:[\\.\\:](\\d{2,3}))?\\]\\s*(.*)")

        lrcString.lines().forEach { line ->
            val matchResult = regex.find(line)
            if (matchResult != null) {
                try {
                    val minutes = matchResult.groupValues[1].toLong()
                    val seconds = matchResult.groupValues[2].toLong()
                    val fractionStr = matchResult.groupValues[3]
                    val fractionMs = when (fractionStr.length) {
                        2 -> fractionStr.toLong() * 10
                        3 -> fractionStr.toLong()
                        0 -> 0L
                        else -> fractionStr.take(3).toLong()
                    }

                    val text = matchResult.groupValues[4].trim()
                    if (text.isNotBlank() || lines.isNotEmpty()) { // Allow empty lines for pauses if not first
                        val timeMs = (minutes * 60 + seconds) * 1000 + fractionMs
                        lines.add(SyncedLyricLine(timeMs, text))
                    }
                } catch (e: Exception) {
                    // Skip malformed lines
                }
            }
        }
        return lines.sortedBy { it.startTimeMs }
    }

    fun getCurrentLyricLine(syncedLyrics: List<SyncedLyricLine>?, currentTimeMs: Long): Int {
        if (syncedLyrics.isNullOrEmpty()) return -1
        for (i in syncedLyrics.indices.reversed()) {
            if (syncedLyrics[i].startTimeMs <= currentTimeMs + 50) { // Reduced offset for 50ms sync
                return i
            }
        }
        return -1
    }

    // --- Normalized Levenshtein Similarity ---

    /**
     * Calculates normalized Levenshtein similarity between two strings.
     * Returns a value between 0.0 (completely different) and 1.0 (identical).
     */
    private fun levenshteinSimilarity(a: String, b: String): Double {
        val s1 = a.lowercase().trim()
        val s2 = b.lowercase().trim()

        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val maxLen = maxOf(s1.length, s2.length)

        // Optimization: if length difference is huge, similarity will be very low
        if (Math.abs(s1.length - s2.length) > maxLen * 0.5) return 0.0

        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,       // deletion
                    dp[i][j - 1] + 1,       // insertion
                    dp[i - 1][j - 1] + cost  // substitution
                )
            }
        }

        val distance = dp[s1.length][s2.length]
        return 1.0 - (distance.toDouble() / maxLen.toDouble())
    }

    // --- Metadata Cleanup ---

    /**
     * Holds the cleaned text plus any extracted featured artists and movie/album context.
     */
    private data class CleanedMetadata(
        val cleanText: String,
        val featuredArtists: List<String> = emptyList(),
        val movieOrAlbum: String? = null
    )

    /**
     * Cleans metadata text, extracting featured artists and movie names
     * instead of blindly stripping them.
     */
    private fun cleanMetadataAdvanced(text: String): CleanedMetadata {
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

        // Extract movie/album name from patterns like (From "Movie Name") or [From Movie]
        val movieRegex = Regex("(?i)\\(?(?:from|ost|soundtrack)\\s*[\"']?([^\"'\\)\\]]+)[\"']?\\)?")
        movieRegex.find(working)?.let { match ->
            val name = match.groupValues[1].trim()
            if (name.isNotBlank() && name.length > 2) {
                movieOrAlbum = name
            }
        }

        // Now clean the text for search queries
        working = working
            .replace(Regex("([a-z])([A-Z])"), "$1 $2") // CamelCase split
            // Remove video/audio tags
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
            // Remove from/ost/soundtrack tags
            .replace(Regex("(?i)\\(.*?(?:from|ost|soundtrack).*?\\)"), "")
            .replace(Regex("(?i)\\[.*?(?:from|ost|soundtrack).*?\\]"), "")
            // Remove ft/feat and everything after
            .replace(Regex("(?i)\\s*(?:ft\\.?|feat\\.?)\\s*.+"), "")
            // Common junk
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

    // Simple clean for backward compat within query building
    private fun cleanMetadataPart(text: String): String {
        return cleanMetadataAdvanced(text).cleanText
    }

    private fun parseResult(json: JSONObject): LyricsResult {
        return LyricsResult(
            id = json.optLong("id", 0L),
            trackName = json.optString("trackName", ""),
            artistName = json.optString("artistName", ""),
            albumName = json.optString("albumName", null),
            duration = json.optDouble("duration", 0.0).toLong(),
            instrumental = json.optBoolean("instrumental", false),
            plainLyrics = json.optString("plainLyrics", null).takeIf { it?.isNotBlank() == true },
            syncedLyrics = json.optString("syncedLyrics", null).takeIf { it?.isNotBlank() == true }
        )
    }

    private fun buildResponse(
        strategy: String,
        result: LyricsResult? = null,
        results: List<LyricsResult> = emptyList()
    ): LyricsResponse {
        val bestResult = result ?: results.firstOrNull()

        if (bestResult == null) {
            return LyricsResponse(success = false, strategy = strategy, error = "No lyrics found")
        }

        val plain = bestResult.plainLyrics
        val synced = bestResult.syncedLyrics
        val isInst = bestResult.instrumental

        val parsedLines = parseSyncedLyrics(synced)

        val detectText = synced ?: plain ?: ""
        val langs = detectLanguage(detectText)

        return LyricsResponse(
            success = true,
            strategy = strategy,
            result = bestResult,
            results = results.ifEmpty { listOf(bestResult) },
            plainLyrics = plain,
            syncedLines = parsedLines.takeIf { it.isNotEmpty() },
            languages = langs,
            lyricsStatus = LyricsStatus(
                hasPlain = plain != null,
                hasSynced = synced != null,
                isInstrumental = isInst
            )
        )
    }

    // --- Improved Multi-Factor Scoring Engine ---

    /**
     * Extracts meaningful keywords from a string for scoring purposes.
     * Lowercases, removes very short words (1-2 chars) unless they look meaningful.
     */
    private fun extractKeywords(text: String): Set<String> {
        return text.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 }
            .toSet()
    }

    /**
     * Extracts the core title from an LRCLIB track name by stripping
     * parenthetical/bracketed suffixes like (From "Coolie") (Tamil).
     * E.g. "Monica (From \"Coolie\") (Tamil)" → "Monica"
     */
    private fun extractCoreTitle(trackName: String): String {
        // Strip everything from the first ( or [ onwards
        return trackName
            .replace(Regex("\\s*[\\(\\[].*"), "")
            .trim()
    }

    /**
     * Token Sort Ratio: splits words, sorts them alphabetically,
     * joins them, and then computes Levenshtein distance.
     * Effectively handles "Artist A ft. Artist B" vs "Artist B feat Artist A".
     */
    private fun tokenSortRatio(a: String, b: String): Double {
        val s1 = a.lowercase().replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").split(Regex("\\s+")).filter { it.isNotBlank() }.sorted().joinToString(" ")
        val s2 = b.lowercase().replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").split(Regex("\\s+")).filter { it.isNotBlank() }.sorted().joinToString(" ")
        return levenshteinSimilarity(s1, s2)
    }

    /**
     * Token Set Ratio: robust to substrings and extra words.
     * Finds the intersection set of words between two strings,
     * and constructs combinations to score using Levenshtein.
     * Inspired by FuzzyWuzzy's token_set_ratio.
     */
    private fun tokenSetRatio(a: String, b: String): Double {
        val tokensA = a.lowercase().replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()
        val tokensB = b.lowercase().replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()

        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0

        val intersection = tokensA.intersect(tokensB).sorted().joinToString(" ")
        val diffA = (tokensA - tokensB).sorted().joinToString(" ")
        val diffB = (tokensB - tokensA).sorted().joinToString(" ")

        // If all tokens match, perfect score
        if (diffA.isEmpty() && diffB.isEmpty() && intersection.isNotEmpty()) return 1.0

        val combined1 = if (intersection.isNotEmpty() && diffA.isNotEmpty()) "$intersection $diffA" else (intersection.ifEmpty { diffA })
        val combined2 = if (intersection.isNotEmpty() && diffB.isNotEmpty()) "$intersection $diffB" else (intersection.ifEmpty { diffB })

        // Compare intersection against each combined string
        val scores = mutableListOf<Double>()
        if (intersection.isNotEmpty()) {
            scores.add(levenshteinSimilarity(intersection, combined1))
            scores.add(levenshteinSimilarity(intersection, combined2))
        }
        scores.add(levenshteinSimilarity(combined1, combined2))

        return scores.maxOrNull() ?: 0.0
    }

    /**
     * Scores how well a candidate's title matches the input title.
     * Uses multiple strategies: exact match, core title match, token set ratio.
     * Returns 0.0 to 1.0.
     */
    private fun scoreTitleMatch(
        candidateTrackName: String,
        inputTitle: String,
        titleKeywords: Set<String>
    ): Double {
        val candLower = candidateTrackName.lowercase().trim()
        val inputLower = inputTitle.lowercase().trim()

        // Exact match
        if (candLower == inputLower) return 1.0

        val coreTitle = extractCoreTitle(candLower)
        val coreMatch = if (coreTitle.isNotBlank() && coreTitle == inputLower) 0.95 else 0.0

        val tokenSetScore = tokenSetRatio(coreTitle, inputLower)
        val tokenSortScore = tokenSortRatio(coreTitle, inputLower)

        val setScoreRaw = tokenSetRatio(candLower, inputLower)
        val sortScoreRaw = tokenSortRatio(candLower, inputLower)

        return maxOf(coreMatch, tokenSetScore, tokenSortScore, setScoreRaw, sortScoreRaw)
    }

    /**
     * Scores how well a candidate's artist matches the input artist.
     * Returns 0.0 to 1.0.
     */
    private fun scoreArtistMatch(
        candidateArtistName: String,
        inputArtist: String,
        artistKeywords: Set<String>,
        featuredArtists: List<String>
    ): Double {
        val candLower = candidateArtistName.lowercase().trim()
        val inputLower = inputArtist.lowercase().trim()

        // Exact match
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

    /**
     * Scores how close the durations are.
     * Returns 0.0 to 1.0.
     */
    private fun scoreDuration(candidateDuration: Long, inputDurationSecs: Long): Double {
        if (inputDurationSecs <= 0 || candidateDuration <= 0) return 0.3 // Unknown → neutral

        val diff = Math.abs(candidateDuration - inputDurationSecs)
        return when {
            diff <= 2  -> 1.0
            diff <= 5  -> 0.9
            diff <= 10 -> 0.7
            diff <= 20 -> 0.4
            diff <= 30 -> 0.2
            else       -> 0.0
        }
    }

    /**
     * Scores lyrics availability.
     * Returns 0.0 to 1.0.
     */
    private fun scoreLyricsAvailability(candidate: LyricsResult): Double {
        return when {
            candidate.syncedLyrics != null -> 1.0
            candidate.plainLyrics != null  -> 0.5
            candidate.instrumental         -> 0.1 // instrumental is valid but less useful
            else                           -> 0.0
        }
    }

    /**
     * Checks if the YouTube 'artist' (often a movie/label for regional music)
     * appears in the candidate's album name or track name suffix.
     * Returns a bonus score 0.0 to 0.25.
     */
    private fun contextBonus(
        candidate: LyricsResult,
        inputArtist: String,
        movieOrAlbum: String?
    ): Double {
        val artistLower = inputArtist.lowercase().trim()
        val albumLower = (candidate.albumName ?: "").lowercase().trim()
        val trackLower = candidate.trackName.lowercase().trim()
        var bonus = 0.0

        // Check if YouTube artist name appears in candidate album or track
        // e.g., YouTube artist = "Coolie", candidate album = "Coolie" → strong signal
        if (artistLower.isNotBlank() && artistLower.length > 2) {
            if (albumLower.contains(artistLower) || artistLower.contains(albumLower)) {
                bonus += 0.20
            } else if (trackLower.contains(artistLower)) {
                bonus += 0.15
            }
        }

        // Check movie/album context from title metadata
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

        return minOf(bonus, 0.25) // Cap at 0.25
    }

    /**
     * Full multi-factor scoring of a candidate.
     */
    private fun scoreCandidate(
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

        // Dynamic weight adjustment:
        // If artist score is very low (< 0.15) but title is strong (>= 0.6),
        // this is likely a regional music pattern. Shift weight to title + context.
        val score = if (artistScore < 0.15 && titleScore >= 0.6) {
            // Reduced artist influence, boosted title and context
            (titleScore * 0.50) + (artistScore * 0.05) + (durationScore * 0.25) + (lyricsScore * 0.10) + ctxBonus
        } else {
            // Standard weights
            (titleScore * 0.40) + (artistScore * 0.25) + (durationScore * 0.25) + (lyricsScore * 0.10) + ctxBonus
        }

        return minOf(score, 1.0)
    }

    // --- Multi-Source Orchestration ---

    private data class ScoredCandidate(
        val result: LyricsResult,
        val score: Double,
        val query: String,
        val source: String = "LRCLIB"
    )

    private suspend fun fetchFromNetEase(
        title: String,
        artist: String,
        durationSecs: Long,
        titleKeywords: Set<String>,
        artistKeywords: Set<String>,
        featuredArtists: List<String>,
        movieOrAlbum: String?
    ): List<ScoredCandidate> = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<ScoredCandidate>()
        try {
            val query = "$title $artist".trim()
            val eQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$NETEASE_SEARCH_API?s=$eQuery&type=1&limit=5"
            
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            
            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val resultObj = json.optJSONObject("result") ?: return@withContext emptyList()
            val songs = resultObj.optJSONArray("songs") ?: return@withContext emptyList()
            
            for (i in 0 until songs.length()) {
                val song = songs.getJSONObject(i)
                val id = song.optLong("id")
                val name = song.optString("name")
                val artistsArr = song.optJSONArray("artists")
                val artistName = if (artistsArr != null && artistsArr.length() > 0) {
                    artistsArr.getJSONObject(0).optString("name")
                } else "Unknown"
                val album = song.optJSONObject("album")
                val albumName = album?.optString("name")
                val durationMs = song.optLong("duration")
                
                // Fetch lyrics for each candidate to score accurately
                val lyricUrl = "$NETEASE_LYRIC_API?id=$id&lv=1&kv=1&tv=-1"
                val lyricResp = client.newCall(Request.Builder().url(lyricUrl).build()).execute()
                if (lyricResp.isSuccessful) {
                    val lyricBody = lyricResp.body?.string() ?: ""
                    val lyricJson = JSONObject(lyricBody)
                    val lrcObj = lyricJson.optJSONObject("lrc")
                    val syncedLrc = lrcObj?.optString("lyric")
                    val tlyricObj = lyricJson.optJSONObject("tlyric")
                    val transLrc = tlyricObj?.optString("lyric")
                    
                    // Combine original and translated lyrics if available
                    val combinedLrc = if (!transLrc.isNullOrBlank()) {
                        "$syncedLrc\n$transLrc"
                    } else syncedLrc

                    if (!syncedLrc.isNullOrBlank()) {
                        val result = LyricsResult(
                            id = id,
                            trackName = name,
                            artistName = artistName,
                            albumName = albumName,
                            duration = durationMs / 1000,
                            instrumental = false,
                            plainLyrics = null,
                            syncedLyrics = combinedLrc
                        )
                        
                        val score = scoreCandidate(
                            result, title, titleKeywords, artist, artistKeywords, 
                            featuredArtists, durationSecs, movieOrAlbum
                        )
                        
                        candidates.add(ScoredCandidate(result, score, "NetEase:$query", "NetEase"))
                    }
                }
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "NetEase fetch failed: ${e.message}")
        }
        return@withContext candidates
    }

    // --- Main Entry Point ---

    suspend fun findLyricsUniversal(
        rawTitle: String,
        rawArtist: String,
        durationSecs: Long
    ): LyricsResponse = withContext(Dispatchers.IO) {

        // --- Advanced Metadata Cleanup ---
        val titleMeta = cleanMetadataAdvanced(rawTitle)
        val artistMeta = cleanMetadataAdvanced(rawArtist)

        val originalCleanTitle = titleMeta.cleanText
        val originalCleanArtist = artistMeta.cleanText
        val featuredArtists = titleMeta.featuredArtists + artistMeta.featuredArtists
        val movieOrAlbum = titleMeta.movieOrAlbum

        // Extract language if present
        var extractedLanguage = ""
        val languageRegex = Regex("(?i)\\b(tamil|telugu|hindi|malayalam|kannada|punjabi|bengali|marathi|gujarati|urdu|bhojpuri|odia|assamese|nepali|sinhala)\\b")
        val langMatch = languageRegex.find(rawTitle) ?: languageRegex.find(rawArtist)
        if (langMatch != null) {
            extractedLanguage = langMatch.groupValues[1]
        }

        val titleWithoutLang = if (extractedLanguage.isNotBlank()) {
            originalCleanTitle.replace(Regex("(?i)\\b$extractedLanguage\\b"), "").replace(Regex("\\s+"), " ").trim()
        } else originalCleanTitle

        var extractedArtist = originalCleanArtist
        var extractedTitle = titleWithoutLang
        var splitPartA = ""
        var splitPartB = ""

        val separators = listOf(" - ", " – ", " — ", " : ", " | ")
        for (sep in separators) {
            if (rawTitle.contains(sep)) {
                val parts = rawTitle.split(sep, limit = 2)
                if (parts.size >= 2) {
                    splitPartA = cleanMetadataPart(parts[0])
                    splitPartB = cleanMetadataPart(parts[1])
                    if (extractedLanguage.isNotBlank()) {
                        splitPartA = splitPartA.replace(Regex("(?i)\\b$extractedLanguage\\b"), "").replace(Regex("\\s+"), " ").trim()
                        splitPartB = splitPartB.replace(Regex("(?i)\\b$extractedLanguage\\b"), "").replace(Regex("\\s+"), " ").trim()
                    }
                    extractedArtist = splitPartA
                    extractedTitle = splitPartB
                    break
                }
            }
        }

        val titleKeywords = extractKeywords(extractedTitle)
        val artistKeywords = extractKeywords(extractedArtist)

        SecureLogger.d(TAG, "Multi-Source Search: T='$extractedTitle' A='$extractedArtist'")

        try {
            // STRATEGY 1: Exact Match with Duration
            if (extractedTitle.isNotBlank() && extractedArtist.isNotBlank() && durationSecs > 0) {
                val eTitle = URLEncoder.encode(extractedTitle, "UTF-8")
                val eArtist = URLEncoder.encode(extractedArtist, "UTF-8")
                val url = "$API_BASE/get?track_name=$eTitle&artist_name=$eArtist&duration=$durationSecs"
                val response = client.newCall(Request.Builder().url(url).header("Lrclib-Client", CLIENT_HEADER).build()).execute()

                if (response.isSuccessful) {
                    response.body?.string()?.let { body ->
                        if (body.isNotBlank()) {
                            val json = JSONObject(body)
                            if (json.has("id")) {
                                return@withContext buildResponse("STRATEGY_1_EXACT_DURATION", parseResult(json))
                            }
                        }
                    }
                }
            }

            val allCandidates = mutableListOf<ScoredCandidate>()
            val seenIds = mutableSetOf<String>()
            var earlyTermination = false

            fun executeLrclibSearch(queryUrl: String, label: String): Boolean {
                try {
                    val response = client.newCall(Request.Builder().url(queryUrl).header("Lrclib-Client", CLIENT_HEADER).build()).execute()
                    if (response.isSuccessful) {
                        response.body?.string()?.let { body ->
                            val arr = JSONArray(body)
                            for (i in 0 until minOf(arr.length(), 10)) {
                                val result = parseResult(arr.getJSONObject(i))
                                val gid = "LRCLIB:${result.id}"
                                if (gid !in seenIds) {
                                    seenIds.add(gid)
                                    val score = scoreCandidate(result, extractedTitle, titleKeywords, extractedArtist, artistKeywords, featuredArtists, durationSecs, movieOrAlbum)
                                    allCandidates.add(ScoredCandidate(result, score, label))
                                    if (score >= HIGH_CONFIDENCE_THRESHOLD) return true
                                }
                            }
                        }
                    }
                } catch (e: Exception) {}
                return false
            }

            // Phase 1: Structured LRCLIB
            if (extractedTitle.isNotBlank() && extractedArtist.isNotBlank()) {
                val eTitle = URLEncoder.encode(extractedTitle, "UTF-8")
                val eArtist = URLEncoder.encode(extractedArtist, "UTF-8")
                earlyTermination = executeLrclibSearch("$API_BASE/search?track_name=$eTitle&artist_name=$eArtist", "structured")
            }

            // Phase 2: NetEase
            if (!earlyTermination) {
                val neteaseResults = fetchFromNetEase(extractedTitle, extractedArtist, durationSecs, titleKeywords, artistKeywords, featuredArtists, movieOrAlbum)
                allCandidates.addAll(neteaseResults)
                if (neteaseResults.any { it.score >= HIGH_CONFIDENCE_THRESHOLD }) earlyTermination = true
            }

            // Phase 3: Fallback LRCLIB
            if (!earlyTermination) {
                val fallbackQuery = "$extractedTitle $extractedArtist".trim()
                if (fallbackQuery.isNotBlank()) {
                    val eQuery = URLEncoder.encode(fallbackQuery, "UTF-8")
                    executeLrclibSearch("$API_BASE/search?q=$eQuery", "fallback")
                }
            }

            if (allCandidates.isNotEmpty()) {
                val best = allCandidates.maxByOrNull { it.score }!!
                if (best.score >= MIN_CONFIDENCE_SCORE) {
                    val sorted = allCandidates.sortedByDescending { it.score }.map { it.result }
                    return@withContext buildResponse("MULTI_SOURCE_${best.source}", best.result, sorted)
                }
            }

            return@withContext LyricsResponse(success = false, error = "No lyrics found")
        } catch (e: Exception) {
            return@withContext LyricsResponse(success = false, error = e.localizedMessage)
        }
    }

    // --- Manual Search API ---

    /**
     * Search LRCLIB with a free-text query. Returns raw results for manual selection.
     */
    suspend fun searchLyrics(query: String): List<LyricsResult> = withContext(Dispatchers.IO) {
        try {
            val eQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$API_BASE/search?q=$eQuery"
            val response = client.newCall(
                Request.Builder().url(url).header("Lrclib-Client", CLIENT_HEADER).build()
            ).execute()

            if (response.isSuccessful) {
                response.body?.string()?.let { body ->
                    val arr = JSONArray(body)
                    val results = mutableListOf<LyricsResult>()
                    val limit = minOf(arr.length(), 20)
                    for (i in 0 until limit) {
                        val result = parseResult(arr.getJSONObject(i))
                        // Only include results that have lyrics
                        if (result.plainLyrics != null || result.syncedLyrics != null) {
                            results.add(result)
                        }
                    }
                    return@withContext results
                }
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Search failed: ${e.message}")
        }
        return@withContext emptyList()
    }

    /**
     * Fetch a specific lyrics entry by its LRCLIB ID.
     * Used for loading saved/preferred lyrics.
     */
    suspend fun getLyricsById(id: Long): LyricsResult? = withContext(Dispatchers.IO) {
        try {
            val url = "$API_BASE/get/$id"
            val response = client.newCall(
                Request.Builder().url(url).header("Lrclib-Client", CLIENT_HEADER).build()
            ).execute()

            if (response.isSuccessful) {
                response.body?.string()?.let { body ->
                    if (body.isNotBlank()) {
                        val json = JSONObject(body)
                        if (json.has("id")) {
                            return@withContext parseResult(json)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "GetById failed for id=$id: ${e.message}")
        }
        return@withContext null
    }
}

package com.vyllo.music.data

import com.vyllo.music.BuildConfig
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.data.lyrics.LrcParser
import com.vyllo.music.domain.model.LyricsResponse
import com.vyllo.music.domain.model.LyricsResult
import com.vyllo.music.domain.model.LyricsStatus
import com.vyllo.music.domain.model.SyncedLyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Multi-Source Lyrics Engine
 *
 * Key insight: YouTube titles are often "Artist - Song (Official Video)"
 * and uploaders are channel names like "Artist - Topic" or "ArtistVEVO".
 * We must aggressively clean and also support title-only fallback.
 */
@Singleton
class LyricsEngine @Inject constructor(
    @Named("lyrics") private val client: OkHttpClient
) {

    companion object {
        private const val TAG = "LyricsEngine"
        private const val CLIENT_USER_AGENT = "Vyllo/1.0 (Android Lyrics Client)"
        private const val LRCLIB_API = BuildConfig.LYRICS_API_BASE
        private const val LYRICS_OVH_API = "https://api.lyrics.ovh/v1"
        private const val NETEASE_SEARCH_API = BuildConfig.NETEASE_SEARCH_API
        private const val NETEASE_LYRIC_API = BuildConfig.NETEASE_LYRIC_API

        fun detectLanguage(text: String): List<String> = LrcParser.detectLanguage(text)
        fun parseSyncedLyrics(lrcString: String?): List<SyncedLyricLine> = LrcParser.parseSyncedLyrics(lrcString)
        fun getCurrentLyricLine(syncedLyrics: List<SyncedLyricLine>?, currentTimeMs: Long): Int =
            LrcParser.getCurrentLyricLine(syncedLyrics, currentTimeMs)
    }

    /**
     * Main entry point. Tries multiple strategies in order of reliability.
     */
    suspend fun findLyricsUniversal(
        rawTitle: String,
        rawArtist: String,
        durationSecs: Long,
        extractedTitle: String? = null,
        extractedArtist: String? = null,
        extractedAlbum: String? = null,
        extractedLanguage: String? = null,
        extractedMusic: String? = null
    ): LyricsResponse = withContext(Dispatchers.IO) {
        val (cleanedTitle, cleanedArtist) = cleanTitleAndArtist(rawTitle, rawArtist)
        
        val cleanTitle = if (!extractedTitle.isNullOrBlank()) extractedTitle.trim() else cleanedTitle
        
        var cleanArtist = when {
            !extractedArtist.isNullOrBlank() -> extractedArtist.trim()
            !extractedMusic.isNullOrBlank() -> extractedMusic.trim()
            else -> cleanedArtist
        }

        if (isRecordLabel(cleanArtist) && !extractedMusic.isNullOrBlank()) {
            cleanArtist = extractedMusic.trim()
        }

        if (isRecordLabel(cleanArtist)) {
            cleanArtist = ""
        }
        
        if (cleanArtist.startsWith("@")) {
            cleanArtist = cleanArtist.removePrefix("@")
        }

        SecureLogger.d(TAG, "=== LYRICS FETCH === rawTitle='$rawTitle' rawArtist='$rawArtist' cleanTitle='$cleanTitle' cleanArtist='$cleanArtist' dur=$durationSecs")

        if (cleanTitle.isBlank()) {
            return@withContext LyricsResponse(success = false, strategy = "NO_TITLE", error = "Song title is empty")
        }

        // Strategy 1: Paxsenix (Dynamic Apple Music token scrape & Paxsenix synced lyrics fetch)
        if (cleanTitle.isNotBlank() && cleanArtist.isNotBlank()) {
            SecureLogger.d(TAG, "Strategy 1: Paxsenix (title='$cleanTitle', artist='$cleanArtist')")
            val paxLrc = com.vyllo.music.data.lyrics.PaxsenixProvider.getLyrics(client, cleanTitle, cleanArtist, durationSecs, extractedAlbum)
            if (paxLrc != null) {
                SecureLogger.d(TAG, "FOUND via Paxsenix: $cleanTitle")
                val lyricsResult = buildResultFromLrc(cleanTitle, cleanArtist, extractedAlbum, durationSecs, paxLrc)
                return@withContext buildResponse("PAXSENIX", lyricsResult)
            }
        }

        // Strategy 2: BetterLyrics (Boidu cached Timed-Text XML API)
        if (cleanTitle.isNotBlank() && cleanArtist.isNotBlank()) {
            SecureLogger.d(TAG, "Strategy 2: BetterLyrics (title='$cleanTitle', artist='$cleanArtist')")
            val betterLrc = com.vyllo.music.data.lyrics.BetterLyricsProvider.getLyrics(client, cleanTitle, cleanArtist, durationSecs, extractedAlbum)
            if (betterLrc != null) {
                SecureLogger.d(TAG, "FOUND via BetterLyrics: $cleanTitle")
                val lyricsResult = buildResultFromLrc(cleanTitle, cleanArtist, extractedAlbum, durationSecs, betterLrc)
                return@withContext buildResponse("BETTERLYRICS", lyricsResult)
            }
        }

        // Strategy 3: LRCLIB exact match with clean data
        if (cleanTitle.isNotBlank() && cleanArtist.isNotBlank()) {
            SecureLogger.d(TAG, "Strategy 3: LRCLIB exact (title='$cleanTitle', artist='$cleanArtist')")
            val result = tryLrclibExact(cleanTitle, cleanArtist, durationSecs)
            if (result != null) {
                SecureLogger.d(TAG, "FOUND via LRCLIB exact: ${result.trackName}")
                return@withContext buildResponse("LRCLIB_EXACT", result)
            }
        }

        // Strategy 4: LRCLIB structured search (track_name + artist_name)
        if (cleanTitle.isNotBlank() && cleanArtist.isNotBlank()) {
            SecureLogger.d(TAG, "Strategy 4: LRCLIB search (track_name='$cleanTitle', artist_name='$cleanArtist')")
            val result = tryLrclibSearch(cleanTitle, cleanArtist, durationSecs)
            if (result != null) {
                SecureLogger.d(TAG, "FOUND via LRCLIB search: ${result.trackName}")
                return@withContext buildResponse("LRCLIB_SEARCH", result)
            }
        }

        // Strategy 5: LRCLIB title-only search (most reliable fallback)
        SecureLogger.d(TAG, "Strategy 5: LRCLIB title-only search (title='$cleanTitle')")
        val titleOnly = tryLrclibSearch(cleanTitle, "", durationSecs)
        if (titleOnly != null) {
            SecureLogger.d(TAG, "FOUND via LRCLIB title-only: ${titleOnly.trackName}")
            return@withContext buildResponse("LRCLIB_TITLE_ONLY", titleOnly)
        }

        // Strategy 6: LyricsPlus / Binimum API (mirrors and metadata search)
        if (cleanTitle.isNotBlank() && cleanArtist.isNotBlank()) {
            SecureLogger.d(TAG, "Strategy 6: LyricsPlus (title='$cleanTitle', artist='$cleanArtist')")
            val plusLrc = com.vyllo.music.data.lyrics.LyricsPlusProvider.getLyrics(client, cleanTitle, cleanArtist, durationSecs, extractedAlbum)
            if (plusLrc != null) {
                SecureLogger.d(TAG, "FOUND via LyricsPlus: $cleanTitle")
                val lyricsResult = buildResultFromLrc(cleanTitle, cleanArtist, extractedAlbum, durationSecs, plusLrc)
                return@withContext buildResponse("LYRICS_PLUS", lyricsResult)
            }
        }

        // Strategy 7: KuGou (East Asian/regional and instrumental)
        if (cleanTitle.isNotBlank() && cleanArtist.isNotBlank()) {
            SecureLogger.d(TAG, "Strategy 7: KuGou (title='$cleanTitle', artist='$cleanArtist')")
            val kugouLrc = com.vyllo.music.data.lyrics.KuGouProvider.getLyrics(client, cleanTitle, cleanArtist, durationSecs, extractedAlbum)
            if (kugouLrc != null) {
                SecureLogger.d(TAG, "FOUND via KuGou: $cleanTitle")
                val lyricsResult = buildResultFromLrc(cleanTitle, cleanArtist, extractedAlbum, durationSecs, kugouLrc)
                return@withContext buildResponse("KUGOU", lyricsResult)
            }
        }

        // Strategy 8: Lyrics.ovh (plain text only)
        if (cleanTitle.isNotBlank() && cleanArtist.isNotBlank()) {
            SecureLogger.d(TAG, "Strategy 8: Lyrics.ovh")
            val ovh = tryLyricsOvh(cleanTitle, cleanArtist)
            if (ovh != null) {
                SecureLogger.d(TAG, "FOUND via Lyrics.ovh: ${ovh.trackName}")
                return@withContext buildResponse("LYRICS_OVH", ovh)
            }
        }

        // Strategy 9: NetEase (Asian music)
        SecureLogger.d(TAG, "Strategy 9: NetEase")
        val netease = tryNetEase(cleanTitle, cleanArtist, durationSecs)
        if (netease != null) {
            SecureLogger.d(TAG, "FOUND via NetEase: ${netease.trackName}")
            return@withContext buildResponse("NETEASE", netease)
        }

        SecureLogger.w(TAG, "=== ALL LYRICS SOURCES FAILED ===")
        return@withContext LyricsResponse(
            success = false,
            strategy = "ALL_FAILED",
            error = "No lyrics found from any source."
        )
    }

    /**
     * LRCLIB exact match endpoint.
     * GET /get?track_name=...&artist_name=...&duration=...
     */
    private fun tryLrclibExact(title: String, artist: String, durationSecs: Long): LyricsResult? {
        return try {
            val eTitle = URLEncoder.encode(title, "UTF-8")
            val eArtist = URLEncoder.encode(artist, "UTF-8")
            val url = if (durationSecs > 0) {
                "$LRCLIB_API/get?track_name=$eTitle&artist_name=$eArtist&duration=$durationSecs"
            } else {
                "$LRCLIB_API/get?track_name=$eTitle&artist_name=$eArtist"
            }

            SecureLogger.d(TAG, "  [1/5] LRCLIB exact URL: $url")
            val response = client.newCall(newRequest(url)).execute()
            val body = response.body?.string()
            SecureLogger.d(TAG, "  [1/5] HTTP ${response.code}, body length=${body?.length ?: 0}, body preview: ${body?.take(100)}")

            if (response.isSuccessful && !body.isNullOrBlank()) {
                try {
                    val json = JSONObject(body)
                    SecureLogger.d(TAG, "  [1/5] JSON has id=${json.has("id")}, has synced=${json.opt("syncedLyrics") != null}, has plain=${json.opt("plainLyrics") != null}")
                    if (json.has("id")) {
                        val synced = json.optString("syncedLyrics", null)
                        val plain = json.optString("plainLyrics", null)
                        if ((synced != null && synced.isNotBlank()) || (plain != null && plain.isNotBlank())) {
                            SecureLogger.d(TAG, "  [1/5] SUCCESS - parsed LRCLIB result")
                            return parseLrclibResult(json)
                        } else {
                            SecureLogger.d(TAG, "  [1/5] Has id but no lyrics content")
                        }
                    }
                } catch (e: Exception) {
                    SecureLogger.e(TAG, "  [1/5] JSON parse error: ${e.message}")
                }
            } else {
                SecureLogger.d(TAG, "  [1/5] Failed: success=${response.isSuccessful}, blank=${body.isNullOrBlank()}")
            }
            null
        } catch (e: Exception) {
            SecureLogger.e(TAG, "  [1/5] Network error: ${e.message}")
            null
        }
    }

    /**
     * LRCLIB search endpoint.
     * GET /search?track_name=...&artist_name=...  OR  GET /search?q=...
     */
    private fun tryLrclibSearch(title: String, artist: String, durationSecs: Long): LyricsResult? {
        return try {
            val url = if (artist.isNotBlank()) {
                val eTitle = URLEncoder.encode(title, "UTF-8")
                val eArtist = URLEncoder.encode(artist, "UTF-8")
                "$LRCLIB_API/search?track_name=$eTitle&artist_name=$eArtist"
            } else {
                val eQuery = URLEncoder.encode(title, "UTF-8")
                "$LRCLIB_API/search?q=$eQuery"
            }

            val response = client.newCall(newRequest(url)).execute()
            val body = response.body?.string()
            SecureLogger.d(TAG, "  LRCLIB search: HTTP ${response.code}, results=${try { JSONArray(body!!).length() } catch (_: Exception) { 0 }}")

            if (!response.isSuccessful || body.isNullOrBlank()) return null

            val arr = JSONArray(body)
            if (arr.length() == 0) return null

            var bestResult: LyricsResult? = null
            var bestScore = -1.0

            for (i in 0 until minOf(arr.length(), 15)) {
                val json = arr.getJSONObject(i)
                val synced = json.optString("syncedLyrics", null)
                val plain = json.optString("plainLyrics", null)
                if (synced.isNullOrBlank() && plain.isNullOrBlank()) continue

                val result = parseLrclibResult(json)
                var score = 0.0

                // Duration match
                if (durationSecs > 0 && result.duration > 0) {
                    val diff = kotlin.math.abs(result.duration - durationSecs).toDouble()
                    score += if (diff < 5) 5.0 else if (diff < 15) 3.0 else if (diff < 30) 1.0 else 0.0
                }

                // Title similarity (word-level Jaccard)
                val titleSim = wordJaccard(title.lowercase(), result.trackName.lowercase())
                score += titleSim * 3.0

                // Synced lyrics bonus
                if (!synced.isNullOrBlank()) score += 2.0

                SecureLogger.d(TAG, "    Candidate: '${result.trackName}' score=$score (titleSim=${String.format("%.2f", titleSim)})")

                if (score > bestScore) {
                    bestScore = score
                    bestResult = result
                }
            }

            if (bestResult != null && bestScore >= 0.5) {
                return bestResult
            }
            null
        } catch (e: Exception) {
            SecureLogger.e(TAG, "  LRCLIB search error: ${e.message}")
            null
        }
    }

    /**
     * Lyrics.ovh for plain text.
     * GET /v1/Artist/Title
     */
    private fun tryLyricsOvh(title: String, artist: String): LyricsResult? {
        return try {
            val eArtist = URLEncoder.encode(artist, "UTF-8")
            val eTitle = URLEncoder.encode(title, "UTF-8")
            val url = "$LYRICS_OVH_API/$eArtist/$eTitle"

            val response = client.newCall(newRequest(url)).execute()
            val body = response.body?.string()
            SecureLogger.d(TAG, "  Lyrics.ovh: HTTP ${response.code}")

            if (response.isSuccessful && !body.isNullOrBlank()) {
                val json = JSONObject(body)
                val lyrics = json.optString("lyrics", "").trim()
                if (lyrics.isNotBlank() && lyrics != "Not found" && !lyrics.contains("Song not found", ignoreCase = true)) {
                    return LyricsResult(
                        id = 0L, trackName = title, artistName = artist,
                        albumName = null, duration = 0L, instrumental = false,
                        plainLyrics = lyrics, syncedLyrics = null
                    )
                }
            }
            null
        } catch (e: Exception) {
            SecureLogger.e(TAG, "  Lyrics.ovh error: ${e.message}")
            null
        }
    }

    /**
     * NetEase for Asian music.
     */
    private fun tryNetEase(title: String, artist: String, durationSecs: Long): LyricsResult? {
        return try {
            val query = "$title $artist".trim()
            val eQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "$NETEASE_SEARCH_API?s=$eQuery&type=1&limit=3"

            val searchResp = client.newCall(newRequest(searchUrl, referer = "https://music.163.com/")).execute()
            if (!searchResp.isSuccessful) {
                SecureLogger.d(TAG, "  NetEase: search HTTP ${searchResp.code}")
                return null
            }

            val body = searchResp.body?.string() ?: return null
            val json = JSONObject(body)
            val resultObj = json.optJSONObject("result") ?: return null
            val songs = resultObj.optJSONArray("songs") ?: return null

            SecureLogger.d(TAG, "  NetEase: found ${songs.length()} candidates")

            for (i in 0 until minOf(songs.length(), 3)) {
                val song = songs.getJSONObject(i)
                val id = song.optLong("id")
                val name = song.optString("name")
                val artistsArr = song.optJSONArray("artists")
                val artistName = if (artistsArr != null && artistsArr.length() > 0) {
                    artistsArr.getJSONObject(0).optString("name")
                } else "Unknown"
                val durationMs = song.optLong("duration")

                val lyricUrl = "$NETEASE_LYRIC_API?id=$id&lv=1&kv=1&tv=-1"
                val lyricResp = client.newCall(newRequest(lyricUrl, referer = "https://music.163.com/")).execute()
                if (lyricResp.isSuccessful) {
                    val lyricBody = lyricResp.body?.string() ?: continue
                    val lyricJson = JSONObject(lyricBody)
                    val lrcObj = lyricJson.optJSONObject("lrc")
                    val syncedLrc = lrcObj?.optString("lyric")

                    if (!syncedLrc.isNullOrBlank()) {
                        val tlyricObj = lyricJson.optJSONObject("tlyric")
                        val transLrc = tlyricObj?.optString("lyric")
                        val combinedLrc = if (!transLrc.isNullOrBlank()) "$syncedLrc\n$transLrc" else syncedLrc

                        val result = LyricsResult(
                            id = id, trackName = name, artistName = artistName,
                            albumName = null, duration = durationMs / 1000, instrumental = false,
                            plainLyrics = null, syncedLyrics = combinedLrc
                        )

                        if (durationSecs > 0 && result.duration > 0) {
                            val diff = kotlin.math.abs(result.duration - durationSecs)
                            if (diff > 30) {
                                SecureLogger.d(TAG, "    NetEase candidate skipped: duration diff=${diff}s")
                                continue
                            }
                        }

                        return result
                    }
                }
            }
            null
        } catch (e: Exception) {
            SecureLogger.e(TAG, "  NetEase error: ${e.message}")
            null
        }
    }

    private fun parseLrclibResult(json: JSONObject): LyricsResult {
        val syncedLyrics = json.optString("syncedLyrics", null).takeIf { it?.isNotBlank() == true }
        val plainLyrics = json.optString("plainLyrics", null).takeIf { it?.isNotBlank() == true }
        val trackName = json.optString("trackName", "").ifBlank { json.optString("name", "") }

        return LyricsResult(
            id = json.optLong("id", 0L),
            trackName = trackName,
            artistName = json.optString("artistName", ""),
            albumName = json.optString("albumName", null),
            duration = json.optDouble("duration", 0.0).toLong(),
            instrumental = json.optBoolean("instrumental", false),
            plainLyrics = plainLyrics,
            syncedLyrics = syncedLyrics ?: plainLyrics.takeIf { looksLikeTimedLyrics(it) }
        )
    }

    private fun buildResultFromLrc(title: String, artist: String, album: String?, durationSecs: Long, lrc: String): LyricsResult {
        val hasTimestamps = looksLikeTimedLyrics(lrc)
        return LyricsResult(
            id = System.currentTimeMillis(),
            trackName = title,
            artistName = artist,
            albumName = album,
            duration = durationSecs,
            instrumental = false,
            plainLyrics = if (hasTimestamps) null else lrc,
            syncedLyrics = if (hasTimestamps) lrc else null
        )
    }

    private fun buildResponse(strategy: String, result: LyricsResult): LyricsResponse {
        val syncedSource = result.syncedLyrics ?: result.plainLyrics?.takeIf { looksLikeTimedLyrics(it) }
        val parsedLines = parseSyncedLyrics(syncedSource)
        val plainLyrics = result.plainLyrics?.let { lyrics ->
            if (parsedLines.isNotEmpty()) {
                parsedLines.joinToString("\n") { it.content }
            } else {
                lyrics
            }
        }
        val detectText = syncedSource ?: plainLyrics ?: ""
        val langs = detectLanguage(detectText)

        return LyricsResponse(
            success = true, strategy = strategy, result = result,
            results = listOf(result),
            plainLyrics = plainLyrics,
            syncedLines = parsedLines.takeIf { it.isNotEmpty() },
            languages = langs,
            lyricsStatus = LyricsStatus(
                hasPlain = !plainLyrics.isNullOrBlank(),
                hasSynced = parsedLines.isNotEmpty(),
                isInstrumental = result.instrumental
            )
        )
    }

    /**
     * Aggressively clean YouTube title and extract artist.
     *
     * YouTube titles: "Song Name - Artist (Official Video)", "Artist - Song Name [Lyrics]"
     * YouTube uploaders: "Artist - Topic", "ArtistVEVO", random channel names
     *
     * Goal: extract clean song name and artist for LRCLIB lookup.
     */
    fun isRecordLabel(name: String): Boolean {
        val labelKeywords = listOf("music", "records", "series", "company", "entertainment", "channel", "official", "tv", "indie", "south", "india")
        val lower = name.lowercase()
        return labelKeywords.any { lower.contains(it) }
    }

    private fun cleanTitleAndArtist(rawTitle: String, rawArtist: String): Pair<String, String> {
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
    private fun wordJaccard(a: String, b: String): Double {
        if (a.isBlank() || b.isBlank()) return 0.0
        if (a == b) return 1.0
        val aSet = a.split("\\s+".toRegex()).filter { it.length > 1 }.toSet()
        val bSet = b.split("\\s+".toRegex()).filter { it.length > 1 }.toSet()
        if (aSet.isEmpty() || bSet.isEmpty()) return 0.0
        val intersection = aSet.intersect(bSet).size.toDouble()
        val union = aSet.union(bSet).size.toDouble()
        return intersection / union
    }

    suspend fun searchLyrics(query: String): List<LyricsResult> = withContext(Dispatchers.IO) {
        SecureLogger.d(TAG, "MANUAL SEARCH: query='$query'")
        return@withContext try {
            val results = linkedMapOf<Long, LyricsResult>()

            searchLrclibByQuery(query).forEach { results[it.id] = it }

            if (results.isEmpty()) {
                extractArtistAndTitleFromQuery(query)?.let { (artist, title) ->
                    SecureLogger.d(TAG, "MANUAL SEARCH: retrying structured search with artist='$artist' title='$title'")
                    tryLrclibSearch(title, artist, durationSecs = 0)?.let { results[it.id] = it }
                }
            }

            val finalResults = results.values.toList()
            SecureLogger.d(TAG, "MANUAL SEARCH: returning ${finalResults.size} results")
            finalResults
        } catch (e: Exception) {
            SecureLogger.e(TAG, "MANUAL SEARCH error: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getLyricsById(id: Long): LyricsResult? = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "$LRCLIB_API/get/$id"
            val response = client.newCall(newRequest(url)).execute()
            if (response.isSuccessful) {
                response.body?.string()?.let { body ->
                    if (body.isNotBlank()) {
                        return@withContext parseLrclibResult(JSONObject(body))
                    }
                }
            }
            null
        } catch (e: Exception) {
            SecureLogger.e(TAG, "GetById error: ${e.message}")
            null
        }
    }

    private fun newRequest(url: String, referer: String? = null): Request {
        return Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", CLIENT_USER_AGENT)
            .apply {
                if (!referer.isNullOrBlank()) {
                    header("Referer", referer)
                }
            }
            .build()
    }

    private fun looksLikeTimedLyrics(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return Regex("(?m)^\\s*\\[(\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?)\\]").containsMatchIn(text)
    }

    private fun searchLrclibByQuery(query: String): List<LyricsResult> {
        val eQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$LRCLIB_API/search?q=$eQuery"
        SecureLogger.d(TAG, "MANUAL SEARCH URL: $url")
        val response = client.newCall(newRequest(url)).execute()
        val body = response.body?.string()
        SecureLogger.d(TAG, "MANUAL SEARCH: HTTP ${response.code}, body length=${body?.length ?: 0}, preview: ${body?.take(100)}")

        if (!response.isSuccessful || body.isNullOrBlank()) {
            SecureLogger.d(TAG, "MANUAL SEARCH: failed, success=${response.isSuccessful}")
            return emptyList()
        }

        val arr = JSONArray(body)
        SecureLogger.d(TAG, "MANUAL SEARCH: JSON array length=${arr.length()}")
        val results = mutableListOf<LyricsResult>()
        for (i in 0 until minOf(arr.length(), 20)) {
            val json = arr.getJSONObject(i)
            val result = parseLrclibResult(json)
            val hasLyrics = !result.syncedLyrics.isNullOrBlank() || !result.plainLyrics.isNullOrBlank()
            SecureLogger.d(TAG, "  Result $i: synced=${!result.syncedLyrics.isNullOrBlank()}, plain=${!result.plainLyrics.isNullOrBlank()}")
            if (hasLyrics) {
                results.add(result)
            }
        }
        return results
    }

    private fun extractArtistAndTitleFromQuery(query: String): Pair<String, String>? {
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

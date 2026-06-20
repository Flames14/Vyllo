package com.vyllo.music.data.lyrics

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import com.vyllo.music.core.security.SecureLogger
import java.net.URLEncoder
import java.util.Locale

object PaxsenixProvider {
    private const val TAG = "PaxsenixProvider"
    private const val APPLE_MUSIC_API_BASE = "https://amp-api.music.apple.com/v1/catalog/us"

    object AppleTokenManager {
        private var cachedToken: String? = null
        private val lock = Any()

        fun getToken(client: OkHttpClient): String {
            synchronized(lock) {
                cachedToken?.let { return it }

                try {
                    SecureLogger.d(TAG, "Fetching new Apple Music developer token...")
                    // 1. Fetch beta page
                    val request1 = Request.Builder()
                        .url("https://beta.music.apple.com")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .build()
                    val response1 = client.newCall(request1).execute()
                    val body1 = response1.body?.string() ?: throw Exception("Empty body from Apple Music Beta page")

                    // 2. Regex match the index JS script
                    val indexJsRegex = Regex("/assets/index~[^/]+\\.js")
                    val indexJsMatch = indexJsRegex.find(body1) ?: throw Exception("Could not find index JS script link")
                    val indexJsUri = indexJsMatch.value

                    // 3. Fetch index JS
                    val request2 = Request.Builder()
                        .url("https://beta.music.apple.com$indexJsUri")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .build()
                    val response2 = client.newCall(request2).execute()
                    val body2 = response2.body?.string() ?: throw Exception("Empty body from Apple Music main JS bundle")

                    // 4. Find the JWT token
                    val tokenRegex = Regex("eyJ[A-Za-z0-9\\-_=]+\\.[A-Za-z0-9\\-_=]+\\.[A-Za-z0-9\\-_=]+")
                    val tokenMatch = tokenRegex.find(body2) ?: throw Exception("Could not find JWT token inside JS script")
                    val token = tokenMatch.value
                    cachedToken = token
                    SecureLogger.d(TAG, "Successfully fetched Apple Music token!")
                    return token
                } catch (e: Exception) {
                    SecureLogger.e(TAG, "Failed to scrape Apple Music token: ${e.message}", e)
                    throw e
                }
            }
        }

        fun clearToken() {
            synchronized(lock) {
                cachedToken = null
                SecureLogger.d(TAG, "Cleared cached Apple Music token")
            }
        }
    }

    data class AppleSongCandidate(
        val id: String,
        val name: String,
        val artistName: String,
        val albumName: String?,
        val durationSecs: Long
    )

    fun getLyrics(client: OkHttpClient, title: String, artist: String, durationSecs: Long, album: String?): String? {
        SecureLogger.d(TAG, "getLyrics called for title='$title', artist='$artist', duration=$durationSecs, album='$album'")
        
        val cleanPattern = Regex("(?i)\\s*\\(.*?\\)|\\s*\\[.*?\\]")
        val cleanedTitle = title.replace(cleanPattern, "").trim()
        val cleanedArtist = artist.replace(cleanPattern, "").trim()
        
        val searchQueries = listOf(
            "$cleanedTitle $cleanedArtist",
            cleanedTitle
        )
        
        var candidates: List<AppleSongCandidate> = emptyList()
        for (query in searchQueries) {
            if (candidates.isEmpty()) {
                SecureLogger.d(TAG, "Searching Apple Music with query: '$query'")
                candidates = searchAppleMusic(client, query)
            }
        }
        
        if (candidates.isEmpty()) {
            SecureLogger.w(TAG, "No Apple Music candidates found")
            return null
        }
        
        // Score and sort candidates
        val scored = candidates.map { candidate ->
            val score = scoreCandidate(candidate, title, artist, durationSecs)
            candidate to score
        }.filter { it.second > 0 }.sortedByDescending { it.second }
        
        SecureLogger.d(TAG, "Found ${scored.size} scored candidates (top 3):")
        scored.take(3).forEach { (cand, score) ->
            SecureLogger.d(TAG, "  - '${cand.name}' by '${cand.artistName}' (ID: ${cand.id}, score: $score)")
        }
        
        // Try top candidates in order of score
        for ((candidate, score) in scored.take(5)) {
            SecureLogger.d(TAG, "Trying to fetch lyrics for candidate ID: ${candidate.id} (Score: $score)")
            val lrc = fetchPaxsenixLyrics(client, candidate.id)
            if (!lrc.isNullOrBlank()) {
                SecureLogger.i(TAG, "Got lyrics from Paxsenix for candidate ID: ${candidate.id}")
                return lrc
            }
        }
        
        SecureLogger.w(TAG, "No lyrics found from Paxsenix for any of the matched candidates")
        return null
    }

    private fun searchAppleMusic(client: OkHttpClient, query: String): List<AppleSongCandidate> {
        try {
            val token = AppleTokenManager.getToken(client)
            return searchAppleMusicWithToken(client, token, query)
        } catch (e: Exception) {
            // Check if 401 response or general exception. If so, clear token and retry once.
            SecureLogger.w(TAG, "Apple Music search failed, clearing token and retrying: ${e.message}")
            AppleTokenManager.clearToken()
            return try {
                val token = AppleTokenManager.getToken(client)
                searchAppleMusicWithToken(client, token, query)
            } catch (e2: Exception) {
                SecureLogger.e(TAG, "Retry Apple Music search failed: ${e2.message}", e2)
                emptyList()
            }
        }
    }

    private fun searchAppleMusicWithToken(client: OkHttpClient, token: String, query: String): List<AppleSongCandidate> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$APPLE_MUSIC_API_BASE/search?term=$encodedQuery&types=songs&limit=15&l=en-US&platform=web&format[resources]=map&include[songs]=artists&extend=artistUrl"
        
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Origin", "https://music.apple.com")
            .header("Referer", "https://music.apple.com/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .header("Accept", "application/json")
            .build()
            
        val response = client.newCall(request).execute()
        if (response.code == 401) {
            throw Exception("401 Unauthorized from Apple Music Search API")
        }
        if (!response.isSuccessful) {
            SecureLogger.w(TAG, "Search API returned status: ${response.code}")
            return emptyList()
        }
        
        val body = response.body?.string() ?: return emptyList()
        
        val list = mutableListOf<AppleSongCandidate>()
        try {
            val root = JSONObject(body)
            val results = root.optJSONObject("results") ?: return emptyList()
            val songs = results.optJSONObject("songs") ?: return emptyList()
            val data = songs.optJSONArray("data") ?: return emptyList()
            
            val resources = root.optJSONObject("resources")
            val resourceSongs = resources?.optJSONObject("songs")
            
            for (i in 0 until data.length()) {
                val songData = data.getJSONObject(i)
                val id = songData.getString("id")
                
                val songDetail = resourceSongs?.optJSONObject(id) ?: songData
                val attributes = songDetail.optJSONObject("attributes") ?: continue
                
                val name = attributes.optString("name", "")
                val artistName = attributes.optString("artistName", "")
                val albumName = attributes.optString("albumName", null)
                val durationMs = attributes.optLong("durationInMillis", 0L)
                
                list.add(AppleSongCandidate(id, name, artistName, albumName, durationMs / 1000))
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Error parsing Apple Search response JSON: ${e.message}", e)
        }
        return list
    }

    private fun scoreCandidate(candidate: AppleSongCandidate, title: String, artist: String, durationSecs: Long): Double {
        var score = 0.0
        
        // 1. Duration match (within bounds)
        if (durationSecs > 0 && candidate.durationSecs > 0) {
            val diff = kotlin.math.abs(candidate.durationSecs - durationSecs)
            when {
                diff <= 2 -> score += 100.0
                diff <= 5 -> score += 50.0
                diff <= 10 -> score += 10.0
                else -> score -= 50.0
            }
        }
        
        // 2. Title similarity
        val cleanPattern = Regex("(?i)\\s*\\(.*?\\)|\\s*\\[.*?\\]")
        val targetTitleCleaned = title.replace(cleanPattern, "").lowercase(Locale.US).trim()
        val candidateTitleCleaned = candidate.name.replace(cleanPattern, "").lowercase(Locale.US).trim()
        
        when {
            candidateTitleCleaned == targetTitleCleaned -> score += 80.0
            candidateTitleCleaned.contains(targetTitleCleaned) || targetTitleCleaned.contains(candidateTitleCleaned) -> score += 40.0
        }
        
        // Mixed/Remix mismatch penalties
        val targetIsMixed = title.contains("mixed", ignoreCase = true)
        val targetIsRemix = title.contains("remix", ignoreCase = true)
        val candidateIsMixed = candidate.name.contains("mixed", ignoreCase = true)
        val candidateIsRemix = candidate.name.contains("remix", ignoreCase = true)
        
        if (candidateIsMixed && !targetIsMixed) score -= 60.0
        if (candidateIsRemix && !targetIsRemix) score -= 40.0
        
        // 3. Artist similarity
        val targetArtistLower = artist.lowercase(Locale.US).trim()
        val candidateArtistLower = candidate.artistName.lowercase(Locale.US).trim()
        
        when {
            candidateArtistLower.contains(targetArtistLower) || targetArtistLower.contains(candidateArtistLower) -> score += 50.0
            else -> {
                val words = targetArtistLower.split(Regex("\\s+")).filter { it.length > 2 }
                if (words.any { candidateArtistLower.contains(it) }) {
                    score += 25.0
                }
            }
        }
        
        return score
    }

    private fun fetchPaxsenixLyrics(client: OkHttpClient, trackId: String): String? {
        try {
            val url = "https://lyrics.paxsenix.org/apple-music/lyrics?id=$trackId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Vyllo/2.0")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            
            val json = JSONObject(body)
            
            // 1. Try ttmlContent using our TTMLParser
            val ttml = json.optString("ttmlContent", "").trim()
            if (ttml.isNotEmpty()) {
                val parsed = TTMLParser.parseTTML(ttml)
                if (parsed.isNotEmpty()) {
                    val lrc = TTMLParser.toLRC(parsed).trim()
                    if (lrc.isNotEmpty()) {
                        SecureLogger.d(TAG, "Parsed lyrics successfully via TTMLParser")
                        return lrc
                    }
                }
            }
            
            // 2. Try pre-converted ELRC multi person
            val elrcMulti = json.optString("elrcMultiPerson", "").trim()
            if (elrcMulti.isNotEmpty()) {
                SecureLogger.d(TAG, "Using pre-converted elrcMultiPerson")
                return elrcMulti
            }
            
            // 3. Try pre-converted ELRC
            val elrc = json.optString("elrc", "").trim()
            if (elrc.isNotEmpty()) {
                SecureLogger.d(TAG, "Using pre-converted elrc")
                return elrc
            }
            
            // 4. Try plain
            val plain = json.optString("plain", "").trim()
            if (plain.isNotEmpty()) {
                SecureLogger.d(TAG, "Using pre-converted plain")
                return plain
            }
            
            // 5. Parse content array
            val content = json.optJSONArray("content")
            if (content != null && content.length() > 0) {
                val lyricsType = json.optString("type", "")
                val hasWordLevel = lyricsType.equals("Syllable", ignoreCase = true)
                
                if (!hasWordLevel) {
                    val plainLines = mutableListOf<String>()
                    for (i in 0 until content.length()) {
                        val lineObj = content.getJSONObject(i)
                        val textArray = lineObj.optJSONArray("text")
                        val lineText = if (textArray != null) {
                            val sb = StringBuilder()
                            for (j in 0 until textArray.length()) {
                                sb.append(textArray.getJSONObject(j).optString("text", ""))
                            }
                            sb.toString()
                        } else {
                            lineObj.optString("text", "")
                        }
                        if (lineText.isNotBlank()) {
                            plainLines.add(lineText.trim())
                        }
                    }
                    SecureLogger.d(TAG, "Generated plain lyrics from content array")
                    return plainLines.joinToString("\n")
                } else {
                    val sb = StringBuilder()
                    for (i in 0 until content.length()) {
                        val lineObj = content.getJSONObject(i)
                        val timeMs = lineObj.optLong("timestamp", 0L)
                        val minutes = timeMs / 1000 / 60
                        val seconds = (timeMs / 1000) % 60
                        val centiseconds = (timeMs % 1000) / 10
                        
                        val textArray = lineObj.optJSONArray("text") ?: continue
                        val lineTextSb = StringBuilder()
                        for (j in 0 until textArray.length()) {
                            lineTextSb.append(textArray.getJSONObject(j).optString("text", ""))
                        }
                        val lineText = lineTextSb.toString().trim()
                        
                        if (lineText.isNotEmpty()) {
                            sb.append(String.format(Locale.US, "[%02d:%02d.%02d]%s\n", minutes, seconds, centiseconds, lineText))
                            
                            val wordTimings = mutableListOf<String>()
                            for (j in 0 until textArray.length()) {
                                val wordObj = textArray.getJSONObject(j)
                                val wText = wordObj.optString("text", "").trim()
                                val wStart = wordObj.optDouble("timestamp", 0.0) / 1000.0
                                val wEnd = wordObj.optDouble("endtime", 0.0) / 1000.0
                                if (wText.isNotEmpty()) {
                                    wordTimings.add("$wText:$wStart:$wEnd")
                                }
                            }
                            if (wordTimings.isNotEmpty()) {
                                sb.append("<").append(wordTimings.joinToString("|")).append(">\n")
                            }
                        }
                    }
                    SecureLogger.d(TAG, "Generated syllable timings from content array")
                    return sb.toString().trim()
                }
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Error fetching/parsing Paxsenix lyrics for track $trackId: ${e.message}", e)
        }
        return null
    }
}

package com.vyllo.music.data.lyrics.engine

import com.vyllo.music.BuildConfig
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.model.LyricsResult
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * LRCLIB source extracted from LyricsEngine without behavior change.
 */
object LrclibClient {

    private const val TAG = "LyricsEngine"
    private const val LRCLIB_API = BuildConfig.LYRICS_API_BASE

    /**
     * LRCLIB exact match endpoint.
     * GET /get?track_name=...&artist_name=...&duration=...
     */
    fun tryLrclibExact(client: OkHttpClient, title: String, artist: String, durationSecs: Long): LyricsResult? {
        return try {
            val eTitle = URLEncoder.encode(title, "UTF-8")
            val eArtist = URLEncoder.encode(artist, "UTF-8")
            val url = if (durationSecs > 0) {
                "$LRCLIB_API/get?track_name=$eTitle&artist_name=$eArtist&duration=$durationSecs"
            } else {
                "$LRCLIB_API/get?track_name=$eTitle&artist_name=$eArtist"
            }

            SecureLogger.d(TAG, "  [1/5] LRCLIB exact URL: $url")
            val response = client.newCall(LyricsResponseFactory.newRequest(url)).execute()
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
    fun tryLrclibSearch(client: OkHttpClient, title: String, artist: String, durationSecs: Long): LyricsResult? {
        return try {
            val url = if (artist.isNotBlank()) {
                val eTitle = URLEncoder.encode(title, "UTF-8")
                val eArtist = URLEncoder.encode(artist, "UTF-8")
                "$LRCLIB_API/search?track_name=$eTitle&artist_name=$eArtist"
            } else {
                val eQuery = URLEncoder.encode(title, "UTF-8")
                "$LRCLIB_API/search?q=$eQuery"
            }

            val response = client.newCall(LyricsResponseFactory.newRequest(url)).execute()
            val body = response.body?.string()
            val resultCount = try {
                if (body.isNullOrBlank()) 0 else JSONArray(body).length()
            } catch (_: Exception) { 0 }
            SecureLogger.d(TAG, "  LRCLIB search: HTTP ${response.code}, results=$resultCount")

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
                val titleSim = TitleArtistCleaner.wordJaccard(title.lowercase(), result.trackName.lowercase())
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

    fun parseLrclibResult(json: JSONObject): LyricsResult {
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
            syncedLyrics = syncedLyrics ?: plainLyrics.takeIf { LyricsResponseFactory.looksLikeTimedLyrics(it) }
        )
    }

    fun searchLrclibByQuery(client: OkHttpClient, query: String): List<LyricsResult> {
        val eQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$LRCLIB_API/search?q=$eQuery"
        SecureLogger.d(TAG, "MANUAL SEARCH URL: $url")
        val response = client.newCall(LyricsResponseFactory.newRequest(url)).execute()
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
}

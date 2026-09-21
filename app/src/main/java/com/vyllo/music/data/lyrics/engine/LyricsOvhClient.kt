package com.vyllo.music.data.lyrics.engine

import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.model.LyricsResult
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Lyrics.ovh source extracted from LyricsEngine without behavior change.
 */
object LyricsOvhClient {

    private const val TAG = "LyricsEngine"
    private const val LYRICS_OVH_API = "https://api.lyrics.ovh/v1"

    /**
     * Lyrics.ovh for plain text.
     * GET /v1/Artist/Title
     */
    fun tryLyricsOvh(client: OkHttpClient, title: String, artist: String): LyricsResult? {
        return try {
            val eArtist = URLEncoder.encode(artist, "UTF-8")
            val eTitle = URLEncoder.encode(title, "UTF-8")
            val url = "$LYRICS_OVH_API/$eArtist/$eTitle"

            val response = client.newCall(LyricsResponseFactory.newRequest(url)).execute()
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
}

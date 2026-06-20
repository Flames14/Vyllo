package com.vyllo.music.data.lyrics

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import com.vyllo.music.core.security.SecureLogger
import java.net.URLEncoder

object BetterLyricsProvider {
    private const val TAG = "BetterLyricsProvider"

    fun getLyrics(client: OkHttpClient, title: String, artist: String, durationSecs: Long, album: String?): String? {
        SecureLogger.d(TAG, "getLyrics called for title='$title', artist='$artist', duration=$durationSecs, album='$album'")
        try {
            val eTitle = URLEncoder.encode(title, "UTF-8")
            val eArtist = URLEncoder.encode(artist, "UTF-8")
            var url = "https://lyrics-api.boidu.dev/getLyrics?s=$eTitle&a=$eArtist"
            if (durationSecs > 0) {
                url += "&d=$durationSecs"
            }
            if (!album.isNullOrBlank()) {
                url += "&al=${URLEncoder.encode(album, "UTF-8")}"
            }
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .build()
                
            val response = client.newCall(request).execute()
            if (response.code == 200) {
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val ttml = json.optString("ttml", "").trim()
                if (ttml.isNotEmpty()) {
                    val parsed = TTMLParser.parseTTML(ttml)
                    if (parsed.isNotEmpty()) {
                        SecureLogger.i(TAG, "Successfully fetched and parsed lyrics from BetterLyrics (Boidu API)")
                        return TTMLParser.toLRC(parsed)
                    }
                }
            } else {
                SecureLogger.d(TAG, "Boidu API returned code: ${response.code} (Could mean uncached or unavailable)")
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Error fetching from BetterLyrics: ${e.message}", e)
        }
        return null
    }
}

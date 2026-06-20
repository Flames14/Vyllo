package com.vyllo.music.data.lyrics

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import com.vyllo.music.core.security.SecureLogger
import java.net.URLEncoder
import kotlin.math.abs

object KuGouProvider {
    private const val TAG = "KuGouProvider"
    private const val DURATION_TOLERANCE = 8

    fun getLyrics(client: OkHttpClient, title: String, artist: String, durationSecs: Long, album: String?): String? {
        SecureLogger.d(TAG, "getLyrics called for title='$title', artist='$artist', duration=$durationSecs, album='$album'")
        try {
            val keyword = "$title - $artist"
            val eKeyword = URLEncoder.encode(keyword, "UTF-8")
            
            // 1. Search song to get hash
            val searchUrl = "https://mobileservice.kugou.com/api/v3/search/song?keyword=$eKeyword&version=9108&plat=0&pagesize=8"
            val searchRequest = Request.Builder()
                .url(searchUrl)
                .build()
            val searchResponse = client.newCall(searchRequest).execute()
            if (!searchResponse.isSuccessful) {
                SecureLogger.d(TAG, "Search request failed with status: ${searchResponse.code}")
                return null
            }
            
            val searchBody = searchResponse.body?.string() ?: return null
            val searchJson = JSONObject(searchBody)
            val data = searchJson.optJSONObject("data") ?: return null
            val info = data.optJSONArray("info") ?: return null
            
            var matchedHash: String? = null
            
            for (i in 0 until info.length()) {
                val song = info.getJSONObject(i)
                val songDuration = song.optLong("duration", 0L)
                if (durationSecs <= 0 || abs(songDuration - durationSecs) <= DURATION_TOLERANCE) {
                    matchedHash = song.optString("hash", "")
                    if (matchedHash.isNotEmpty()) {
                        SecureLogger.d(TAG, "Found matched hash by duration: $matchedHash (duration=${songDuration}s)")
                        break
                    }
                }
            }
            
            if (matchedHash.isNullOrEmpty()) {
                SecureLogger.d(TAG, "No song matched the duration tolerance")
                return null
            }
            
            // 2. Search lyric candidate
            val lyricSearchUrl = "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&hash=$matchedHash"
            val lyricSearchRequest = Request.Builder()
                .url(lyricSearchUrl)
                .build()
            val lyricSearchResponse = client.newCall(lyricSearchRequest).execute()
            if (!lyricSearchResponse.isSuccessful) {
                SecureLogger.d(TAG, "Lyrics search failed with status: ${lyricSearchResponse.code}")
                return null
            }
            
            val lyricSearchBody = lyricSearchResponse.body?.string() ?: return null
            val lyricSearchJson = JSONObject(lyricSearchBody)
            val candidates = lyricSearchJson.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) {
                SecureLogger.d(TAG, "No lyrics candidates found for hash $matchedHash")
                return null
            }
            
            val candidate = candidates.getJSONObject(0)
            val id = candidate.optLong("id")
            val accessKey = candidate.optString("accesskey")
            
            // 3. Download lyric
            val downloadUrl = "https://lyrics.kugou.com/download?fmt=lrc&charset=utf8&client=pc&ver=1&id=$id&accesskey=$accessKey"
            val downloadRequest = Request.Builder()
                .url(downloadUrl)
                .build()
            val downloadResponse = client.newCall(downloadRequest).execute()
            if (!downloadResponse.isSuccessful) {
                SecureLogger.d(TAG, "Lyrics download failed with status: ${downloadResponse.code}")
                return null
            }
            
            val downloadBody = downloadResponse.body?.string() ?: return null
            val downloadJson = JSONObject(downloadBody)
            val contentBase64 = downloadJson.optString("content", "")
            if (contentBase64.isNotEmpty()) {
                val decodedBytes = android.util.Base64.decode(contentBase64, android.util.Base64.DEFAULT)
                val rawLrc = String(decodedBytes, Charsets.UTF_8)
                SecureLogger.i(TAG, "Successfully downloaded lyrics from KuGou")
                return cleanKugouLrc(rawLrc)
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Error fetching from KuGou: ${e.message}", e)
        }
        return null
    }

    private fun cleanKugouLrc(lrc: String): String {
        val acceptedRegex = Regex("\\[(\\d\\d):(\\d\\d)\\.(\\d{2,3})\\].*")
        val bannedRegex = Regex(".+\\][^:]+[:：].+")
        
        val lines = lrc.lines().filter { it.matches(acceptedRegex) }
        var headCutLine = 0
        val headLimit = minOf(30, lines.lastIndex)
        for (i in headLimit downTo 0) {
            if (lines[i].matches(bannedRegex)) {
                headCutLine = i + 1
                break
            }
        }
        val filtered = lines.drop(headCutLine)
        
        var tailCutLine = 0
        val tailLimit = minOf(lines.size - 30, filtered.lastIndex)
        if (tailLimit > 0) {
            for (i in tailLimit downTo 0) {
                if (filtered[filtered.lastIndex - i].matches(bannedRegex)) {
                    tailCutLine = i + 1
                    break
                }
            }
        }
        return filtered.dropLast(tailCutLine).joinToString("\n")
    }
}

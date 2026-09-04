package com.vyllo.music.data.network

import android.net.Uri
import com.vyllo.music.core.security.SecureLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeThumbnailResolver @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<String, String>()

    suspend fun resolveHighResThumbnail(
        urlOrId: String,
        fallbackThumbnail: String? = null,
        title: String? = null,
        artist: String? = null
    ): String = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(urlOrId) ?: extractVideoId(fallbackThumbnail ?: "")
        
        // 1. Check in-memory cache
        if (videoId != null && cache.containsKey(videoId)) {
            return@withContext cache[videoId]!!
        }

        // 2. Check if fallback thumbnail is already a Google CDN URL
        if (fallbackThumbnail != null && isGoogleCdnUrl(fallbackThumbnail)) {
            val upgraded = upgradeGoogleCdnUrl(fallbackThumbnail)
            if (videoId != null) cache[videoId] = upgraded
            return@withContext upgraded
        }

        if (videoId == null) {
            return@withContext fallbackThumbnail ?: urlOrId
        }

        try {
            // 3. Query YouTube Music's WEB_REMIX next endpoint
            val nextThumbnail = fetchFromWebRemixNext(videoId)
            if (nextThumbnail != null) {
                cache[videoId] = nextThumbnail
                return@withContext nextThumbnail
            }

            // 4. If next endpoint didn't give a square Google CDN image, try song search on YT Music
            if (!title.isNullOrBlank()) {
                val searchThumbnail = fetchFromWebRemixSearch(title, artist)
                if (searchThumbnail != null) {
                    cache[videoId] = searchThumbnail
                    return@withContext searchThumbnail
                }
            }
        } catch (e: Exception) {
            SecureLogger.w("YouTubeThumbnailResolver", "Failed to resolve high-res thumbnail for $videoId", e)
        }

        // 5. Fallback ladder
        val finalUrl = fallbackThumbnail ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        val upgradedFallback = if (isGoogleCdnUrl(finalUrl)) {
            upgradeGoogleCdnUrl(finalUrl)
        } else {
            finalUrl
        }
        cache[videoId] = upgradedFallback
        upgradedFallback
    }

    private fun fetchFromWebRemixNext(videoId: String): String? {
        val jsonPayload = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", "1.20240101.01.00")
                    put("hl", "en")
                })
            })
            put("videoId", videoId)
        }

        val request = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/next")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Referer", "https://music.youtube.com/")
            .post(jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val responseBody = response.body?.string() ?: return null
            val root = JSONObject(responseBody)

            val tabs = root.optJSONObject("contents")
                ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
                ?.optJSONObject("tabbedRenderer")
                ?.optJSONObject("watchNextTabbedResultsRenderer")
                ?.optJSONArray("tabs") ?: return null

            val playlistPanel = tabs.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("musicQueueRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("playlistPanelRenderer")
                ?.optJSONArray("contents") ?: return null

            val videoRenderer = playlistPanel.optJSONObject(0)
                ?.optJSONObject("playlistPanelVideoRenderer") ?: return null

            val thumbnails = videoRenderer.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails") ?: return null

            var bestUrl: String? = null
            var maxDimension = 0

            for (i in 0 until thumbnails.length()) {
                val thumbObj = thumbnails.optJSONObject(i) ?: continue
                val url = thumbObj.optString("url")
                if (url.isBlank()) continue

                if (isGoogleCdnUrl(url)) {
                    return upgradeGoogleCdnUrl(url)
                }

                val width = thumbObj.optInt("width", 0)
                val height = thumbObj.optInt("height", 0)
                val dimension = width * height
                if (dimension > maxDimension || url.contains("hq720.jpg")) {
                    maxDimension = dimension
                    bestUrl = url
                }
            }

            return bestUrl
        }
    }

    private fun fetchFromWebRemixSearch(title: String, artist: String?): String? {
        val query = if (artist.isNullOrBlank()) title else "$title $artist"
        val jsonPayload = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", "1.20240101.01.00")
                    put("hl", "en")
                })
            })
            put("query", query)
            // Songs filter
            put("params", "Eg-KAQwIARAAGAAgACgAMABqChAEEAMQCRAFEAo%3D")
        }

        val request = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/search")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Referer", "https://music.youtube.com/")
            .post(jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val responseBody = response.body?.string() ?: return null
            val root = JSONObject(responseBody)

            val sectionContents = root.optJSONObject("contents")
                ?.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return null

            val shelfContents = sectionContents.optJSONObject(0)
                ?.optJSONObject("musicShelfRenderer")
                ?.optJSONArray("contents") ?: return null

            val firstSong = shelfContents.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemRenderer") ?: return null

            val thumbnails = firstSong.optJSONObject("thumbnail")
                ?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails") ?: return null

            for (i in 0 until thumbnails.length()) {
                val thumbObj = thumbnails.optJSONObject(i) ?: continue
                val url = thumbObj.optString("url")
                if (isGoogleCdnUrl(url)) {
                    return upgradeGoogleCdnUrl(url)
                }
            }

            return null
        }
    }

    private fun isGoogleCdnUrl(url: String): Boolean {
        return url.contains("googleusercontent.com") || url.contains("ggpht.com")
    }

    private fun upgradeGoogleCdnUrl(url: String): String {
        return when {
            url.contains(Regex("=w\\d+-h\\d+")) ->
                url.replace(Regex("=w\\d+-h\\d+[^?&]*"), "=w1200-h1200-l90-rj")
            url.contains(Regex("=s\\d+")) ->
                url.replace(Regex("=s\\d+[^?&]*"), "=s1200")
            else -> url
        }
    }

    fun extractVideoId(urlOrId: String): String? {
        val trimmed = urlOrId.trim()
        if (trimmed.length == 11 && trimmed.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            return trimmed
        }
        val uri = try { Uri.parse(trimmed) } catch (e: Exception) { return null }
        val vParam = uri.getQueryParameter("v")
        if (!vParam.isNullOrBlank() && vParam.length == 11) {
            return vParam
        }
        if (trimmed.contains("youtu.be/")) {
            val id = trimmed.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
            if (id.length == 11) return id
        }
        if (trimmed.contains("ytimg.com/vi/")) {
            val id = trimmed.substringAfter("ytimg.com/vi/").substringBefore("/")
            if (id.length == 11) return id
        }
        if (trimmed.contains("/vi_webp/")) {
            val id = trimmed.substringAfter("/vi_webp/").substringBefore("/")
            if (id.length == 11) return id
        }
        return null
    }
}

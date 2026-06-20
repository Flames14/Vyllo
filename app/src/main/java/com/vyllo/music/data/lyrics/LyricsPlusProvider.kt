package com.vyllo.music.data.lyrics

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import com.vyllo.music.core.security.SecureLogger
import java.net.URLEncoder

object LyricsPlusProvider {
    private const val TAG = "LyricsPlusProvider"
    private const val BINIMUM_API_BASE_URL = "https://lyrics-api.binimum.org/"
    private val BASE_URLS = listOf(
        "https://lyricsplus.binimum.org",
        "https://lyricsplus.atomix.one",
        "https://lyricsplus.prjktla.my.id",
        "https://lyricsplus-seven.vercel.app"
    )

    @Volatile
    private var lastWorkingServer: String? = null

    private fun getPrioritizedServers(): List<String> {
        val last = lastWorkingServer
        return if (last != null && last in BASE_URLS) {
            listOf(last) + BASE_URLS.filter { it != last }
        } else {
            BASE_URLS
        }
    }

    fun getLyrics(client: OkHttpClient, title: String, artist: String, durationSecs: Long, album: String?): String? {
        SecureLogger.d(TAG, "getLyrics called for title='$title', artist='$artist', duration=$durationSecs, album='$album'")
        
        // 1. Try Binimum API first
        val binimumLrc = fetchBinimumLyrics(client, title, artist, durationSecs, album)
        if (binimumLrc != null) {
            SecureLogger.i(TAG, "Successfully retrieved lyrics from Binimum API")
            return binimumLrc
        }
        
        // 2. Try LyricsPlus mirrors
        for (url in getPrioritizedServers()) {
            val lyricsPlusLrc = fetchLyricsPlusMirror(client, url, title, artist, durationSecs, album)
            if (lyricsPlusLrc != null) {
                lastWorkingServer = url
                SecureLogger.i(TAG, "Successfully retrieved lyrics from LyricsPlus mirror: $url")
                return lyricsPlusLrc
            }
        }
        
        SecureLogger.d(TAG, "No lyrics found from Binimum/LyricsPlus")
        return null
    }

    private fun fetchBinimumLyrics(client: OkHttpClient, title: String, artist: String, durationSecs: Long, album: String?): String? {
        try {
            val eTitle = URLEncoder.encode(title, "UTF-8")
            val eArtist = URLEncoder.encode(artist, "UTF-8")
            var url = "$BINIMUM_API_BASE_URL?track=$eTitle&artist=$eArtist"
            if (durationSecs > 0) {
                url += "&duration=$durationSecs"
            }
            if (!album.isNullOrBlank()) {
                url += "&album=${URLEncoder.encode(album, "UTF-8")}"
            }
            
            val request = Request.Builder()
                .url(url)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return null
            if (results.length() == 0) return null
            
            val selected = results.getJSONObject(0)
            val lyricsUrl = selected.optString("lyricsUrl", "")
            if (lyricsUrl.isEmpty()) return null
            
            val ttmlRequest = Request.Builder().url(lyricsUrl).build()
            val ttmlResponse = client.newCall(ttmlRequest).execute()
            if (!ttmlResponse.isSuccessful) return null
            
            val ttml = ttmlResponse.body?.string() ?: return null
            val parsed = TTMLParser.parseTTML(ttml)
            if (parsed.isNotEmpty()) {
                return TTMLParser.toLRC(parsed)
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Binimum API request failed: ${e.message}")
        }
        return null
    }

    private fun fetchLyricsPlusMirror(client: OkHttpClient, baseUrl: String, title: String, artist: String, durationSecs: Long, album: String?): String? {
        try {
            val eTitle = URLEncoder.encode(title, "UTF-8")
            val eArtist = URLEncoder.encode(artist, "UTF-8")
            var url = "$baseUrl/v2/lyrics/get?title=$eTitle&artist=$eArtist"
            if (durationSecs > 0) {
                url += "&duration=$durationSecs"
            }
            if (!album.isNullOrBlank()) {
                url += "&album=${URLEncoder.encode(album, "UTF-8")}"
            }
            
            val request = Request.Builder()
                .url(url)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            return convertLyricsPlusJsonToLrc(json)
        } catch (e: Exception) {
            SecureLogger.d(TAG, "Mirror request failed for $baseUrl: ${e.message}")
        }
        return null
    }

    private fun convertLyricsPlusJsonToLrc(json: JSONObject): String? {
        val lyrics = json.optJSONArray("lyrics") ?: return null
        if (lyrics.length() == 0) return null
        
        val isWordSync = json.optString("type", "").equals("Word", ignoreCase = true)
        val sb = StringBuilder()
        var lastWasBg = false
        
        // Build agent mappings
        val agentMap = mutableMapOf<String, String>()
        for (i in 0 until lyrics.length()) {
            val line = lyrics.getJSONObject(i)
            val element = line.optJSONObject("element")
            val raw = element?.optString("singer", "")?.lowercase() ?: continue
            if (raw.isNotEmpty() && !agentMap.containsKey(raw)) {
                agentMap[raw] = when (raw) {
                    "v1", "v2", "v1000" -> raw
                    else -> {
                        val taken = agentMap.values.toSet()
                        if ("v1" !in taken) "v1" else if ("v2" !in taken) "v2" else "v1"
                    }
                }
            }
        }
        val isMultiAgent = agentMap.size > 1 || (agentMap.size == 1 && !agentMap.containsKey("v1"))

        for (i in 0 until lyrics.length()) {
            val line = lyrics.getJSONObject(i)
            val time = line.optLong("time", 0L)
            
            val syllabus = line.optJSONArray("syllabus")
            val mainWords = mutableListOf<JSONObject>()
            val bgWords = mutableListOf<JSONObject>()
            if (syllabus != null) {
                for (j in 0 until syllabus.length()) {
                    val word = syllabus.getJSONObject(j)
                    if (word.optBoolean("isBackground", false)) {
                        bgWords.add(word)
                    } else {
                        mainWords.add(word)
                    }
                }
            }
            
            val isFullBgLine = syllabus != null && mainWords.isEmpty() && bgWords.isNotEmpty()
            val textRaw = line.optString("text", "").trim()
            val mainText = when {
                isWordSync && mainWords.isNotEmpty() -> joinWordsText(mainWords)
                isFullBgLine -> ""
                else -> textRaw
            }
            
            if (mainText.isNotEmpty()) {
                lastWasBg = false
                val element = line.optJSONObject("element")
                val singer = element?.optString("singer", "")?.lowercase()
                val agentId = agentMap[singer]
                val agentTag = if (isMultiAgent && agentId != null) "{agent:$agentId}" else ""
                
                sb.append(formatLrcTime(time)).append(agentTag).append(mainText).append("\n")
                if (isWordSync && mainWords.isNotEmpty()) {
                    appendWordBlock(sb, mainWords)
                }
            }
            
            if (bgWords.isNotEmpty()) {
                val bgText = if (isWordSync) joinWordsText(bgWords) else textRaw
                if (bgText.isNotEmpty()) {
                    val bgTime = bgWords.minOf { it.optLong("time", time) }
                    val bgTag = if (lastWasBg) "" else "{bg}"
                    sb.append(formatLrcTime(bgTime)).append(bgTag).append(bgText).append("\n")
                    lastWasBg = true
                    if (isWordSync) {
                        appendWordBlock(sb, bgWords)
                    }
                }
            }
        }
        
        return sb.toString().trim()
    }

    private fun joinWordsText(words: List<JSONObject>): String =
        words.joinToString("") { it.optString("text", "") }.trim()

    private fun appendWordBlock(sb: StringBuilder, words: List<JSONObject>) {
        val valid = words.filter { it.optString("text", "").isNotBlank() }
        if (valid.isEmpty()) return
        sb.append("<")
        valid.forEachIndexed { idx, w ->
            val wText = w.optString("text", "").trim()
            val wTime = w.optLong("time") / 1000.0
            val wDuration = w.optLong("duration") / 1000.0
            sb.append(wText).append(":").append(wTime).append(":").append(wTime + wDuration)
            if (idx < valid.lastIndex) sb.append("|")
        }
        sb.append(">\n")
    }

    private fun formatLrcTime(timeMs: Long): String {
        val m = timeMs / 60000
        val s = (timeMs % 60000) / 1000
        val c = (timeMs % 1000) / 10
        val sb = java.lang.StringBuilder(10)
        sb.append('[')
        if (m < 10) sb.append('0')
        sb.append(m).append(':')
        if (s < 10) sb.append('0')
        sb.append(s).append('.')
        if (c < 10) sb.append('0')
        sb.append(c).append(']')
        return sb.toString()
    }
}

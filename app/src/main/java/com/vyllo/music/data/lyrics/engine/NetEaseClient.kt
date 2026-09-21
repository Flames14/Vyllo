package com.vyllo.music.data.lyrics.engine

import com.vyllo.music.BuildConfig
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.model.LyricsResult
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URLEncoder

/**
 * NetEase source extracted from LyricsEngine without behavior change.
 */
object NetEaseClient {

    private const val TAG = "LyricsEngine"
    private const val NETEASE_SEARCH_API = BuildConfig.NETEASE_SEARCH_API
    private const val NETEASE_LYRIC_API = BuildConfig.NETEASE_LYRIC_API

    /**
     * NetEase for Asian music.
     */
    fun tryNetEase(client: OkHttpClient, title: String, artist: String, durationSecs: Long): LyricsResult? {
        return try {
            val query = "$title $artist".trim()
            val eQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "$NETEASE_SEARCH_API?s=$eQuery&type=1&limit=3"

            val searchResp = client.newCall(LyricsResponseFactory.newRequest(searchUrl, referer = "https://music.163.com/")).execute()
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
                val lyricResp = client.newCall(LyricsResponseFactory.newRequest(lyricUrl, referer = "https://music.163.com/")).execute()
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
}

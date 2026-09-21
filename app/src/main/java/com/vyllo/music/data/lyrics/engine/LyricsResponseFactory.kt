package com.vyllo.music.data.lyrics.engine

import com.vyllo.music.data.lyrics.LrcParser
import com.vyllo.music.domain.model.LyricsResponse
import com.vyllo.music.domain.model.LyricsResult
import com.vyllo.music.domain.model.LyricsStatus
import okhttp3.Request

/**
 * Response building utilities extracted from LyricsEngine without behavior change.
 */
object LyricsResponseFactory {

    private const val TAG = "LyricsEngine"
    private const val CLIENT_USER_AGENT = "Vyllo/1.0 (Android Lyrics Client)"

    fun newRequest(url: String, referer: String? = null): Request {
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

    fun looksLikeTimedLyrics(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return Regex("(?m)^\\s*\\[(\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?)\\]").containsMatchIn(text)
    }

    fun buildResultFromLrc(title: String, artist: String, album: String?, durationSecs: Long, lrc: String): LyricsResult {
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

    fun buildResponse(strategy: String, result: LyricsResult): LyricsResponse {
        val syncedSource = result.syncedLyrics ?: result.plainLyrics?.takeIf { looksLikeTimedLyrics(it) }
        val parsedLines = LrcParser.parseSyncedLyrics(syncedSource)
        val plainLyrics = result.plainLyrics?.let { lyrics ->
            if (parsedLines.isNotEmpty()) {
                parsedLines.joinToString("\n") { it.content }
            } else {
                lyrics
            }
        }
        val detectText = syncedSource ?: plainLyrics ?: ""
        val langs = LrcParser.detectLanguage(detectText)

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
}

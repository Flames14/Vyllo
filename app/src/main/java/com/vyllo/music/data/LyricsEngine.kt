package com.vyllo.music.data

import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.data.lyrics.BetterLyricsProvider
import com.vyllo.music.data.lyrics.KuGouProvider
import com.vyllo.music.data.lyrics.LrcParser
import com.vyllo.music.data.lyrics.LyricsPlusProvider
import com.vyllo.music.data.lyrics.PaxsenixProvider
import com.vyllo.music.data.lyrics.engine.LrclibClient
import com.vyllo.music.data.lyrics.engine.LyricsOvhClient
import com.vyllo.music.data.lyrics.engine.LyricsResponseFactory
import com.vyllo.music.data.lyrics.engine.NetEaseClient
import com.vyllo.music.data.lyrics.engine.TitleArtistCleaner
import com.vyllo.music.domain.model.LyricsResponse
import com.vyllo.music.domain.model.LyricsResult
import com.vyllo.music.domain.model.SyncedLyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Multi-Source Lyrics Engine (orchestrator). Delegates to data/lyrics/engine/ without behavior change. */
@Singleton
class LyricsEngine @Inject constructor(
    @Named("lyrics") private val client: OkHttpClient
) {
    companion object {
        private const val TAG = "LyricsEngine"

        fun detectLanguage(text: String): List<String> = LrcParser.detectLanguage(text)
        fun parseSyncedLyrics(lrcString: String?): List<SyncedLyricLine> = LrcParser.parseSyncedLyrics(lrcString)
        fun getCurrentLyricLine(syncedLyrics: List<SyncedLyricLine>?, currentTimeMs: Long): Int =
            LrcParser.getCurrentLyricLine(syncedLyrics, currentTimeMs)
    }
    fun isRecordLabel(name: String): Boolean = TitleArtistCleaner.isRecordLabel(name)
    /** Main entry point. Tries multiple strategies in order of reliability. */
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
        val (cleanedTitle, cleanedArtist) = TitleArtistCleaner.cleanTitleAndArtist(rawTitle, rawArtist)
        val cleanTitle = if (!extractedTitle.isNullOrBlank()) extractedTitle.trim() else cleanedTitle
        var cleanArtist = when {
            !extractedArtist.isNullOrBlank() -> extractedArtist.trim()
            !extractedMusic.isNullOrBlank() -> extractedMusic.trim()
            else -> cleanedArtist
        }
        if (TitleArtistCleaner.isRecordLabel(cleanArtist) && !extractedMusic.isNullOrBlank()) {
            cleanArtist = extractedMusic.trim()
        }
        if (TitleArtistCleaner.isRecordLabel(cleanArtist)) {
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
            val paxLrc = PaxsenixProvider.getLyrics(client, cleanTitle, cleanArtist, durationSecs, extractedAlbum)
            if (paxLrc != null) {
                SecureLogger.d(TAG, "FOUND via Paxsenix: $cleanTitle")
                val lyricsResult = LyricsResponseFactory.buildResultFromLrc(cleanTitle, cleanArtist, extractedAlbum, durationSecs, paxLrc)
                return@withContext LyricsResponseFactory.buildResponse("PAXSENIX", lyricsResult)
            }
        }
        // Strategy 2: BetterLyrics (Boidu cached Timed-Text XML API)
        if (cleanTitle.isNotBlank() && cleanArtist.isNotBlank()) {
            SecureLogger.d(TAG, "Strategy 2: BetterLyrics (title='$cleanTitle', artist='$cleanArtist')")
            val betterLrc = BetterLyricsProvider.getLyrics(client, cleanTitle, cleanArtist, durationSecs, extractedAlbum)
            if (betterLrc != null) {
                SecureLogger.d(TAG, "FOUND via BetterLyrics: $cleanTitle")
                val lyricsResult = LyricsResponseFactory.buildResultFromLrc(cleanTitle, cleanArtist, extractedAlbum, durationSecs, betterLrc)
                return@withContext LyricsResponseFactory.buildResponse("BETTERLYRICS", lyricsResult)
            }
        }
        // Strategy 3: LRCLIB exact match with clean data
        if (cleanTitle.isNotBlank() && cleanArtist.isNotBlank()) {
            SecureLogger.d(TAG, "Strategy 3: LRCLIB exact (title='$cleanTitle', artist='$cleanArtist')")
            val result = LrclibClient.tryLrclibExact(client, cleanTitle, cleanArtist, durationSecs)
            if (result != null) {
                SecureLogger.d(TAG, "FOUND via LRCLIB exact: ${result.trackName}")
                return@withContext LyricsResponseFactory.buildResponse("LRCLIB_EXACT", result)
            }
        }
        // Strategy 4: LRCLIB structured search (track_name + artist_name)
        if (cleanTitle.isNotBlank() && cleanArtist.isNotBlank()) {
            SecureLogger.d(TAG, "Strategy 4: LRCLIB search (track_name='$cleanTitle', artist_name='$cleanArtist')")
            val result = LrclibClient.tryLrclibSearch(client, cleanTitle, cleanArtist, durationSecs)
            if (result != null) {
                SecureLogger.d(TAG, "FOUND via LRCLIB search: ${result.trackName}")
                return@withContext LyricsResponseFactory.buildResponse("LRCLIB_SEARCH", result)
            }
        }
        // Strategy 5: LRCLIB title-only search (most reliable fallback)
        SecureLogger.d(TAG, "Strategy 5: LRCLIB title-only search (title='$cleanTitle')")
        val titleOnly = LrclibClient.tryLrclibSearch(client, cleanTitle, "", durationSecs)
        if (titleOnly != null) {
            SecureLogger.d(TAG, "FOUND via LRCLIB title-only: ${titleOnly.trackName}")
            return@withContext LyricsResponseFactory.buildResponse("LRCLIB_TITLE_ONLY", titleOnly)
        }
        // Strategy 6: LyricsPlus / Binimum API (mirrors and metadata search)
        if (cleanTitle.isNotBlank() && cleanArtist.isNotBlank()) {
            SecureLogger.d(TAG, "Strategy 6: LyricsPlus (title='$cleanTitle', artist='$cleanArtist')")
            val plusLrc = LyricsPlusProvider.getLyrics(client, cleanTitle, cleanArtist, durationSecs, extractedAlbum)
            if (plusLrc != null) {
                SecureLogger.d(TAG, "FOUND via LyricsPlus: $cleanTitle")
                val lyricsResult = LyricsResponseFactory.buildResultFromLrc(cleanTitle, cleanArtist, extractedAlbum, durationSecs, plusLrc)
                return@withContext LyricsResponseFactory.buildResponse("LYRICS_PLUS", lyricsResult)
            }
        }
        // Strategy 7: KuGou (East Asian/regional and instrumental)
        if (cleanTitle.isNotBlank() && cleanArtist.isNotBlank()) {
            SecureLogger.d(TAG, "Strategy 7: KuGou (title='$cleanTitle', artist='$cleanArtist')")
            val kugouLrc = KuGouProvider.getLyrics(client, cleanTitle, cleanArtist, durationSecs, extractedAlbum)
            if (kugouLrc != null) {
                SecureLogger.d(TAG, "FOUND via KuGou: $cleanTitle")
                val lyricsResult = LyricsResponseFactory.buildResultFromLrc(cleanTitle, cleanArtist, extractedAlbum, durationSecs, kugouLrc)
                return@withContext LyricsResponseFactory.buildResponse("KUGOU", lyricsResult)
            }
        }
        // Strategy 8: Lyrics.ovh (plain text only)
        if (cleanTitle.isNotBlank() && cleanArtist.isNotBlank()) {
            SecureLogger.d(TAG, "Strategy 8: Lyrics.ovh")
            val ovh = LyricsOvhClient.tryLyricsOvh(client, cleanTitle, cleanArtist)
            if (ovh != null) {
                SecureLogger.d(TAG, "FOUND via Lyrics.ovh: ${ovh.trackName}")
                return@withContext LyricsResponseFactory.buildResponse("LYRICS_OVH", ovh)
            }
        }
        // Strategy 9: NetEase (Asian music)
        SecureLogger.d(TAG, "Strategy 9: NetEase")
        val netease = NetEaseClient.tryNetEase(client, cleanTitle, cleanArtist, durationSecs)
        if (netease != null) {
            SecureLogger.d(TAG, "FOUND via NetEase: ${netease.trackName}")
            return@withContext LyricsResponseFactory.buildResponse("NETEASE", netease)
        }

        SecureLogger.w(TAG, "=== ALL LYRICS SOURCES FAILED ===")
        return@withContext LyricsResponse(
            success = false,
            strategy = "ALL_FAILED",
            error = "No lyrics found from any source."
        )
    }
    suspend fun searchLyrics(query: String): List<LyricsResult> = withContext(Dispatchers.IO) {
        SecureLogger.d(TAG, "MANUAL SEARCH: query='$query'")
        return@withContext try {
            val results = linkedMapOf<Long, LyricsResult>()

            LrclibClient.searchLrclibByQuery(client, query).forEach { results[it.id] = it }

            if (results.isEmpty()) {
                TitleArtistCleaner.extractArtistAndTitleFromQuery(query)?.let { (artist, title) ->
                    SecureLogger.d(TAG, "MANUAL SEARCH: retrying structured search with artist='$artist' title='$title'")
                    LrclibClient.tryLrclibSearch(client, title, artist, durationSecs = 0)?.let { results[it.id] = it }
                }
            }

            val finalResults = results.values.toList()
            SecureLogger.d(TAG, "MANUAL SEARCH: returning ${finalResults.size} results")
            finalResults
        } catch (e: Exception) {
            SecureLogger.e(TAG, "MANUAL SEARCH error: ${e.message}")
            emptyList()
        }
    }
    suspend fun getLyricsById(id: Long): LyricsResult? = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "${com.vyllo.music.BuildConfig.LYRICS_API_BASE}/get/$id"
            val response = client.newCall(LyricsResponseFactory.newRequest(url)).execute()
            if (response.isSuccessful) {
                response.body?.string()?.let { body ->
                    if (body.isNotBlank()) {
                        return@withContext LrclibClient.parseLrclibResult(JSONObject(body))
                    }
                }
            }
            null
        } catch (e: Exception) {
            SecureLogger.e(TAG, "GetById error: ${e.message}")
            null
        }
    }
}

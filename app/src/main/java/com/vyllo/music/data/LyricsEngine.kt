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
import com.vyllo.music.data.manager.PreferenceManager
import com.vyllo.music.domain.model.LyricsResponse
import com.vyllo.music.domain.model.LyricsResult
import com.vyllo.music.domain.model.LyricsSource
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
    @Named("lyrics") private val client: OkHttpClient,
    private val preferenceManager: PreferenceManager
) {
    companion object {
        private const val TAG = "LyricsEngine"

        fun detectLanguage(text: String): List<String> = LrcParser.detectLanguage(text)
        fun parseSyncedLyrics(lrcString: String?): List<SyncedLyricLine> = LrcParser.parseSyncedLyrics(lrcString)
        fun getCurrentLyricLine(syncedLyrics: List<SyncedLyricLine>?, currentTimeMs: Long): Int =
            LrcParser.getCurrentLyricLine(syncedLyrics, currentTimeMs)
    }
    fun isRecordLabel(name: String): Boolean = TitleArtistCleaner.isRecordLabel(name)
    /** Main entry point. Tries the user-ordered list of sources. */
    suspend fun findLyricsUniversal(
        rawTitle: String,
        rawArtist: String,
        durationSecs: Long,
        extractedTitle: String? = null,
        extractedArtist: String? = null,
        extractedAlbum: String? = null,
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

        // The chain is user-ordered. Sources that need both title and artist are
        // skipped when cleaning left no artist, but they keep their slot: a user
        // who moved a provider up should not silently get a different one.
        val order = LyricsSource.parse(preferenceManager.lyricsSourceOrder)
        order.forEachIndexed { index, source ->
            if (source.needsArtist && cleanArtist.isBlank()) return@forEachIndexed
            SecureLogger.d(TAG, "Source ${index + 1}/${order.size}: ${source.key}")
            val response = trySource(source, cleanTitle, cleanArtist, extractedAlbum, durationSecs)
            if (response != null) {
                SecureLogger.d(TAG, "FOUND via ${source.key}: $cleanTitle")
                return@withContext response
            }
        }

        SecureLogger.w(TAG, "=== ALL LYRICS SOURCES FAILED ===")
        return@withContext LyricsResponse(
            success = false,
            strategy = "ALL_FAILED",
            error = "No lyrics found from any source."
        )
    }

    /**
     * One provider attempt. Null means "not found" and lets the caller continue
     * down the chain; a hit is returned as a finished [LyricsResponse] because
     * the result carries the strategy name the UI reports.
     */
    private suspend fun trySource(
        source: LyricsSource,
        cleanTitle: String,
        cleanArtist: String,
        extractedAlbum: String?,
        durationSecs: Long
    ): LyricsResponse? {
        fun lrc(raw: String?, strategy: String): LyricsResponse? {
            if (raw == null) return null
            val result = LyricsResponseFactory.buildResultFromLrc(
                cleanTitle, cleanArtist, extractedAlbum, durationSecs, raw
            )
            return LyricsResponseFactory.buildResponse(strategy, result)
        }
        fun structured(result: LyricsResult?, strategy: String): LyricsResponse? =
            result?.let { LyricsResponseFactory.buildResponse(strategy, it) }

        return when (source) {
            LyricsSource.PAXSENIX -> lrc(
                PaxsenixProvider.getLyrics(client, cleanTitle, cleanArtist, durationSecs, extractedAlbum),
                "PAXSENIX"
            )
            LyricsSource.BETTER_LYRICS -> lrc(
                BetterLyricsProvider.getLyrics(client, cleanTitle, cleanArtist, durationSecs, extractedAlbum),
                "BETTERLYRICS"
            )
            LyricsSource.LRCLIB_EXACT -> structured(
                LrclibClient.tryLrclibExact(client, cleanTitle, cleanArtist, durationSecs),
                "LRCLIB_EXACT"
            )
            LyricsSource.LRCLIB_SEARCH -> structured(
                LrclibClient.tryLrclibSearch(client, cleanTitle, cleanArtist, durationSecs),
                "LRCLIB_SEARCH"
            )
            LyricsSource.LYRICS_PLUS -> lrc(
                LyricsPlusProvider.getLyrics(client, cleanTitle, cleanArtist, durationSecs, extractedAlbum),
                "LYRICS_PLUS"
            )
            LyricsSource.KUGOU -> lrc(
                KuGouProvider.getLyrics(client, cleanTitle, cleanArtist, durationSecs, extractedAlbum),
                "KUGOU"
            )
            LyricsSource.NETEASE -> structured(
                NetEaseClient.tryNetEase(client, cleanTitle, cleanArtist, durationSecs),
                "NETEASE"
            )
            LyricsSource.LYRICS_OVH -> structured(
                LyricsOvhClient.tryLyricsOvh(client, cleanTitle, cleanArtist),
                "LYRICS_OVH"
            )
            LyricsSource.LRCLIB_TITLE_ONLY -> structured(
                LrclibClient.tryLrclibSearch(client, cleanTitle, "", durationSecs),
                "LRCLIB_TITLE_ONLY"
            )
        }
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

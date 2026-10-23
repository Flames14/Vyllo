package com.vyllo.music.data.lastfm

import com.vyllo.music.domain.model.LastFmScrobbleRules
import com.vyllo.music.domain.model.LastFmSession
import com.vyllo.music.domain.model.LastFmSignature
import com.vyllo.music.domain.model.LastFmTrack
import com.vyllo.music.domain.model.PendingScrobble
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Thrown when Last.fm answers with its documented `error` payload. */
class LastFmApiException(val code: Int, message: String) : IOException(message) {
    val isRetryable: Boolean
        get() = code in RETRYABLE_CODES

    companion object {
        private val RETRYABLE_CODES = setOf(8, 11, 16, 29)
    }
}

/**
 * Minimal Last.fm 2.0 client. Credentials are supplied by the user (Vyllo
 * ships no shared app secret); only the documented auth, now-playing and
 * scrobble methods are used.
 */
@Singleton
class LastFmApi @Inject constructor(
    private val client: OkHttpClient
) {
    suspend fun getToken(apiKey: String, apiSecret: String): String {
        val params = mapOf(
            "method" to "auth.getToken",
            "api_key" to apiKey
        )
        val json = call(params, apiSecret)
        return json.optString("token").takeIf { it.isNotBlank() }
            ?: throw IOException("Last.fm did not return a token")
    }

    suspend fun getSession(apiKey: String, apiSecret: String, token: String): LastFmSession {
        val params = mapOf(
            "method" to "auth.getSession",
            "api_key" to apiKey,
            "token" to token
        )
        val session = call(params, apiSecret).optJSONObject("session")
            ?: throw IOException("Last.fm did not return a session")
        val key = session.optString("key")
        val name = session.optString("name")
        if (key.isBlank()) throw IOException("Last.fm session has no key")
        return LastFmSession(username = name, sessionKey = key)
    }

    suspend fun updateNowPlaying(
        apiKey: String,
        apiSecret: String,
        sessionKey: String,
        track: LastFmTrack
    ) {
        val params = trackParams("track.updateNowPlaying", apiKey, sessionKey, track)
        call(params, apiSecret)
    }

    suspend fun scrobble(
        apiKey: String,
        apiSecret: String,
        sessionKey: String,
        scrobble: PendingScrobble
    ) {
        val params = trackParams("track.scrobble", apiKey, sessionKey, scrobble.track) +
            ("timestamp" to scrobble.timestampSecs.toString())
        call(params, apiSecret)
    }

    fun authUrl(apiKey: String, token: String): String {
        return "https://www.last.fm/api/auth/".toHttpUrl().newBuilder()
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("token", token)
            .build()
            .toString()
    }

    private fun trackParams(
        method: String,
        apiKey: String,
        sessionKey: String,
        track: LastFmTrack
    ): Map<String, String> {
        val params = mutableMapOf(
            "method" to method,
            "api_key" to apiKey,
            "sk" to sessionKey,
            "artist" to track.artist,
            "track" to track.title
        )
        track.album?.takeIf { it.isNotBlank() }?.let { params["album"] = it }
        if (track.durationSecs > 0) params["duration"] = track.durationSecs.toString()
        if (method == "track.scrobble") params["chosenByUser"] = "0"
        return params
    }

    private suspend fun call(params: Map<String, String>, apiSecret: String): JSONObject {
        val signature = LastFmSignature.sign(params, apiSecret)
        val body = FormBody.Builder()
            .apply {
                params.forEach { (key, value) -> add(key, value) }
                add("api_sig", signature)
                add("format", "json")
            }
            .build()
        val request = Request.Builder()
            .url(API_ENDPOINT)
            .post(body)
            .header("User-Agent", "Vyllo/1.0 (Last.fm scrobbler)")
            .build()

        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrNull()
                    ?: throw IOException("Last.fm returned an invalid response")
                val errorCode = json.optInt("error", 0)
                if (errorCode != 0) {
                    throw LastFmApiException(
                        code = errorCode,
                        message = json.optString("message").ifBlank { "Last.fm error $errorCode" }
                    )
                }
                if (!response.isSuccessful) {
                    throw IOException("Last.fm returned HTTP ${response.code}")
                }
                json
            }
        }
    }

    companion object {
        private const val API_ENDPOINT = "https://ws.audioscrobbler.com/2.0/"
        const val MAX_PENDING_SCROBBLES = 200
    }
}

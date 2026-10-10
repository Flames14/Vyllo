package com.vyllo.music.data.repository.delegates

import com.vyllo.music.core.security.SecureLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Open so the probe behaviour can be substituted in unit tests — the concurrency
 * contract of [firstPlayableStreamUrl] is not observable without hitting a real host.
 */
open class StreamSelector @Inject constructor() {
    private val streamProbeClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
 * Recovery path for a rejected stream URL.
 *
 * Probes every candidate **concurrently** and returns the first that answers, so
 * the serial probe chain that used to sit on the tap path costs one round trip
 * instead of N.
 */
suspend fun firstPlayableStreamUrl(candidates: List<String>): String? {
    if (candidates.isEmpty()) return null
    if (candidates.size == 1) return candidates.first().takeIf { isPlayableStreamUrl(it) }

    return coroutineScope {
        val probes = candidates.map { url ->
            async(Dispatchers.IO) { url to isPlayableStreamUrl(url) }
        }
        // awaitAll keeps every probe concurrent; the first *successful* candidate in
        // original preference order wins, so quality ordering is preserved.
        probes.awaitAll().firstOrNull { it.second }?.first
    }
}

/**
 * Audio stream URLs in preference order (highest bitrate first).
 *
 * Selection is deliberately **probe-free**: this used to issue a real
 * `bytes=0-1` HTTP request per candidate until one answered, adding one or more
 * round trips to every single tap. The happy path now just takes the first entry
 * and lets ExoPlayer surface a 403 through `PlayerErrorListener`, which re-enters
 * via `force = true` and uses [firstPlayableStreamUrl].
 */
fun audioCandidates(streams: List<AudioStream>): List<String> {
    logStreams(streams) { "${it.format}, bitrate=${it.averageBitrate}" }
    return streams.asSequence()
        .filter { it.isUrl && !it.url.isNullOrBlank() }
        .sortedByDescending { it.averageBitrate }
        .mapNotNull { it.url }
        .toList()
}

/** Video stream URLs, highest bitrate first. Same lazy-validation contract. */
fun videoCandidates(streams: List<VideoStream>): List<String> {
    logStreams(streams) { "${it.format}, bitrate=${it.bitrate}" }
    return streams.asSequence()
        .filter { it.isUrl && !it.url.isNullOrBlank() }
        .sortedByDescending { it.bitrate }
        .mapNotNull { it.url }
        .toList()
}

/**
 * Audio fallback when no audio-only stream exists: lowest-resolution video stream
 * first, since only its audio is used and bandwidth matters.
 */
fun videoCandidatesForAudio(streams: List<VideoStream>): List<String> {
    logStreams(streams) { "${it.format}, bitrate=${it.bitrate}" }
    return streams.asSequence()
        .filter { it.isUrl && !it.url.isNullOrBlank() }
        .sortedBy { it.bitrate }
        .mapNotNull { it.url }
        .toList()
}

private fun <T> logStreams(streams: List<T>, describe: (T) -> String) {
    if (!SecureLogger.isDebugEnabled) return
    streams.forEach { s -> SecureLogger.d("MusicRepositoryImpl") { "Available stream: ${describe(s)}" } }
}

    open fun isPlayableStreamUrl(streamUrl: String): Boolean {
        return try {
            val ua = userAgentForStreamUrl(streamUrl)
            val request = Request.Builder()
                .url(streamUrl)
                .header("Range", "bytes=0-1")
                .header("User-Agent", ua)
                .build()

            streamProbeClient.newCall(request).execute().use { response ->
                val playable = response.isSuccessful || response.code == 206
                SecureLogger.d("MusicRepositoryImpl") {
                    "Probe result: http=${response.code}, playable=$playable, client=${clientNameFromUrl(streamUrl)}, UA=$ua"
                }
                if (!playable) {
                    SecureLogger.w(
                        "MusicRepositoryImpl",
                        "Rejected stream candidate: http=${response.code}, client=${clientNameFromUrl(streamUrl)}"
                    )
                }
                playable
            }
        } catch (e: Exception) {
            SecureLogger.w(
                "MusicRepositoryImpl",
                "Rejected stream candidate: ${e.javaClass.simpleName}, client=${clientNameFromUrl(streamUrl)}",
                e
            )
            false
        }
    }

    fun userAgentForStreamUrl(streamUrl: String): String {
        return when {
            streamUrl.contains("c=IOS", ignoreCase = true) ->
                "com.google.ios.youtube/21.03.2(iPhone16,2; U; CPU iOS 18_7_2 like Mac OS X; US)"
            streamUrl.contains("c=ANDROID", ignoreCase = true) ->
                "com.google.android.youtube/21.03.36 (Linux; U; Android 15; US) gzip"
            else ->
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        }
    }

    fun clientNameFromUrl(streamUrl: String): String {
        return when {
            streamUrl.contains("c=IOS", ignoreCase = true) -> "IOS"
            streamUrl.contains("c=ANDROID", ignoreCase = true) -> "ANDROID"
            streamUrl.contains("c=WEB", ignoreCase = true) -> "WEB"
            else -> "UNKNOWN"
        }
    }

    fun fetchUrlContent(url: String, userAgent: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .build()
            streamProbeClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: Exception) {
            SecureLogger.e("MusicRepositoryImpl", "Failed to download content: ${e.message}", e)
            null
        }
    }
}

package com.vyllo.music.data.repository.delegates

import com.vyllo.music.core.security.SecureLogger
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamSelector @Inject constructor() {
    private val streamProbeClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun selectPlayableAudioUrl(streams: List<AudioStream>): String? {
        streams.forEach { stream ->
            SecureLogger.d("MusicRepositoryImpl") { "Available stream: format=${stream.format}, bitrate=${stream.averageBitrate}, url=${stream.url}" }
        }
        return streams
            .asSequence()
            .filter { it.isUrl && !it.url.isNullOrBlank() }
            .sortedByDescending { it.averageBitrate }
            .mapNotNull { it.url }
            .firstOrNull(::isPlayableStreamUrl)
    }

    fun selectPlayableVideoUrl(streams: List<VideoStream>): String? {
        streams.forEach { stream ->
            SecureLogger.d("MusicRepositoryImpl") { "Available video stream: format=${stream.format}, bitrate=${stream.bitrate}, url=${stream.url}" }
        }
        return streams
            .asSequence()
            .filter { it.isUrl && !it.url.isNullOrBlank() }
            .sortedByDescending { it.bitrate }
            .mapNotNull { it.url }
            .firstOrNull(::isPlayableStreamUrl)
    }

    fun selectPlayableVideoUrlForAudio(streams: List<VideoStream>): String? {
        streams.forEach { stream ->
            SecureLogger.d("MusicRepositoryImpl") { "Available video stream for audio: format=${stream.format}, bitrate=${stream.bitrate}, url=${stream.url}" }
        }
        return streams
            .asSequence()
            .filter { it.isUrl && !it.url.isNullOrBlank() }
            // Sort by ascending bitrate to get the lowest-resolution video stream
            // to save bandwidth, while maintaining standard audio quality.
            .sortedBy { it.bitrate }
            .mapNotNull { it.url }
            .firstOrNull(::isPlayableStreamUrl)
    }

    fun isPlayableStreamUrl(streamUrl: String): Boolean {
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

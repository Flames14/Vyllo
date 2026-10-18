package com.vyllo.music.service.audio

import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.core.security.SecurityConfig
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Builds the OkHttp client ExoPlayer uses for media delivery.
 *
 * Extracted from MusicService — no behavior change. A hardened base (DoH + TLS
 * + security headers) is requested first and, if it is unavailable, the tuning
 * is applied to a plain builder so playback still works. `followSslRedirects`
 * stays false so an HTTPS manifest can never be downgraded to HTTP.
 */
object PlaybackHttpClientFactory {

    fun create(): OkHttpClient {
        val secureBase = runCatching {
            SecurityConfig().createFlexibleSecureHttpClient()
        }.getOrNull()
        return (secureBase?.newBuilder() ?: OkHttpClient.Builder())
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .readTimeout(15, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(false)
            .addInterceptor(::tagGooglevideoRequest)
            .build()
    }

    /**
     * googlevideo.com rejects requests whose User-Agent does not match the client
     * named in the URL's `c=` parameter, so the header is rewritten per request to
     * track the media client NewPipe negotiated. Non-googlevideo hosts pass through
     * untouched. Accept-Encoding is forced to identity because ExoPlayer reads the
     * raw stream.
     */
    private fun tagGooglevideoRequest(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val url = request.url.toString()
        if (!request.url.host.contains("googlevideo.com")) {
            return chain.proceed(request)
        }
        val userAgent = when {
            url.contains("c=ANDROID", ignoreCase = true) ->
                "com.google.android.youtube/21.03.36 (Linux; U; Android 15; US) gzip"
            url.contains("c=IOS", ignoreCase = true) ->
                "com.google.ios.youtube/21.03.2(iPhone16,2; U; CPU iOS 18_7_2 like Mac OS X; US)"
            url.contains("c=WEB", ignoreCase = true) ->
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"
            else -> request.header("User-Agent")
        } ?: return chain.proceed(request)

        val playbackRequest = request.newBuilder()
            .header("User-Agent", userAgent)
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")
            .build()
        SecureLogger.d("MusicService") {
            val hdrs = playbackRequest.headers.toMultimap().entries.joinToString { "${it.key}=${it.value}" }
            "ExoPlayer request: method=${playbackRequest.method}, url_prefix=${url.take(120)}, headers=[$hdrs]"
        }
        val response = chain.proceed(playbackRequest)
        if (!response.isSuccessful) {
            val headerNames = response.headers.toMultimap().keys.joinToString()
            SecureLogger.w(
                "MusicService",
                "googlevideo REJECTED: code=${response.code}, client=${clientNameFromUrl(url)}, header_names=[$headerNames]"
            )
        } else {
            SecureLogger.d("MusicService") { "googlevideo ACCEPTED: code=${response.code}, client=${clientNameFromUrl(url)}" }
        }
        return response
    }

    private fun clientNameFromUrl(url: String): String = when {
        url.contains("c=IOS", ignoreCase = true) -> "IOS"
        url.contains("c=ANDROID", ignoreCase = true) -> "ANDROID"
        url.contains("c=WEB", ignoreCase = true) -> "WEB"
        else -> "UNKNOWN"
    }
}

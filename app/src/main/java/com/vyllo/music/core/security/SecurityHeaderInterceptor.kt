package com.vyllo.music.core.security

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor that adds baseline security headers.
 * Does NOT force Cache-Control on every request — that would disable
 * image/thumbnail disk caching app-wide (severe scroll/bandwidth cost).
 */
class SecurityHeaderInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val securedRequest = originalRequest.newBuilder()
            // Blend in with mainstream browser traffic when no UA is set
            .header(
                "User-Agent",
                originalRequest.header("User-Agent")
                    ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            .addHeader("X-Content-Type-Options", "nosniff")
            .addHeader("X-Frame-Options", "DENY")
            .addHeader("Referrer-Policy", "strict-origin-when-cross-origin")
            .build()

        return chain.proceed(securedRequest)
    }
}

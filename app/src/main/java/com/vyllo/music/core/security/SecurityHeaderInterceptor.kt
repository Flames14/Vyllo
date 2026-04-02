package com.vyllo.music.core.security

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor that adds security headers to all outgoing requests
 * and removes identifying information
 */
class SecurityHeaderInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val securedRequest = originalRequest.newBuilder()
            // Remove identifying User-Agent (blend in with browser traffic)
            .removeHeader("User-Agent")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            
            // Security Headers
            .addHeader("X-Content-Type-Options", "nosniff")
            .addHeader("X-Frame-Options", "DENY")
            .addHeader("Referrer-Policy", "strict-origin-when-cross-origin")
            
            // Cache Control for sensitive requests
            .addHeader("Cache-Control", "no-store, no-cache, must-revalidate")
            .addHeader("Pragma", "no-cache")
            
            .build()

        return chain.proceed(securedRequest)
    }
}

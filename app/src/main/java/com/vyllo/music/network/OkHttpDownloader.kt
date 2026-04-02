package com.vyllo.music.network

import com.vyllo.music.core.security.SecurityConfig
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.TimeUnit

/**
 * Secure OkHttp Downloader for NewPipe Extractor
 * 
 * Uses SecurityConfig for:
 * - Encrypted DNS (DoH)
 * - Modern TLS only
 * - Security headers
 * - Connection hardening
 */
class OkHttpDownloader : Downloader() {

    // Secure HTTP client with DoH and security hardening
    // Note: Certificate pinning is disabled for NewPipe as it connects to many hosts
    private val client: OkHttpClient = SecurityConfig().createFlexibleSecureHttpClient()
        .newBuilder()
        // Connection reuse
        .connectionPool(okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES))
        .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
        // Reasonable timeouts
        .readTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder = okhttp3.Request.Builder()
            .url(url)
            // Security: Use common browser User-Agent to blend in
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

        // Add all headers
        headers.forEach { (key, list) ->
            list.forEach { value ->
                requestBuilder.addHeader(key, value)
            }
        }

        // Handle HTTP methods
        when (httpMethod) {
            "POST" -> {
                val body = dataToSend?.toRequestBody() ?: ByteArray(0).toRequestBody()
                requestBuilder.post(body)
            }
            "GET" -> requestBuilder.get()
            "HEAD" -> requestBuilder.head()
            "PUT" -> {
                val body = dataToSend?.toRequestBody() ?: ByteArray(0).toRequestBody()
                requestBuilder.put(body)
            }
        }

        // Execute Request
        val response = client.newCall(requestBuilder.build()).execute()

        // Convert back to NewPipe Response
        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            response.body?.string(),
            response.request.url.toString()
        )
    }
}

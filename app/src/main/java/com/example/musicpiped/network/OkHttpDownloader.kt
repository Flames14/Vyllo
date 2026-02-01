package com.example.musicpiped.network

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.TimeUnit

class OkHttpDownloader : Downloader() {
    // Use a singleton OkHttpClient to share connection pools
    private val client: OkHttpClient = OkHttpClient.Builder()
        // Connection reuse: Keep connections alive for 5 minutes, pool up to 10 idle connections
        .connectionPool(okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES))
        .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
        // Short timeouts for "instant" feel
        .readTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend() // byte[]

        val requestBuilder = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")

        // Add all headers
        headers.forEach { (key, list) ->
            list.forEach { value ->
                requestBuilder.addHeader(key, value)
            }
        }

        // Handle POST/GET/etc
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
            // Add others if necessary
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
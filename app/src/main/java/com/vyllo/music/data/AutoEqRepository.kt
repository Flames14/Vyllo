package com.vyllo.music.data

import androidx.annotation.VisibleForTesting
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches AutoEQ correction data. The wizard accepts a raw GitHub URL (or any
 * direct CSV/text link) and returns the body for [com.vyllo.music.domain.model.AutoEqParser].
 */
@Singleton
class AutoEqRepository @Inject constructor(
    private val client: OkHttpClient
) {
    @VisibleForTesting
    internal var maxBytes: Long = 512 * 1024

    /** Returns the profile text, or throws [IOException] with a user-safe reason. */
    suspend fun fetch(rawUrl: String): String {
        val url = rawUrl.trim()
        require(url.startsWith("http://") || url.startsWith("https://")) {
            "Only http(s) links are supported"
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Vyllo/1.0 (AutoEQ import)")
            .header("Accept", "text/plain, text/csv, */*")
            .build()

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Server returned ${response.code}")
                }
                val body = response.body ?: throw IOException("Empty response")
                if (body.contentLength() > maxBytes) {
                    throw IOException("Profile is too large")
                }
                val text = body.string()
                if (text.length > maxBytes) throw IOException("Profile is too large")
                text
            }
        }
    }
}

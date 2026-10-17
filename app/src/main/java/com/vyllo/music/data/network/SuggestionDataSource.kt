package com.vyllo.music.data.network

import com.vyllo.music.core.security.SecureLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SuggestionDataSource @Inject constructor(private val client: OkHttpClient) {

    /**
     * Suggestions are fetched per keystroke: a bound call prevents a hung
     * connection from stacking up behind the shared 30 s client while the user
     * keeps typing.
     */
    private val suggestionClient by lazy {
        client.newBuilder()
            .callTimeout(SUGGESTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    suspend fun getSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://suggestqueries.google.com/complete/search?ds=yt&client=firefox&q=$encodedQuery"
            val request = Request.Builder().url(url).build()
            val response = suggestionClient.newCall(request).execute()
            response.use {
                val jsonString = it.body?.string() ?: return@withContext emptyList()
                parseSuggestions(jsonString)
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Get suggestions failed", e)
            emptyList()
        }
    }

    /**
     * The client=firefox response is `[query, [completions…], …]`. The previous
     * parser located the completions array via indexOf('[') string surgery,
     * which broke whenever a suggestion itself contained brackets or quotes.
     */
    internal fun parseSuggestions(jsonString: String): List<String> {
        val outer = JSONArray(jsonString)
        val completions = outer.optJSONArray(1) ?: return emptyList()

        val suggestions = (0 until completions.length()).mapNotNull { i ->
            when (val entry = completions.opt(i)) {
                is String -> entry
                is JSONObject -> entry.optString(SUGGESTION_KEY)
                else -> null
            }.takeIf { !it.isNullOrBlank() }
        }
        return suggestions.distinct()
    }

    private companion object {
        const val TAG = "SuggestionDataSource"
        const val SUGGESTION_TIMEOUT_SECONDS = 4L
        const val SUGGESTION_KEY = "s"
    }
}

package com.vyllo.music.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SuggestionDataSource @Inject constructor(private val client: OkHttpClient) {
    suspend fun getSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://suggestqueries.google.com/complete/search?client=youtube&ds=yt&client=firefox&q=$encodedQuery"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val jsonString = response.body?.string() ?: return@withContext emptyList()
            
            val startIndex = jsonString.indexOf("[", jsonString.indexOf("[") + 1)
            val endIndex = jsonString.lastIndexOf("]")
            if (startIndex == -1 || endIndex == -1) return@withContext emptyList()
            
            var arrayContent = jsonString.substring(startIndex, endIndex + 1)
            arrayContent = arrayContent.removePrefix("[").removeSuffix("]")
            
            return@withContext arrayContent.split("\",\"").map { 
                it.replace("\"", "").replace("[", "").replace("]", "").trim() 
            }.filter { it.isNotEmpty() && it != "s" }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
    }
}

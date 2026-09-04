package com.vyllo.music.data

import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.model.SyncedLyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * High-performance lyrics translation engine.
 *
 * Tier 1: Google Translate Chrome-Ex API (Direct HTTP POST, zero auth token, sub-300ms, batch-capable).
 * Tier 2: MyMemory Neural Machine Translation API (Open API fallback with automatic language detection).
 * Tier 3: In-memory LRU caching for instantaneous toggle and zero repeat overhead.
 */
object TranslationEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    // In-memory LRU translation cache (stores up to 500 entries)
    private val translationCache = object : java.util.LinkedHashMap<String, String>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > 500
        }
    }

    @Volatile
    private var detectedSourceLang: String? = null

    /**
     * Translates a block of text using Google Translate Chrome-Ex API.
     * Returns Pair(translatedText, detectedLang) or null if failed.
     */
    private fun translateViaGoogle(text: String, targetLang: String = "en"): Pair<String, String>? {
        if (text.isBlank()) return null
        try {
            val url = "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=auto&tl=$targetLang"
            val formBody = FormBody.Builder()
                .add("q", text)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(formBody)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: return null
                    val jsonArr = JSONArray(bodyString)
                    if (jsonArr.length() > 0) {
                        val translated = jsonArr.getString(0)
                        val detected = if (jsonArr.length() > 1) jsonArr.getString(1) else "auto"
                        return Pair(translated, detected)
                    }
                } else {
                    SecureLogger.w("TranslationEngine", "Google translate HTTP error: ${response.code}")
                }
            }
        } catch (e: Exception) {
            SecureLogger.w("TranslationEngine", "Google translate exception: ${e.message}")
        }
        return null
    }

    /**
     * Fallback translation using MyMemory API.
     */
    private fun translateViaMyMemory(text: String, targetLang: String = "en"): String? {
        if (text.isBlank()) return null
        try {
            val encoded = URLEncoder.encode(text, "UTF-8")
            val url = "https://api.mymemory.translated.net/get?q=$encoded&langpair=autodetect|$targetLang"

            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile)")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: return null
                    val json = JSONObject(bodyString)
                    val responseData = json.optJSONObject("responseData")
                    val translatedText = responseData?.optString("translatedText")
                    if (!translatedText.isNullOrBlank() && !translatedText.startsWith("MYMEMORY WARNING")) {
                        return translatedText
                    }
                }
            }
        } catch (e: Exception) {
            SecureLogger.w("TranslationEngine", "MyMemory translate exception: ${e.message}")
        }
        return null
    }

    /**
     * Single string translation with multi-tier fallback and in-memory LRU caching.
     */
    suspend fun translateText(text: String, targetLang: String = "en"): String? = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return@withContext null

        val cacheKey = "$targetLang:${trimmed.hashCode()}"
        synchronized(translationCache) {
            translationCache[cacheKey]?.let { return@withContext it }
        }

        // 1. Try Google Translate
        val googleResult = translateViaGoogle(trimmed, targetLang)
        if (googleResult != null) {
            val (translated, detected) = googleResult
            detectedSourceLang = detected
            synchronized(translationCache) {
                translationCache[cacheKey] = translated
            }
            return@withContext translated
        }

        // 2. Fallback to MyMemory
        val myMemoryResult = translateViaMyMemory(trimmed, targetLang)
        if (myMemoryResult != null) {
            synchronized(translationCache) {
                translationCache[cacheKey] = myMemoryResult
            }
            return@withContext myMemoryResult
        }

        return@withContext null
    }

    /**
     * Translates a list of synchronized lyrics lines in bulk.
     * Maintains precise 1-to-1 mapping with input lines.
     */
    suspend fun translateLines(
        lines: List<SyncedLyricLine>,
        sourceLang: String = "auto",
        targetLang: String = "en"
    ): List<String?> = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) return@withContext emptyList()

        val validLines = lines.map { it.content.trim() }
        val joinedText = validLines.joinToString("\n")

        // Strategy 1: Batch translation in one HTTP request (fastest and most context-aware)
        val batchResult = translateViaGoogle(joinedText, targetLang)
        if (batchResult != null) {
            val (translatedBlock, detected) = batchResult
            detectedSourceLang = detected

            val splitLines = translatedBlock.lines()
            if (splitLines.size == lines.size) {
                // Perfect 1-to-1 match
                synchronized(translationCache) {
                    for (i in lines.indices) {
                        val orig = validLines[i]
                        val trans = splitLines[i].trim()
                        if (orig.isNotBlank() && trans.isNotBlank()) {
                            translationCache["$targetLang:${orig.hashCode()}"] = trans
                        }
                    }
                }
                return@withContext splitLines.map { it.takeIf { s -> s.isNotBlank() } }
            }
        }

        // Strategy 2: Parallel unique line translation if batch line count mismatched
        val uniqueLines = validLines.filter { it.isNotBlank() }.distinct()
        val translationMap = mutableMapOf<String, String?>()

        // Check cache first
        val missingLines = mutableListOf<String>()
        synchronized(translationCache) {
            for (line in uniqueLines) {
                val cached = translationCache["$targetLang:${line.hashCode()}"]
                if (cached != null) {
                    translationMap[line] = cached
                } else {
                    missingLines.add(line)
                }
            }
        }

        if (missingLines.isNotEmpty()) {
            coroutineScope {
                val jobs = missingLines.map { line ->
                    async(Dispatchers.IO) {
                        val translated = translateText(line, targetLang)
                        Pair(line, translated)
                    }
                }
                jobs.awaitAll().forEach { (line, trans) ->
                    if (trans != null) {
                        translationMap[line] = trans
                        synchronized(translationCache) {
                            translationCache["$targetLang:${line.hashCode()}"] = trans
                        }
                    }
                }
            }
        }

        lines.map { line ->
            val trimmed = line.content.trim()
            if (trimmed.isBlank()) null else translationMap[trimmed]
        }
    }

    /**
     * Translates plain lyrics text.
     */
    suspend fun translatePlainLyrics(
        plainLyrics: String,
        sourceLang: String = "auto",
        targetLang: String = "en"
    ): String? = withContext(Dispatchers.IO) {
        if (plainLyrics.isBlank()) return@withContext null

        val cacheKey = "plain:$targetLang:${plainLyrics.trim().hashCode()}"
        synchronized(translationCache) {
            translationCache[cacheKey]?.let { return@withContext it }
        }

        // Try batch Google translation
        val googleResult = translateViaGoogle(plainLyrics.trim(), targetLang)
        if (googleResult != null) {
            val (translated, detected) = googleResult
            detectedSourceLang = detected
            synchronized(translationCache) {
                translationCache[cacheKey] = translated
            }
            return@withContext translated
        }

        // Fallback to line-by-line translation
        val lines = plainLyrics.lines()
        val translatedLines = mutableListOf<String>()
        for (line in lines) {
            if (line.isBlank()) {
                translatedLines.add("")
            } else {
                val translated = translateText(line, targetLang)
                translatedLines.add(translated ?: line)
            }
        }

        val result = translatedLines.joinToString("\n")
        synchronized(translationCache) {
            translationCache[cacheKey] = result
        }
        return@withContext result
    }

    fun resetSession() {
        detectedSourceLang = null
    }

    fun clearCache() {
        synchronized(translationCache) {
            translationCache.clear()
        }
        detectedSourceLang = null
    }
}

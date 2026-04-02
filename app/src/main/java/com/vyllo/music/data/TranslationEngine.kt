package com.vyllo.music.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.Base64

/**
 * Translation engine using Microsoft Edge Translator (Cognitive Services) API.
 * This is the same backend engine used by edge/bing translation tools.
 * It perfectly translates romanized Indian lyrics (Hinglish/Punjabi) by using
 * a two-step process: Transliteration (Latn -> Native) -> Translation (Native -> en).
 */
object TranslationEngine {

    private val client = OkHttpClient.Builder()
        .readTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json".toMediaType()

    // Edge Auth Token caching
    private var jwtToken: String? = null
    private var tokenExpiryMs: Long = 0

    // In-memory translation cache
    private val translationCache = mutableMapOf<String, String>()

    // Detected source language for current session
    private var detectedSourceLang: String? = null

    // Supported transliteration parameters for Romanized (Latn) to Native Scripts
    private val TRANSLIT_LANGS = mapOf(
        "hi" to "Deva", // Hindi
        "pa" to "Guru", // Punjabi
        "ta" to "Taml", // Tamil
        "te" to "Telu", // Telugu
        "ml" to "Mlym", // Malayalam
        "bn" to "Beng", // Bengali
        "gu" to "Gujr", // Gujarati
        "kn" to "Knda", // Kannada
        "mr" to "Deva", // Marathi
        "ur" to "Arab", // Urdu
    )

    /**
     * Fetches a free authentication token from Edge's translator endpoint.
     * The token is typical valid for 10 minutes.
     */
    private suspend fun getAuthToken(): String? = withContext(Dispatchers.IO) {
        if (jwtToken != null && System.currentTimeMillis() < tokenExpiryMs - 60000) {
            return@withContext jwtToken
        }

        try {
            val request = Request.Builder()
                .url("https://edge.microsoft.com/translate/auth")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val token = response.body?.string()?.trim()
                if (!token.isNullOrEmpty()) {
                    // Extract expiry from JWT
                    try {
                        val payloadStr = String(Base64.getUrlDecoder().decode(token.split(".")[1]))
                        val payload = JSONObject(payloadStr)
                        val exp = payload.getLong("exp")
                        
                        jwtToken = token
                        tokenExpiryMs = exp * 1000L
                        android.util.Log.d("TranslationEngine", "Fetched new Edge JWT auth token")
                        return@withContext jwtToken
                    } catch (e: Exception) {
                        android.util.Log.e("TranslationEngine", "JWT parse error", e)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("TranslationEngine", "Failed to fetch auth token: ${e.message}")
        }
        return@withContext null
    }

    /**
     * Transforms Romanized text into the native script (e.g. Punjabi Latn -> Gurmukhi).
     */
    private suspend fun transliterateRaw(text: String, lang: String, toScript: String, token: String): String? {
        try {
            val url = "https://api.cognitive.microsofttranslator.com/transliterate?api-version=3.0&language=$lang&fromScript=Latn&toScript=$toScript"
            
            val jsonBody = JSONArray().apply { put(JSONObject().put("Text", text)) }
            
            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Authorization", "Bearer $token")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                val resultArr = JSONArray(body)
                if (resultArr.length() > 0) {
                    return resultArr.getJSONObject(0).getString("text")
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("TranslationEngine", "Transliterate error: ${e.message}")
        }
        return null
    }

    /**
     * Translates native text to target language (e.g. Gurmukhi -> English).
     */
    private suspend fun translateRawMET(text: String, fromLang: String, toLang: String, token: String): String? {
        try {
            // If fromLang is auto, omit the from parameter
            val url = if (fromLang == "auto") {
                "https://api.cognitive.microsofttranslator.com/translate?api-version=3.0&to=$toLang"
            } else {
                "https://api.cognitive.microsofttranslator.com/translate?api-version=3.0&from=$fromLang&to=$toLang"
            }
            
            val jsonBody = JSONArray().apply { put(JSONObject().put("Text", text)) }
            
            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Authorization", "Bearer $token")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                val resultArr = JSONArray(body)
                if (resultArr.length() > 0) {
                    val translations = resultArr.getJSONObject(0).getJSONArray("translations")
                    if (translations.length() > 0) {
                        return translations.getJSONObject(0).getString("text")
                    }
                }
            } else {
                android.util.Log.w("TranslationEngine", "Translate failed: ${response.code} ${response.body?.string()}")
            }
        } catch (e: Exception) {
            android.util.Log.w("TranslationEngine", "Translate error: ${e.message}")
        }
        return null
    }

    /**
     * Translates a string using the full pipeline:
     * 1. If language is supported for transliteration, transliterate Latn -> Native
     * 2. Translate Native -> Target
     */
    private suspend fun translatePipeline(text: String, sourceLang: String, targetLang: String, token: String): String? {
        var processingText = text

        // Step 1: Transliterate if applicable
        if (sourceLang != "auto" && TRANSLIT_LANGS.containsKey(sourceLang)) {
            val toScript = TRANSLIT_LANGS[sourceLang]!!
            val transliterated = transliterateRaw(text, sourceLang, toScript, token)
            if (transliterated != null) {
                processingText = transliterated
            }
        }

        // Step 2: Translate
        return translateRawMET(processingText, sourceLang, targetLang, token)
    }

    private fun calculateWordOverlap(original: String, translated: String): Double {
        val origWords = original.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 2 }.toSet()
        if (origWords.isEmpty()) return 1.0

        val transWords = translated.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 2 }
        if (transWords.isEmpty()) return 1.0

        var unchanged = 0
        for (w in transWords) {
            if (origWords.contains(w)) unchanged++
        }

        return unchanged.toDouble() / transWords.size.toDouble()
    }

    /**
     * Detects language for Romanized lyrics by finding which language
     * yields the best English translation (lowest word overlap).
     */
    private suspend fun detectAndLockSourceLanguage(lines: List<String>, targetLang: String) {
        if (detectedSourceLang != null) return

        val sampleLines = lines.map { it.trim() }.filter { it.isNotBlank() }.take(5)
        if (sampleLines.isEmpty()) return

        val sampleText = sampleLines.joinToString("\n")
        val token = getAuthToken() ?: return

        withContext(Dispatchers.IO) {
            // 1. Try "auto" first
            val autoResult = translateRawMET(sampleText, "auto", targetLang, token)
            if (autoResult != null) {
                val overlap = calculateWordOverlap(sampleText, autoResult)
                if (overlap < 0.4) {
                    detectedSourceLang = "auto"
                    android.util.Log.d("TranslationEngine", "Locked 'auto' (overlap: $overlap)")
                    return@withContext
                }
            }

            android.util.Log.d("TranslationEngine", "Probing ${TRANSLIT_LANGS.size} languages for Romanized text...")

            val probeJobs = TRANSLIT_LANGS.keys.map { lang ->
                async {
                    val translated = translatePipeline(sampleText, lang, targetLang, token)
                    val overlap = if (translated != null) calculateWordOverlap(sampleText, translated) else 1.0
                    Triple(lang, translated, overlap)
                }
            }

            val results = probeJobs.awaitAll()

            var bestLang = "auto"
            var lowestOverlap = 1.0
            
            for ((lang, translated, overlap) in results) {
                if (translated != null) {
                    android.util.Log.d("TranslationEngine", "Probe [$lang] overlap: $overlap ->\n$translated")
                }
                if (overlap < lowestOverlap) {
                    lowestOverlap = overlap
                    bestLang = lang
                }
            }

            detectedSourceLang = bestLang
            android.util.Log.d("TranslationEngine", "LOCKED source language: [$bestLang] with overlap $lowestOverlap")
        }
    }

    private suspend fun translateTextLocked(
        text: String,
        targetLang: String = "en"
    ): String? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null

        val cacheKey = "${targetLang}:${text.trim().hashCode()}"
        translationCache[cacheKey]?.let { return@withContext it }

        val token = getAuthToken() ?: return@withContext null
        val effectiveSourceLang = detectedSourceLang ?: "auto"
        
        val result = translatePipeline(text.trim(), effectiveSourceLang, targetLang, token)

        if (result != null) {
            translationCache[cacheKey] = result
        }
        
        return@withContext result
    }

    suspend fun translateLines(
        lines: List<SyncedLyricLine>,
        sourceLang: String = "auto",
        targetLang: String = "en"
    ): List<String?> = withContext(Dispatchers.IO) {
        detectAndLockSourceLanguage(lines.map { it.content }, targetLang)

        val uniqueTexts = lines.map { it.content.trim() }.filter { it.isNotBlank() }.distinct()
        
        val translationMap = mutableMapOf<String, String?>()
        for (text in uniqueTexts) {
            val cacheKey = "${targetLang}:${text.hashCode()}"
            if (translationCache.containsKey(cacheKey)) {
                translationMap[text] = translationCache[cacheKey]
            } else {
                translationMap[text] = translateTextLocked(text, targetLang)
            }
        }

        lines.map { line ->
            val trimmed = line.content.trim()
            if (trimmed.isBlank()) null else translationMap[trimmed]
        }
    }

    suspend fun translatePlainLyrics(
        plainLyrics: String,
        sourceLang: String = "auto",
        targetLang: String = "en"
    ): String? = withContext(Dispatchers.IO) {
        if (plainLyrics.isBlank()) return@withContext null

        val cacheKey = "plain:${targetLang}:${plainLyrics.hashCode()}"
        translationCache[cacheKey]?.let { return@withContext it }

        val lines = plainLyrics.lines()
        detectAndLockSourceLanguage(lines, targetLang)

        val translatedLines = mutableListOf<String>()
        for (line in lines) {
            if (line.isBlank()) {
                translatedLines.add("")
            } else {
                val translated = translateTextLocked(line, targetLang)
                translatedLines.add(translated ?: line)
            }
        }

        val result = translatedLines.joinToString("\n")
        translationCache[cacheKey] = result
        result
    }

    fun resetSession() {
        detectedSourceLang = null
    }

    fun clearCache() {
        translationCache.clear()
        detectedSourceLang = null
    }
}

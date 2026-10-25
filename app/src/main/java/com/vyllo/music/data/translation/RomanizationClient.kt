package com.vyllo.music.data.translation

import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.core.security.SecurityConfig
import com.vyllo.music.domain.model.RomanizationScript
import com.vyllo.music.domain.model.TranslationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Latin-script transliteration for non-Latin lyrics.
 *
 * Uses Google Input Tools (keyless) for CJK/Cyrillic/Arabic and falls back to
 * the configured AI provider, which handles mixed-script lines better.
 */
@Singleton
class RomanizationClient @Inject constructor(
    private val aiClient: AiTranslationClient
) {
    private val client: OkHttpClient by lazy {
        SecurityConfig()
            .createFlexibleSecureHttpClient()
            .newBuilder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val cache = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > 1_000
    }

    /**
     * Returns one entry per input line, or null when the lyrics need no
     * romanization or the script could not be determined.
     */
    suspend fun romanizeLines(
        lines: List<String>,
        settings: TranslationSettings
    ): List<String?>? = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) return@withContext null
        if (lines.none(RomanizationScript::needsRomanization)) return@withContext null

        val results = arrayOfNulls<String>(lines.size)
        var changed = false
        coroutineScope {
            lines.chunked(MAX_CONCURRENT).forEachIndexed { chunkIndex, chunk ->
                val start = chunkIndex * MAX_CONCURRENT
                val jobs = chunk.mapIndexed { offset, line ->
                    async {
                        val script = RomanizationScript.detect(line)
                        if (script == null) line else transliterateLine(line, script)
                    } to (start + offset)
                }
                jobs.forEach { (job, index) ->
                    val value = job.await()
                    results[index] = value
                    if (value != null && value != lines[index]) changed = true
                }
            }
        }

        if (!changed || results.any { it == null }) {
            if (!settings.isUsableAsProvider()) return@withContext null
            val fallback = aiClient.romanize(lines.joinToString("\n"), settings)
                ?.replace("\r\n", "\n")
                ?.lines()
                ?.takeIf { it.size == lines.size && it.none(String::isBlank) }
            if (fallback != null) return@withContext fallback
        }
        if (!changed) null else results.toList()
    }

    private fun transliterateLine(
        line: String,
        script: RomanizationScript.Script
    ): String? {
        val key = "${script.itc}:$line"
        synchronized(cache) { cache[key]?.let { return it } }

        val encoded = URLEncoder.encode(line, "UTF-8")
        val url = "https://inputtools.google.com/request?text=$encoded&itc=${script.itc}" +
            "&num=1&cp=0&cs=1&ie=utf-8&oe=utf-8"
        val request = Request.Builder().url(url).get().build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val parsed = parseTransliteration(body) ?: return null
                synchronized(cache) { cache[key] = parsed }
                parsed
            }
        } catch (error: IOException) {
            SecureLogger.w(TAG, "Transliteration failed: ${error.message}")
            null
        }
    }

    private fun parseTransliteration(body: String): String? = runCatching {
        val root = JSONArray(body)
        if (!root.optString(0).equals("SUCCESS", ignoreCase = true)) return null
        val payload = root.optJSONArray(1) ?: return null
        val first = payload.optJSONArray(0) ?: return null
        val candidates = first.optJSONArray(1) ?: return null
        candidates.optString(0).takeIf { it.isNotBlank() }
    }.getOrNull()

    private companion object {
        const val TAG = "Romanization"
        const val MAX_CONCURRENT = 4
    }
}

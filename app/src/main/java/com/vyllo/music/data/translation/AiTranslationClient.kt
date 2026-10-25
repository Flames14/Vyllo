package com.vyllo.music.data.translation

import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.core.security.SecurityConfig
import com.vyllo.music.domain.model.TranslationPrompt
import com.vyllo.music.domain.model.TranslationProvider
import com.vyllo.music.domain.model.TranslationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HTTP client for user-configured translation providers.
 *
 * Returns null on any failure so callers can fall back to the keyless engine;
 * errors are logged without request bodies or credentials.
 */
@Singleton
class AiTranslationClient @Inject constructor() {

    private val client: OkHttpClient by lazy {
        SecurityConfig()
            .createFlexibleSecureHttpClient()
            .newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun translateLines(
        lines: List<String>,
        targetLang: String,
        settings: TranslationSettings
    ): List<String?>? = withContext(Dispatchers.IO) {
        if (lines.isEmpty() || !settings.isUsableAsProvider()) return@withContext null
        val prompt = TranslationPrompt.linesPrompt(lines, targetLang)
        val raw = complete(prompt, settings) ?: return@withContext null
        TranslationPrompt.parseNumberedLines(raw, lines.size)
    }

    suspend fun translateBlock(
        text: String,
        targetLang: String,
        settings: TranslationSettings
    ): String? = withContext(Dispatchers.IO) {
        if (text.isBlank() || !settings.isUsableAsProvider()) return@withContext null
        complete(TranslationPrompt.blockPrompt(text, targetLang), settings)
    }

    suspend fun romanize(
        text: String,
        settings: TranslationSettings
    ): String? = withContext(Dispatchers.IO) {
        if (text.isBlank() || !settings.isUsableAsProvider()) return@withContext null
        complete(TranslationPrompt.romanizePrompt(text), settings)
    }

    private fun complete(prompt: String, settings: TranslationSettings): String? {
        if (!settings.isUsableAsProvider()) return null
        return try {
            when (settings.provider) {
                TranslationProvider.AUTO -> null
                TranslationProvider.DEEPL -> deepl(prompt, settings)
                TranslationProvider.OPENAI -> chatCompletion(OPENAI_URL, prompt, settings, extraHeaders = emptyMap())
                TranslationProvider.OPENROUTER -> chatCompletion(
                    OPENROUTER_URL,
                    prompt,
                    settings,
                    extraHeaders = mapOf("HTTP-Referer" to REFERER, "X-Title" to "Vyllo")
                )
                TranslationProvider.PERPLEXITY -> chatCompletion(PERPLEXITY_URL, prompt, settings, extraHeaders = emptyMap())
                TranslationProvider.CLAUDE -> claude(prompt, settings)
                TranslationProvider.GEMINI -> gemini(prompt, settings)
            }
        } catch (error: IOException) {
            SecureLogger.w(TAG, "Translation request failed: ${error.message}")
            null
        } catch (error: Exception) {
            SecureLogger.w(TAG, "Translation provider error: ${error.message}")
            null
        }
    }

    private fun chatCompletion(
        url: String,
        prompt: String,
        settings: TranslationSettings,
        extraHeaders: Map<String, String>
    ): String? {
        val body = JSONObject()
            .put("model", settings.effectiveModel())
            .put("temperature", 0)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                    .put(JSONObject().put("role", "user").put("content", prompt))
            )
            .toString()

        val builder = Request.Builder()
            .url(url)
            .post(body.toRequestBody(jsonMedia))
            .header("Authorization", "Bearer ${settings.apiKey}")
        extraHeaders.forEach { (key, value) -> builder.header(key, value) }

        return execute(builder.build()) { json ->
            json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
        }
    }

    private fun claude(prompt: String, settings: TranslationSettings): String? {
        val body = JSONObject()
            .put("model", settings.effectiveModel())
            .put("max_tokens", 4096)
            .put("temperature", 0)
            .put("system", SYSTEM_PROMPT)
            .put(
                "messages",
                JSONArray().put(JSONObject().put("role", "user").put("content", prompt))
            )
            .toString()

        val request = Request.Builder()
            .url(CLAUDE_URL)
            .post(body.toRequestBody(jsonMedia))
            .header("x-api-key", settings.apiKey)
            .header("anthropic-version", "2023-06-01")
            .build()

        return execute(request) { json ->
            json.optJSONArray("content")?.optJSONObject(0)?.optString("text")
        }
    }

    private fun gemini(prompt: String, settings: TranslationSettings): String? {
        val body = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
            )
            .put("generationConfig", JSONObject().put("temperature", 0))
            .toString()

        val url = "${GEMINI_BASE}${settings.effectiveModel()}:generateContent"
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(jsonMedia))
            .header("x-goog-api-key", settings.apiKey)
            .build()

        return execute(request) { json ->
            json.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
        }
    }

    private fun deepl(text: String, settings: TranslationSettings): String? {
        val host = if (settings.apiKey.endsWith(":fx")) DEEPL_FREE_URL else DEEPL_PRO_URL
        val body = FormBody.Builder()
            .add("text", text)
            .add("target_lang", settings.targetLanguage.uppercase().substringBefore('-'))
            .add("preserve_formatting", "1")
            .build()

        val request = Request.Builder()
            .url(host)
            .post(body)
            .header("Authorization", "DeepL-Auth-Key ${settings.apiKey}")
            .build()

        return execute(request) { json ->
            json.optJSONArray("translations")?.optJSONObject(0)?.optString("text")
        }
    }

    private fun execute(request: Request, parse: (JSONObject) -> String?): String? {
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string()
            if (!response.isSuccessful) {
                SecureLogger.w(TAG, "Provider HTTP ${response.code}")
                return null
            }
            if (payload.isNullOrBlank()) return null
            return parse(JSONObject(payload)).orEmpty().trim().ifBlank { null }
        }
    }

    private companion object {
        const val TAG = "AiTranslation"
        const val SYSTEM_PROMPT = "You translate song lyrics faithfully and concisely."
        const val REFERER = "https://github.com/Flames14/Vyllo"
        const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"
        const val OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
        const val PERPLEXITY_URL = "https://api.perplexity.ai/chat/completions"
        const val CLAUDE_URL = "https://api.anthropic.com/v1/messages"
        const val GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models/"
        const val DEEPL_FREE_URL = "https://api-free.deepl.com/v2/translate"
        const val DEEPL_PRO_URL = "https://api.deepl.com/v2/translate"
    }
}

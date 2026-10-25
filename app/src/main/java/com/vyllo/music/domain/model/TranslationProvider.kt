package com.vyllo.music.domain.model

/**
 * Lyrics translation backends.
 *
 * [AUTO] keeps the keyless Google/MyMemory chain. Every other provider needs a
 * user-supplied key; nothing is bundled with the app.
 */
enum class TranslationProvider(
    val id: String,
    val label: String,
    val defaultModel: String,
    val supportsModel: Boolean
) {
    AUTO("auto", "Automatic", "", false),
    DEEPL("deepl", "DeepL", "", false),
    OPENAI("openai", "OpenAI", "gpt-4o-mini", true),
    CLAUDE("claude", "Claude", "claude-3-5-haiku-latest", true),
    GEMINI("gemini", "Gemini", "gemini-2.0-flash", true),
    OPENROUTER("openrouter", "OpenRouter", "openai/gpt-4o-mini", true),
    PERPLEXITY("perplexity", "Perplexity", "sonar", true);

    val needsKey: Boolean get() = this != AUTO

    companion object {
        fun fromId(id: String?): TranslationProvider =
            entries.firstOrNull { it.id == id } ?: AUTO
    }
}

data class TranslationSettings(
    val provider: TranslationProvider = TranslationProvider.AUTO,
    val apiKey: String = "",
    val model: String = "",
    val targetLanguage: String = "en",
    val romanize: Boolean = false
) {
    fun isUsableAsProvider(): Boolean =
        provider == TranslationProvider.AUTO || apiKey.isNotBlank()

    fun effectiveModel(): String = model.ifBlank { provider.defaultModel }
}

/**
 * Pure prompt construction and response parsing for AI translation providers.
 *
 * Numbered lines are the only reliable way to keep lyric timing intact: models
 * sometimes merge or split lines, so the parser refuses lossy output and the
 * caller falls back to the keyless engine.
 */
object TranslationPrompt {

    fun linesPrompt(lines: List<String>, targetLang: String): String = buildString {
        append("Translate each numbered lyrics line into ")
        append(languageName(targetLang))
        append(". Keep the same numbering and one output line per input line. ")
        append("Do not merge, split, explain, or add commentary. Keep the original line order.\n\n")
        lines.forEachIndexed { index, line ->
            append(index + 1).append(". ").append(line).append('\n')
        }
    }

    fun blockPrompt(text: String, targetLang: String): String =
        "Translate the following song lyrics into ${languageName(targetLang)}. " +
            "Preserve every line break and do not add commentary. Output only the translation.\n\n" +
            text

    fun romanizePrompt(text: String): String =
        "Transliterate the following lyrics into Latin script (romaji/romanization). " +
            "Preserve every line break and do not translate the meaning. Output only the romanization.\n\n" +
            text

    /**
     * Parses "1. text" output. Returns null when the model dropped, duplicated,
     * or invented lines so the caller can fall back instead of showing garbage.
     */
    fun parseNumberedLines(raw: String, expected: Int): List<String>? {
        if (expected <= 0) return null
        val cleaned = raw.replace("\r\n", "\n").trim()
        if (cleaned.isEmpty()) return null

        val prefix = Regex("^\\s*(\\d{1,3})\\s*[.):]\\s*(.*)$")
        val parsed = HashMap<Int, String>()
        for (line in cleaned.lines()) {
            val match = prefix.find(line) ?: continue
            val index = match.groupValues[1].toIntOrNull() ?: continue
            if (index !in 1..expected) continue
            parsed.putIfAbsent(index, match.groupValues[2].trim())
        }
        if (parsed.size == expected) {
            return (1..expected).map { parsed[it] ?: return null }
        }

        // A malformed numbered response must not fall through as "bare" text:
        // that would show duplicated/wrong line numbers as translated lyrics.
        if (cleaned.lines().any(prefix::containsMatchIn)) return null

        // Some models answer with bare lines; accept only an exact line count.
        val bare = cleaned.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return if (bare.size == expected) bare else null
    }

    fun languageName(code: String): String = when (code.lowercase().substringBefore('-')) {
        "en" -> "English"
        "es" -> "Spanish"
        "pt" -> "Portuguese"
        "fr" -> "French"
        "de" -> "German"
        "it" -> "Italian"
        "nl" -> "Dutch"
        "pl" -> "Polish"
        "ru" -> "Russian"
        "uk" -> "Ukrainian"
        "tr" -> "Turkish"
        "ar" -> "Arabic"
        "he" -> "Hebrew"
        "hi" -> "Hindi"
        "bn" -> "Bengali"
        "id" -> "Indonesian"
        "vi" -> "Vietnamese"
        "th" -> "Thai"
        "ja" -> "Japanese"
        "ko" -> "Korean"
        "zh" -> "Chinese"
        else -> code
    }
}

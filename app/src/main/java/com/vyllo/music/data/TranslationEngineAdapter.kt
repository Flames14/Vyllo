package com.vyllo.music.data

import com.vyllo.music.data.translation.AiTranslationClient
import com.vyllo.music.data.translation.RomanizationClient
import com.vyllo.music.data.translation.TranslationSettingsStore
import com.vyllo.music.domain.model.SyncedLyricLine
import com.vyllo.music.domain.repository.LyricsTranslator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tries the user's configured provider first, then falls back to the keyless
 * Google/MyMemory chain so translation keeps working when a key is exhausted.
 */
@Singleton
class TranslationEngineAdapter @Inject constructor(
    private val settingsStore: TranslationSettingsStore,
    private val aiClient: AiTranslationClient,
    private val romanizationClient: RomanizationClient
) : LyricsTranslator {

    override suspend fun translateLines(
        lines: List<SyncedLyricLine>,
        targetLang: String
    ): List<String?> {
        val settings = settingsStore.settings()
        if (settings.provider.needsKey && settings.apiKey.isNotBlank()) {
            val viaProvider = aiClient.translateLines(
                lines = lines.map { it.content },
                targetLang = targetLang,
                settings = settings
            )
            if (viaProvider != null) return viaProvider
        }
        return TranslationEngine.translateLines(lines, targetLang = targetLang)
    }

    override suspend fun translatePlain(text: String, targetLang: String): String? {
        val settings = settingsStore.settings()
        if (settings.provider.needsKey && settings.apiKey.isNotBlank()) {
            aiClient.translateBlock(text, targetLang, settings)?.let { return it }
        }
        return TranslationEngine.translatePlainLyrics(text, targetLang = targetLang)
    }

    override suspend fun romanizeLines(lines: List<String>): List<String?>? =
        romanizationClient.romanizeLines(lines, settingsStore.settings())

    override suspend fun romanizePlain(text: String): String? {
        val settings = settingsStore.settings()
        return romanizationClient.romanizeLines(text.lines(), settings)?.joinToString("\n")
    }

    override fun preferredTargetLanguage(): String = settingsStore.settings().targetLanguage

    override fun romanizationEnabled(): Boolean = settingsStore.settings().romanize

    override fun setRomanizationEnabled(enabled: Boolean) {
        val settings = settingsStore.settings()
        if (settings.romanize == enabled) return
        settingsStore.save(settings.copy(romanize = enabled))
    }

    override fun resetSession() = TranslationEngine.resetSession()
}

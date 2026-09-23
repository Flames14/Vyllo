package com.vyllo.music.data

import com.vyllo.music.domain.model.SyncedLyricLine
import com.vyllo.music.domain.repository.LyricsTranslator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationEngineAdapter @Inject constructor() : LyricsTranslator {
    override suspend fun translateLines(lines: List<SyncedLyricLine>, targetLang: String): List<String?> =
        TranslationEngine.translateLines(lines, targetLang = targetLang)

    override suspend fun translatePlain(text: String, targetLang: String): String? =
        TranslationEngine.translatePlainLyrics(text, targetLang = targetLang)

    override fun resetSession() = TranslationEngine.resetSession()
}

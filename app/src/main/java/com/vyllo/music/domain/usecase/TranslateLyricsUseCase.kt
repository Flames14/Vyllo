package com.vyllo.music.domain.usecase

import com.vyllo.music.domain.model.SyncedLyricLine
import com.vyllo.music.data.TranslationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class TranslateLyricsUseCase @Inject constructor() {

    suspend fun translateLines(lines: List<SyncedLyricLine>, targetLang: String = "en"): List<String?> = withContext(Dispatchers.IO) {
        TranslationEngine.translateLines(lines, targetLang = targetLang)
    }

    suspend fun translatePlain(text: String, targetLang: String = "en"): String? = withContext(Dispatchers.IO) {
        TranslationEngine.translatePlainLyrics(text, targetLang = targetLang)
    }
}

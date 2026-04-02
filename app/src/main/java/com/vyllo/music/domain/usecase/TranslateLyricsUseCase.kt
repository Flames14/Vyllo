package com.vyllo.music.domain.usecase

import com.vyllo.music.data.SyncedLyricLine
import com.vyllo.music.data.TranslationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class TranslateLyricsUseCase @Inject constructor() {
    
    suspend fun translateLines(lines: List<SyncedLyricLine>, targetLang: String = "auto"): List<String?> = withContext(Dispatchers.IO) {
        TranslationEngine.translateLines(lines, targetLang)
    }
    
    suspend fun translatePlain(text: String, targetLang: String = "auto"): String? = withContext(Dispatchers.IO) {
        TranslationEngine.translatePlainLyrics(text, targetLang)
    }
}

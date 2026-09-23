package com.vyllo.music.domain.usecase

import com.vyllo.music.domain.model.SyncedLyricLine
import com.vyllo.music.domain.repository.LyricsTranslator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class TranslateLyricsUseCase @Inject constructor(
    private val translator: LyricsTranslator
) {

    suspend fun translateLines(lines: List<SyncedLyricLine>, targetLang: String = "en"): List<String?> = withContext(Dispatchers.IO) {
        translator.translateLines(lines, targetLang = targetLang)
    }

    suspend fun translatePlain(text: String, targetLang: String = "en"): String? = withContext(Dispatchers.IO) {
        translator.translatePlain(text, targetLang = targetLang)
    }
}


package com.vyllo.music.domain.repository

import com.vyllo.music.domain.model.SyncedLyricLine

/**
 * Translates lyrics text/lines. Implemented by the data-layer translation engine.
 */
interface LyricsTranslator {
    suspend fun translateLines(lines: List<SyncedLyricLine>, targetLang: String = "en"): List<String?>
    suspend fun translatePlain(text: String, targetLang: String = "en"): String?
    fun resetSession()
}

package com.vyllo.music.domain.repository

import com.vyllo.music.domain.model.SyncedLyricLine

/**
 * Translates and romanizes lyrics. Implemented by the data-layer engine.
 */
interface LyricsTranslator {
    suspend fun translateLines(lines: List<SyncedLyricLine>, targetLang: String = "en"): List<String?>
    suspend fun translatePlain(text: String, targetLang: String = "en"): String?

    /**
     * Romanizes non-Latin lyric lines. Returns null when the lyrics are already
     * Latin or no transliteration backend is available.
     */
    suspend fun romanizeLines(lines: List<String>): List<String?>? = null

    suspend fun romanizePlain(text: String): String? = null

    /** Target language configured by the user (BCP-47-ish code). */
    fun preferredTargetLanguage(): String = "en"

    /** Whether the user enabled romanization in settings. */
    fun romanizationEnabled(): Boolean = false

    /** Persists the romanization preference; no-op for implementations without settings. */
    fun setRomanizationEnabled(enabled: Boolean) = Unit

    fun resetSession()
}

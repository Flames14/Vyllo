package com.vyllo.music.domain.model

/**
 * Decides whether a lyric line is written in a non-Latin script and which
 * transliteration backend should be used for it.
 *
 * Detection is deliberately conservative: a line needs at least [MIN_RATIO] of
 * its letters in one script, so a Latin hook with one Japanese word is left
 * alone and a short "OK" is never romanized.
 */
object RomanizationScript {

    private const val MIN_RATIO = 0.2

    data class Script(val code: String, val itc: String)

    fun detect(text: String): Script? {
        if (text.isBlank()) return null
        var letters = 0
        val counts = HashMap<String, Int>()
        for (char in text) {
            if (!char.isLetter()) continue
            letters++
            val script = scriptOf(char) ?: continue
            counts[script] = (counts[script] ?: 0) + 1
        }
        if (letters == 0) return null
        val best = counts.maxByOrNull { it.value } ?: return null
        if (best.value.toDouble() / letters < MIN_RATIO) return null
        return SCRIPTS[best.key]
    }

    fun needsRomanization(text: String): Boolean = detect(text) != null

    private fun scriptOf(char: Char): String? = when (char.code) {
        in 0x3040..0x309F, in 0x30A0..0x30FF -> "kana"
        in 0xAC00..0xD7AF, in 0x1100..0x11FF -> "hangul"
        in 0x4E00..0x9FFF, in 0x3400..0x4DBF -> "han"
        in 0x0400..0x04FF -> "cyrillic"
        in 0x0370..0x03FF -> "greek"
        in 0x0600..0x06FF, in 0x0750..0x077F -> "arabic"
        in 0x0590..0x05FF -> "hebrew"
        in 0x0E00..0x0E7F -> "thai"
        in 0x0900..0x097F -> "devanagari"
        else -> null
    }

    private val SCRIPTS = mapOf(
        "kana" to Script("ja", "ja-t-i0-und"),
        "hangul" to Script("ko", "ko-t-i0-und"),
        "han" to Script("zh", "zh-t-i0-pinyin"),
        "cyrillic" to Script("ru", "ru-t-i0-und"),
        "greek" to Script("el", "el-t-i0-und"),
        "arabic" to Script("ar", "ar-t-i0-und"),
        "hebrew" to Script("he", "he-t-i0-und"),
        "thai" to Script("th", "th-t-i0-und"),
        "devanagari" to Script("hi", "hi-t-i0-und")
    )
}

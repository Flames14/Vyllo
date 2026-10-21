package com.vyllo.music.domain.model

/**
 * Stream quality ceiling for audio playback. YouTube serves a handful of fixed
 * bitrates (Opus ~160 kbps, AAC ~128 kbps, low Opus ~50 kbps), so these are
 * ceilings rather than exact targets; when nothing fits, the caller keeps the
 * available streams instead of failing playback.
 */
enum class StreamQuality(val key: String, val maxAudioBitrate: Int) {
    AUTO("Auto", 0),
    HIGH("High", 256_000),
    MEDIUM("Medium", 140_000),
    LOW("Low", 64_000);

    /** Keeps [items] at or below the ceiling; falls back to all of them. */
    fun <T> withinBitrate(items: List<T>, bitrate: (T) -> Int): List<T> {
        if (maxAudioBitrate <= 0) return items
        val under = items.filter { bitrate(it) in 1..maxAudioBitrate }
        return under.ifEmpty { items }
    }

    companion object {
        fun from(key: String?): StreamQuality = entries.firstOrNull { it.key == key } ?: AUTO
    }
}

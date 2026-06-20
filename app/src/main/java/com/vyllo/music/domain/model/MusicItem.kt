package com.vyllo.music.domain.model

import android.net.Uri

/**
 * Represents a music item (song or playlist) in the application.
 * Immutable data class for thread safety and Compose stability.
 */
enum class MusicItemType {
    SONG,
    PLAYLIST
}

/**
 * Music item data class.
 * Marked as immutable for Compose stability.
 */
@androidx.compose.runtime.Immutable
data class MusicItem(
    val id: String = "",
    val title: String,
    val url: String,
    val uploader: String = "",
    val thumbnailUrl: String = "",
    val type: MusicItemType = MusicItemType.SONG,
    val durationSecs: Long = 0L
) {
    /**
     * Legacy constructor for backward compatibility.
     */
    constructor(
        title: String,
        url: String,
        uploader: String = "",
        thumbnailUrl: String = "",
        type: MusicItemType = MusicItemType.SONG
    ) : this(
        id = url,
        title = title,
        url = url,
        uploader = uploader,
        thumbnailUrl = thumbnailUrl,
        type = type,
        durationSecs = 0L
    )

    fun getUniversalShareUrl(): String? {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isBlank()) return null

        val parsedUri = Uri.parse(normalizedUrl)
        val videoIdFromQuery = parsedUri.getQueryParameter("v")
            ?.takeIf(::isLikelyYouTubeId)
        if (videoIdFromQuery != null) {
            return "https://www.youtube.com/watch?v=$videoIdFromQuery"
        }

        val pathSegments = parsedUri.pathSegments.orEmpty()
        val candidate = when {
            parsedUri.host?.contains("youtu.be", ignoreCase = true) == true -> pathSegments.firstOrNull()
            parsedUri.host?.contains("youtube.com", ignoreCase = true) == true && pathSegments.firstOrNull() == "shorts" -> pathSegments.getOrNull(1)
            normalizedUrl.startsWith("http", ignoreCase = true) -> null
            else -> normalizedUrl.substringAfterLast("/")
        }?.takeIf(::isLikelyYouTubeId)

        return candidate?.let { "https://www.youtube.com/watch?v=$it" }
    }

    private fun isLikelyYouTubeId(candidate: String): Boolean {
        return candidate.length >= 11 && candidate.all { it.isLetterOrDigit() || it == '-' || it == '_' }
    }

    /**
     * Attempts to get the highest resolution thumbnail from a standard YouTube thumbnail URL.
     * Often 'hqdefault.jpg' or 'mqdefault.jpg' can be upgraded to 'maxresdefault.jpg'.
     */
    fun getHighResThumbnailUrl(): String {
        if (thumbnailUrl.contains("ytimg.com/vi/")) {
            // Replace standard resolutions with maximum resolution
            return thumbnailUrl
                .replace("hqdefault.jpg", "maxresdefault.jpg")
                .replace("mqdefault.jpg", "maxresdefault.jpg")
                .replace("sddefault.jpg", "maxresdefault.jpg")
                .replace("default.jpg", "maxresdefault.jpg")
        }
        return thumbnailUrl
    }
}

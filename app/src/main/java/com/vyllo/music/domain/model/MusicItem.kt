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

    fun getUniversalShareUrl(): String {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isBlank()) return "https://music.youtube.com"

        if (normalizedUrl.startsWith("http://") || normalizedUrl.startsWith("https://")) {
            try {
                val parsedUri = Uri.parse(normalizedUrl)
                val videoId = parsedUri.getQueryParameter("v")
                    ?: if (parsedUri.host?.contains("youtu.be", ignoreCase = true) == true) parsedUri.pathSegments.firstOrNull() else null
                if (videoId != null && videoId.isNotBlank() && isLikelyYouTubeId(videoId)) {
                    return "https://www.youtube.com/watch?v=$videoId"
                }
            } catch (_: Exception) {}
            return normalizedUrl
        }

        val candidate = normalizedUrl.substringAfterLast("/").substringAfter("v=")
        return if (isLikelyYouTubeId(candidate)) {
            "https://www.youtube.com/watch?v=$candidate"
        } else {
            "https://www.youtube.com/watch?v=$normalizedUrl"
        }
    }

    private fun isLikelyYouTubeId(candidate: String): Boolean {
        return candidate.length >= 11 && candidate.all { it.isLetterOrDigit() || it == '-' || it == '_' }
    }

    /**
     * Attempts to get the highest resolution thumbnail from YouTube or Google CDN thumbnail URLs.
     */
    fun getHighResThumbnailUrl(): String {
        return getThumbnailCandidates().firstOrNull() ?: thumbnailUrl
    }

    /**
     * Returns an ordered list of thumbnail URL candidates from highest to lowest resolution.
     * Useful for player fallback ladders if maxres 404s.
     */
    fun getThumbnailCandidates(): List<String> {
        if (thumbnailUrl.isBlank()) return emptyList()

        // 1. Google CDN / YouTube Music artwork (lh3.googleusercontent.com, yt3.ggpht.com)
        if (thumbnailUrl.contains("googleusercontent.com") || thumbnailUrl.contains("ggpht.com")) {
            val highResGoogle = when {
                thumbnailUrl.contains(Regex("=w\\d+-h\\d+")) ->
                    thumbnailUrl.replace(Regex("=w\\d+-h\\d+[^?&]*"), "=w1080-h1080-l90-rj")
                thumbnailUrl.contains(Regex("=s\\d+")) ->
                    thumbnailUrl.replace(Regex("=s\\d+[^?&]*"), "=s1080")
                else -> thumbnailUrl
            }
            return if (highResGoogle != thumbnailUrl) listOf(highResGoogle, thumbnailUrl) else listOf(thumbnailUrl)
        }

        // 2. Standard YouTube video thumbnail (i.ytimg.com/vi/)
        if (thumbnailUrl.contains("ytimg.com/vi/")) {
            val maxRes = thumbnailUrl
                .replace("hqdefault.jpg", "maxresdefault.jpg")
                .replace("mqdefault.jpg", "maxresdefault.jpg")
                .replace("sddefault.jpg", "maxresdefault.jpg")
                .replace("default.jpg", "maxresdefault.jpg")

            val sdRes = thumbnailUrl
                .replace("maxresdefault.jpg", "sddefault.jpg")
                .replace("hqdefault.jpg", "sddefault.jpg")
                .replace("mqdefault.jpg", "sddefault.jpg")
                .replace("default.jpg", "sddefault.jpg")

            val hqRes = thumbnailUrl
                .replace("maxresdefault.jpg", "hqdefault.jpg")
                .replace("sddefault.jpg", "hqdefault.jpg")
                .replace("mqdefault.jpg", "hqdefault.jpg")
                .replace("default.jpg", "hqdefault.jpg")

            return listOf(maxRes, sdRes, hqRes, thumbnailUrl).distinct()
        }

        return listOf(thumbnailUrl)
    }
}

package com.vyllo.music.domain.model

/**
 * Single source of truth for the artwork fallback ladder.
 *
 * Both the player and the story-share renderer walk this list in order, so a
 * track that shows artwork in the player can never render a blank share card
 * just because it picked a different URL.
 */
object ArtworkCandidates {

    /**
     * Ordered, de-duplicated artwork URLs: the already-resolved high-res URL
     * first, then the item's own candidate ladder, and finally URLs derived
     * from the video id when the item has no thumbnail at all (local/backup
     * entries, restored queue items).
     */
    fun forItem(item: MusicItem, resolvedThumbnailUrl: String? = null): List<String> {
        val candidates = ArrayList<String>(6)
        val resolved = resolvedThumbnailUrl?.trim().orEmpty()
        if (resolved.isNotEmpty()) candidates += resolved
        candidates += item.getThumbnailCandidates()
        if (candidates.isEmpty()) candidates += derivedFromVideoId(item.url)
        return candidates.distinct()
    }

    /**
     * Extracts a YouTube video id from a watch URL, a youtu.be link, a
     * music.youtube.com link, or a bare id. Deliberately avoids android.net.Uri
     * so this stays usable from plain JVM unit tests.
     */
    fun videoIdOf(rawUrl: String): String? {
        val url = rawUrl.trim()
        if (url.isEmpty()) return null

        if (url.contains("youtu.be/")) {
            val id = url.substringAfter("youtu.be/").substringBefore('/').substringBefore('?').substringBefore('#')
            return id.takeIf { isLikelyVideoId(it) }
        }

        val queryId = url.substringAfter("v=", "").substringBefore('&').substringBefore('#')
        if (queryId.isNotEmpty() && isLikelyVideoId(queryId)) return queryId

        return url.substringAfterLast('/')
            .substringBefore('?')
            .substringBefore('#')
            .takeIf { isLikelyVideoId(it) }
    }

    private fun derivedFromVideoId(rawUrl: String): List<String> {
        val videoId = videoIdOf(rawUrl) ?: return emptyList()
        return listOf(
            "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg",
            "https://i.ytimg.com/vi/$videoId/sddefault.jpg",
            "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        )
    }

    private fun isLikelyVideoId(candidate: String): Boolean =
        candidate.length == 11 && candidate.all { it.isLetterOrDigit() || it == '-' || it == '_' }
}

package com.vyllo.music.domain.repository

/**
 * Resolves a higher-resolution thumbnail URL for a media item.
 */
interface HighResThumbnailResolver {
    suspend fun resolveHighResThumbnail(
        urlOrId: String,
        fallbackThumbnail: String? = null,
        title: String? = null,
        artist: String? = null
    ): String
}

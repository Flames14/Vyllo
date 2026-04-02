package com.vyllo.music.domain.model

/**
 * Download status enum representing the current state of a download.
 */
enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

/**
 * Entity representing a downloaded song stored locally.
 */
data class DownloadEntity(
    val url: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String,
    val filePath: String,
    val fileSize: Long = 0,
    val downloadedAt: Long = System.currentTimeMillis(),
    val status: DownloadStatus = DownloadStatus.PENDING
)

/**
 * Entity representing a user-created playlist.
 */
data class PlaylistEntity(
    val id: Long = 0,
    val name: String
)

/**
 * Entity representing a song in a playlist.
 */
data class PlaylistSongEntity(
    val playlistId: Long,
    val url: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String,
    val addedAt: Long = System.currentTimeMillis()
)

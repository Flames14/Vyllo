package com.vyllo.music.data.download

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a downloaded song stored locally.
 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val url: String,               // YouTube URL (unique identifier)
    val title: String,
    val uploader: String,
    val thumbnailUrl: String,
    val filePath: String,          // Local file path
    val fileSize: Long = 0,        // File size in bytes
    val downloadedAt: Long = System.currentTimeMillis(),
    val status: DownloadStatus = DownloadStatus.PENDING
)

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

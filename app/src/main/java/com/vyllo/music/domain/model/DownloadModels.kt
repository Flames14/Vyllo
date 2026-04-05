package com.vyllo.music.domain.model

/**
 * Download status enum representing the current state of a download.
 * This is shared between domain and data layers.
 */
enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

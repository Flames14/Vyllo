package com.vyllo.music.domain.model

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
}

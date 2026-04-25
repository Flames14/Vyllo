package com.vyllo.music.recognition.domain.model

import com.vyllo.music.domain.model.MusicItem

/**
 * Represents the status of the music recognition process.
 */
sealed class RecognitionStatus {
    data object Idle : RecognitionStatus()
    data object Listening : RecognitionStatus()
    data object Processing : RecognitionStatus()
    data class Success(val result: RecognitionResult) : RecognitionStatus()
    data class NoMatch(val message: String) : RecognitionStatus()
    data class Error(val message: String) : RecognitionStatus()
}

/**
 * High-level result of a successful music recognition.
 * Mapped from Shazam's internal format to our app's domain.
 */
data class RecognitionResult(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val coverArtUrl: String?,
    val coverArtHqUrl: String?,
    val genre: String?,
    val releaseDate: String?,
    val label: String?,
    val lyrics: String?,
    val shazamUrl: String?,
    val appleMusicUrl: String?,
    val spotifyUrl: String?,
    val isrc: String?,
    val youtubeVideoId: String?
) {
    /**
     * Convert to the standard MusicItem used in Vyllo.
     */
    fun toMusicItem(): MusicItem {
        return MusicItem(
            title = title,
            url = youtubeVideoId?.let { "https://www.youtube.com/watch?v=$it" } ?: shazamUrl ?: "",
            uploader = artist,
            thumbnailUrl = coverArtHqUrl ?: coverArtUrl ?: "",
            type = com.vyllo.music.domain.model.MusicItemType.SONG
        )
    }
}

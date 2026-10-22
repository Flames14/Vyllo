package com.vyllo.music.domain.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A track saved to the user's library independently from its like rating. */
@Entity(
    tableName = "local_library_songs",
    indices = [Index("album"), Index("artist")]
)
data class LocalLibrarySong(
    @PrimaryKey val url: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val thumbnailUrl: String = "",
    val durationSecs: Long = 0L,
    val addedAt: Long = System.currentTimeMillis()
) {
    fun toMusicItem() = MusicItem(
        id = url,
        title = title,
        url = url,
        uploader = artist,
        thumbnailUrl = thumbnailUrl,
        durationSecs = durationSecs
    )
}

fun MusicItem.toLocalLibrarySong(album: String = "") = LocalLibrarySong(
    url = url,
    title = title,
    artist = uploader,
    album = album,
    thumbnailUrl = thumbnailUrl,
    durationSecs = durationSecs
)

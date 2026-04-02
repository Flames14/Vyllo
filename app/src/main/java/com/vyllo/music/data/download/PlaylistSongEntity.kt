package com.vyllo.music.data.download

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "url"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId")]
)
data class PlaylistSongEntity(
    val playlistId: Long,
    val url: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String,
    val addedAt: Long = System.currentTimeMillis()
)

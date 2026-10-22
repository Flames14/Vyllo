package com.vyllo.music.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val coverUrl: String? = null,
    val coverCropX: Float = 0.5f,
    val coverCropY: Float = 0.5f
)

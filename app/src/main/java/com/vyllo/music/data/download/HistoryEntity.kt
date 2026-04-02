package com.vyllo.music.data.download

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.MusicItemType

@Entity(tableName = "playback_history")
data class HistoryEntity(
    @PrimaryKey
    val url: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String,
    val type: MusicItemType,
    val lastPlayedAt: Long = System.currentTimeMillis()
)

fun HistoryEntity.toMusicItem() = MusicItem(
    title = title,
    url = url,
    uploader = uploader,
    thumbnailUrl = thumbnailUrl,
    type = type
)

fun MusicItem.toHistoryEntity() = HistoryEntity(
    url = url,
    title = title,
    uploader = uploader,
    thumbnailUrl = thumbnailUrl,
    type = type
)

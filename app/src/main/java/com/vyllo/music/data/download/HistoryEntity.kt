package com.vyllo.music.data.download

import androidx.room.ColumnInfo
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
    val lastPlayedAt: Long = System.currentTimeMillis(),
    /**
     * How many times this track started playing. Rows that predate the stats
     * feature start at 0 because the real count was never recorded.
     */
    @ColumnInfo(defaultValue = "0")
    val playCount: Int = 0,
    /** Total milliseconds actually played, across every play. */
    @ColumnInfo(defaultValue = "0")
    val listenedMs: Long = 0L
)

/** Aggregate row for the stats dashboard. */
data class HistoryTotalsRow(
    val totalPlays: Int = 0,
    val totalListenedMs: Long = 0L,
    val totalTracks: Int = 0
)

/** Per-artist aggregate row; `uploader` is the only artist field we store. */
data class ArtistStatRow(
    val uploader: String,
    val plays: Int,
    val listenedMs: Long
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

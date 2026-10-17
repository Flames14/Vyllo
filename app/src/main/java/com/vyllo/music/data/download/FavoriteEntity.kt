package com.vyllo.music.data.download

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.MusicItemType

/**
 * A user rating for a track. Replaces the previous cosmetic like/dislike buttons
 * which only mutated local Compose state and were lost on recomposition.
 *
 * [rating] uses the same signed convention as YouTube's vote endpoint:
 * 1 = liked, -1 = disliked, 0 = no rating (row is deleted, not stored).
 */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey
    val url: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String,
    val type: MusicItemType,
    val rating: Int,
    val likedAt: Long = System.currentTimeMillis()
)

fun FavoriteEntity.toMusicItem(): MusicItem = MusicItem(
    title = title,
    url = url,
    uploader = uploader,
    thumbnailUrl = thumbnailUrl,
    type = type
)
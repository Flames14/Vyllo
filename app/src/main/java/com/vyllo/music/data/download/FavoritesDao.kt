package com.vyllo.music.data.download

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritesDao {

    @Query("SELECT * FROM favorites WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): FavoriteEntity?

    /** Signed rating for one track: 1 liked, -1 disliked, 0 none. */
    @Query("SELECT rating FROM favorites WHERE url = :url LIMIT 1")
    suspend fun getRating(url: String): Int?

    @Query("SELECT * FROM favorites WHERE rating = 1 ORDER BY likedAt DESC")
    fun observeLiked(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE rating = -1 ORDER BY likedAt DESC")
    fun observeDisliked(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites ORDER BY likedAt DESC LIMIT :limit")
    suspend fun getAllList(limit: Int = 1000): List<FavoriteEntity>

    @Query("SELECT COUNT(*) FROM favorites WHERE rating = 1")
    fun observeLikedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM favorites")
    suspend fun clear()
}
package com.vyllo.music.data.download

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 20): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM playback_history ORDER BY lastPlayedAt DESC LIMIT :limit")
    suspend fun getRecentHistoryList(limit: Int = 20): List<HistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: HistoryEntity)

    @Query("DELETE FROM playback_history WHERE url = :url")
    suspend fun deleteFromHistory(url: String)

    @Query("DELETE FROM playback_history")
    suspend fun clearHistory()
}

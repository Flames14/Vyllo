package com.vyllo.music.data.download

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class HistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY lastPlayedAt DESC LIMIT :limit")
    abstract fun getRecentHistory(limit: Int = 50): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM playback_history ORDER BY lastPlayedAt DESC LIMIT :limit")
    abstract suspend fun getRecentHistoryList(limit: Int = 50): List<HistoryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIgnore(history: HistoryEntity): Long

    @Query(
        "UPDATE playback_history SET title = :title, uploader = :uploader, " +
            "thumbnailUrl = :thumbnailUrl, type = :type, lastPlayedAt = :lastPlayedAt " +
            "WHERE url = :url"
    )
    abstract suspend fun updateMetadata(
        url: String,
        title: String,
        uploader: String,
        thumbnailUrl: String,
        type: String,
        lastPlayedAt: Long
    )

    @Query("UPDATE playback_history SET playCount = playCount + 1, lastPlayedAt = :at WHERE url = :url")
    abstract suspend fun incrementPlayCount(url: String, at: Long)

    @Query("UPDATE playback_history SET listenedMs = listenedMs + :listenedMs, lastPlayedAt = :at WHERE url = :url")
    abstract suspend fun addListenedTime(url: String, listenedMs: Long, at: Long)

    @Query(
        "SELECT COALESCE(SUM(playCount), 0) AS totalPlays, " +
            "COALESCE(SUM(listenedMs), 0) AS totalListenedMs, " +
            "COUNT(*) AS totalTracks FROM playback_history"
    )
    abstract fun observeTotals(): Flow<HistoryTotalsRow>

    @Query(
        "SELECT * FROM playback_history " +
            "ORDER BY playCount DESC, listenedMs DESC, lastPlayedAt DESC LIMIT :limit"
    )
    abstract fun observeTopTracks(limit: Int): Flow<List<HistoryEntity>>

    @Query(
        "SELECT uploader AS uploader, SUM(playCount) AS plays, SUM(listenedMs) AS listenedMs " +
            "FROM playback_history GROUP BY uploader " +
            "ORDER BY plays DESC, listenedMs DESC LIMIT :limit"
    )
    abstract fun observeTopArtists(limit: Int): Flow<List<ArtistStatRow>>

    @Query("DELETE FROM playback_history WHERE url = :url")
    abstract suspend fun deleteFromHistory(url: String)

    @Query("DELETE FROM playback_history")
    abstract suspend fun clearHistory()

    /**
     * Refresh an existing row without touching its counters; insert it when the
     * track is new. Used by history import/alarm paths that are not "plays".
     */
    @Transaction
    open suspend fun insertHistory(history: HistoryEntity) {
        val rowId = insertIgnore(history)
        if (rowId == -1L) {
            updateMetadata(
                url = history.url,
                title = history.title,
                uploader = history.uploader,
                thumbnailUrl = history.thumbnailUrl,
                type = history.type.name,
                lastPlayedAt = history.lastPlayedAt
            )
        }
    }

    /** A track started playing: ensure the row exists, then count the play. */
    @Transaction
    open suspend fun recordPlayStart(history: HistoryEntity) {
        insertHistory(history)
        incrementPlayCount(history.url, history.lastPlayedAt)
    }

    /** Accumulated playback time for an already-known track. */
    @Transaction
    open suspend fun recordListenedTime(url: String, listenedMs: Long, at: Long) {
        if (listenedMs <= 0L) return
        addListenedTime(url, listenedMs, at)
    }
}

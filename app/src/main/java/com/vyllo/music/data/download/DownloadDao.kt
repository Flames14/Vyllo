package com.vyllo.music.data.download

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for download operations.
 */
@Dao
interface DownloadDao {
    
    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY downloadedAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>
    
    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY downloadedAt DESC")
    suspend fun getAllDownloadsList(): List<DownloadEntity>
    
    @Query("SELECT * FROM downloads WHERE url = :url LIMIT 1")
    suspend fun getDownloadByUrl(url: String): DownloadEntity?
    
    @Query("SELECT * FROM downloads WHERE status = 'DOWNLOADING' OR status = 'PENDING'")
    fun getActiveDownloads(): Flow<List<DownloadEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity)
    
    @Update
    suspend fun updateDownload(download: DownloadEntity)
    
    @Delete
    suspend fun deleteDownload(download: DownloadEntity)
    
    @Query("DELETE FROM downloads WHERE url = :url")
    suspend fun deleteByUrl(url: String)
    
    @Query("UPDATE downloads SET status = :status WHERE url = :url")
    suspend fun updateStatus(url: String, status: DownloadStatus)
    
    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE url = :url AND status = 'COMPLETED')")
    suspend fun isDownloaded(url: String): Boolean
}

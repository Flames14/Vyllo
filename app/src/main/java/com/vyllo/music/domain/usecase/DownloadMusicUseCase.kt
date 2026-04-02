package com.vyllo.music.domain.usecase

import com.vyllo.music.data.IMusicRepository
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.data.download.DownloadEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class DownloadMusicUseCase @Inject constructor(
    private val repository: IMusicRepository
) {
    fun getAllDownloads(): Flow<List<DownloadEntity>> = repository.getAllDownloads()
    
    fun downloadSong(item: MusicItem) = repository.downloadSong(item)
    
    fun cancelDownload(url: String) = repository.cancelDownload(url)
    
    suspend fun deleteDownload(url: String) = repository.deleteDownload(url)
    
    suspend fun isDownloaded(url: String): Boolean = repository.isDownloaded(url)
}

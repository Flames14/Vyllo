package com.vyllo.music.data.repository

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import androidx.work.workDataOf
import androidx.work.OutOfQuotaPolicy
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.data.download.DownloadDao
import com.vyllo.music.data.download.DownloadEntity
import com.vyllo.music.data.download.DownloadStatus
import com.vyllo.music.service.DownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    fun getAllDownloads(): Flow<List<DownloadEntity>> =
        downloadDao.getAllDownloads().distinctUntilChanged()

    suspend fun isDownloaded(url: String): Boolean = downloadDao.isDownloaded(url)

    fun downloadSong(item: MusicItem) {
        repositoryScope.launch {
            val existing = downloadDao.getDownloadByUrl(item.url)
            if (existing?.status == DownloadStatus.COMPLETED && File(existing.filePath).exists()) {
                return@launch
            }

            downloadDao.insertDownload(
                DownloadEntity(
                    url = item.url,
                    title = item.title,
                    uploader = item.uploader,
                    thumbnailUrl = item.thumbnailUrl,
                    filePath = existing?.filePath.orEmpty(),
                    fileSize = existing?.fileSize ?: 0L,
                    downloadedAt = existing?.downloadedAt ?: System.currentTimeMillis(),
                    status = DownloadStatus.PENDING
                )
            )
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequestBuilder = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(
                "url" to item.url,
                "title" to item.title,
                "uploader" to item.uploader,
                "thumbnailUrl" to item.thumbnailUrl
            ))
            .addTag("download_song")
            .addTag("download_${item.url}")

        // CRITICAL: setExpedited() ensures the work survives screen-off on Android 12+
        // Without this, WorkManager defers work when the device enters Doze mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            workRequestBuilder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }

        val workRequest = workRequestBuilder.build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "download_${item.url}",
            ExistingWorkPolicy.KEEP,
            workRequest
        )
    }

    fun cancelDownload(url: String) {
        WorkManager.getInstance(context).cancelUniqueWork("download_$url")
        repositoryScope.launch {
            downloadDao.updateStatus(url, DownloadStatus.FAILED)
        }
    }

    suspend fun deleteDownload(url: String) = withContext(Dispatchers.IO) {
        val download = downloadDao.getDownloadByUrl(url)
        if (download != null) {
            File(download.filePath).let { if (it.exists()) it.delete() }
            downloadDao.deleteByUrl(url)
        }
    }

    suspend fun getLocalStreamUrl(url: String): String? {
        val download = downloadDao.getDownloadByUrl(url)
        return if (download != null && download.status == DownloadStatus.COMPLETED) {
            val file = File(download.filePath)
            if (file.exists()) "file://${download.filePath}" else null
        } else null
    }
}

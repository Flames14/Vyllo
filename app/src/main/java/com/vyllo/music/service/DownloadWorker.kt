package com.vyllo.music.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vyllo.music.data.IMusicRepository
import com.vyllo.music.data.download.DownloadDao
import com.vyllo.music.data.download.DownloadEntity
import com.vyllo.music.data.download.DownloadStatus
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import android.content.pm.ServiceInfo
import androidx.hilt.work.HiltWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: IMusicRepository,
    private val downloadDao: DownloadDao,
    private val client: OkHttpClient
) : CoroutineWorker(appContext, params) {

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val channelId = "download_channel"
    private val notificationId = params.id.hashCode()

    override suspend fun doWork(): Result {
        val url = inputData.getString("url") ?: return Result.failure()
        val title = inputData.getString("title") ?: "Unknown Song"
        val uploader = inputData.getString("uploader") ?: "Unknown Artist"
        val thumbnailUrl = inputData.getString("thumbnailUrl") ?: ""

        // Create notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Set foreground info to keep the worker running
        setForeground(createForegroundInfo(title, 0))

        try {
            // 1. Resolve stream URL with retry
            var streamUrl = repository.getStreamUrl(url, force = false)
            if (streamUrl == null) {
                kotlinx.coroutines.delay(500)
                streamUrl = repository.getStreamUrl(url, force = true)
            }
            streamUrl ?: return Result.failure()

            // 2. Prepare local file
            val downloadsDir = File(applicationContext.filesDir, "downloads")
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            val safeTitle = title.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(100)
            val extension = getExtensionFromUrl(streamUrl)
            val localFile = File(downloadsDir, "${safeTitle}-${System.currentTimeMillis()}.$extension")

            // 3. Start download with optimized settings
            val request = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", getRandomUserAgent())
                .header("Accept", "*/*")
                .header("Accept-Encoding", "identity")
                .header("Connection", "Keep-Alive")
                .header("Cache-Control", "no-cache")
                .build()

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val response = client.newCall(request).execute()

                if (!response.isSuccessful || response.body == null) {
                    android.util.Log.e("DownloadWorker", "Download failed: ${response.code} ${response.message}")
                    throw Exception("Failed to download: ${response.code}")
                }

                val body = response.body!!
                val totalBytes = body.contentLength()
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(localFile)

                val buffer = ByteArray(32 * 1024)
                var bytesRead: Int
                var totalRead: Long = 0
                var lastProgressUpdate = 0L
                val progressUpdateInterval = 100L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalRead += bytesRead

                    val currentTime = System.currentTimeMillis()
                    if (totalBytes > 0 && currentTime - lastProgressUpdate >= progressUpdateInterval) {
                        val progress = (totalRead * 100 / totalBytes).toInt()
                        setProgress(workDataOf("progress" to progress))
                        notificationManager.notify(notificationId, createNotification(title, progress))
                        lastProgressUpdate = currentTime
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                if (!localFile.exists() || localFile.length() == 0L) {
                    throw Exception("Downloaded file is empty or missing")
                }

                // 4. Save metadata to database
                val entity = DownloadEntity(
                    url = url,
                    title = title,
                    uploader = uploader,
                    thumbnailUrl = thumbnailUrl,
                    filePath = localFile.absolutePath,
                    fileSize = localFile.length(),
                    status = DownloadStatus.COMPLETED
                )
                downloadDao.insertDownload(entity)

                android.util.Log.d("DownloadWorker", "Download complete: ${localFile.name} (${localFile.length() / 1024}KB)")
            }

            val successNotification = NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Download Complete")
                .setContentText(title)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            notificationManager.notify(notificationId, successNotification)

            return Result.success()

        } catch (e: Exception) {
            android.util.Log.e("DownloadWorker", "Download failed for $title: ${e.message}", e)
            try {
                downloadDao.updateStatus(url, DownloadStatus.FAILED)
            } catch (_: Exception) {}
            return Result.failure()
        }
    }

    private fun getExtensionFromUrl(url: String): String {
        return when {
            url.contains("opus", ignoreCase = true) -> "opus"
            url.contains("webm", ignoreCase = true) -> "webm"
            url.contains("3gp", ignoreCase = true) -> "3gp"
            url.contains("mp4", ignoreCase = true) -> "m4a"
            else -> "m4a"
        }
    }

    private fun getRandomUserAgent(): String {
        val userAgents = listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )
        return userAgents.random()
    }

    private fun createForegroundInfo(title: String, progress: Int): ForegroundInfo {
        val notification = createNotification(title, progress)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun createNotification(title: String, progress: Int) =
        NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading Music")
            .setContentText(title)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
}

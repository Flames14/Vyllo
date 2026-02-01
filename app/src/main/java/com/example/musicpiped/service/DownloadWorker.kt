package com.example.musicpiped.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.musicpiped.data.MusicRepository
import com.example.musicpiped.data.download.DownloadDatabase
import com.example.musicpiped.data.download.DownloadEntity
import com.example.musicpiped.data.download.DownloadStatus
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import android.content.pm.ServiceInfo

/**
 * Worker class to handle background song downloads.
 */
class DownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val channelId = "download_channel"
    private val notificationId = params.id.hashCode()

    override suspend fun doWork(): Result {
        val url = inputData.getString("url") ?: return Result.failure()
        val title = inputData.getString("title") ?: "Unknown Song"
        val uploader = inputData.getString("uploader") ?: "Unknown Artist"
        val thumbnailUrl = inputData.getString("thumbnailUrl") ?: ""

        // Initialize repository
        MusicRepository.init(applicationContext)
        
        val db = DownloadDatabase.getInstance(applicationContext)
        val dao = db.downloadDao()

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
            // 1. Resolve stream URL
            val streamUrl = MusicRepository.getStreamUrl(url, force = true)
                ?: return Result.failure()

            // 2. Prepare local file
            val downloadsDir = File(applicationContext.filesDir, "downloads")
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            
            // Clean title for filename
            val safeTitle = title.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val extension = if (streamUrl.contains("opus")) "opus" else "m4a"
            val localFile = File(downloadsDir, "$safeTitle-${System.currentTimeMillis()}.$extension")

            // 3. Start download
            // Fix: Add User-Agent to prevent 403/Forbidden on some streams
            val request = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
                .header("Connection", "Keep-Alive")
                .build()
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                // Optimization: Lower thread priority to prevent UI/Playback jitter
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)

                val response = client.newCall(request).execute()

                if (!response.isSuccessful || response.body == null) {
                    throw Exception("Failed to download: ${response.code}")
                }

                val body = response.body!!
                val totalBytes = body.contentLength()
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(localFile)

                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var totalRead: Long = 0
                var throttleCounter = 0

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    
                    // Throttling: Yield every ~80KB to let Player stream efficiently
                    if (++throttleCounter % 10 == 0) {
                       kotlinx.coroutines.delay(5) 
                    }

                    if (totalBytes > 0) {
                        val progress = (totalRead * 100 / totalBytes).toInt()
                        // Update progress sparingly to avoid notification spam
                        if (progress % 5 == 0) {
                            setProgress(workDataOf("progress" to progress))
                            notificationManager.notify(notificationId, createNotification(title, progress))
                        }
                    }
                }

                outputStream.close()
                inputStream.close()

                // 4. Save metadata to database
                val entity = DownloadEntity(
                    url = url,
                    title = title,
                    uploader = uploader,
                    thumbnailUrl = thumbnailUrl,
                    filePath = localFile.absolutePath,
                    fileSize = totalRead,
                    status = DownloadStatus.COMPLETED
                )
                dao.insertDownload(entity)
            }

            // Success notification
            val successNotification = NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Download Complete")
                .setContentText(title)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            notificationManager.notify(notificationId, successNotification)

            return Result.success()

        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("DownloadWorker", "Download failed for $title", e)
            try {
                dao.updateStatus(url, DownloadStatus.FAILED)
            } catch (_: Exception) {}
            return Result.failure()
        }
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

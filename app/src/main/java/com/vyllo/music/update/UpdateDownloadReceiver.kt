package com.vyllo.music.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

class UpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadId == -1L) return

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)

            if (cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                if (statusIndex >= 0 && cursor.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL) {
                    val contentUri = downloadManager.getUriForDownloadedFile(downloadId)
                    if (contentUri != null) {
                        promptInstall(context, contentUri)
                    } else {
                        val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                        if (uriIndex >= 0) {
                            val uriString = cursor.getString(uriIndex)
                            if (uriString != null) {
                                val apkUri = Uri.parse(uriString)
                                if (apkUri.scheme == "file") {
                                    val file = java.io.File(apkUri.path!!)
                                    val providerUri = androidx.core.content.FileProvider.getUriForFile(
                                        context, 
                                        "${context.packageName}.provider", 
                                        file
                                    )
                                    promptInstall(context, providerUri)
                                } else {
                                    promptInstall(context, apkUri)
                                }
                            }
                        }
                    }
                }
            }
            cursor.close()
        }
    }

    private fun promptInstall(context: Context, apkUri: Uri) {
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        try {
            context.startActivity(installIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            // If direct URI fails, fallback to standard
        }
    }
}

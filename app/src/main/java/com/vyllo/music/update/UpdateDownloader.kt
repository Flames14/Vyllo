package com.vyllo.music.update

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class UpdateDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    suspend fun downloadApk(url: String, fileName: String = "vyllo-update.apk"): File? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                // Use a standard generic User-Agent if needed, though OkHttp default usually works
                .header("User-Agent", "VylloApp/Updater")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext null
            }

            val responseBody = response.body ?: return@withContext null

            // Save to external files dir (app-specific, no permission required)
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir != null && !downloadDir.exists()) {
                downloadDir.mkdirs()
            }

            val apkFile = File(downloadDir, fileName)
            if (apkFile.exists()) {
                apkFile.delete()
            }

            val inputStream = responseBody.byteStream()
            val outputStream = FileOutputStream(apkFile)

            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            outputStream.flush()
            outputStream.close()
            inputStream.close()

            return@withContext apkFile
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
}

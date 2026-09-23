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
            if (!AppUpdateRepository.isTrustedApkHost(url)) {
                com.vyllo.music.core.security.SecureLogger.w(
                    "UpdateDownloader",
                    "Refusing download from untrusted host"
                )
                return@withContext null
            }

            val request = Request.Builder()
                .url(url)
                // Use a standard generic User-Agent if needed, though OkHttp default usually works
                .header("User-Agent", "VylloApp/Updater")
                .build()

            val response = okHttpClient.newCall(request).execute()
            response.use {
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

                responseBody.byteStream().use { inputStream ->
                    FileOutputStream(apkFile).use { outputStream ->
                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                        outputStream.flush()
                    }
                }

                // Reject non-APK payloads (HTML error pages, etc.) before install prompt.
                if (!isZipFile(apkFile)) {
                    apkFile.delete()
                    com.vyllo.music.core.security.SecureLogger.w(
                        "UpdateDownloader",
                        "Downloaded file is not a valid APK/ZIP — discarded"
                    )
                    return@withContext null
                }

                return@withContext apkFile
            }
        } catch (e: Exception) {
            com.vyllo.music.core.security.SecureLogger.e("UpdateDownloader", "APK download failed", e)
            return@withContext null
        }
    }

    private fun isZipFile(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        val header = ByteArray(4)
        file.inputStream().use { it.read(header) }
        // APK = ZIP: local file header PK\x03\x04 (or empty/spanned PK\x05\x06 / PK\x07\x08)
        return header[0] == 'P'.code.toByte() &&
            header[1] == 'K'.code.toByte() &&
            header[2] in listOf(3, 5, 7).map { it.toByte() }
    }
}

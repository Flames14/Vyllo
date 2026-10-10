package com.vyllo.music.update

import android.content.Context
import com.vyllo.music.core.security.SecureLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject

class UpdateDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    okHttpClient: OkHttpClient
) {
    private val updateClient = okHttpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun downloadApk(url: String): File? = withContext(Dispatchers.IO) {
        if (!AppUpdateRepository.isTrustedApkHost(url)) {
            SecureLogger.w("UpdateDownloader", "Refusing download from untrusted host")
            return@withContext null
        }

        val updateDir = File(context.cacheDir, UPDATE_DIRECTORY)
        if (!updateDir.exists() && !updateDir.mkdirs()) {
            SecureLogger.e("UpdateDownloader", "Unable to create update cache directory")
            return@withContext null
        }

        val destination = File(updateDir, UPDATE_FILE_NAME)
        val partial = File(updateDir, "$UPDATE_FILE_NAME.part")
        partial.delete()

        try {
            openTrustedResponse(url).use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                if (!isAllowedDownloadSize(body.contentLength())) {
                    SecureLogger.w("UpdateDownloader", "Refusing oversized update")
                    return@withContext null
                }

                var downloaded = 0L
                body.byteStream().use { input ->
                    FileOutputStream(partial).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val bytesRead = input.read(buffer)
                            if (bytesRead == -1) break
                            downloaded += bytesRead
                            if (!isAllowedDownloadSize(downloaded)) {
                                throw IOException("Update exceeds the maximum allowed size")
                            }
                            output.write(buffer, 0, bytesRead)
                        }
                        output.fd.sync()
                    }
                }

                if (!isZipFile(partial)) {
                    SecureLogger.w("UpdateDownloader", "Downloaded file is not a valid APK")
                    return@withContext null
                }

                if (destination.exists() && !destination.delete()) {
                    SecureLogger.e("UpdateDownloader", "Unable to replace previous update")
                    return@withContext null
                }
                if (!partial.renameTo(destination)) {
                    SecureLogger.e("UpdateDownloader", "Unable to finalize update download")
                    return@withContext null
                }
                destination
            }
        } catch (e: Exception) {
            SecureLogger.e("UpdateDownloader", "APK download failed", e)
            null
        } finally {
            partial.delete()
        }
    }

    private fun openTrustedResponse(initialUrl: String): Response {
        var nextUrl = initialUrl
        repeat(MAX_REDIRECTS + 1) {
            if (!AppUpdateRepository.isTrustedApkHost(nextUrl)) {
                throw IOException("Update redirect target is not trusted")
            }
            val response = updateClient.newCall(
                Request.Builder().url(nextUrl).header("User-Agent", "VylloApp/Updater").build()
            ).execute()
            if (!response.isRedirect) return response

            val redirect = response.header("Location")?.let { response.request.url.resolve(it) }
            response.close()
            nextUrl = redirect?.toString()
                ?: throw IOException("Update redirect is missing a valid Location")
        }
        throw IOException("Update exceeded the redirect limit")
    }

    private fun isZipFile(file: File): Boolean {
        if (!file.isFile || file.length() < 4) return false
        val header = ByteArray(4)
        file.inputStream().use { if (it.read(header) != header.size) return false }
        return header[0] == 'P'.code.toByte() &&
            header[1] == 'K'.code.toByte() &&
            header[2] in ZIP_HEADER_TYPES
    }

    companion object {
        private const val UPDATE_DIRECTORY = "updates"
        private const val UPDATE_FILE_NAME = "vyllo-update.apk"
        private const val MAX_REDIRECTS = 5
        private const val BUFFER_SIZE = 64 * 1024
        internal const val MAX_APK_BYTES = 250L * 1024L * 1024L
        private val ZIP_HEADER_TYPES = setOf(3.toByte(), 5.toByte(), 7.toByte())

        internal fun isAllowedDownloadSize(contentLength: Long): Boolean =
            contentLength < 0L || contentLength <= MAX_APK_BYTES
    }
}

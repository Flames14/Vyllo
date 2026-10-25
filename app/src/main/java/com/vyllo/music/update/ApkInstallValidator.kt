package com.vyllo.music.update

import androidx.core.net.toUri

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.vyllo.music.core.security.SecureLogger
import java.io.File
import java.security.MessageDigest

object ApkInstallValidator {

    fun sha256Hex(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            SecureLogger.e("AppUpdate", "SHA-256 failed", e)
            null
        }
    }

    private fun signerSha256Hex(context: Context, packageName: String, apkPath: String?): Set<String>? {
        return try {
            val pm = context.packageManager
            // GET_SIGNING_CERTIFICATES only exists on API 28+. The constant is
            // inlined as a bare int, so on API 24-27 it is an unknown flag bit:
            // the package manager would fill in neither signingInfo nor
            // signatures, and in-app update validation silently failed for every
            // device below Pie. Pick the flag that actually works per platform.
            val archiveFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val info = if (apkPath == null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                }
            } else {
                pm.getPackageArchiveInfo(apkPath, archiveFlags)
            } ?: return null
            val signingInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo ?: return null
            } else {
                @Suppress("DEPRECATION")
                return info.signatures?.map {
                    MessageDigest.getInstance("SHA-256").digest(it.toByteArray())
                        .joinToString("") { b -> "%02x".format(b) }
                }?.toSet()
            }
            val signers = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            signers.map {
                MessageDigest.getInstance("SHA-256").digest(it.toByteArray())
                    .joinToString("") { b -> "%02x".format(b) }
            }.toSet()
        } catch (e: Exception) {
            SecureLogger.e("AppUpdate", "Signer read failed", e)
            null
        }
    }

    fun isSameSigner(context: Context, apkFile: File): Boolean {
        val installed = signerSha256Hex(context, context.packageName, null) ?: return false
        val candidate = signerSha256Hex(context, context.packageName, apkFile.absolutePath) ?: return false
        return installed.intersect(candidate).isNotEmpty()
    }

    fun buildInstallIntent(context: Context, apkFile: File): Intent? {
        val archiveFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val archive = context.packageManager.getPackageArchiveInfo(
            apkFile.absolutePath,
            archiveFlags
        )
        if (archive?.packageName != context.packageName) {
            SecureLogger.e("AppUpdate", "Refusing APK for a different package")
            return null
        }
        if (!isSameSigner(context, apkFile)) {
            SecureLogger.e("AppUpdate", "Refusing to install APK with unknown signer")
            return null
        }
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(context.contentResolver, "update", uri)
        }
    }

    fun ensureUnknownSourcesAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val pm = context.packageManager
        if (pm.canRequestPackageInstalls()) return true
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    "package:${context.packageName}".toUri()
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            )
        } catch (e: Exception) {
            SecureLogger.e("AppUpdate", "Cannot open unknown-sources settings", e)
        }
        return false
    }
}

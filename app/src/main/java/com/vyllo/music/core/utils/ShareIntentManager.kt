package com.vyllo.music.core.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.vyllo.music.R
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.model.MusicItem
import java.io.File

object ShareIntentManager {

    private const val TAG = "ShareIntentManager"

    /**
     * Shares an image to Instagram Stories.
     */
    fun shareToInstagramStory(context: Context, imageFile: File) {
        val uri = getUriForFile(context, imageFile)
        val intent = Intent("com.instagram.share.ADD_TO_STORY").apply {
            setDataAndType(uri, "image/png")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            
            // To make the background sticker interactive and full screen, we pass it as the interactive asset.
            // Since we don't have a background video, we use our generated 9:16 image as the background asset.
            putExtra("dataset_background_uri", uri)
            // Required for Android 11+
            clipData = android.content.ClipData.newRawUri("Story Background", uri)
            setPackage("com.instagram.android")
        }

        try {
            val resInfoList = context.packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Instagram share failed", e)
            Toast.makeText(context, "Instagram is not installed", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Shares an image to WhatsApp Status/Chat with full link.
     */
    fun shareToWhatsApp(context: Context, imageFile: File, item: MusicItem) {
        val uri = getUriForFile(context, imageFile)
        val shareUrl = item.getUniversalShareUrl()
        val text = "🎵 Listening to \"${item.title}\" by ${item.uploader} on Vyllo Music\n🔗 $shareUrl"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            setPackage("com.whatsapp")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            clipData = android.content.ClipData.newRawUri("Story Background", uri)
        }

        try {
            val resInfoList = context.packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            SecureLogger.e(TAG, "WhatsApp share failed", e)
            Toast.makeText(context, "WhatsApp is not installed", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Shares universally via the system chooser.
     */
    fun shareToAny(context: Context, imageFile: File, text: String) {
        val uri = getUriForFile(context, imageFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            clipData = android.content.ClipData.newRawUri("Story Background", uri)
        }
        
        val chooser = Intent.createChooser(intent, "Share Music Story")
        
        val resInfoList = context.packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        for (resolveInfo in resInfoList) {
            val packageName = resolveInfo.activityInfo.packageName
            context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        try {
            context.startActivity(chooser)
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Generic share failed", e)
            Toast.makeText(context, "Failed to share", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Shares direct song link (without image) universally.
     */
    fun shareSongLink(context: Context, item: MusicItem) {
        val shareUrl = item.getUniversalShareUrl()
        val text = "🎵 Listening to \"${item.title}\" by ${item.uploader} on Vyllo Music\n\n🔗 Stream here: $shareUrl"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Listen to ${item.title} on Vyllo Music")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, "Share Song Link")
        try {
            context.startActivity(chooser)
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Share link failed", e)
            Toast.makeText(context, "Failed to share link", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Copies direct song link to clipboard.
     */
    fun copySongLink(context: Context, item: MusicItem) {
        try {
            val shareUrl = item.getUniversalShareUrl()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Song Link", shareUrl)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Link copied to clipboard! 🔗", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Copy link failed", e)
            Toast.makeText(context, "Failed to copy link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
    }
}

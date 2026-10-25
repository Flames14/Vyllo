package com.vyllo.music.core.utils

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.vyllo.music.R
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.model.MusicItem
import java.io.File

object ShareIntentManager {

    private const val TAG = "ShareIntentManager"
    private const val INSTAGRAM_PACKAGE = "com.instagram.android"
    private val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

    /**
     * Shares an image to Instagram Stories.
     *
     * The card is passed as both the intent data and `background_asset_uri`
     * (the documented Stories extra). The previous `dataset_background_uri`
     * key does not exist, so some Instagram builds dropped the image.
     */
    fun shareToInstagramStory(context: Context, imageFile: File) {
        val uri = getUriForFile(context, imageFile)
        val intent = Intent("com.instagram.share.ADD_TO_STORY").apply {
            setDataAndType(uri, "image/*")
            putExtra("background_asset_uri", uri.toString())
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            // Required for Android 11+ package visibility.
            clipData = ClipData.newRawUri("Story Background", uri)
            setPackage(INSTAGRAM_PACKAGE)
        }

        if (!launchWithPermission(context, intent, uri, INSTAGRAM_PACKAGE)) {
            Toast.makeText(context, context.getString(R.string.share_instagram_missing), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Shares an image to WhatsApp Status/Chat with full link.
     * Tries the consumer app first, then WhatsApp Business.
     */
    fun shareToWhatsApp(context: Context, imageFile: File, item: MusicItem) {
        val shareUrl = item.getUniversalShareUrl()
        val text = context.getString(R.string.share_listening_story, item.title, item.uploader, shareUrl)

        for (packageName in WHATSAPP_PACKAGES) {
            val uri = getUriForFile(context, imageFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, text)
                setPackage(packageName)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                clipData = ClipData.newRawUri("Story Background", uri)
            }
            if (launchWithPermission(context, intent, uri, packageName)) return
        }

        Toast.makeText(context, context.getString(R.string.share_whatsapp_missing), Toast.LENGTH_SHORT).show()
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
            clipData = ClipData.newRawUri("Story Background", uri)
        }

        grantReadToResolvers(context, intent, uri)

        val chooser = Intent.createChooser(intent, context.getString(R.string.share_story_chooser))

        try {
            context.startActivity(chooser)
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Generic share failed", e)
            Toast.makeText(context, context.getString(R.string.share_failed), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Shares direct song link (without image) universally.
     */
    fun shareSongLink(context: Context, item: MusicItem) {
        val shareUrl = item.getUniversalShareUrl()
        val text = context.getString(R.string.share_listening_link, item.title, item.uploader, shareUrl)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_subject, item.title))
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, context.getString(R.string.share_song_link_chooser))
        try {
            context.startActivity(chooser)
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Share link failed", e)
            Toast.makeText(context, context.getString(R.string.share_link_failed), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Copies direct song link to clipboard.
     */
    fun copySongLink(context: Context, item: MusicItem) {
        try {
            val shareUrl = item.getUniversalShareUrl()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = ClipData.newPlainText(context.getString(R.string.share_clip_label), shareUrl)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, context.getString(R.string.share_link_copied), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Copy link failed", e)
            Toast.makeText(context, context.getString(R.string.share_copy_failed), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Grants the read permission to every activity that can handle [intent] and
     * to the explicit [targetPackage], then starts it.
     *
     * @return true when an activity was launched, false when the app is absent
     * or the launch failed. Callers report the failure instead of failing silently.
     */
    private fun launchWithPermission(
        context: Context,
        intent: Intent,
        uri: Uri,
        targetPackage: String
    ): Boolean {
        grantRead(context, uri, targetPackage)
        grantReadToResolvers(context, intent, uri)

        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            SecureLogger.w(TAG, "No activity for $targetPackage", e)
            false
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Share failed for $targetPackage", e)
            false
        }
    }

    private fun grantReadToResolvers(context: Context, intent: Intent, uri: Uri) {
        val resolved = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        resolved.forEach { info -> grantRead(context, uri, info.activityInfo.packageName) }
    }

    private fun grantRead(context: Context, uri: Uri, packageName: String) {
        runCatching {
            context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
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

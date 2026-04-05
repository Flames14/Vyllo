package com.vyllo.music.domain.manager

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.data.manager.PreferenceManager
import com.vyllo.music.service.FloatingWindowService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FloatingPlayerManager @Inject constructor(
    private val context: Context,
    private val preferenceManager: PreferenceManager
) {

    fun isEnabled(): Boolean {
        return preferenceManager.isFloatingPlayerEnabled
    }

    fun shouldShowFloatingPlayer(currentItem: MusicItem?): Boolean {
        return isEnabled() && currentItem != null
    }

    fun showFloatingPlayer(): Boolean {
        if (!isEnabled()) {
            return false
        }

        if (!hasOverlayPermission()) {
            return false
        }

        val intent = Intent(context, FloatingWindowService::class.java)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
            return true
        } catch (e: Exception) {
            SecureLogger.e("FloatingPlayerManager", "Failed to start floating player", e)
            return false
        }
    }

    fun hideFloatingPlayer() {
        try {
            val intent = Intent(context, FloatingWindowService::class.java)
            context.stopService(intent)
        } catch (e: Exception) {
            SecureLogger.e("FloatingPlayerManager", "Failed to stop floating player", e)
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.provider.Settings.canDrawOverlays(context)
        } else {
            true 
        }
    }

    fun getOverlayPermissionIntent(): android.content.Intent {
        return android.content.Intent(
            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}")
        )
    }

    fun needsOverlayPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !android.provider.Settings.canDrawOverlays(context)
    }

    fun savePosition(x: Int, y: Int) {
        preferenceManager.floatingPlayerX = x
        preferenceManager.floatingPlayerY = y
    }

    fun getSavedPosition(): Pair<Int, Int> {
        return Pair(
            preferenceManager.floatingPlayerX,
            preferenceManager.floatingPlayerY
        )
    }
}

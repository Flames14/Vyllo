package com.vyllo.music.domain.manager

import androidx.core.net.toUri

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import com.vyllo.music.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // No SDK_INT guards: Settings.canDrawOverlays, ACTION_MANAGE_OVERLAY_PERMISSION
    // and isIgnoringBatteryOptimizations are all API 23, below minSdk 24.

    fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun requestOverlayPermission(activity: Activity) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:${context.packageName}".toUri()
        )
        activity.startActivity(intent)
    }

    /**
     * Check if battery optimization is disabled for this app.
     * When battery optimization is enabled, Android may kill background services
     * when screen is off for extended periods.
     */
    fun isBatteryOptimizationDisabled(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Request to disable battery optimization for reliable background playback.
     * This is optional but recommended for music apps.
     *
     * Lint: BatteryLife — same justification as BatteryOptimizationHelper: doze
     * exemption is core to a music player's background playback and alarms, and
     * the prompt is preceded by an in-app rationale the user can decline.
     */
    @Suppress("BatteryLife")
    fun requestDisableBatteryOptimization(activity: Activity) {
        // Check first: when the exemption is already granted, launching the system
        // settings screen again looks like nothing happened and confuses users.
        if (isBatteryOptimizationDisabled()) {
            Toast.makeText(context, context.getString(R.string.permission_already_unrestricted), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:${context.packageName}".toUri()
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            // Some OEM skins reject the direct intent; fall back to the manual page.
            openBatteryOptimizationSettings(activity)
        }
    }

    /**
     * Show settings page where user can disable battery optimization manually.
     */
    fun openBatteryOptimizationSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
        }
        activity.startActivity(intent)
    }

    fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestMicrophonePermission(launcher: ActivityResultLauncher<String>) {
        launcher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun isSpeechRecognitionAvailable(): Boolean {
        val packageManager = context.packageManager
        val activities = packageManager.queryIntentActivities(
            Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
            0
        )
        return activities.isNotEmpty()
    }

}

package com.vyllo.music.domain.manager

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun requestOverlayPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            activity.startActivity(intent)
        }
    }

    /**
     * Check if battery optimization is disabled for this app.
     * When battery optimization is enabled, Android may kill background services
     * when screen is off for extended periods.
     */
    fun isBatteryOptimizationDisabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    /**
     * Request to disable battery optimization for reliable background playback.
     * This is optional but recommended for music apps.
     */
    fun requestDisableBatteryOptimization(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            activity.startActivity(intent)
        }
    }

    /**
     * Show settings page where user can disable battery optimization manually.
     */
    fun openBatteryOptimizationSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
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

    fun showPermissionRationale(permissionType: PermissionType) {
        val message = when (permissionType) {
            PermissionType.OVERLAY -> "Overlay permission is needed for the floating player feature"
            PermissionType.MICROPHONE -> "Microphone permission is needed for voice search"
            PermissionType.BATTERY -> "Disable battery optimization to keep music playing when screen is off"
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    enum class PermissionType {
        OVERLAY,
        MICROPHONE,
        BATTERY
    }
}

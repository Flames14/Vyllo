package com.vyllo.music.core.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimizationHelper {

    // NOTE: no SDK_INT guards — PowerManager#isIgnoringBatteryOptimizations and
    // ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS both landed in API 23, below
    // our minSdk 24. Guards would be dead code.

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Lint: BatteryLife.
     *
     * REQUEST_IGNORE_BATTERY_OPTIMIZATIONS is flagged as a Play policy concern,
     * and it is — for apps that do not need it. Vyllo's two headline features are
     * uninterrupted background playback and alarms that must fire after hours of
     * doze, which is exactly the "core app functionality" exemption the policy
     * carves out. It is also only ever requested after
     * [com.vyllo.music.MainActivity] shows a one-time rationale dialog the user
     * can decline.
     */
    @Suppress("BatteryLife")
    fun requestExemptionIfNeeded(context: Context) {
        if (isIgnoringBatteryOptimizations(context)) return
        try {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (_: Exception) { }
        }
    }
}

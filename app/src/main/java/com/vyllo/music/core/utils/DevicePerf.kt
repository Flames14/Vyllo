package com.vyllo.music.core.utils

import android.app.ActivityManager
import android.content.Context

object DevicePerf {
    @Volatile
    private var cachedLowRam: Boolean? = null

    fun isLowRamDevice(context: Context): Boolean {
        cachedLowRam?.let { return it }
        val am = context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val low = am?.isLowRamDevice ?: false
        cachedLowRam = low
        return low
    }

    fun artworkBlurRadius(context: Context, fullDp: Int, lowDp: Int = 18): Int =
        if (isLowRamDevice(context)) lowDp else fullDp
}

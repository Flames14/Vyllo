package com.vyllo.music.core.tv

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * Decides whether the app should render its television layout.
 *
 * Kept as a pure function so the decision is unit-testable without an
 * emulator; [isTelevision] is the thin Android wrapper.
 */
object TvDevice {

    fun shouldUseTvUi(uiModeType: Int, hasLeanbackFeature: Boolean): Boolean =
        uiModeType == Configuration.UI_MODE_TYPE_TELEVISION || hasLeanbackFeature

    fun isTelevision(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        val hasLeanback = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        return shouldUseTvUi(uiMode, hasLeanback)
    }
}

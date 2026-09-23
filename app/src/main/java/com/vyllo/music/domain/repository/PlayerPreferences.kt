package com.vyllo.music.domain.repository

import com.vyllo.music.domain.model.EqualizerSettings

/**
 * Narrow preferences surface needed by domain playback managers.
 * Implemented by the data-layer PreferenceManager so domain stops
 * importing data directly.
 */
interface PlayerPreferences {
    var isFloatingPlayerEnabled: Boolean
    var floatingPlayerX: Int
    var floatingPlayerY: Int
    var volumeBoostMultiplier: Float
    fun saveEqualizerSettings(settings: EqualizerSettings)
}

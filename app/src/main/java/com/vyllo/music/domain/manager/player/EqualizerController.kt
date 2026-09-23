package com.vyllo.music.domain.manager.player

import com.vyllo.music.PlayerUiState
import com.vyllo.music.domain.repository.PlayerPreferences
import com.vyllo.music.domain.model.EqualizerPreset
import com.vyllo.music.domain.model.EqualizerSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns all equalizer / bass / virtualizer / band / preset / reset / volume-boost logic
 * extracted from PlayerViewModel. No behavior change — verbatim logic moved here.
 */
class EqualizerController(
    private val preferenceManager: PlayerPreferences,
    private val getState: () -> PlayerUiState,
    private val updateState: (((PlayerUiState) -> PlayerUiState)) -> Unit,
    private val scopeProvider: () -> CoroutineScope
) {
    private var volumeBoosterJob: Job? = null

    fun setEqualizerEnabled(enabled: Boolean) {
        updateEqualizerSettings(getState().equalizerSettings.copy(enabled = enabled))
    }

    fun updateBassBoost(strength: Int) {
        updateEqualizerSettings(
            getState().equalizerSettings.copy(
                bassBoostStrength = strength.coerceIn(EqualizerSettings.STRENGTH_MIN, EqualizerSettings.STRENGTH_MAX)
            )
        )
    }

    fun updateVirtualizer(strength: Int) {
        updateEqualizerSettings(
            getState().equalizerSettings.copy(
                virtualizerStrength = strength.coerceIn(EqualizerSettings.STRENGTH_MIN, EqualizerSettings.STRENGTH_MAX)
            )
        )
    }

    fun updateEqualizerBand(index: Int, level: Int) {
        val current = getState().equalizerSettings
        if (index !in current.bands.indices) return

        val updatedBands = current.bands.mapIndexed { bandIndex, band ->
            if (bandIndex == index) {
                band.copy(level = level.coerceIn(EqualizerSettings.BAND_LEVEL_MIN, EqualizerSettings.BAND_LEVEL_MAX))
            } else {
                band
            }
        }
        updateEqualizerSettings(current.copy(bands = updatedBands))
    }

    fun applyEqualizerPreset(preset: EqualizerPreset) {
        val updated = preset.applyTo(getState().equalizerSettings).copy(enabled = true)
        updateEqualizerSettings(updated)
    }

    fun resetEqualizer() {
        updateEqualizerSettings(EqualizerSettings())
    }

    private fun updateEqualizerSettings(settings: EqualizerSettings) {
        val sanitized = settings.sanitized()
        updateState { it.copy(equalizerSettings = sanitized) }
        preferenceManager.saveEqualizerSettings(sanitized)
    }

    fun updateVolumeBoost(multiplier: Float) {
        val clamped = multiplier.coerceIn(1.0f, 3.0f)
        preferenceManager.volumeBoostMultiplier = clamped
        updateState { it.copy(volumeBoostMultiplier = clamped) }
    }

    fun adjustVolumeBoost(delta: Float) {
        val current = getState().volumeBoostMultiplier
        updateVolumeBoost(current + delta)
        showVolumeBoosterUI()
    }

    fun showVolumeBoosterUI() {
        updateState { it.copy(isVolumeBoosterUIVisible = true) }
        volumeBoosterJob?.cancel()
        volumeBoosterJob = scopeProvider().launch {
            delay(3000)
            updateState { it.copy(isVolumeBoosterUIVisible = false) }
        }
    }
}

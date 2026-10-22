package com.vyllo.music.domain.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists user-saved equalizer presets in SharedPreferences as JSON.
 *
 * Decoding is defensive: a malformed entry is skipped instead of throwing, so a
 * bad write can never brick the player screen.
 */
object EqualizerPresetCodec {

    fun encode(presets: List<EqualizerPreset>): String {
        val array = JSONArray()
        presets.forEach { preset ->
            array.put(
                JSONObject()
                    .put("name", preset.name)
                    .put("bass", preset.bassBoostStrength)
                    .put("virtualizer", preset.virtualizerStrength)
                    .put("bands", JSONArray(preset.bandLevels))
            )
        }
        return array.toString()
    }

    fun decode(raw: String?): List<EqualizerPreset> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val name = item.optString("name").trim()
                if (name.isEmpty()) return@mapNotNull null
                val bandsArray = item.optJSONArray("bands") ?: JSONArray()
                val bands = (0 until bandsArray.length()).map { bandsArray.optInt(it, 0) }
                EqualizerPreset(
                    name = name,
                    bassBoostStrength = item.optInt("bass", 0)
                        .coerceIn(EqualizerSettings.STRENGTH_MIN, EqualizerSettings.STRENGTH_MAX),
                    virtualizerStrength = item.optInt("virtualizer", 0)
                        .coerceIn(EqualizerSettings.STRENGTH_MIN, EqualizerSettings.STRENGTH_MAX),
                    bandLevels = EqualizerSettings.DEFAULT_BANDS.indices.map { bandIndex ->
                        (bands.getOrNull(bandIndex) ?: 0)
                            .coerceIn(EqualizerSettings.BAND_LEVEL_MIN, EqualizerSettings.BAND_LEVEL_MAX)
                    }
                )
            }.distinctBy { it.name.lowercase() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Snapshot of the current settings as a reusable preset. */
    fun fromSettings(name: String, settings: EqualizerSettings): EqualizerPreset {
        val sanitized = settings.sanitized()
        return EqualizerPreset(
            name = name.trim(),
            bassBoostStrength = sanitized.bassBoostStrength,
            virtualizerStrength = sanitized.virtualizerStrength,
            bandLevels = sanitized.bands.map { it.level }
        )
    }
}

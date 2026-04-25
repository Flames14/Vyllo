package com.vyllo.music.domain.model

data class EqualizerBandSetting(
    val label: String,
    val level: Int
)

data class EqualizerSettings(
    val enabled: Boolean = false,
    val bassBoostStrength: Int = 0,
    val virtualizerStrength: Int = 0,
    val bands: List<EqualizerBandSetting> = DEFAULT_BANDS
) {
    fun sanitized(): EqualizerSettings {
        return copy(
            bassBoostStrength = bassBoostStrength.coerceIn(STRENGTH_MIN, STRENGTH_MAX),
            virtualizerStrength = virtualizerStrength.coerceIn(STRENGTH_MIN, STRENGTH_MAX),
            bands = DEFAULT_BANDS.mapIndexed { index, defaultBand ->
                val level = bands.getOrNull(index)?.level ?: defaultBand.level
                defaultBand.copy(level = level.coerceIn(BAND_LEVEL_MIN, BAND_LEVEL_MAX))
            }
        )
    }

    companion object {
        const val BAND_LEVEL_MIN = -1000
        const val BAND_LEVEL_MAX = 1000
        const val STRENGTH_MIN = 0
        const val STRENGTH_MAX = 1000

        val DEFAULT_BANDS = listOf(
            EqualizerBandSetting(label = "Bass", level = 0),
            EqualizerBandSetting(label = "Low Mid", level = 0),
            EqualizerBandSetting(label = "Mid", level = 0),
            EqualizerBandSetting(label = "Upper Mid", level = 0),
            EqualizerBandSetting(label = "Treble", level = 0)
        )
    }
}

package com.vyllo.music.domain.model

data class EqualizerBandSetting(
    val label: String,
    val level: Int
)

data class EqualizerPreset(
    val name: String,
    val bassBoostStrength: Int,
    val virtualizerStrength: Int,
    val bandLevels: List<Int>
) {
    fun applyTo(current: EqualizerSettings): EqualizerSettings {
        val updatedBands = EqualizerSettings.DEFAULT_BANDS.mapIndexed { index, defaultBand ->
            val level = bandLevels.getOrElse(index) { 0 }
            defaultBand.copy(level = level.coerceIn(EqualizerSettings.BAND_LEVEL_MIN, EqualizerSettings.BAND_LEVEL_MAX))
        }
        return current.copy(
            bassBoostStrength = bassBoostStrength.coerceIn(EqualizerSettings.STRENGTH_MIN, EqualizerSettings.STRENGTH_MAX),
            virtualizerStrength = virtualizerStrength.coerceIn(EqualizerSettings.STRENGTH_MIN, EqualizerSettings.STRENGTH_MAX),
            bands = updatedBands
        )
    }
}

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

    fun matchesPreset(preset: EqualizerPreset): Boolean {
        if (bassBoostStrength != preset.bassBoostStrength) return false
        if (virtualizerStrength != preset.virtualizerStrength) return false
        if (bands.size != preset.bandLevels.size) return false
        return bands.map { it.level } == preset.bandLevels
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

        val PRESETS = listOf(
            EqualizerPreset("Flat", bassBoostStrength = 0, virtualizerStrength = 0, bandLevels = listOf(0, 0, 0, 0, 0)),
            EqualizerPreset("Bass Boost", bassBoostStrength = 600, virtualizerStrength = 100, bandLevels = listOf(600, 400, 100, 0, 0)),
            EqualizerPreset("Rock", bassBoostStrength = 300, virtualizerStrength = 200, bandLevels = listOf(500, 300, -100, 200, 500)),
            EqualizerPreset("Pop", bassBoostStrength = 200, virtualizerStrength = 150, bandLevels = listOf(-100, 200, 450, 200, -100)),
            EqualizerPreset("Jazz", bassBoostStrength = 200, virtualizerStrength = 300, bandLevels = listOf(350, 150, 100, 200, 350)),
            EqualizerPreset("Electronic", bassBoostStrength = 500, virtualizerStrength = 350, bandLevels = listOf(600, 400, 0, 200, 500)),
            EqualizerPreset("Vocal", bassBoostStrength = 0, virtualizerStrength = 0, bandLevels = listOf(-200, 0, 500, 400, 100)),
            EqualizerPreset("Classical", bassBoostStrength = 100, virtualizerStrength = 400, bandLevels = listOf(400, 250, 0, 250, 400))
        )
    }
}

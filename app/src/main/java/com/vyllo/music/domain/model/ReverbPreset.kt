package com.vyllo.music.domain.model

import androidx.annotation.StringRes
import com.vyllo.music.R

/**
 * Reverb programs exposed by [android.media.audiofx.PresetReverb]. Ids are the
 * platform constants, so persisting the id keeps the setting stable across runs.
 */
enum class ReverbPreset(val id: Int, @StringRes val labelRes: Int) {
    NONE(0, R.string.reverb_off),
    SMALL_ROOM(1, R.string.reverb_small_room),
    MEDIUM_ROOM(2, R.string.reverb_medium_room),
    LARGE_ROOM(3, R.string.reverb_large_room),
    MEDIUM_HALL(4, R.string.reverb_medium_hall),
    LARGE_HALL(5, R.string.reverb_large_hall),
    PLATE(6, R.string.reverb_plate);

    companion object {
        fun fromId(id: Int): ReverbPreset = entries.firstOrNull { it.id == id } ?: NONE
    }
}

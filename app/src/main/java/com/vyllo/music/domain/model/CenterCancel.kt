package com.vyllo.music.domain.model

import kotlin.math.roundToInt

/** Sample math for classic centre-channel vocal reduction. */
object CenterCancel {
    /** Returns `(left - right) / 2`, clamped to signed 16-bit PCM. */
    fun sideSample(left: Int, right: Int): Short =
        ((left - right) / 2f).roundToInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
}

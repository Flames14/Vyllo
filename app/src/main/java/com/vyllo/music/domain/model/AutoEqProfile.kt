package com.vyllo.music.domain.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A parametric EQ filter from an AutoEQ export.
 *
 * Only peaking filters are honored; AutoEQ shelf filters are exported as PK in
 * its standard "Parametric EQ" text, so a profile that mixes them stays usable.
 */
data class ParametricFilter(
    val type: String,
    val frequencyHz: Float,
    val gainDb: Float,
    val q: Float
)

/**
 * Headphone correction profile reduced to the five bands the device equalizer
 * actually exposes.
 *
 * The reduction is explicit: gains are evaluated at the band center frequencies
 * of [EqualizerSettings], so the result is an approximation of the published
 * curve, never a hidden "perfect" match.
 */
data class AutoEqProfile(
    val name: String,
    val preampDb: Float = 0f,
    val filters: List<ParametricFilter> = emptyList(),
    val curve: List<Pair<Float, Float>> = emptyList()
) {

    /** Combined response in dB at [frequencyHz], preamp included. */
    fun gainAt(frequencyHz: Float): Float {
        val correction = if (curve.isNotEmpty()) {
            interpolateCurve(frequencyHz)
        } else {
            filters.sumOf { filter ->
                if (filter.type.uppercase() == "PK") {
                    peakingGainDb(frequencyHz, filter.frequencyHz, filter.q, filter.gainDb).toDouble()
                } else {
                    0.0
                }
            }.toFloat()
        }
        return correction + preampDb
    }

    /** Five band levels in millibels, clamped to the equalizer range. */
    fun toBandLevels(): List<Int> =
        EqualizerSettings.BAND_FREQUENCIES_HZ.map { frequency ->
            (gainAt(frequency) * 100f).toInt()
                .coerceIn(EqualizerSettings.BAND_LEVEL_MIN, EqualizerSettings.BAND_LEVEL_MAX)
        }

    private fun interpolateCurve(frequencyHz: Float): Float {
        val points = curve.sortedBy { it.first }
        if (frequencyHz <= points.first().first) return points.first().second
        if (frequencyHz >= points.last().first) return points.last().second
        for (index in 0 until points.lastIndex) {
            val (lowHz, lowGain) = points[index]
            val (highHz, highGain) = points[index + 1]
            if (frequencyHz in lowHz..highHz && highHz > lowHz) {
                val fraction = (frequencyHz - lowHz) / (highHz - lowHz)
                return lowGain + (highGain - lowGain) * fraction
            }
        }
        return points.last().second
    }

    companion object {
        /**
         * Magnitude of a single RBJ peaking filter at [frequencyHz] (48 kHz
         * reference rate — the error against other rates is negligible for the
         * wide bands we collapse into).
         */
        fun peakingGainDb(frequencyHz: Float, centerHz: Float, q: Float, gainDb: Float): Float {
            if (centerHz <= 0f || frequencyHz <= 0f) return 0f
            val sampleRate = 48_000f
            val amplitude = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * centerHz / sampleRate
            val alpha = sin(w0) / (2.0 * q.coerceAtLeast(0.1f))
            val b0 = 1.0 + alpha * amplitude
            val b1 = -2.0 * cos(w0)
            val b2 = 1.0 - alpha * amplitude
            val a0 = 1.0 + alpha / amplitude
            val a1 = -2.0 * cos(w0)
            val a2 = 1.0 - alpha / amplitude

            val w = 2.0 * PI * frequencyHz / sampleRate
            val numeratorReal = b0 + b1 * cos(w) + b2 * cos(2 * w)
            val numeratorImag = -(b1 * sin(w) + b2 * sin(2 * w))
            val denominatorReal = a0 + a1 * cos(w) + a2 * cos(2 * w)
            val denominatorImag = -(a1 * sin(w) + a2 * sin(2 * w))
            val numerator = sqrt(numeratorReal * numeratorReal + numeratorImag * numeratorImag)
            val denominator = sqrt(denominatorReal * denominatorReal + denominatorImag * denominatorImag)
            if (denominator == 0.0 || numerator == 0.0) return 0f
            val ratio = numerator / denominator
            if (ratio <= 0.0 || abs(ratio - 1.0) < 1e-9) return 0f
            return (20.0 * log10(ratio)).toFloat()
        }
    }
}

/**
 * Parses the two export formats the AutoEQ project produces: its parametric
 * filter text and its CSV response table. Returns null when nothing usable is
 * found so the UI can show a real error instead of a silent flat curve.
 */
object AutoEqParser {

    private val preampRegex = Regex("""Preamp:\s*(-?\d+(?:\.\d+)?)\s*dB""", RegexOption.IGNORE_CASE)
    private val filterRegex = Regex(
        """Filter\s*\d*\s*:\s*ON\s+(\w+)\s+Fc\s+(-?\d+(?:\.\d+)?)\s*Hz\s+Gain\s+(-?\d+(?:\.\d+)?)\s*dB(?:\s+Q\s+(-?\d+(?:\.\d+)?))?""",
        RegexOption.IGNORE_CASE
    )
    private val numberRegex = Regex("""-?\d+(?:\.\d+)?""")

    fun parse(text: String, fallbackName: String = "AutoEQ"): AutoEqProfile? {
        if (text.isBlank()) return null
        val preamp = preampRegex.find(text)?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: 0f
        val filters = filterRegex.findAll(text).mapNotNull { match ->
            val values = match.groupValues
            ParametricFilter(
                type = values[1],
                frequencyHz = values[2].toFloatOrNull() ?: return@mapNotNull null,
                gainDb = values[3].toFloatOrNull() ?: return@mapNotNull null,
                q = values.getOrNull(4)?.toFloatOrNull() ?: 1f
            )
        }.toList()

        if (filters.isNotEmpty()) {
            return AutoEqProfile(name = fallbackName, preampDb = preamp, filters = filters)
        }

        val curve = parseCsvCurve(text)
        return if (curve.size >= 2) {
            AutoEqProfile(name = fallbackName, preampDb = preamp, curve = curve)
        } else {
            null
        }
    }

    private fun parseCsvCurve(text: String): List<Pair<Float, Float>> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val points = mutableListOf<Pair<Float, Float>>()
        for (line in lines) {
            val numbers = numberRegex.findAll(line.substringBefore('#')).mapNotNull { it.value.toFloatOrNull() }.toList()
            if (numbers.size < 2) continue
            val frequency = numbers.first()
            if (frequency <= 0f || frequency > 24_000f) continue
            val gain = numbers.last()
            if (abs(gain) > 60f) continue
            points.add(frequency to gain)
        }
        return points.distinctBy { it.first }.sortedBy { it.first }
    }
}

package com.vyllo.music.core.utils

import java.util.Locale

/**
 * Formats byte counts for storage labels, e.g. `1536` becomes `1.5 KB`.
 * Uses binary units (1024) to match what the caches actually allocate.
 */
object ByteSizeFormatter {

    private const val UNIT = 1024.0
    private const val KB = 1024L
    private const val MB = KB * 1024L
    private const val GB = MB * 1024L

    fun format(bytes: Long): String {
        val safe = bytes.coerceAtLeast(0L)
        return when {
            safe < KB -> "$safe B"
            safe < MB -> oneDecimal(safe / UNIT, "KB")
            safe < GB -> oneDecimal(safe / (UNIT * UNIT), "MB")
            else -> oneDecimal(safe / (UNIT * UNIT * UNIT), "GB")
        }
    }

    private fun oneDecimal(value: Double, unit: String): String =
        String.format(Locale.US, "%.1f %s", value, unit)
}

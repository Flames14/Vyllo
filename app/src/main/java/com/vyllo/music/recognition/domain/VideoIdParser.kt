package com.vyllo.music.recognition.domain

/**
 * Strictly extracts an 11-char YouTube video ID from the many URI shapes
 * Shazam hub actions come in (watch?v=, youtu.be, shorts, embed, raw ID).
 * Returns null for anything else — a garbage ID is worse than none, because
 * it makes the player resolve (and play) the wrong video while the card
 * still shows the right metadata.
 */
fun extractYouTubeVideoId(uri: String): String? {
    val trimmed = uri.trim()
    if (trimmed.isEmpty()) return null
    Regex("[?&]v=([A-Za-z0-9_-]{11})(?:[?&#/]|$)").find(trimmed)?.let { return it.groupValues[1] }
    val pathTail = trimmed.substringAfterLast('/').substringBefore('?').substringBefore('&').substringBefore('#')
    return pathTail.takeIf { it.length == 11 && it.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' } }
}

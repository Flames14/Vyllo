package com.vyllo.music.domain.model

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Line-based storage format for scrobbles that have not reached Last.fm yet.
 *
 * Fields are URL-encoded so titles/artists containing the separator can never
 * corrupt the payload. Malformed lines are dropped instead of failing the whole
 * queue — an old entry must never block new scrobbles.
 */
object LastFmPendingCodec {

    private const val FIELD_SEPARATOR = "|"
    private const val LINE_SEPARATOR = "\n"
    private const val FIELD_COUNT = 5

    fun encode(scrobbles: List<PendingScrobble>): String =
        scrobbles.joinToString(LINE_SEPARATOR) { scrobble ->
            listOf(
                scrobble.track.title,
                scrobble.track.artist,
                scrobble.track.album.orEmpty(),
                scrobble.track.durationSecs.toString(),
                scrobble.timestampSecs.toString()
            ).joinToString(FIELD_SEPARATOR) { encodeField(it) }
        }

    fun decode(raw: String?): List<PendingScrobble> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lineSequence()
            .mapNotNull(::decodeLine)
            .toList()
    }

    private fun decodeLine(line: String): PendingScrobble? {
        val fields = line.split(FIELD_SEPARATOR)
        if (fields.size != FIELD_COUNT) return null
        val title = decodeField(fields[0]).takeIf { it.isNotBlank() } ?: return null
        val artist = decodeField(fields[1]).takeIf { it.isNotBlank() } ?: return null
        val album = decodeField(fields[2]).takeIf { it.isNotBlank() }
        val duration = fields[3].toLongOrNull() ?: return null
        val timestamp = fields[4].toLongOrNull() ?: return null
        return PendingScrobble(
            track = LastFmTrack(title = title, artist = artist, album = album, durationSecs = duration),
            timestampSecs = timestamp
        )
    }

    private fun encodeField(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun decodeField(value: String): String = runCatching {
        URLDecoder.decode(value, "UTF-8")
    }.getOrDefault(value)
}

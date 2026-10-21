package com.vyllo.music.domain.model

/**
 * Reads and writes playlist files so users can move a playlist between apps.
 *
 * Two formats are supported:
 *
 * - **M3U/M3U8**: the interchange format every music player understands. We
 *   emit `#EXTM3U` / `#PLAYLIST:` / `#EXTINF:` and parse the common variations
 *   (missing `#EXTINF`, `-1` durations, bare URLs).
 * - **CSV**: `title,artist,url,duration`, RFC 4180 quoting, with the header
 *   optional on import so hand-made sheets work too.
 *
 * Pure and Android-free so both directions are unit-testable.
 */
object PlaylistFileCodec {

    enum class Format { M3U, CSV }

    data class Track(
        val title: String,
        val artist: String,
        val url: String,
        val durationSecs: Long = 0L
    )

    fun formatFor(fileName: String): Format =
        if (fileName.substringAfterLast('.', "").equals("csv", ignoreCase = true)) {
            Format.CSV
        } else {
            Format.M3U
        }

    /**
     * Picks the parser for an imported file: extension first (authoritative),
     * then the CSV header, then M3U as the fallback interchange format.
     */
    fun detectFormat(fileName: String?, text: String): Format {
        val lower = fileName?.lowercase().orEmpty()
        if (lower.endsWith(".csv")) return Format.CSV
        if (lower.endsWith(".m3u") || lower.endsWith(".m3u8")) return Format.M3U

        val firstLine = text.removePrefix("\uFEFF")
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        return if (
            firstLine.contains("title", ignoreCase = true) &&
            firstLine.contains("artist", ignoreCase = true) &&
            firstLine.contains("url", ignoreCase = true)
        ) {
            Format.CSV
        } else {
            Format.M3U
        }
    }

    fun encode(tracks: List<Track>, playlistName: String, format: Format): String =
        when (format) {
            Format.M3U -> encodeM3u(tracks, playlistName)
            Format.CSV -> encodeCsv(tracks)
        }

    fun decode(text: String, format: Format): List<Track> =
        when (format) {
            Format.M3U -> decodeM3u(text)
            Format.CSV -> decodeCsv(text)
        }

    // ------------------------------------------------------------------ M3U

    private fun encodeM3u(tracks: List<Track>, playlistName: String): String = buildString {
        val playable = tracks.filter { it.url.isNotBlank() }
        appendLine("#EXTM3U")
        if (playlistName.isNotBlank()) {
            appendLine("#PLAYLIST:${oneLine(playlistName)}")
        }
        playable.forEach { track ->
            val artist = oneLine(track.artist)
            val title = oneLine(track.title)
            val label = if (artist.isBlank()) title else "$artist - $title"
            appendLine("#EXTINF:${track.durationSecs.coerceAtLeast(0L)},$label")
            appendLine(track.url.trim())
        }
    }

    private fun decodeM3u(text: String): List<Track> {
        val tracks = mutableListOf<Track>()
        var pendingLabel: String? = null
        var pendingDuration = 0L

        text.removePrefix("\uFEFF").lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val payload = line.substringAfter(':')
                    val comma = payload.indexOf(',')
                    val durationPart = if (comma >= 0) payload.substring(0, comma) else payload
                    pendingDuration = durationPart.trim().toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                    pendingLabel = if (comma >= 0) payload.substring(comma + 1).trim() else ""
                }
                line.startsWith("#") -> Unit
                else -> {
                    val (artist, title) = splitLabel(pendingLabel)
                    tracks += Track(
                        title = title,
                        artist = artist,
                        url = line,
                        durationSecs = pendingDuration
                    )
                    pendingLabel = null
                    pendingDuration = 0L
                }
            }
        }
        return tracks
    }

    private fun splitLabel(label: String?): Pair<String, String> {
        val clean = label?.trim().orEmpty()
        if (clean.isEmpty()) return "" to ""
        val separator = clean.indexOf(" - ")
        return if (separator > 0) {
            clean.substring(0, separator).trim() to clean.substring(separator + 3).trim()
        } else {
            "" to clean
        }
    }

    private fun oneLine(value: String): String =
        value.replace('\r', ' ').replace('\n', ' ').trim()

    // ------------------------------------------------------------------ CSV

    private fun encodeCsv(tracks: List<Track>): String = buildString {
        append("title,artist,url,duration\n")
        tracks.filter { it.url.isNotBlank() }.forEach { track ->
            append(csvField(track.title)).append(',')
            append(csvField(track.artist)).append(',')
            append(csvField(track.url.trim())).append(',')
            append(track.durationSecs.coerceAtLeast(0L)).append('\n')
        }
    }

    private fun csvField(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"${value.replace("\"", "\"\"")}\"" else value
    }

    private fun decodeCsv(text: String): List<Track> {
        val rows = parseCsvRows(text.removePrefix("\uFEFF"))
        if (rows.isEmpty()) return emptyList()

        val first = rows.first()
        val hasHeader = first.size >= 3 &&
            first[0].trim().equals("title", ignoreCase = true) &&
            first[1].trim().equals("artist", ignoreCase = true)
        val dataRows = if (hasHeader) rows.drop(1) else rows

        return dataRows.mapNotNull { row ->
            val url = row.getOrNull(2)?.trim().orEmpty()
            if (url.isEmpty()) return@mapNotNull null
            Track(
                title = row.getOrNull(0)?.trim().orEmpty(),
                artist = row.getOrNull(1)?.trim().orEmpty(),
                url = url,
                durationSecs = row.getOrNull(3)?.trim()?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            )
        }
    }

    private fun parseCsvRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < text.length) {
            val char = text[index]
            when {
                inQuotes -> when {
                    char == '"' && index + 1 < text.length && text[index + 1] == '"' -> {
                        field.append('"')
                        index++
                    }
                    char == '"' -> inQuotes = false
                    else -> field.append(char)
                }
                char == '"' -> inQuotes = true
                char == ',' -> {
                    row += field.toString()
                    field.clear()
                }
                char == '\n' -> {
                    row += field.toString()
                    field.clear()
                    rows += row
                    row = mutableListOf()
                }
                char == '\r' -> Unit
                else -> field.append(char)
            }
            index++
        }

        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString()
            rows += row
        }

        return rows.filter { cells -> cells.any { it.isNotBlank() } }
    }
}

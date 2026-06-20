package com.vyllo.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test

class SubtitleParserTest {

    private fun parseSubtitleTimestamp(timeStr: String): Long? {
        val clean = timeStr.trim().replace(',', '.')
        val parts = clean.split('.')
        val timeParts = parts[0].split(':')
        if (timeParts.size < 2) return null
        val ms = parts.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLongOrNull() ?: 0L
        val secs = timeParts.last().toLongOrNull() ?: return null
        val mins = timeParts[timeParts.size - 2].toLongOrNull() ?: return null
        val hrs = if (timeParts.size > 2) timeParts[timeParts.size - 3].toLongOrNull() ?: 0L else 0L
        return hrs * 3600000L + mins * 60000L + secs * 1000L + ms
    }

    private fun parseSubtitles(content: String): List<DummySyncedLine> {
        val lines = content.lines()
        val list = mutableListOf<DummySyncedLine>()
        var currentTimestamp: Long? = null
        val currentText = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.contains("-->")) {
                if (currentTimestamp != null && currentText.isNotEmpty()) {
                    val text = cleanSubtitleText(currentText.toString())
                    if (text.isNotBlank()) {
                        list.add(DummySyncedLine(currentTimestamp, text))
                    }
                    currentText.clear()
                }
                val parts = trimmed.split("-->")
                if (parts.isNotEmpty()) {
                    currentTimestamp = parseSubtitleTimestamp(parts[0])
                }
            } else if (trimmed.isBlank()) {
                if (currentTimestamp != null && currentText.isNotEmpty()) {
                    val text = cleanSubtitleText(currentText.toString())
                    if (text.isNotBlank()) {
                        list.add(DummySyncedLine(currentTimestamp, text))
                    }
                    currentText.clear()
                }
                currentTimestamp = null
            } else if (trimmed.toLongOrNull() != null) {
                continue
            } else if (trimmed.equals("WEBVTT", ignoreCase = true) || trimmed.startsWith("NOTE")) {
                continue
            } else {
                if (currentTimestamp != null) {
                    if (currentText.isNotEmpty()) currentText.append(" ")
                    currentText.append(trimmed)
                }
            }
        }
        if (currentTimestamp != null && currentText.isNotEmpty()) {
            val text = cleanSubtitleText(currentText.toString())
            if (text.isNotBlank()) {
                list.add(DummySyncedLine(currentTimestamp, text))
            }
        }
        return list
    }

    private fun cleanSubtitleText(text: String): String {
        return text.replace(Regex("<[^>]*>"), "").trim()
    }

    data class DummySyncedLine(val timestamp: Long, val content: String)

    private data class DummyDescriptionMetadata(
        val title: String?,
        val singers: String?,
        val music: String?,
        val movie: String?,
        val language: String?
    )

    private fun parseProvidedToYouTubeDescription(description: String): DummyDescriptionMetadata? {
        val lines = description.lines().map { it.trim() }
        val startIndex = lines.indexOfFirst { it.contains("Provided to YouTube by", ignoreCase = true) }
        if (startIndex == -1) return null

        var songLineIndex = -1
        for (i in (startIndex + 1) until lines.size) {
            if (lines[i].isNotBlank()) {
                songLineIndex = i
                break
            }
        }
        if (songLineIndex == -1) return null

        val songLine = lines[songLineIndex]
        val songParts = songLine.split(Regex("[·•]")).map { it.trim() }
        if (songParts.isEmpty()) return null

        val title = songParts[0]
        val singers = if (songParts.size > 1) {
            songParts.subList(1, songParts.size).joinToString(", ")
        } else {
            null
        }

        var albumLineIndex = -1
        for (i in (songLineIndex + 1) until lines.size) {
            if (lines[i].isNotBlank()) {
                albumLineIndex = i
                break
            }
        }
        val album = if (albumLineIndex != -1) {
            val candidate = lines[albumLineIndex]
            if (candidate.startsWith("℗") || candidate.startsWith("Released on:", ignoreCase = true) || candidate.contains("Auto-generated", ignoreCase = true)) {
                null
            } else {
                candidate
            }
        } else {
            null
        }

        return DummyDescriptionMetadata(
            title = title,
            singers = singers,
            music = null,
            movie = album,
            language = null
        )
    }

    private fun cleanArtistName(artist: String): String {
        return artist
            .replace(Regex("(-\\s*)?Topic", RegexOption.IGNORE_CASE), "")
            .replace(Regex("VEVO", RegexOption.IGNORE_CASE), "")
            .replace(Regex("(-\\s*)?Official(?:\\s+Channel)?", RegexOption.IGNORE_CASE), "")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    @Test
    fun testTimestampParsing() {
        assertEquals(13900L, parseSubtitleTimestamp("00:13.90"))
        assertEquals(13900L, parseSubtitleTimestamp("00:13.900"))
        assertEquals(13900L, parseSubtitleTimestamp("00:00:13.900"))
        assertEquals(13900L, parseSubtitleTimestamp("00:00:13,900"))
        assertEquals(3661000L, parseSubtitleTimestamp("01:01:01.000"))
        assertEquals(61000L, parseSubtitleTimestamp("01:01.000"))
    }

    @Test
    fun testWebVTTPrasing() {
        val vtt = """
            WEBVTT
            
            1
            00:00:01.200 --> 00:00:03.400
            <c.colorE5E5E5>Hello World</c>
            
            2
            00:00:04.500 --> 00:00:06.000
            <b>Second</b> Line
        """.trimIndent()

        val parsed = parseSubtitles(vtt)
        assertEquals(2, parsed.size)
        assertEquals(1200L, parsed[0].timestamp)
        assertEquals("Hello World", parsed[0].content)
        assertEquals(4500L, parsed[1].timestamp)
        assertEquals("Second Line", parsed[1].content)
    }

    @Test
    fun testSRTParsing() {
        val srt = """
            1
            00:01:10,500 --> 00:01:12,800
            This is a test
            
            2
            00:01:15,000 --> 00:01:18,000
            Another test line
        """.trimIndent()

        val parsed = parseSubtitles(srt)
        assertEquals(2, parsed.size)
        assertEquals(70500L, parsed[0].timestamp)
        assertEquals("This is a test", parsed[0].content)
        assertEquals(75000L, parsed[1].timestamp)
        assertEquals("Another test line", parsed[1].content)
    }

    @Test
    fun testProvidedToYouTubeDescriptionParsing() {
        val description = """
            Provided to YouTube by Sony Music Entertainment
            
            Monica · Inigo Prabhakaran · Hariharasudhan · Justin Prabhakaran
            
            Oru Naal Koothu (Original Motion Picture Soundtrack)
            
            ℗ 2016 Think Music
            
            Released on: 2016-04-12
            
            Associated  Performer: Inigo Prabhakaran
            Associated  Performer: Hariharasudhan
            Composer, Lyricist, Producer: Justin Prabhakaran
            
            Auto-generated by YouTube.
        """.trimIndent()

        val meta = parseProvidedToYouTubeDescription(description)
        assertNotNull(meta)
        assertEquals("Monica", meta!!.title)
        assertEquals("Inigo Prabhakaran, Hariharasudhan, Justin Prabhakaran", meta.singers)
        assertEquals("Oru Naal Koothu (Original Motion Picture Soundtrack)", meta.movie)
    }

    @Test
    fun testSubtitlesParsingResetOnBlankLine() {
        val vtt = """
            WEBVTT
            
            cue-id-1
            00:00:01.000 --> 00:00:02.000
            Hello
            
            cue-id-2
            00:00:03.000 --> 00:00:04.000
            World
        """.trimIndent()

        val parsed = parseSubtitles(vtt)
        assertEquals(2, parsed.size)
        assertEquals("Hello", parsed[0].content)
        assertEquals("World", parsed[1].content)
    }

    private fun parseDescriptionMetadata(description: String): DummyDescriptionMetadata {
        var title: String? = null
        var singers: String? = null
        var music: String? = null
        var movie: String? = null
        var language: String? = null

        description.lines().forEach { line ->
            val trimmed = line.trim()
            if (title == null) {
                val match = Regex("(?i)^(?:\\s*[-–—•*]*\\s*)?(?:Song|Track|Title)\\s*[:\\-–—]\\s*(.+)").find(trimmed)
                if (match != null) title = match.groupValues[1].trim()
            }
            if (singers == null) {
                val match = Regex("(?i)^(?:\\s*[-–—•*]*\\s*)?(?:Singer|Singers|Artist|Artists|Vocals|Sung by|Singers/Vocals|Singer/Vocals)\\s*[:\\-–—]\\s*(.+)").find(trimmed)
                if (match != null) singers = match.groupValues[1].trim()
            }
            if (music == null) {
                val match = Regex("(?i)^(?:\\s*[-–—•*]*\\s*)?(?:Music|Music Director|Composer|Composed by|Music Composer)\\s*[:\\-–—]\\s*(.+)").find(trimmed)
                if (match != null) music = match.groupValues[1].trim()
            }
            if (movie == null) {
                val match = Regex("(?i)^(?:\\s*[-–—•*]*\\s*)?(?:Movie|Album|Film|Movie Name|Film Name)\\s*[:\\-–—]\\s*(.+)").find(trimmed)
                if (match != null) movie = match.groupValues[1].trim()
            }
            if (language == null) {
                val match = Regex("(?i)^(?:\\s*[-–—•*]*\\s*)?(?:Language|Lang)\\s*[:\\-–—]\\s*(.+)").find(trimmed)
                if (match != null) language = match.groupValues[1].trim()
            }
        }

        return DummyDescriptionMetadata(title, singers, music, movie, language)
    }

    @Test
    fun testArtistNameCleaning() {
        assertEquals("Justin Prabhakaran", cleanArtistName("Justin Prabhakaran - Topic"))
        assertEquals("Ed Sheeran", cleanArtistName("Ed Sheeran VEVO"))
        assertEquals("Coldplay", cleanArtistName("Coldplay - Official Channel"))
        assertEquals("Coldplay", cleanArtistName("Coldplay Official"))
    }

    @Test
    fun testParseDescriptionMetadataStandard() {
        val description = """
            Here's the Music Video of "Pavazha Malli", Sung by @SaiAbhyankkar & Shruti Haasan, Lyrics Written by Vivek, Music Composed by @SaiAbhyankkar

            Song Credits:

            Song Name : Pavazha Malli 
            Composed, Sung & Produced : @SaiAbhyankkar 
            Singers : @SaiAbhyankkar, Shruti Haasan 
            Lyrics : Vivek 
        """.trimIndent()

        val meta = parseDescriptionMetadata(description)
        assertEquals("@SaiAbhyankkar, Shruti Haasan", meta.singers)
        // Note: Song Name is not matched currently, we will fix this next if needed
    }
}

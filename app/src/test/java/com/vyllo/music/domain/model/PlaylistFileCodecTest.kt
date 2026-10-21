package com.vyllo.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistFileCodecTest {

    private val tracks = listOf(
        PlaylistFileCodec.Track("Song One", "Artist A", "https://youtu.be/aaa", 180),
        PlaylistFileCodec.Track("Song, Two", "Artist \"B\"", "https://youtu.be/bbb", 240)
    )

    @Test
    fun `m3u round-trips title artist url and duration`() {
        val encoded = PlaylistFileCodec.encode(tracks, "Road trip", PlaylistFileCodec.Format.M3U)

        assertTrue(encoded.startsWith("#EXTM3U"))
        assertTrue(encoded.contains("#PLAYLIST:Road trip"))
        assertEquals(tracks, PlaylistFileCodec.decode(encoded, PlaylistFileCodec.Format.M3U))
    }

    @Test
    fun `m3u parses bare urls and negative durations`() {
        val text = """
            #EXTM3U
            #EXTINF:-1,Some title
            https://youtu.be/one
            https://youtu.be/two
        """.trimIndent()

        val decoded = PlaylistFileCodec.decode(text, PlaylistFileCodec.Format.M3U)

        assertEquals(2, decoded.size)
        assertEquals("Some title", decoded[0].title)
        assertEquals(0L, decoded[0].durationSecs)
        assertEquals("", decoded[1].title)
        assertEquals("https://youtu.be/two", decoded[1].url)
    }

    @Test
    fun `m3u splits artist and title on the first separator`() {
        val text = """
            #EXTM3U
            #EXTINF:90,Artist - Title - Live
            https://youtu.be/live
        """.trimIndent()

        val decoded = PlaylistFileCodec.decode(text, PlaylistFileCodec.Format.M3U).single()

        assertEquals("Artist", decoded.artist)
        assertEquals("Title - Live", decoded.title)
    }

    @Test
    fun `m3u ignores comments and blank lines`() {
        val text = """
            #EXTM3U

            # a comment
            #EXTINF:10,A - B

            https://youtu.be/x
        """.trimIndent()

        assertEquals(1, PlaylistFileCodec.decode(text, PlaylistFileCodec.Format.M3U).size)
    }

    @Test
    fun `csv round-trips values containing commas quotes and newlines`() {
        val tricky = PlaylistFileCodec.Track(
            title = "Line one\nLine two",
            artist = "Comma, Artist",
            url = "https://youtu.be/x?q=1,2",
            durationSecs = 61
        )

        val encoded = PlaylistFileCodec.encode(listOf(tricky), "", PlaylistFileCodec.Format.CSV)
        val decoded = PlaylistFileCodec.decode(encoded, PlaylistFileCodec.Format.CSV).single()

        assertEquals(tricky, decoded)
    }

    @Test
    fun `csv header is optional and blank urls are skipped`() {
        val text = """
            Song A,Artist A,https://youtu.be/a,100
            Song B,Artist B,,
        """.trimIndent()

        val decoded = PlaylistFileCodec.decode(text, PlaylistFileCodec.Format.CSV)

        assertEquals(1, decoded.size)
        assertEquals("Song A", decoded[0].title)
        assertEquals(100L, decoded[0].durationSecs)
    }

    @Test
    fun `csv tolerates malformed duration and crlf line endings`() {
        val text = "title,artist,url,duration\r\nSong,Artist,https://youtu.be/z,not-a-number\r\n"

        val decoded = PlaylistFileCodec.decode(text, PlaylistFileCodec.Format.CSV).single()

        assertEquals(0L, decoded.durationSecs)
        assertEquals("https://youtu.be/z", decoded.url)
    }

    @Test
    fun `format is inferred from the file extension`() {
        assertEquals(PlaylistFileCodec.Format.CSV, PlaylistFileCodec.formatFor("mix.CSV"))
        assertEquals(PlaylistFileCodec.Format.M3U, PlaylistFileCodec.formatFor("mix.m3u"))
        assertEquals(PlaylistFileCodec.Format.M3U, PlaylistFileCodec.formatFor("mix"))
    }

    @Test
    fun `import detection prefers extension then csv header`() {
        assertEquals(
            PlaylistFileCodec.Format.CSV,
            PlaylistFileCodec.detectFormat("mix.csv", "#EXTM3U")
        )
        assertEquals(
            PlaylistFileCodec.Format.M3U,
            PlaylistFileCodec.detectFormat("mix.m3u8", "title,artist,url,duration")
        )
        assertEquals(
            PlaylistFileCodec.Format.CSV,
            PlaylistFileCodec.detectFormat("mix.txt", "title,artist,url,duration\n")
        )
        assertEquals(
            PlaylistFileCodec.Format.M3U,
            PlaylistFileCodec.detectFormat(null, "#EXTM3U\nhttps://youtu.be/a")
        )
    }
}

package com.vyllo.music.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadFileNamesTest {
    @Test
    fun `pending item is clearly marked and has a final audio name after completion`() {
        val base = DownloadFileNames.base("A Song", 1234L)

        assertEquals("A_Song-1234.m4a.download", DownloadFileNames.pending(base, "m4a"))
        assertEquals("A_Song-1234.m4a", DownloadFileNames.completed(base, "m4a"))
    }

    @Test
    fun `base name removes path and unsafe characters and limits title length`() {
        val base = DownloadFileNames.base("../" + "x".repeat(150), 99L)
        val titlePart = base.substringBeforeLast('-')

        assertTrue('/' !in base && '\\' !in base)
        assertEquals(100, titlePart.length)
        assertEquals("-99", base.takeLast(3))
    }

    @Test
    fun `blank title receives a readable fallback`() {
        assertEquals("Unknown_Song-5", DownloadFileNames.base("", 5L))
    }
}

package com.vyllo.music.core.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteSizeFormatterTest {

    @Test
    fun `formats zero and bytes below one kilobyte`() {
        assertEquals("0 B", ByteSizeFormatter.format(0))
        assertEquals("512 B", ByteSizeFormatter.format(512))
        assertEquals("1023 B", ByteSizeFormatter.format(1023))
    }

    @Test
    fun `formats kilobytes`() {
        assertEquals("1.0 KB", ByteSizeFormatter.format(1024))
        assertEquals("1.5 KB", ByteSizeFormatter.format(1536))
    }

    @Test
    fun `formats megabytes`() {
        assertEquals("1.0 MB", ByteSizeFormatter.format(1024L * 1024L))
        assertEquals("300.0 MB", ByteSizeFormatter.format(300L * 1024L * 1024L))
    }

    @Test
    fun `formats gigabytes`() {
        assertEquals("1.5 GB", ByteSizeFormatter.format(1536L * 1024L * 1024L))
    }

    @Test
    fun `negative input is clamped to zero`() {
        assertEquals("0 B", ByteSizeFormatter.format(-42))
    }
}

package com.vyllo.music.core.security

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for InputSanitizer.
 * Verifies input validation and injection prevention.
 */
class InputSanitizerTest {

    @Test
    fun `sanitizeSearchQuery removes dangerous characters`() {
        val input = "test<script>alert('xss')</script>"
        val result = InputSanitizer.sanitizeSearchQuery(input)
        assertFalse(result.contains("<"))
        assertFalse(result.contains(">"))
        assertFalse(result.contains("'"))
    }

    @Test
    fun `sanitizeSearchQuery truncates to max length`() {
        val input = "a".repeat(150)
        val result = InputSanitizer.sanitizeSearchQuery(input)
        assertEquals(100, result.length)
    }

    @Test
    fun `sanitizeSearchQuery returns empty for blank input`() {
        assertTrue(InputSanitizer.sanitizeSearchQuery("").isEmpty())
        assertTrue(InputSanitizer.sanitizeSearchQuery("   ").isEmpty())
    }

    @Test
    fun `sanitizeSearchQuery normalizes whitespace`() {
        val input = "hello    world   test"
        val result = InputSanitizer.sanitizeSearchQuery(input)
        assertEquals("hello world test", result)
    }

    @Test
    fun `sanitizePlaylistName returns default for blank input`() {
        assertEquals("Untitled Playlist", InputSanitizer.sanitizePlaylistName(""))
        assertEquals("Untitled Playlist", InputSanitizer.sanitizePlaylistName("   "))
    }

    @Test
    fun `sanitizePlaylistName truncates to max length`() {
        val input = "a".repeat(60)
        val result = InputSanitizer.sanitizePlaylistName(input)
        assertEquals(50, result.length)
    }

    @Test
    fun `isValidUrl rejects invalid URLs`() {
        assertFalse(InputSanitizer.isValidUrl(null))
        assertFalse(InputSanitizer.isValidUrl(""))
        assertFalse(InputSanitizer.isValidUrl("not-a-url"))
        assertFalse(InputSanitizer.isValidUrl("http://example.com"))
    }

    @Test
    fun `isValidUrl accepts valid HTTPS URLs`() {
        assertTrue(InputSanitizer.isValidUrl("https://example.com"))
        assertTrue(InputSanitizer.isValidUrl("https://example.com/path?q=1"))
    }

    @Test
    fun `isValidUrl rejects URLs with path traversal`() {
        assertFalse(InputSanitizer.isValidUrl("https://example.com/../etc/passwd"))
        assertFalse(InputSanitizer.isValidUrl("https://example.com/../../secret"))
    }

    @Test
    fun `isValidUrl rejects overly long URLs`() {
        val longUrl = "https://example.com/${"a".repeat(2100)}"
        assertFalse(InputSanitizer.isValidUrl(longUrl))
    }

    @Test
    fun `isPotentialSqlInjection detects common SQL patterns`() {
        assertTrue(InputSanitizer.isPotentialSqlInjection("1 OR 1=1; DROP TABLE users"))
        assertTrue(InputSanitizer.isPotentialSqlInjection("UNION SELECT * FROM passwords"))
        assertTrue(InputSanitizer.isPotentialSqlInjection("'; DELETE FROM users;--"))
    }

    @Test
    fun `isPotentialSqlInjection allows normal text`() {
        assertFalse(InputSanitizer.isPotentialSqlInjection("chill music"))
        assertFalse(InputSanitizer.isPotentialSqlInjection("Ed Sheeran"))
    }

    @Test
    fun `validateInput returns error for blank input`() {
        val result = InputSanitizer.validateInput("", InputSanitizer.InputType.SEARCH)
        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun `validateInput rejects overly long search queries`() {
        val result = InputSanitizer.validateInput("a".repeat(101), InputSanitizer.InputType.SEARCH)
        assertFalse(result.isValid)
    }

    @Test
    fun `validateInput accepts valid search query`() {
        val result = InputSanitizer.validateInput("test song", InputSanitizer.InputType.SEARCH)
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    @Test
    fun `validateInput accepts valid playlist name`() {
        val result = InputSanitizer.validateInput("My Playlist", InputSanitizer.InputType.PLAYLIST_NAME)
        assertTrue(result.isValid)
    }

    @Test
    fun `validateInput rejects overly long playlist name`() {
        val result = InputSanitizer.validateInput("a".repeat(51), InputSanitizer.InputType.PLAYLIST_NAME)
        assertFalse(result.isValid)
    }
}

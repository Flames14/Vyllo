package com.vyllo.music.presentation.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ThemeManager.
 * Tests theme color scheme generation and dark theme detection.
 */
class ThemeManagerTest {

    @Test
    fun testGetColorScheme_Ocean() {
        val colorScheme = ThemeManager.getColorScheme("Ocean", false)
        assertNotNull(colorScheme)
        assertNotNull(colorScheme.primary)
    }

    @Test
    fun testGetColorScheme_Forest() {
        val colorScheme = ThemeManager.getColorScheme("Forest", false)
        assertNotNull(colorScheme)
        assertNotNull(colorScheme.primary)
    }

    @Test
    fun testGetColorScheme_Sunset() {
        val colorScheme = ThemeManager.getColorScheme("Sunset", false)
        assertNotNull(colorScheme)
        assertNotNull(colorScheme.primary)
    }

    @Test
    fun testGetColorScheme_AMOLED() {
        val colorScheme = ThemeManager.getColorScheme("AMOLED", false)
        assertNotNull(colorScheme)
        assertEquals(Color.Black, colorScheme.background)
    }

    @Test
    fun testGetColorScheme_MidnightGold() {
        val colorScheme = ThemeManager.getColorScheme("Midnight Gold", false)
        assertNotNull(colorScheme)
        assertNotNull(colorScheme.primary)
    }

    @Test
    fun testGetColorScheme_Cyberpunk() {
        val colorScheme = ThemeManager.getColorScheme("Cyberpunk", false)
        assertNotNull(colorScheme)
        assertNotNull(colorScheme.primary)
    }

    @Test
    fun testGetColorScheme_Lavender() {
        val colorScheme = ThemeManager.getColorScheme("Lavender", false)
        assertNotNull(colorScheme)
        assertNotNull(colorScheme.primary)
    }

    @Test
    fun testGetColorScheme_Crimson() {
        val colorScheme = ThemeManager.getColorScheme("Crimson", false)
        assertNotNull(colorScheme)
        assertNotNull(colorScheme.primary)
    }

    @Test
    fun testGetColorScheme_Light() {
        val colorScheme = ThemeManager.getColorScheme("Light", false)
        assertNotNull(colorScheme)
        assertEquals(Color.White, colorScheme.background)
    }

    @Test
    fun testGetColorScheme_System_Dark() {
        val colorScheme = ThemeManager.getColorScheme("System", true)
        assertNotNull(colorScheme)
        assertTrue(colorScheme.background.red < 0.2f)
    }

    @Test
    fun testGetColorScheme_System_Light() {
        val colorScheme = ThemeManager.getColorScheme("System", false)
        assertNotNull(colorScheme)
        assertEquals(Color.White, colorScheme.background)
    }

    @Test
    fun testGetColorScheme_Unknown_DefaultsToDark() {
        val colorScheme = ThemeManager.getColorScheme("Unknown", true)
        assertNotNull(colorScheme)
        assertTrue(colorScheme.background.red < 0.2f)
    }

    @Test
    fun testIsDarkTheme_Light() {
        assertFalse(ThemeManager.isDarkTheme("Light", false))
    }

    @Test
    fun testIsDarkTheme_Dark() {
        assertTrue(ThemeManager.isDarkTheme("Dark", false))
    }

    @Test
    fun testIsDarkTheme_System_Dark() {
        assertTrue(ThemeManager.isDarkTheme("System", true))
    }

    @Test
    fun testIsDarkTheme_System_Light() {
        assertFalse(ThemeManager.isDarkTheme("System", false))
    }

    @Test
    fun testIsDarkTheme_Ocean() {
        assertTrue(ThemeManager.isDarkTheme("Ocean", false))
    }

    @Test
    fun testPremiumAccent() {
        val colorScheme = ThemeManager.getColorScheme("Ocean", false)
        val accent = ThemeManager.premiumAccent(colorScheme)
        assertEquals(colorScheme.primary, accent)
    }

    @Test
    fun testGlassWhite() {
        val colorScheme = ThemeManager.getColorScheme("Dark", false)
        val glassWhite = ThemeManager.glassWhite(colorScheme)
        assertNotNull(glassWhite)
        assertTrue(glassWhite.alpha < 1.0f)
    }

    @Test
    fun testGlassBorder() {
        val colorScheme = ThemeManager.getColorScheme("Dark", false)
        val glassBorder = ThemeManager.glassBorder(colorScheme)
        assertNotNull(glassBorder)
        assertTrue(glassBorder.alpha < 1.0f)
    }
}

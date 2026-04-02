package com.vyllo.music.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Manages application theme color schemes.
 * Extracted from MainActivity to reduce complexity and improve maintainability.
 * 
 * Supported themes:
 * - System (follows device theme)
 * - Light
 * - Dark (default)
 * - Ocean (cyan accent)
 * - Forest (green accent)
 * - Sunset (orange accent)
 * - AMOLED (pure black)
 * - Midnight Gold (gold accent)
 * - Cyberpunk (neon cyan)
 * - Lavender (purple accent)
 * - Crimson (red accent)
 */
object ThemeManager {

    /**
     * Get the ColorScheme for the given theme mode.
     * @param themeMode The theme mode name (e.g., "Dark", "Ocean", "Light", "System")
     * @param isSystemDark Whether the system is in dark mode
     * @return The appropriate ColorScheme
     */
    fun getColorScheme(themeMode: String, isSystemDark: Boolean): ColorScheme {
        return when (themeMode) {
            "Ocean" -> oceanDarkScheme()
            "Forest" -> forestDarkScheme()
            "Sunset" -> sunsetDarkScheme()
            "AMOLED" -> amoledDarkScheme()
            "Midnight Gold" -> midnightGoldScheme()
            "Cyberpunk" -> cyberpunkScheme()
            "Lavender" -> lavenderScheme()
            "Crimson" -> crimsonScheme()
            "Light" -> lightScheme()
            "System" -> if (isSystemDark) defaultDarkScheme() else lightScheme()
            else -> if (isSystemDark) defaultDarkScheme() else lightScheme()
        }
    }

    /**
     * Check if the theme mode is a dark theme.
     * Used for status bar color configuration.
     */
    fun isDarkTheme(themeMode: String, isSystemDark: Boolean): Boolean {
        return when (themeMode) {
            "Light" -> false
            "System" -> isSystemDark
            else -> true
        }
    }

    // ==================== Dark Themes ====================

    /**
     * Default dark theme with YT Music Red accent.
     */
    private fun defaultDarkScheme(): ColorScheme = darkColorScheme(
        primary = Color(0xFFE91E63),
        onPrimary = Color.White,
        background = Color(0xFF0F0F0F),
        onBackground = Color.White,
        surface = Color(0xFF161616),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF212121),
        onSurfaceVariant = Color.White.copy(alpha = 0.7f),
        secondaryContainer = Color(0xFF2B2B2B),
        onSecondaryContainer = Color.White
    )

    /**
     * Ocean theme - Cyan/Teal accent colors.
     */
    private fun oceanDarkScheme(): ColorScheme = darkColorScheme(
        primary = Color(0xFF00BCD4),
        onPrimary = Color.White,
        background = Color(0xFF001F24),
        onBackground = Color.White,
        surface = Color(0xFF003840),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF005662),
        onSurfaceVariant = Color.White.copy(alpha = 0.7f),
        secondaryContainer = Color(0xFF008291),
        onSecondaryContainer = Color.White
    )

    /**
     * Forest theme - Green accent colors.
     */
    private fun forestDarkScheme(): ColorScheme = darkColorScheme(
        primary = Color(0xFF4CAF50),
        onPrimary = Color.White,
        background = Color(0xFF0F1B0F),
        onBackground = Color.White,
        surface = Color(0xFF1E381E),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF2E552E),
        onSurfaceVariant = Color.White.copy(alpha = 0.7f),
        secondaryContainer = Color(0xFF3E723E),
        onSecondaryContainer = Color.White
    )

    /**
     * Sunset theme - Orange/Red accent colors.
     */
    private fun sunsetDarkScheme(): ColorScheme = darkColorScheme(
        primary = Color(0xFFFF5722),
        onPrimary = Color.White,
        background = Color(0xFF2A0F05),
        onBackground = Color.White,
        surface = Color(0xFF3C1509),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF5B1F0E),
        onSurfaceVariant = Color.White.copy(alpha = 0.7f),
        secondaryContainer = Color(0xFF7A2913),
        onSecondaryContainer = Color.White
    )

    /**
     * AMOLED theme - Pure black background for OLED screens.
     */
    private fun amoledDarkScheme(): ColorScheme = darkColorScheme(
        primary = Color(0xFFE91E63),
        onPrimary = Color.White,
        background = Color.Black,
        onBackground = Color.White,
        surface = Color(0xFF111111),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF222222),
        onSurfaceVariant = Color.White.copy(alpha = 0.7f),
        secondaryContainer = Color(0xFF333333),
        onSecondaryContainer = Color.White
    )

    /**
     * Midnight Gold theme - Gold accent on dark background.
     */
    private fun midnightGoldScheme(): ColorScheme = darkColorScheme(
        primary = Color(0xFFFFC107),
        onPrimary = Color.Black,
        background = Color(0xFF121212),
        onBackground = Color.White,
        surface = Color(0xFF1E1E1E),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF2C2C2C),
        onSurfaceVariant = Color.White.copy(alpha = 0.7f),
        secondaryContainer = Color(0xFF3A3A3A),
        onSecondaryContainer = Color.White
    )

    /**
     * Cyberpunk theme - Neon cyan on dark purple.
     */
    private fun cyberpunkScheme(): ColorScheme = darkColorScheme(
        primary = Color(0xFF00FFCC),
        onPrimary = Color.Black,
        background = Color(0xFF0D0221),
        onBackground = Color.White,
        surface = Color(0xFF1A0A38),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF2E1B59),
        onSurfaceVariant = Color.White.copy(alpha = 0.7f),
        secondaryContainer = Color(0xFF3D2173),
        onSecondaryContainer = Color.White
    )

    /**
     * Lavender theme - Purple accent colors.
     */
    private fun lavenderScheme(): ColorScheme = darkColorScheme(
        primary = Color(0xFFB39DDB),
        onPrimary = Color.Black,
        background = Color(0xFF1A1A24),
        onBackground = Color.White,
        surface = Color(0xFF232331),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF323246),
        onSurfaceVariant = Color.White.copy(alpha = 0.7f),
        secondaryContainer = Color(0xFF43435C),
        onSecondaryContainer = Color.White
    )

    /**
     * Crimson theme - Red accent colors.
     */
    private fun crimsonScheme(): ColorScheme = darkColorScheme(
        primary = Color(0xFFFF3333),
        onPrimary = Color.White,
        background = Color(0xFF1A0000),
        onBackground = Color.White,
        surface = Color(0xFF2A0000),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF400000),
        onSurfaceVariant = Color.White.copy(alpha = 0.7f),
        secondaryContainer = Color(0xFF5A0000),
        onSecondaryContainer = Color.White
    )

    // ==================== Light Theme ====================

    /**
     * Light theme with YT Music Red accent.
     */
    private fun lightScheme(): ColorScheme = lightColorScheme(
        primary = Color(0xFFE91E63),
        onPrimary = Color.White,
        background = Color.White,
        onBackground = Color.Black,
        surface = Color(0xFFF5F5F5),
        onSurface = Color.Black,
        surfaceVariant = Color(0xFFEEEEEE),
        onSurfaceVariant = Color.Black.copy(alpha = 0.7f)
    )

    // ==================== Design System Tokens ====================

    /**
     * Get the premium accent color from the current theme.
     * Use this for primary actions and highlights.
     */
    fun premiumAccent(colorScheme: ColorScheme): Color = colorScheme.primary

    /**
     * Get the glass white color for glassmorphism effects.
     * Use for semi-transparent overlays.
     */
    fun glassWhite(colorScheme: ColorScheme): Color = colorScheme.onBackground.copy(alpha = 0.08f)

    /**
     * Get the glass border color for glassmorphism effects.
     * Use for subtle borders on glass surfaces.
     */
    fun glassBorder(colorScheme: ColorScheme): Color = colorScheme.onBackground.copy(alpha = 0.15f)
}

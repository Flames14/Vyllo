package com.vyllo.music.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

object ThemeManager {
    fun getColorScheme(themeMode: String, isDark: Boolean): ColorScheme {
        return when (themeMode) {
            "Ocean" -> darkColorScheme(
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
            "Forest" -> darkColorScheme(
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
            "Sunset" -> darkColorScheme(
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
            "AMOLED" -> darkColorScheme(
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
            "Midnight Gold" -> darkColorScheme(
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
            "Cyberpunk" -> darkColorScheme(
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
            "Lavender" -> darkColorScheme(
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
            "Crimson" -> darkColorScheme(
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
            else -> if (isDark) {
                darkColorScheme(
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
            } else {
                lightColorScheme(
                    primary = Color(0xFFE91E63),
                    onPrimary = Color.White,
                    background = Color.White,
                    onBackground = Color.Black,
                    surface = Color(0xFFF5F5F5),
                    onSurface = Color.Black,
                    surfaceVariant = Color(0xFFEEEEEE),
                    onSurfaceVariant = Color.Black.copy(alpha = 0.7f)
                )
            }
        }
    }

    fun isDarkTheme(themeMode: String, isSystemDark: Boolean): Boolean {
        return when (themeMode) {
            "Light" -> false
            "Dark", "Ocean", "Forest", "Sunset", "AMOLED", "Midnight Gold", "Cyberpunk", "Lavender", "Crimson" -> true
            "System" -> isSystemDark
            else -> true
        }
    }

    fun premiumAccent(colorScheme: ColorScheme): Color = colorScheme.primary
    fun glassWhite(colorScheme: ColorScheme): Color = colorScheme.onBackground.copy(alpha = 0.08f)
    fun glassBorder(colorScheme: ColorScheme): Color = colorScheme.onBackground.copy(alpha = 0.15f)
}

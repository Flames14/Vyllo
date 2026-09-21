package com.vyllo.music.presentation.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Vyllo design system.
 *
 * A single source of truth for spacing, radii, motion and typography so that every
 * screen and component shares the exact same visual rhythm. Previously these values
 * were hard-coded per file which caused drifting paddings, radii and font sizes.
 *
 * Nothing here changes behaviour — these are purely presentation tokens.
 */
object VylloSpacing {
    /** 2dp – hairline separations between tightly coupled elements. */
    val xxs: Dp = 2.dp

    /** 4dp – icon to label gaps. */
    val xs: Dp = 4.dp

    /** 8dp – related elements inside the same group. */
    val sm: Dp = 8.dp

    /** 12dp – default gap between list items of the same kind. */
    val md: Dp = 12.dp

    /** 16dp – the standard horizontal screen margin. */
    val lg: Dp = 16.dp

    /** 20dp – breathing room around large media. */
    val xl: Dp = 20.dp

    /** 24dp – gap between distinct content sections. */
    val xxl: Dp = 24.dp

    /** 32dp – gap around empty/error state blocks. */
    val xxxl: Dp = 32.dp

    /** Screen edge margin used by every list and header in the app. */
    val screenHorizontal: Dp = 16.dp

    /** Vertical margin above a new content section. */
    val sectionTop: Dp = 24.dp

    /** Vertical padding applied around a section header row. */
    val sectionHeaderVertical: Dp = 10.dp

    /** Bottom padding so the last list item clears the mini player + nav bar. */
    val listBottom: Dp = 16.dp
}

/**
 * Corner radii. Kept deliberately tight — premium music apps use restrained radii
 * rather than "everything is a pill".
 */
object VylloRadius {
    /** 4dp – progress tracks, tiny chips. */
    val xs: Dp = 4.dp

    /** 8dp – artwork thumbnails, chips, row highlight backgrounds. */
    val sm: Dp = 8.dp

    /** 12dp – inputs, dialogs, suggestion rows. */
    val md: Dp = 12.dp

    /** 16dp – cards, sheets, feature surfaces. */
    val lg: Dp = 16.dp

    /** 20dp – large hero surfaces. */
    val xl: Dp = 20.dp

    /** 28dp – grouped containers (settings blocks). */
    val xxl: Dp = 28.dp

    /** Fully rounded – pills, search bars, avatars. */
    val pill: Dp = 100.dp
}

/**
 * Interaction sizing.
 */
object VylloSize {
    /** Minimum comfortable touch target. Material and Apple both recommend >= 48dp. */
    val minTouchTarget: Dp = 48.dp

    /** Standard toolbars / search field height. */
    val toolbar: Dp = 48.dp

    /** Icon sizes. */
    val iconSmall: Dp = 18.dp
    val iconMedium: Dp = 20.dp
    val iconLarge: Dp = 24.dp
    val iconXLarge: Dp = 28.dp

    /** Artwork sizes used across list rows and cards. */
    val artworkRow: Dp = 52.dp
    val artworkCompact: Dp = 48.dp
    val artworkMiniPlayer: Dp = 48.dp
    val artworkCard: Dp = 140.dp

    /** Bottom navigation bar height (excludes system insets). */
    val bottomBar: Dp = 60.dp

    /** Collapsed mini player height. */
    val miniPlayer: Dp = 64.dp

    /** Progress track thickness. */
    val progressTrack: Dp = 2.dp

    /** Hairline divider thickness. */
    val hairline: Dp = 0.5.dp
}

/**
 * Motion tokens.
 *
 * The goal is "the interface feels alive, but the user barely notices why":
 * fast enough to never delay interaction, slow enough to read as intentional.
 */
object VylloMotion {
    /** 90ms – pressed / released colour changes. */
    const val instant: Int = 90

    /** 150ms – enter of small elements (chip selection, icon swaps). */
    const val fast: Int = 150

    /** 220ms – standard transition for content swaps and fades. */
    const val medium: Int = 220

    /** 320ms – screen level transitions. */
    const val slow: Int = 320

    /** Shimmer sweep duration for skeleton placeholders. */
    const val shimmer: Int = 1200

    /** Standard decelerate curve for entering content. */
    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Emphasised curve for larger surfaces (sheets, players). */
    val emphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Curve for exiting content. */
    val exit: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)
}

/**
 * Shape scale handed to [androidx.compose.material3.MaterialTheme].
 * Keeps stock Material components (dialogs, sheets, buttons, cards) visually aligned
 * with the custom surfaces used throughout the app.
 */
val VylloShapes = Shapes(
    extraSmall = RoundedCornerShape(VylloRadius.xs),
    small = RoundedCornerShape(VylloRadius.sm),
    medium = RoundedCornerShape(VylloRadius.md),
    large = RoundedCornerShape(VylloRadius.lg),
    extraLarge = RoundedCornerShape(VylloRadius.xxl)
)

/**
 * Typography scale.
 *
 * Deliberately close in size to the Material 3 defaults (so existing layouts never
 * overflow) but with a calmer, more deliberate hierarchy: tighter headline weights,
 * controlled line heights and subtle letter spacing. This replaces the previous
 * per-screen `copy(fontWeight = ...)` improvisation.
 */
val VylloTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 54.sp,
        letterSpacing = (-1).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.8).sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp
    ),

    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp
    ),

    /** Section titles. */
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),

    // Reading text.
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    ),

    // Metadata, chips, navigation labels.
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.15.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    )
)
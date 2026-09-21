package com.vyllo.music.presentation.components.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vyllo.music.presentation.components.ytmClickable
import com.vyllo.music.presentation.theme.VylloMotion
import com.vyllo.music.presentation.theme.VylloRadius
import com.vyllo.music.presentation.theme.VylloSpacing
import com.vyllo.music.presentation.theme.VylloSize

@Composable
fun YTMFilterChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isDark = com.vyllo.music.presentation.theme.ThemeManager.isDarkColor(MaterialTheme.colorScheme.background)
    val targetBg = when {
        isSelected -> if (isDark) Color.White else Color(0xFF0F0F0F)
        isDark -> Color.White.copy(alpha = 0.10f)
        else -> Color(0xFF000000).copy(alpha = 0.06f)
    }
    val targetText = when {
        isSelected -> if (isDark) Color(0xFF0F0F0F) else Color.White
        isDark -> Color.White.copy(alpha = 0.9f)
        else -> Color(0xFF0F0F0F)
    }

    // Selection animates instead of snapping — the chip reads as a single
    // continuous surface rather than a flashed rectangle.
    val bgColor by animateColorAsState(
        targetValue = targetBg,
        animationSpec = tween(VylloMotion.fast, easing = VylloMotion.standard),
        label = "chip_bg"
    )
    val textColor by animateColorAsState(
        targetValue = targetText,
        animationSpec = tween(VylloMotion.fast, easing = VylloMotion.standard),
        label = "chip_text"
    )
    val shape = RoundedCornerShape(VylloRadius.sm)

    // The visual chip stays compact (36dp) while the tappable surface is a full
    // 48dp tall, so the chip row is comfortable to hit without looking bulky.
    Box(
        modifier = Modifier
            .heightIn(min = VylloSize.minTouchTarget)
            .clip(shape)
            .ytmClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .height(36.dp)
                .clip(shape)
                .background(bgColor)
                .padding(horizontal = VylloSpacing.md),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    letterSpacing = 0.1.sp
                ),
                color = textColor
            )
        }
    }
}

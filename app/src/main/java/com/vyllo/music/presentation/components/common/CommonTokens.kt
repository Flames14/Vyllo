package com.vyllo.music.presentation.components.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun PremiumAccent() = MaterialTheme.colorScheme.primary

@Composable
fun GlassWhite() = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)

@Composable
fun GlassBorder() = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)

package com.vyllo.music.presentation.components.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun premiumAccent() = MaterialTheme.colorScheme.primary

@Composable
fun glassWhite() = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)

@Composable
fun glassBorder() = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)

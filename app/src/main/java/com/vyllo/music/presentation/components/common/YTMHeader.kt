package com.vyllo.music.presentation.components.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vyllo.music.R
import com.vyllo.music.presentation.components.ytmClickable
import com.vyllo.music.presentation.theme.VylloSize
import com.vyllo.music.presentation.theme.VylloSpacing

@Composable
fun YTMHeader(
    onSearchClick: (() -> Unit)? = null,
    onSettingsClick: () -> Unit,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onRecognizeClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                start = VylloSpacing.screenHorizontal,
                end = VylloSpacing.sm,
                top = VylloSpacing.sm,
                bottom = VylloSpacing.md
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = com.vyllo.music.R.drawable.app_icon),
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(VylloSpacing.sm))
            Text(
                text = "Music",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VylloSpacing.xxs)
        ) {
            // Passive feedback while a manual refresh is in flight so the header never
            // looks frozen.
            AnimatedVisibility(visible = isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = VylloSpacing.sm)
                        .size(VylloSize.iconMedium),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (onSearchClick != null) {
                HeaderIconButton(
                    icon = Icons.Rounded.Search,
                    contentDescription = androidx.compose.ui.res.stringResource(com.vyllo.music.R.string.accessibility_search),
                    onClick = onSearchClick
                )
            }
            HeaderIconButton(
                icon = Icons.Rounded.GraphicEq,
                contentDescription = androidx.compose.ui.res.stringResource(com.vyllo.music.R.string.accessibility_recognize),
                onClick = onRecognizeClick
            )
            HeaderIconButton(
                icon = Icons.Rounded.Settings,
                contentDescription = androidx.compose.ui.res.stringResource(com.vyllo.music.R.string.accessibility_settings),
                onClick = onSettingsClick
            )
        }
    }
}

/**
 * Header action button: a compact circular surface inside a full 48dp touch target.
 * Keeping the visual at 40dp while the hit area stays 48dp is what makes the header
 * feel precise rather than clumsy.
 */
@Composable
fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(VylloSize.minTouchTarget)
            .clip(CircleShape)
            .ytmClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(VylloSize.iconMedium)
            )
        }
    }
}

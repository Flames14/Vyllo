package com.vyllo.music.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.vyllo.music.R
import com.vyllo.music.core.network.rememberIsOnline
import com.vyllo.music.presentation.theme.VylloSpacing

/**
 * Slim strip shown while the device has no validated internet path.
 *
 * It never blocks content: local downloads and the cached feed stay usable, so the
 * banner is informational rather than a modal. Sits above the bottom bar so it is
 * visible on every tab without covering the player.
 */
@Composable
fun VylloOfflineBanner(
    modifier: Modifier = Modifier
) {
    VylloOfflineBanner(isOnline = rememberIsOnline(), modifier = modifier)
}

/**
 * @param isOnline connectivity to render. The no-arg overload derives this from
 * [rememberIsOnline]; passing it explicitly is the test seam so instrumented
 * tests can drive the offline state without tearing down the device network.
 */
@Composable
fun VylloOfflineBanner(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !isOnline,
        modifier = modifier,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VylloSpacing.md, vertical = VylloSpacing.xs)
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = VylloSpacing.md,
                    vertical = VylloSpacing.sm
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VylloSpacing.md)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(VylloSpacing.xl)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.state_offline_banner),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Start
                    )
                    Text(
                        text = stringResource(R.string.state_offline_detail),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}

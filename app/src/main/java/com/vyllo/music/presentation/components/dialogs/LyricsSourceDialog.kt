package com.vyllo.music.presentation.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vyllo.music.R
import com.vyllo.music.domain.model.LyricsSource
import com.vyllo.music.presentation.theme.VylloRadius
import com.vyllo.music.presentation.theme.VylloSize
import com.vyllo.music.presentation.theme.VylloSpacing

/**
 * Reorders the lyrics provider chain. No drag-and-drop on purpose: the list is
 * short, arrows work with TalkBack, and every move is persisted immediately so
 * dismissing the dialog never loses an edit.
 */
@Composable
fun LyricsSourceDialog(
    order: List<LyricsSource>,
    onMove: (LyricsSource, Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_lyrics_sources)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.settings_lyrics_sources_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(VylloSpacing.sm))
                order.forEachIndexed { index, source ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(VylloRadius.sm)
                            )
                            .padding(start = VylloSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = lyricsSourceLabel(source),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onMove(source, -1) },
                            enabled = index > 0,
                            modifier = Modifier.size(VylloSize.minTouchTarget)
                        ) {
                            Icon(
                                Icons.Rounded.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.lyrics_source_move_up),
                                modifier = Modifier.size(VylloSize.iconMedium)
                            )
                        }
                        IconButton(
                            onClick = { onMove(source, 1) },
                            enabled = index < order.lastIndex,
                            modifier = Modifier.size(VylloSize.minTouchTarget)
                        ) {
                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.lyrics_source_move_down),
                                modifier = Modifier.size(VylloSize.iconMedium)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_done))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        titleContentColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun lyricsSourceLabel(source: LyricsSource): String = when (source) {
    LyricsSource.PAXSENIX -> stringResource(R.string.lyrics_source_paxsenix)
    LyricsSource.BETTER_LYRICS -> stringResource(R.string.lyrics_source_better_lyrics)
    LyricsSource.LRCLIB_EXACT -> stringResource(R.string.lyrics_source_lrclib_exact)
    LyricsSource.LRCLIB_SEARCH -> stringResource(R.string.lyrics_source_lrclib_search)
    LyricsSource.LYRICS_PLUS -> stringResource(R.string.lyrics_source_lyrics_plus)
    LyricsSource.KUGOU -> stringResource(R.string.lyrics_source_kugou)
    LyricsSource.NETEASE -> stringResource(R.string.lyrics_source_netease)
    LyricsSource.LYRICS_OVH -> stringResource(R.string.lyrics_source_lyrics_ovh)
    LyricsSource.LRCLIB_TITLE_ONLY -> stringResource(R.string.lyrics_source_lrclib_title_only)
}

package com.vyllo.music.presentation.components.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vyllo.music.R
import com.vyllo.music.domain.model.LyricsStyle
import com.vyllo.music.presentation.theme.VylloSpacing

/**
 * Live lyrics typography controls. Every change is applied to the style flow
 * immediately so the sheet doubles as the preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsStyleSheet(
    show: Boolean,
    style: LyricsStyle,
    onChange: (LyricsStyle) -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = VylloSpacing.lg)
                .padding(bottom = VylloSpacing.lg)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.lyrics_style_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                TextButton(onClick = { onChange(LyricsStyle()) }) {
                    Text(stringResource(R.string.lyrics_style_reset))
                }
            }

            LabeledSlider(
                title = stringResource(R.string.lyrics_style_size),
                valueText = "${LyricsStyle.percentOf(style.scale)}%",
                value = style.scale,
                range = LyricsStyle.MIN_SCALE..LyricsStyle.MAX_SCALE,
                onValueChange = { onChange(style.copy(scale = it)) }
            )
            LabeledSlider(
                title = stringResource(R.string.lyrics_style_spacing),
                valueText = "${LyricsStyle.percentOf(style.lineSpacing)}%",
                value = style.lineSpacing,
                range = LyricsStyle.MIN_SPACING..LyricsStyle.MAX_SPACING,
                onValueChange = { onChange(style.copy(lineSpacing = it)) }
            )

            Text(
                text = stringResource(R.string.lyrics_style_alignment),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = VylloSpacing.sm)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(VylloSpacing.sm)) {
                FilterChip(
                    selected = !style.alignStart,
                    onClick = { onChange(style.copy(alignStart = false)) },
                    label = { Text(stringResource(R.string.lyrics_style_align_center)) }
                )
                FilterChip(
                    selected = style.alignStart,
                    onClick = { onChange(style.copy(alignStart = true)) },
                    label = { Text(stringResource(R.string.lyrics_style_align_start)) }
                )
            }

            ToggleRow(
                title = stringResource(R.string.lyrics_style_glow),
                checked = style.glow,
                onCheckedChange = { onChange(style.copy(glow = it)) }
            )
            ToggleRow(
                title = stringResource(R.string.lyrics_style_animate),
                checked = style.animate,
                onCheckedChange = { onChange(style.copy(animate = it)) }
            )
        }
    }
}

@Composable
private fun LabeledSlider(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = VylloSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
    Spacer(Modifier.height(2.dp))
}

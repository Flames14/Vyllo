package com.vyllo.music.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vyllo.music.R
import com.vyllo.music.domain.model.PlaylistSongEntity

@Composable
fun PlaylistCoverEditorDialog(
    songs: List<PlaylistSongEntity>,
    initialCoverUrl: String?,
    initialCropX: Float,
    initialCropY: Float,
    onDismiss: () -> Unit,
    onSave: (String?, Float, Float) -> Unit
) {
    var selectedUrl by remember(initialCoverUrl, songs) {
        mutableStateOf(initialCoverUrl ?: songs.firstOrNull()?.thumbnailUrl?.takeIf(String::isNotBlank))
    }
    var cropX by remember(initialCropX) { mutableFloatStateOf(initialCropX.coerceIn(0f, 1f)) }
    var cropY by remember(initialCropY) { mutableFloatStateOf(initialCropY.coerceIn(0f, 1f)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_cover_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!selectedUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = selectedUrl,
                        contentDescription = stringResource(R.string.playlist_cover_preview),
                        contentScale = ContentScale.Crop,
                        alignment = androidx.compose.ui.BiasAlignment(cropX * 2f - 1f, cropY * 2f - 1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
                Text(
                    text = stringResource(R.string.playlist_cover_choose),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    songs.filter { it.thumbnailUrl.isNotBlank() }.distinctBy { it.thumbnailUrl }.forEach { song ->
                        AsyncImage(
                            model = song.thumbnailUrl,
                            contentDescription = song.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedUrl = song.thumbnailUrl }
                        )
                    }
                }
                Text(stringResource(R.string.playlist_cover_horizontal_crop))
                Slider(value = cropX, onValueChange = { cropX = it }, valueRange = 0f..1f)
                Text(stringResource(R.string.playlist_cover_vertical_crop))
                Slider(value = cropY, onValueChange = { cropY = it }, valueRange = 0f..1f)
                OutlinedButton(
                    onClick = { selectedUrl = null; cropX = 0.5f; cropY = 0.5f },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text(stringResource(R.string.playlist_cover_use_default))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selectedUrl, cropX, cropY) }) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

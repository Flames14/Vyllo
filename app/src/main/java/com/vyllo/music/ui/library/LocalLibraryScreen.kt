package com.vyllo.music.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vyllo.music.R
import com.vyllo.music.domain.model.LocalLibraryAlbum
import com.vyllo.music.domain.model.LocalLibrarySong

@Composable
fun LocalLibraryScreen(
    songs: List<LocalLibrarySong>,
    albums: List<LocalLibraryAlbum>,
    onBack: () -> Unit,
    onPlay: (LocalLibrarySong) -> Unit,
    onEdit: (LocalLibrarySong, String, String, String) -> Unit,
    onRemove: (LocalLibrarySong) -> Unit
) {
    var selectedAlbumKey by rememberSaveable { mutableStateOf<String?>(null) }
    var editingSong by remember { mutableStateOf<LocalLibrarySong?>(null) }
    val selectedAlbum = albums.firstOrNull { albumKey(it) == selectedAlbumKey }

    if (editingSong != null) {
        EditLocalLibraryMetadataDialog(
            song = editingSong!!,
            onDismiss = { editingSong = null },
            onSave = { title, artist, album ->
                onEdit(editingSong!!, title, artist, album)
                editingSong = null
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (selectedAlbum != null) selectedAlbumKey = null else onBack() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
            Column {
                Text(
                    text = selectedAlbum?.title ?: stringResource(R.string.local_library_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = selectedAlbum?.artist ?: pluralStringResource(
                        R.plurals.local_library_song_count,
                        songs.size,
                        songs.size
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (selectedAlbum != null) {
            LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = selectedAlbum.coverUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(16.dp))
                        )
                        Column(modifier = Modifier.padding(start = 14.dp)) {
                            Text(selectedAlbum.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(selectedAlbum.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                pluralStringResource(R.plurals.local_library_song_count, selectedAlbum.songs.size, selectedAlbum.songs.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                items(selectedAlbum.songs, key = { it.url }) { song ->
                    LocalLibrarySongRow(song, onPlay = { onPlay(song) }, onEdit = { editingSong = song }, onRemove = { onRemove(song) })
                }
            }
        } else if (songs.isEmpty()) {
            Text(
                text = stringResource(R.string.local_library_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 56.dp)
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
                if (albums.isNotEmpty()) {
                    item(key = "local_albums_heading") {
                        Text(
                            text = stringResource(R.string.local_library_albums),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                        )
                    }
                    item(key = "local_albums_row") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(albums, key = ::albumKey) { album ->
                                Card(
                                    modifier = Modifier.width(148.dp).clickable { selectedAlbumKey = albumKey(album) },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        AsyncImage(
                                            model = album.coverUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(128.dp).clip(RoundedCornerShape(10.dp))
                                        )
                                        Text(album.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                                        Text(album.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                item(key = "local_songs_heading") {
                    Text(
                        text = stringResource(R.string.local_library_songs),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
                    )
                }
                items(songs, key = { it.url }) { song ->
                    LocalLibrarySongRow(song, onPlay = { onPlay(song) }, onEdit = { editingSong = song }, onRemove = { onRemove(song) })
                }
            }
        }
    }
}

@Composable
private fun LocalLibrarySongRow(
    song: LocalLibrarySong,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp))
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(song.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(song.artist, song.album).filter(String::isNotBlank).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.local_library_edit_metadata)) }
        IconButton(onClick = onRemove) { Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.common_delete)) }
    }
}

@Composable
private fun EditLocalLibraryMetadataDialog(
    song: LocalLibrarySong,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var title by remember(song.url) { mutableStateOf(song.title) }
    var artist by remember(song.url) { mutableStateOf(song.artist) }
    var album by remember(song.url) { mutableStateOf(song.album) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.local_library_edit_metadata)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.song_title)) }, singleLine = true)
                OutlinedTextField(artist, { artist = it }, label = { Text(stringResource(R.string.song_artist)) }, singleLine = true)
                OutlinedTextField(album, { album = it }, label = { Text(stringResource(R.string.album_title)) }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, artist, album) }) { Text(stringResource(R.string.common_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

private fun albumKey(album: LocalLibraryAlbum): String = "${album.title.lowercase()}|${album.artist.lowercase()}"

package com.vyllo.music.domain.model

/** Album summary derived only from explicitly saved local-library songs. */
data class LocalLibraryAlbum(
    val title: String,
    val artist: String,
    val coverUrl: String,
    val songs: List<LocalLibrarySong>
)

object LocalLibraryAlbums {
    fun fromSongs(songs: List<LocalLibrarySong>): List<LocalLibraryAlbum> =
        songs.asSequence()
            .filter { it.album.isNotBlank() }
            .groupBy { it.album.trim().lowercase() to it.artist.trim().lowercase() }
            .values
            .map { tracks ->
                val ordered = tracks.sortedByDescending { it.addedAt }
                val displayMetadata = tracks.minBy { it.addedAt }
                LocalLibraryAlbum(
                    title = displayMetadata.album.trim(),
                    artist = displayMetadata.artist.trim(),
                    coverUrl = ordered.firstNotNullOfOrNull { it.thumbnailUrl.takeIf(String::isNotBlank) }.orEmpty(),
                    songs = ordered
                )
            }
            .sortedBy { it.title.lowercase() }
}

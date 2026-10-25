package com.vyllo.music.service.audio

import androidx.core.net.toUri

import android.net.Uri
import android.util.Base64
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.vyllo.music.data.download.FavoritesDao
import com.vyllo.music.data.download.LocalLibraryDao
import com.vyllo.music.data.download.PlaylistDao
import com.vyllo.music.data.download.toMusicItem
import com.vyllo.music.domain.model.LocalLibraryAlbums
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.PlaylistEntity
import javax.inject.Inject
import javax.inject.Singleton

/** Read-only browse tree used by Android Auto and other MediaLibrary clients. */
@Singleton
class AndroidAutoCatalog @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val favoritesDao: FavoritesDao,
    private val localLibraryDao: LocalLibraryDao
) {
    fun root(): MediaItem = folder(ROOT_ID, "Vyllo", MediaMetadata.FOLDER_TYPE_MIXED)

    fun rootChildren(): List<MediaItem> = listOf(
        folder(LOCAL_SONGS_ID, "My library", MediaMetadata.FOLDER_TYPE_TITLES),
        folder(ALBUMS_ID, "Albums", MediaMetadata.FOLDER_TYPE_ALBUMS),
        folder(PLAYLISTS_ID, "Playlists", MediaMetadata.FOLDER_TYPE_PLAYLISTS),
        folder(LIKED_ID, "Liked songs", MediaMetadata.FOLDER_TYPE_TITLES)
    )

    suspend fun children(parentId: String): List<MediaItem> = when {
        parentId == ROOT_ID -> rootChildren()
        parentId == LOCAL_SONGS_ID -> localLibraryDao.getSongs().map { it.toMusicItem().toPlayableNode() }
        parentId == ALBUMS_ID -> LocalLibraryAlbums.fromSongs(localLibraryDao.getSongs()).map { album ->
            val encoded = Base64.encodeToString(
                "${album.title}\u0000${album.artist}".toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            folder("$ALBUM_PREFIX$encoded", album.title, MediaMetadata.FOLDER_TYPE_TITLES).buildUpon()
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(album.title)
                        .setSubtitle(album.artist)
                        .setArtist(album.artist)
                        .setArtworkUri(album.coverUrl.takeIf(String::isNotBlank)?.let(Uri::parse))
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                        .setFolderType(MediaMetadata.FOLDER_TYPE_TITLES)
                        .build()
                ).build()
        }
        parentId == PLAYLISTS_ID -> playlistDao.getAllPlaylistsList().map { playlist ->
            playlistFolder(playlist)
        }
        parentId == LIKED_ID -> favoritesDao.getAllList()
            .filter { it.rating > 0 }
            .map { it.toMusicItem().toPlayableNode() }
        parentId.startsWith(ALBUM_PREFIX) -> albumSongs(parentId)
        parentId.startsWith(PLAYLIST_PREFIX) -> playlistSongs(parentId)
        else -> emptyList()
    }

    suspend fun item(mediaId: String): MediaItem? {
        if (mediaId == ROOT_ID) return root()
        rootChildren().firstOrNull { it.mediaId == mediaId }?.let { return it }
        if (mediaId.startsWith(PLAYLIST_PREFIX)) {
            return playlistDao.getAllPlaylistsList()
                .firstOrNull { playlistId(it) == mediaId }
                ?.let(::playlistFolder)
        }
        children(ALBUMS_ID).firstOrNull { it.mediaId == mediaId }?.let { return it }
        localLibraryDao.getSongs().firstOrNull { it.url == mediaId }
            ?.let { return it.toMusicItem().toPlayableNode() }
        favoritesDao.getByUrl(mediaId)?.takeIf { it.rating > 0 }
            ?.let { return it.toMusicItem().toPlayableNode() }
        for (playlist in playlistDao.getAllPlaylistsList()) {
            playlistDao.getSongsByPlaylistList(playlist.id).firstOrNull { it.url == mediaId }
                ?.let { song ->
                    return MusicItem(
                        id = song.url,
                        title = song.title,
                        url = song.url,
                        uploader = song.uploader,
                        thumbnailUrl = song.thumbnailUrl
                    ).toPlayableNode()
                }
        }
        return null
    }

    fun searchResultItems(items: List<MusicItem>): List<MediaItem> = items.map { it.toPlayableNode() }

    private suspend fun albumSongs(albumId: String): List<MediaItem> {
        val decoded = runCatching {
            val data = Base64.decode(albumId.removePrefix(ALBUM_PREFIX), Base64.URL_SAFE)
                .toString(Charsets.UTF_8)
            val separator = data.indexOf('\u0000')
            data.substring(0, separator) to data.substring(separator + 1)
        }.getOrNull() ?: return emptyList()
        return localLibraryDao.getSongs()
            .filter { it.album.equals(decoded.first, ignoreCase = true) && it.artist.equals(decoded.second, ignoreCase = true) }
            .map { it.toMusicItem().toPlayableNode() }
    }

    private suspend fun playlistSongs(playlistId: String): List<MediaItem> {
        val id = playlistId.removePrefix(PLAYLIST_PREFIX).toLongOrNull() ?: return emptyList()
        return playlistDao.getSongsByPlaylistList(id).map { song ->
            MusicItem(
                id = song.url,
                title = song.title,
                url = song.url,
                uploader = song.uploader,
                thumbnailUrl = song.thumbnailUrl
            ).toPlayableNode()
        }
    }

    private fun playlistFolder(playlist: PlaylistEntity): MediaItem =
        folder(playlistId(playlist), playlist.name, MediaMetadata.FOLDER_TYPE_TITLES)

    private fun playlistId(playlist: PlaylistEntity) = "$PLAYLIST_PREFIX${playlist.id}"

    private fun folder(id: String, title: String, folderType: Int): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .setFolderType(folderType)
                    .build()
            )
            .build()

    private fun MusicItem.toPlayableNode(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(uploader)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply { thumbnailUrl.takeIf(String::isNotBlank)?.let { setArtworkUri(it.toUri()) } }
            .build()
        return MediaItem.Builder()
            .setMediaId(url)
            .setUri(url)
            .setMediaMetadata(metadata)
            .build()
    }

    companion object {
        const val ROOT_ID = "vyllo_root"
        const val LOCAL_SONGS_ID = "local_songs"
        const val ALBUMS_ID = "local_albums"
        const val PLAYLISTS_ID = "playlists"
        const val LIKED_ID = "liked_songs"
        private const val ALBUM_PREFIX = "album:"
        private const val PLAYLIST_PREFIX = "playlist:"
    }
}

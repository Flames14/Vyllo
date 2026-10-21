package com.vyllo.music.data.repository

import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.data.download.PlaylistDao
import com.vyllo.music.domain.model.PlaylistEntity
import com.vyllo.music.domain.model.PlaylistSongEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao
) {
    fun getAllPlaylists(): Flow<List<PlaylistEntity>> =
        playlistDao.getAllPlaylists().distinctUntilChanged()

    suspend fun createPlaylist(name: String): Long = playlistDao.insertPlaylist(PlaylistEntity(name = name))

    suspend fun deletePlaylist(playlist: PlaylistEntity) {
        playlistDao.deletePlaylist(playlist)
    }

    suspend fun addSongToPlaylist(playlistId: Long, item: MusicItem): Boolean {
        val currentSongs = playlistDao.getSongsByPlaylistList(playlistId)
        val nextAddedAt = maxOf(
            System.currentTimeMillis(),
            (currentSongs.maxOfOrNull { it.addedAt } ?: Long.MIN_VALUE).let {
                if (it == Long.MAX_VALUE) Long.MAX_VALUE else it + 1
            }
        )
        val insertedRowId = playlistDao.insertSongIfAbsent(
            PlaylistSongEntity(
                playlistId = playlistId,
                url = item.url,
                title = item.title,
                uploader = item.uploader,
                thumbnailUrl = item.thumbnailUrl,
                addedAt = nextAddedAt
            )
        )
        return insertedRowId != -1L
    }

    suspend fun moveSongInPlaylist(playlistId: Long, fromIndex: Int, toIndex: Int): Boolean {
        val songs = playlistDao.getSongsByPlaylistList(playlistId)
        val reordered = PlaylistSongOrdering.move(songs, fromIndex, toIndex, System.currentTimeMillis())
            ?: return false
        playlistDao.updatePlaylistSongs(reordered)
        return true
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, url: String) {
        playlistDao.removeSongFromPlaylist(playlistId, url)
    }

    fun getSongsInPlaylist(playlistId: Long): Flow<List<PlaylistSongEntity>> =
        playlistDao.getSongsByPlaylist(playlistId).distinctUntilChanged()
}

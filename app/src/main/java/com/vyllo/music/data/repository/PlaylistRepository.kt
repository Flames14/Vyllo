package com.vyllo.music.data.repository

import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.data.download.PlaylistDao
import com.vyllo.music.data.download.PlaylistEntity
import com.vyllo.music.data.download.PlaylistSongEntity
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

    suspend fun createPlaylist(name: String) {
        playlistDao.insertPlaylist(PlaylistEntity(name = name))
    }

    suspend fun deletePlaylist(playlist: PlaylistEntity) {
        playlistDao.deletePlaylist(playlist)
    }

    suspend fun addSongToPlaylist(playlistId: Long, item: MusicItem) {
        playlistDao.insertSongToPlaylist(
            PlaylistSongEntity(
                playlistId = playlistId,
                url = item.url,
                title = item.title,
                uploader = item.uploader,
                thumbnailUrl = item.thumbnailUrl
            )
        )
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, url: String) {
        playlistDao.removeSongFromPlaylist(playlistId, url)
    }

    fun getSongsInPlaylist(playlistId: Long): Flow<List<PlaylistSongEntity>> =
        playlistDao.getSongsByPlaylist(playlistId).distinctUntilChanged()
}

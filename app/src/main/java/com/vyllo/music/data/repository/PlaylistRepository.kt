package com.vyllo.music.data.repository

import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.data.download.PlaylistDao
import com.vyllo.music.domain.model.PlaylistEntity
import com.vyllo.music.domain.model.PlaylistSongEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao
) {
    private val addSongMutex = Mutex()
    fun getAllPlaylists(): Flow<List<PlaylistEntity>> =
        playlistDao.getAllPlaylists().distinctUntilChanged()

    suspend fun createPlaylist(name: String): Long = playlistDao.insertPlaylist(PlaylistEntity(name = name))

    suspend fun deletePlaylist(playlist: PlaylistEntity) {
        playlistDao.deletePlaylist(playlist)
    }

    suspend fun updatePlaylistCover(playlistId: Long, coverUrl: String?, cropX: Float, cropY: Float) {
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return
        playlistDao.updatePlaylist(
            playlist.copy(
                coverUrl = coverUrl?.takeIf(String::isNotBlank),
                coverCropX = cropX.coerceIn(0f, 1f),
                coverCropY = cropY.coerceIn(0f, 1f)
            )
        )
    }

    suspend fun addSongToPlaylist(playlistId: Long, item: MusicItem, position: Int? = null): Boolean =
        addSongMutex.withLock {
            val currentSongs = playlistDao.getSongsByPlaylistList(playlistId)
            if (currentSongs.any { it.url == item.url }) return@withLock false

            val newSong = PlaylistSongEntity(
                playlistId = playlistId,
                url = item.url,
                title = item.title,
                uploader = item.uploader,
                thumbnailUrl = item.thumbnailUrl
            )
            if (position == null) {
                val previousMax = currentSongs.maxOfOrNull { it.addedAt } ?: Long.MIN_VALUE
                val nextOrderKey = if (previousMax == Long.MAX_VALUE) Long.MAX_VALUE else previousMax + 1
                val addedAt = maxOf(System.currentTimeMillis(), nextOrderKey)
                return@withLock playlistDao.insertSongIfAbsent(newSong.copy(addedAt = addedAt)) != -1L
            }
            val targetIndex = position?.coerceIn(0, currentSongs.size) ?: currentSongs.size
            val ordered = PlaylistSongOrdering.insert(
                songs = currentSongs,
                song = newSong,
                position = targetIndex,
                nowMillis = System.currentTimeMillis()
            )
            val insertedSong = ordered.first { it.url == item.url }
            val insertedRowId = playlistDao.insertSongIfAbsent(insertedSong)
            if (insertedRowId == -1L) return@withLock false
            playlistDao.updatePlaylistSongs(ordered)
            true
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

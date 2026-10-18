package com.vyllo.music.domain.usecase

import com.vyllo.music.domain.repository.IMusicRepository
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.PlaylistEntity
import com.vyllo.music.domain.model.PlaylistSongEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ManagePlaylistUseCase @Inject constructor(
    private val repository: IMusicRepository
) {
    fun getAllPlaylists(): Flow<List<PlaylistEntity>> = repository.getAllPlaylists()
    
    suspend fun createPlaylist(name: String) = repository.createPlaylist(name)
    
    suspend fun deletePlaylist(playlist: PlaylistEntity) = repository.deletePlaylist(playlist)
    
    suspend fun addSongToPlaylist(playlistId: Long, item: MusicItem) = 
        repository.addSongToPlaylist(playlistId, item)
    
    suspend fun removeSongFromPlaylist(playlistId: Long, url: String) = 
        repository.removeSongFromPlaylist(playlistId, url)

    suspend fun moveSongInPlaylist(playlistId: Long, fromIndex: Int, toIndex: Int) =
        repository.moveSongInPlaylist(playlistId, fromIndex, toIndex)
    
    fun getSongsInPlaylist(playlistId: Long): Flow<List<PlaylistSongEntity>> = 
        repository.getSongsInPlaylist(playlistId)
}

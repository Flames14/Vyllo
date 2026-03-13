package com.example.musicpiped.data.download

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongToPlaylist(song: PlaylistSongEntity)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND url = :url")
    suspend fun removeSongFromPlaylist(playlistId: Long, url: String)

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY addedAt DESC")
    fun getSongsByPlaylist(playlistId: Long): Flow<List<PlaylistSongEntity>>
}

package com.vyllo.music.data.repository

import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.data.download.PlaylistDao
import com.vyllo.music.data.download.PlaylistEntity
import com.vyllo.music.data.download.PlaylistSongEntity
import com.vyllo.music.data.network.YouTubeRemotePlaylist
import com.vyllo.music.data.network.YouTubeRemoteTrack
import com.vyllo.music.data.network.YouTubeSyncApiService
import com.vyllo.music.data.oauth.YouTubeOAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class SyncProgress(
    val isSyncing: Boolean = false,
    val playlistTitle: String = "",
    val currentTrack: Int = 0,
    val totalTracks: Int = 0,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
)

@Singleton
class YouTubeSyncRepository @Inject constructor(
    private val oauthManager: YouTubeOAuthManager,
    private val apiService: YouTubeSyncApiService,
    private val playlistDao: PlaylistDao
) {
    companion object {
        private const val TAG = "YouTubeSyncRepository"
    }

    /**
     * Fetches all playlists for the authenticated Google account.
     */
    suspend fun getUserPlaylists(): Result<List<YouTubeRemotePlaylist>> = withContext(Dispatchers.IO) {
        val token = oauthManager.getValidAccessToken()
            ?: return@withContext Result.failure(Exception("Not logged into Google. Please connect your Google account."))

        return@withContext apiService.fetchMyPlaylists(token)
    }

    /**
     * Synchronizes a remote YouTube playlist into the local Room database.
     */
    fun syncRemotePlaylist(remotePlaylist: YouTubeRemotePlaylist): Flow<SyncProgress> = flow {
        emit(SyncProgress(isSyncing = true, playlistTitle = remotePlaylist.title, totalTracks = remotePlaylist.trackCount))

        try {
            val token = oauthManager.getValidAccessToken()
            val tracksResult = apiService.fetchPlaylistTracks(remotePlaylist.id, token)

            if (tracksResult.isFailure) {
                val err = tracksResult.exceptionOrNull()?.message ?: "Failed to fetch playlist tracks"
                emit(SyncProgress(isSyncing = false, playlistTitle = remotePlaylist.title, errorMessage = err))
                return@flow
            }

            val tracks = tracksResult.getOrNull() ?: emptyList()
            savePlaylistAndTracks(remotePlaylist.title, tracks)

            emit(
                SyncProgress(
                    isSyncing = false,
                    playlistTitle = remotePlaylist.title,
                    currentTrack = tracks.size,
                    totalTracks = tracks.size,
                    isSuccess = true
                )
            )
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Error syncing playlist: ${remotePlaylist.title}", e)
            emit(SyncProgress(isSyncing = false, playlistTitle = remotePlaylist.title, errorMessage = e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Imports a playlist from a YouTube / YouTube Music URL (Zero-Login).
     */
    fun importPlaylistFromUrl(urlOrId: String): Flow<SyncProgress> = flow {
        emit(SyncProgress(isSyncing = true, playlistTitle = "Loading YouTube Playlist..."))

        try {
            val extractResult = apiService.extractPlaylistFromUrl(urlOrId)
            if (extractResult.isFailure) {
                val err = extractResult.exceptionOrNull()?.message ?: "Failed to extract playlist URL"
                emit(SyncProgress(isSyncing = false, errorMessage = err))
                return@flow
            }

            val (remotePlaylist, tracks) = extractResult.getOrThrow()
            if (tracks.isEmpty()) {
                emit(SyncProgress(isSyncing = false, playlistTitle = remotePlaylist.title, errorMessage = "No playable songs found in this playlist"))
                return@flow
            }

            emit(SyncProgress(isSyncing = true, playlistTitle = remotePlaylist.title, currentTrack = tracks.size, totalTracks = tracks.size))

            savePlaylistAndTracks(remotePlaylist.title, tracks)

            emit(
                SyncProgress(
                    isSyncing = false,
                    playlistTitle = remotePlaylist.title,
                    currentTrack = tracks.size,
                    totalTracks = tracks.size,
                    isSuccess = true
                )
            )
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Error importing from URL: $urlOrId", e)
            emit(SyncProgress(isSyncing = false, errorMessage = e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun savePlaylistAndTracks(playlistName: String, tracks: List<YouTubeRemoteTrack>) {
        // Find existing playlist or create new one
        val existing = playlistDao.getPlaylistByName(playlistName)
        val playlistId = existing?.id ?: playlistDao.insertPlaylist(PlaylistEntity(name = playlistName))

        // Refresh songs in playlist
        playlistDao.clearSongsInPlaylist(playlistId)

        val songEntities = tracks.mapIndexed { index, track ->
            PlaylistSongEntity(
                playlistId = playlistId,
                url = track.fullUrl,
                title = track.title,
                uploader = track.channelTitle,
                thumbnailUrl = track.thumbnailUrl,
                addedAt = System.currentTimeMillis() + index // Maintain exact playlist order
            )
        }

        playlistDao.insertSongsToPlaylist(songEntities)
        SecureLogger.d(TAG, "Successfully saved ${songEntities.size} tracks to playlist '$playlistName' (ID: $playlistId)")
    }
}

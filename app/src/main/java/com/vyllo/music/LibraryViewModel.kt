package com.vyllo.music

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.DownloadEntity
import com.vyllo.music.domain.model.PlaylistEntity
import com.vyllo.music.domain.model.PlaylistSongEntity
import com.vyllo.music.domain.manager.PlaybackQueueManager
import com.vyllo.music.data.manager.DownloadManager
import com.vyllo.music.domain.usecase.DownloadMusicUseCase
import com.vyllo.music.domain.usecase.ManagePlaylistUseCase
import com.vyllo.music.data.network.YouTubeRemotePlaylist
import com.vyllo.music.data.oauth.YouTubeOAuthManager
import com.vyllo.music.data.repository.SyncProgress
import com.vyllo.music.data.repository.YouTubeSyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val managePlaylistUseCase: ManagePlaylistUseCase,
    private val downloadMusicUseCase: DownloadMusicUseCase,
    private val playbackQueueManager: PlaybackQueueManager,
    private val downloadManager: DownloadManager,
    val youtubeOAuthManager: YouTubeOAuthManager,
    val youtubeSyncRepository: YouTubeSyncRepository,
    application: Application
) : AndroidViewModel(application) {

    private val context = application.applicationContext

    var allPlaylists by mutableStateOf<List<PlaylistEntity>>(emptyList())
    var currentPlaylistSongs by mutableStateOf<List<PlaylistSongEntity>>(emptyList())
    private var playlistSongsJob: kotlinx.coroutines.Job? = null
    var showPlaylistAddDialog by mutableStateOf(false)
    var songToAddToPlaylist by mutableStateOf<MusicItem?>(null)
    var selectedLocalPlaylist by mutableStateOf<PlaylistEntity?>(null)

    // YouTube Sync States
    val isGoogleConnected: StateFlow<Boolean> = youtubeOAuthManager.isAuthorized
    val userEmail: StateFlow<String?> = youtubeOAuthManager.userEmail
    var remoteYouTubePlaylists by mutableStateOf<List<YouTubeRemotePlaylist>>(emptyList())
    var isLoadingRemotePlaylists by mutableStateOf(false)
    var syncProgressState by mutableStateOf<SyncProgress?>(null)
    var showYouTubeSyncSheet by mutableStateOf(false)

    var downloadedSongs by mutableStateOf<List<DownloadEntity>>(emptyList())
    val downloadProgress get() = downloadManager.downloadProgress

    var currentPlayingItem by mutableStateOf<MusicItem?>(null)

    init {
        viewModelScope.launch {
            playbackQueueManager.currentPlayingItem.collectLatest { item ->
                currentPlayingItem = item
            }
        }

        observeDownloads()
        observePlaylists()
        downloadManager.startObserving(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        downloadManager.stopObserving()
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            downloadMusicUseCase.getAllDownloads().collectLatest { downloads ->
                downloadedSongs = downloads
            }
        }
    }

    private fun observePlaylists() {
        viewModelScope.launch {
            managePlaylistUseCase.getAllPlaylists().collectLatest { playlists ->
                allPlaylists = playlists
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            managePlaylistUseCase.createPlaylist(name)
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch {
            managePlaylistUseCase.deletePlaylist(playlist)
        }
    }

    fun addSongToPlaylist(playlistId: Long, item: MusicItem) {
        viewModelScope.launch {
            managePlaylistUseCase.addSongToPlaylist(playlistId, item)
            showPlaylistAddDialog = false
            songToAddToPlaylist = null
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, url: String) {
        viewModelScope.launch {
            managePlaylistUseCase.removeSongFromPlaylist(playlistId, url)
        }
    }

    fun loadPlaylistSongs(playlistId: Long) {
        playlistSongsJob?.cancel()
        playlistSongsJob = viewModelScope.launch {
            managePlaylistUseCase.getSongsInPlaylist(playlistId).collectLatest { songs ->
                currentPlaylistSongs = songs
            }
        }
    }

    fun showPlaylistAddDialog(item: MusicItem) {
        songToAddToPlaylist = item
        showPlaylistAddDialog = true
    }

    fun playNext(item: MusicItem) {
        val nextIdx = (playbackQueueManager.currentIndex + 1).coerceAtLeast(0)
        playbackQueueManager.addItemAt(nextIdx, item)
    }

    fun addToQueue(item: MusicItem) {
        playbackQueueManager.addItem(item)
    }

    fun downloadSong(item: MusicItem) {
        downloadMusicUseCase.downloadSong(item)
    }

    fun cancelDownload(url: String) {
        downloadMusicUseCase.cancelDownload(url)
    }

    fun deleteDownload(url: String) {
        viewModelScope.launch {
            downloadMusicUseCase.deleteDownload(url)
        }
    }

    // ==========================================
    // YOUTUBE PLAYLIST SYNC METHODS
    // ==========================================

    val hasGoogleClientId: StateFlow<Boolean> = youtubeOAuthManager.hasConfiguredClientId

    fun getGoogleClientId(): String? = youtubeOAuthManager.getClientId()

    fun setGoogleClientId(clientId: String?) {
        youtubeOAuthManager.setCustomClientId(clientId)
    }

    fun getGoogleAuthUri(): android.net.Uri? {
        return youtubeOAuthManager.createAuthorizationUri()
    }

    fun handleGoogleAuthRedirect(uri: android.net.Uri) {
        viewModelScope.launch {
            val result = youtubeOAuthManager.handleAuthorizationResponse(uri)
            if (result.isSuccess) {
                loadRemoteYouTubePlaylists()
            }
        }
    }

    fun disconnectGoogle() {
        youtubeOAuthManager.signOut()
        remoteYouTubePlaylists = emptyList()
    }

    fun loadRemoteYouTubePlaylists() {
        viewModelScope.launch {
            isLoadingRemotePlaylists = true
            val result = youtubeSyncRepository.getUserPlaylists()
            if (result.isSuccess) {
                remoteYouTubePlaylists = result.getOrNull() ?: emptyList()
            }
            isLoadingRemotePlaylists = false
        }
    }

    fun syncYouTubePlaylist(remotePlaylist: YouTubeRemotePlaylist) {
        viewModelScope.launch {
            youtubeSyncRepository.syncRemotePlaylist(remotePlaylist).collect { progress ->
                syncProgressState = progress
            }
        }
    }

    fun importPlaylistByUrl(urlOrId: String) {
        viewModelScope.launch {
            youtubeSyncRepository.importPlaylistFromUrl(urlOrId).collect { progress ->
                syncProgressState = progress
            }
        }
    }

    fun clearSyncProgress() {
        syncProgressState = null
    }
}

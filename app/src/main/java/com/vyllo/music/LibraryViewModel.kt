package com.vyllo.music

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.data.download.DownloadEntity
import com.vyllo.music.data.download.PlaylistEntity
import com.vyllo.music.data.download.PlaylistSongEntity
import com.vyllo.music.data.manager.PlaybackQueueManager
import com.vyllo.music.data.manager.DownloadManager
import com.vyllo.music.domain.usecase.DownloadMusicUseCase
import com.vyllo.music.domain.usecase.ManagePlaylistUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val managePlaylistUseCase: ManagePlaylistUseCase,
    private val downloadMusicUseCase: DownloadMusicUseCase,
    private val playbackQueueManager: PlaybackQueueManager,
    private val downloadManager: DownloadManager,
    application: Application
) : AndroidViewModel(application) {

    private val context = application.applicationContext

    var allPlaylists by mutableStateOf<List<PlaylistEntity>>(emptyList())
    var currentPlaylistSongs by mutableStateOf<List<PlaylistSongEntity>>(emptyList())
    var showPlaylistAddDialog by mutableStateOf(false)
    var songToAddToPlaylist by mutableStateOf<MusicItem?>(null)
    var selectedLocalPlaylist by mutableStateOf<PlaylistEntity?>(null)

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
        viewModelScope.launch {
            managePlaylistUseCase.getSongsInPlaylist(playlistId).collectLatest { songs ->
                currentPlaylistSongs = songs
            }
        }
    }

    fun showPlaylistAddDialog(item: MusicItem) {
        songToAddToPlaylist = item
        showPlaylistAddDialog = true
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
}

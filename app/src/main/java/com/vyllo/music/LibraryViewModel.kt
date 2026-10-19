package com.vyllo.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.ArtistSummary
import com.vyllo.music.domain.model.LibraryArtists
import com.vyllo.music.domain.model.normalizeArtistKey
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
import com.vyllo.music.domain.repository.IMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val managePlaylistUseCase: ManagePlaylistUseCase,
    private val downloadMusicUseCase: DownloadMusicUseCase,
    private val playbackQueueManager: PlaybackQueueManager,
    private val downloadManager: DownloadManager,
    private val youtubeOAuthManager: YouTubeOAuthManager,
    private val youtubeSyncRepository: YouTubeSyncRepository,
    private val musicRepository: IMusicRepository
) : ViewModel() {

    var allPlaylists by mutableStateOf<List<PlaylistEntity>>(emptyList())
    var currentPlaylistSongs by mutableStateOf<List<PlaylistSongEntity>>(emptyList())
    private var playlistSongsJob: kotlinx.coroutines.Job? = null
    var showPlaylistAddDialog by mutableStateOf(false)
    var playlistAddWasDuplicate by mutableStateOf(false)
    var songToAddToPlaylist by mutableStateOf<MusicItem?>(null)

    // YouTube Sync States
    val isGoogleConnected: StateFlow<Boolean> = youtubeOAuthManager.isAuthorized
    val userEmail: StateFlow<String?> = youtubeOAuthManager.userEmail
    var remoteYouTubePlaylists by mutableStateOf<List<YouTubeRemotePlaylist>>(emptyList())
    var isLoadingRemotePlaylists by mutableStateOf(false)
    var remotePlaylistsFailed by mutableStateOf(false)
    var syncProgressState by mutableStateOf<SyncProgress?>(null)
    var showYouTubeSyncSheet by mutableStateOf(false)

    var downloadedSongs by mutableStateOf<List<DownloadEntity>>(emptyList())

    /**
     * O(1) membership test over the downloaded set.
     *
     * [com.vyllo.music.presentation.components.common.DownloadButton] sits in
     * every row of every list, and its previous `downloadedSongs.any { … }`
     * walked the whole download list once per row per recomposition — a hidden
     * O(rows x downloads) cost that showed up exactly while scrolling.
     */
    var downloadedUrls: Set<String> by mutableStateOf(emptySet())
        private set

    val downloadProgress get() = downloadManager.downloadProgress

    // ---- History (Tier-0: history screen) ----
    val historyItems = musicRepository.getRecentHistory(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- Artists (derived locally; no artist catalogue is stored) ----
    var libraryArtists by mutableStateOf<List<ArtistSummary>>(emptyList())
        private set

    var selectedArtist by mutableStateOf<ArtistSummary?>(null)
        private set

    var artistSongs by mutableStateOf<List<MusicItem>>(emptyList())
        private set

    var isArtistLoading by mutableStateOf(false)
        private set

    var artistLoadFailed by mutableStateOf(false)
        private set

    /** Everything the library already knows about, merged and de-duplicated. */
    private var librarySongs: List<MusicItem> = emptyList()
    private var artistJob: kotlinx.coroutines.Job? = null
    private var artistLoadToken = 0

    var currentPlayingItem by mutableStateOf<MusicItem?>(null)

    init {
        viewModelScope.launch {
            playbackQueueManager.currentPlayingItem.collectLatest { item ->
                currentPlayingItem = item
            }
        }

        observeDownloads()
        observePlaylists()
        observeLibraryArtists()
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
                // Rebuilt as a fresh set each emission so every reader sees an
                // immutable snapshot with no allocation in its own recomposition.
                downloadedUrls = downloads.mapTo(HashSet(downloads.size)) { it.url }
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

    private fun observeLibraryArtists() {
        viewModelScope.launch {
            combine(
                musicRepository.getRecentHistory(200),
                musicRepository.observeLikedSongs(),
                downloadMusicUseCase.getAllDownloads()
            ) { history, liked, downloads ->
                val downloaded = downloads.map {
                    MusicItem(it.title, it.url, it.uploader, it.thumbnailUrl)
                }
                (history + liked + downloaded).distinctBy { it.url }
            }.collectLatest { songs ->
                librarySongs = songs
                libraryArtists = LibraryArtists.fromSongs(songs)
            }
        }
    }

    /**
     * Opens an artist by display name. The summary is resolved from the derived
     * list so a cold start on the artist route (after process death) still works.
     */
    fun openArtist(name: String) {
        val key = normalizeArtistKey(name)
        val summary = libraryArtists.firstOrNull { it.key == key }
            ?: ArtistSummary(key = key, name = name.trim(), thumbnailUrl = "", songCount = 0)
        loadArtist(summary)
    }

    fun loadArtist(artist: ArtistSummary) {
        val token = ++artistLoadToken
        selectedArtist = artist
        artistLoadFailed = false
        val local = librarySongs
            .filter { it.normalizedArtistKey == artist.key }
            .distinctBy { it.url }
        artistSongs = local
        isArtistLoading = true

        artistJob?.cancel()
        artistJob = viewModelScope.launch {
            val remote = runCatching {
                musicRepository.getArtistSongs(
                    artist = artist.name,
                    excludeUrls = local.mapTo(HashSet(local.size)) { it.url }
                )
            }.getOrNull()
            // A newer selection (or a cleared screen) wins over this result.
            if (token != artistLoadToken) return@launch
            val merged = (local + remote.orEmpty()).distinctBy { it.url }
            artistSongs = merged
            // A cold-start summary has no artwork; adopt the first available one.
            if (artist.thumbnailUrl.isBlank()) {
                merged.firstOrNull { it.thumbnailUrl.isNotBlank() }?.let { first ->
                    selectedArtist = artist.copy(
                        thumbnailUrl = first.thumbnailUrl,
                        songCount = maxOf(artist.songCount, merged.size)
                    )
                }
            }
            artistLoadFailed = remote == null
            isArtistLoading = false
        }
    }

    fun retrySelectedArtist() {
        selectedArtist?.let(::loadArtist)
    }

    fun clearArtist() {
        artistLoadToken++
        artistJob?.cancel()
        artistJob = null
        selectedArtist = null
        artistSongs = emptyList()
        artistLoadFailed = false
        isArtistLoading = false
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
            playlistAddWasDuplicate = false
            val added = managePlaylistUseCase.addSongToPlaylist(playlistId, item)
            if (added) {
                showPlaylistAddDialog = false
                songToAddToPlaylist = null
            } else {
                playlistAddWasDuplicate = true
            }
        }
    }

    fun moveSongInPlaylist(playlistId: Long, fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        viewModelScope.launch {
            managePlaylistUseCase.moveSongInPlaylist(playlistId, fromIndex, toIndex)
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
        playlistAddWasDuplicate = false
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
            remotePlaylistsFailed = false
            val result = youtubeSyncRepository.getUserPlaylists()
            if (result.isSuccess) {
                remoteYouTubePlaylists = result.getOrNull() ?: emptyList()
            } else {
                remotePlaylistsFailed = true
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

    fun clearHistory() {
        viewModelScope.launch { musicRepository.clearHistory() }
    }

    fun removeFromHistory(url: String) {
        viewModelScope.launch { musicRepository.removeFromHistory(url) }
    }
}

package com.vyllo.music.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AppUpdateState {
    object Idle : AppUpdateState()
    object Checking : AppUpdateState()
    data class UpdateAvailable(val release: GithubRelease, val apkUrl: String) : AppUpdateState()
    object UpToDate : AppUpdateState()
    data class Error(val message: String) : AppUpdateState()
    object Downloading : AppUpdateState()
}

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val repository: AppUpdateRepository,
    private val downloader: UpdateDownloader
) : ViewModel() {

    private val _updateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val updateState: StateFlow<AppUpdateState> = _updateState.asStateFlow()

    fun checkForUpdates() {
        viewModelScope.launch {
            _updateState.value = AppUpdateState.Checking
            val result = repository.checkForUpdates()
            when (result) {
                is UpdateResult.UpdateAvailable -> {
                    _updateState.value = AppUpdateState.UpdateAvailable(result.release, result.apkUrl)
                }
                is UpdateResult.NoUpdate -> {
                    _updateState.value = AppUpdateState.UpToDate
                }
                is UpdateResult.Error -> {
                    _updateState.value = AppUpdateState.Error(result.message)
                }
            }
        }
    }

    fun startDownload(url: String) {
        _updateState.value = AppUpdateState.Downloading
        downloader.downloadApk(url)
        // Reset state after triggering download so dialog closes smoothly or updates
        // Leaving it in downloading state can let the UI show "Check notification bar"
    }
    
    fun resetState() {
        _updateState.value = AppUpdateState.Idle
    }
}

package com.vyllo.music.recognition.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vyllo.music.recognition.domain.model.RecognitionStatus
import com.vyllo.music.recognition.domain.usecase.RecognizeMusicUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecognitionViewModel @Inject constructor(
    private val recognizeMusicUseCase: RecognizeMusicUseCase,
    private val repository: com.vyllo.music.data.IMusicRepository
) : ViewModel() {
    
    private var recognitionJob: kotlinx.coroutines.Job? = null

    private val _status = MutableStateFlow<RecognitionStatus>(RecognitionStatus.Idle)
    val status = _status.asStateFlow()

    fun startRecognition() {
        if (_status.value is RecognitionStatus.Listening || _status.value is RecognitionStatus.Processing) return

        recognitionJob?.cancel()
        recognitionJob = viewModelScope.launch {
            _status.value = RecognitionStatus.Listening
            val result = recognizeMusicUseCase()
            
            result.fold(
                onSuccess = { recognitionResult ->
                    _status.value = RecognitionStatus.Success(recognitionResult)
                },
                onFailure = { error ->
                    val message = error.message ?: "Unknown error"
                    _status.value = if (message.contains("No match", ignoreCase = true)) {
                        RecognitionStatus.NoMatch("No match found. Try again with clearer audio.")
                    } else if (error is kotlinx.coroutines.CancellationException) {
                        RecognitionStatus.Idle
                    } else {
                        RecognitionStatus.Error(message)
                    }
                }
            )
        }
    }

    fun stopRecognition() {
        recognitionJob?.cancel()
        recognitionJob = null
        _status.value = RecognitionStatus.Idle
    }

    fun reset() {
        _status.value = RecognitionStatus.Idle
    }

    fun resolveAndPlay(result: com.vyllo.music.recognition.domain.model.RecognitionResult, onResolved: (com.vyllo.music.domain.model.MusicItem) -> Unit) {
        if (result.youtubeVideoId != null) {
            onResolved(result.toMusicItem())
            return
        }

        // We need to find a YouTube video for this track
        viewModelScope.launch {
            _status.value = RecognitionStatus.Processing // Briefly show identifying again
            try {
                val searchQuery = "${result.title} ${result.artist}"
                val searchResults = repository.searchMusic(searchQuery)
                val firstMatch = searchResults.firstOrNull()
                
                if (firstMatch != null) {
                    onResolved(firstMatch)
                } else {
                    // Fallback to whatever we have, even if it might fail streaming
                    onResolved(result.toMusicItem())
                }
            } catch (e: Exception) {
                onResolved(result.toMusicItem())
            } finally {
                _status.value = RecognitionStatus.Success(result)
            }
        }
    }
}

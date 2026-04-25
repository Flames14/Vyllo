package com.vyllo.music.recognition.domain.repository

import com.vyllo.music.recognition.domain.model.RecognitionResult
import kotlinx.coroutines.flow.Flow

interface ShazamRepository {
    suspend fun recognize(): Result<RecognitionResult>
}

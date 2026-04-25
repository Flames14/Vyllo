package com.vyllo.music.recognition.domain.usecase

import com.vyllo.music.recognition.domain.model.RecognitionResult
import com.vyllo.music.recognition.domain.repository.ShazamRepository
import javax.inject.Inject

class RecognizeMusicUseCase @Inject constructor(
    private val repository: ShazamRepository
) {
    suspend operator fun invoke(): Result<RecognitionResult> {
        return repository.recognize()
    }
}

package com.vyllo.music.domain.usecase

import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.data.IMusicRepository
import kotlinx.coroutines.delay
import javax.inject.Inject

class GetStreamUrlUseCase @Inject constructor(
    private val repository: IMusicRepository
) {
    suspend operator fun invoke(url: String, isVideo: Boolean = false): String? {
        if (!isVideo) {
            val localUrl = repository.getLocalStreamUrl(url)
            if (localUrl != null) {
                SecureLogger.d("GetStreamUrlUseCase") { "Using local download: $url" }
                return localUrl
            }
        } else {
            SecureLogger.d("GetStreamUrlUseCase") { "Video mode requested, skipping local check" }
        }

        // We rely on the repository's internal retry mechanism and cache for performance
        val streamUrl = repository.getStreamUrl(url, isVideo = isVideo)

        if (streamUrl != null) {
            SecureLogger.d("GetStreamUrlUseCase") { "Stream URL resolved" }
        } else {
            SecureLogger.w("GetStreamUrlUseCase", "Failed to resolve stream URL")
        }

        return streamUrl
    }
}

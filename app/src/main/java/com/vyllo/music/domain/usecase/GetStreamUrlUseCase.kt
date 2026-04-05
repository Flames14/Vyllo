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

        var streamUrl = repository.getStreamUrl(url, isVideo = isVideo)

        if (streamUrl != null) {
            SecureLogger.d("GetStreamUrlUseCase") { "Stream URL resolved" }
            return streamUrl
        }

        SecureLogger.d("GetStreamUrlUseCase", "First attempt failed, retrying with force=true")
        delay(300)
        streamUrl = repository.getStreamUrl(url, force = true, isVideo = isVideo)

        if (streamUrl != null) {
            SecureLogger.d("GetStreamUrlUseCase") { "Stream URL resolved (retry)" }
        } else {
            SecureLogger.w("GetStreamUrlUseCase", "Failed to resolve stream URL after retry")
        }

        return streamUrl
    }
}

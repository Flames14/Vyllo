package com.vyllo.music.domain.usecase

import com.vyllo.music.data.IMusicRepository
import kotlinx.coroutines.delay
import javax.inject.Inject

class GetStreamUrlUseCase @Inject constructor(
    private val repository: IMusicRepository
) {
    suspend operator fun invoke(url: String, isVideo: Boolean = false): String? {
        // Step 0: Check local download first (ONLY for audio mode)
        // For video mode, always fetch from network since downloads are audio-only
        if (!isVideo) {
            val localUrl = repository.getLocalStreamUrl(url)
            if (localUrl != null) {
                android.util.Log.d("GetStreamUrlUseCase", "Using local download: $url")
                return localUrl
            }
        } else {
            android.util.Log.d("GetStreamUrlUseCase", "Video mode requested, skipping local download check: $url")
        }

        // Step 1: Try standard resolution from network
        var streamUrl = repository.getStreamUrl(url, isVideo = isVideo)

        if (streamUrl != null) {
            android.util.Log.d("GetStreamUrlUseCase", "Stream URL resolved: ${streamUrl.take(50)}...")
            return streamUrl
        }

        // Step 2: Retry with force=true if first attempt failed
        android.util.Log.d("GetStreamUrlUseCase", "First attempt failed, retrying with force=true")
        delay(300)
        streamUrl = repository.getStreamUrl(url, force = true, isVideo = isVideo)
        
        if (streamUrl != null) {
            android.util.Log.d("GetStreamUrlUseCase", "Stream URL resolved (retry): ${streamUrl.take(50)}...")
        } else {
            android.util.Log.w("GetStreamUrlUseCase", "Failed to resolve stream URL after retry")
        }

        return streamUrl
    }
}

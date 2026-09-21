package com.vyllo.music.domain.manager.player

import com.vyllo.music.PlayerUiState
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.data.IMusicRepository
import com.vyllo.music.data.network.YouTubeThumbnailResolver
import com.vyllo.music.domain.manager.StreamUrlCache
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.usecase.GetStreamUrlUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns stream resolution / video-mode toggle / metric formatting /
 * video-stats / thumbnail logic extracted from PlayerViewModel.
 * No behavior change — verbatim logic moved here.
 */
class StreamResolver(
    private val repository: IMusicRepository,
    private val getStreamUrlUseCase: GetStreamUrlUseCase,
    private val streamUrlCache: StreamUrlCache,
    private val thumbnailResolver: YouTubeThumbnailResolver,
    private val getState: () -> PlayerUiState,
    private val updateState: (((PlayerUiState) -> PlayerUiState)) -> Unit,
    private val scopeProvider: () -> CoroutineScope
) {
    fun formatMetricCount(count: Long): String {
        return when {
            count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", count / 1_000_000.0).replace(".0M", "M")
            count >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", count / 1_000.0).replace(".0K", "K")
            count > 0 -> count.toString()
            else -> ""
        }
    }

    fun loadVideoStats(item: MusicItem) {
        scopeProvider().launch {
            try {
                val stats = repository.getVideoStats(item.url)
                if (isActive && getState().currentPlayingItem?.url == item.url && stats != null) {
                    val formattedLikes = if (stats.likeCount > 0) formatMetricCount(stats.likeCount) else null
                    val formattedComments = if (stats.commentCount >= 0) formatMetricCount(stats.commentCount) else null
                    updateState {
                        it.copy(
                            likeCount = stats.likeCount,
                            commentCount = stats.commentCount,
                            likeCountFormatted = formattedLikes,
                            commentCountFormatted = formattedComments
                        )
                    }
                }
            } catch (e: Exception) {
                SecureLogger.w("PlayerViewModel", "Failed to load video stats", e)
            }
        }
    }

    fun resolveThumbnail(item: MusicItem) {
        scopeProvider().launch {
            try {
                val highRes = thumbnailResolver.resolveHighResThumbnail(
                    urlOrId = item.url,
                    fallbackThumbnail = item.thumbnailUrl,
                    title = item.title,
                    artist = item.uploader
                )
                if (isActive && getState().currentPlayingItem?.url == item.url) {
                    updateState { it.copy(resolvedThumbnailUrl = highRes) }
                }
            } catch (e: Exception) {
                SecureLogger.w("PlayerViewModel", "Error resolving thumbnail", e)
            }
        }
    }

    suspend fun resolveStream(item: MusicItem, isVideo: Boolean = false): String? {
        streamUrlCache.get(item.url, isVideo)?.let {
            SecureLogger.d("PlayerViewModel") { "Stream URL cache hit: ${item.url}_$isVideo" }
            return it
        }

        updateState { it.copy(loadingItemUrl = item.url, isLoadingPlayer = true) }
        SecureLogger.d("PlayerViewModel") { "Resolving stream: url=${item.url}, isVideo=$isVideo" }
        val url = getStreamUrlUseCase(item.url, isVideo = isVideo)
        updateState { it.copy(loadingItemUrl = null, isLoadingPlayer = false) }

        if (url != null) {
            streamUrlCache.put(item.url, isVideo, url)
            SecureLogger.d("PlayerViewModel") { "Stream URL resolved" }
        } else {
            SecureLogger.w("PlayerViewModel", "Failed to resolve stream URL")
        }
        return url
    }

    fun toggleVideoMode(
        currentItem: MusicItem?,
        isVideoMode: Boolean,
        currentPosition: Long,
        setVideoMode: (Boolean) -> Unit,
        onSwitch: (String?) -> Unit
    ) {
        val item = currentItem ?: return
        val targetVideoMode = !isVideoMode
        SecureLogger.d("PlayerViewModel") { "toggleVideoMode: current=$isVideoMode, target=$targetVideoMode, position=$currentPosition" }

        setVideoMode(targetVideoMode)

        scopeProvider().launch {
            val newUrl = resolveStream(item, isVideo = getState().isVideoMode)
            if (isActive) {
                SecureLogger.d("PlayerViewModel") { "toggleVideoMode callback: newUrl resolved" }
                onSwitch(newUrl)
            }
        }
    }
}

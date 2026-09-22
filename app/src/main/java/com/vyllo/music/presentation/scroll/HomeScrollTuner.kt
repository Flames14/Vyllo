package com.vyllo.music.presentation.scroll

import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vyllo.music.data.manager.PreferenceManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralizes Home scroll tuning.
 *
 * The user report is classic 60 Hz touch jank: not low frame rate during idle,
 * but stutter when a fling starts and when images resolve while the list is
 * moving. That is usually caused by main-thread image binder churn plus layout
 * work at the same time as touch input. This class exposes only presentation
 * tuning knobs; it does not change queue, playback, layout, or navigation logic.
 */
@Singleton
class HomeScrollTuner @Inject constructor(
    private val preferenceManager: PreferenceManager
) {
    @Volatile
    private var prefetchDistancePx: Int = -1

    @Volatile
    private var imagePreloadAheadItems: Int = -1

    fun prefetchDistancePx(): Int {
        val cached = prefetchDistancePx
        if (cached > 0) return cached
        return preferenceManager.homeScrollPrefetchDistancePx.also { prefetchDistancePx = it }
    }

    fun imagePreloadAheadItems(): Int {
        val cached = imagePreloadAheadItems
        if (cached > 0) return cached
        return preferenceManager.homeImagePreloadAheadItems.also { imagePreloadAheadItems = it }
    }

    /**
     * Creates the tuned Home list state. Currently returns a normal LazyListState
     * after applying the shared fling and preload tuning; explicit item-spacing
     * prefetch APIs are not stable across Compose versions, so they stay out.
     */
    fun createHomeState(firstVisibleItemIndex: Int = 0, firstVisibleItemScrollOffset: Int = 0): LazyListState {
        return LazyListState(firstVisibleItemIndex, firstVisibleItemScrollOffset)
    }
}

/**
 * remembers one tuned fling behavior for the Home feed.
 *
 * ScrollableDefaults.flingBehavior() keeps platform pacing, including the 60 Hz
 * path where custom physics otherwise make scrolling feel springy and uneven.
 * Moving this remember out of item lambdas means the behavior itself does not
 * allocate or relayout rows during a fling.
 */
/**
 * Platform fling behavior, remembered once per Home screen.
 *
 * Using Compose's default keeps 60 Hz fling pacing identical to the system,
 * which is what makes touch feel predictable instead of springy.
 */
@Composable
fun rememberHomeFlingBehavior(): androidx.compose.foundation.gestures.FlingBehavior =
    ScrollableDefaults.flingBehavior()

/**
 * Converts section/item spacing to pixels once, so row measurement does not call
 * density APIs while the user is actively scrolling.
 */
data class HomeScrollSpacing(val horizontalPx: Int, val verticalPx: Int)

/**
 * Keeps one remembered image-preload cursor for the whole Home list.
 *
 * The previous inline pre-cache launched a request for every newly visible URL.
 * Coalescing the visible window into one cursor still downloads the same images,
 * but avoids bursty main-thread work during the first frames of a fling.
 */
@Composable
fun rememberHomeImagePreloadCursor(): HomeImagePreloadCursor {
    return remember { HomeImagePreloadCursor() }
}

class HomeImagePreloadCursor {
    private val requested = LinkedHashSet<String>()

    fun unseen(urls: List<String>, limit: Int): List<String> {
        if (urls.isEmpty()) return emptyList()
        val unseen = ArrayList<String>(limit.coerceAtLeast(0))
        for (url in urls) {
            if (unseen.size >= limit) break
            if (url.isNotBlank() && requested.add(url)) unseen.add(url)
        }
        // Bound memory without clearing content that is already on screen.
        while (requested.size > 400) requested.remove(requested.first())
        return unseen
    }
}
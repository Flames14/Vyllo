package com.vyllo.music.domain.manager

import com.vyllo.music.domain.model.MusicItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackQueueManager @Inject constructor() {
    
    private val lock = Any()
    
    var currentQueue: MutableList<MusicItem> = Collections.synchronizedList(mutableListOf())
    
    @Volatile
    var currentIndex: Int = -1
        set(value) {
            field = value
            _currentPlayingItem.value = currentItem
        }

    private val _currentPlayingItem = MutableStateFlow<MusicItem?>(null)
    val currentPlayingItem: StateFlow<MusicItem?> = _currentPlayingItem.asStateFlow()

    private val _queueVersion = MutableStateFlow(0L)
    val queueVersion: StateFlow<Long> = _queueVersion.asStateFlow()

    @Volatile
    var isShuffleEnabled: Boolean = false
        private set

    /** Snapshot of queue order before shuffle, restored when shuffle is turned off. */
    private var preShuffleQueue: List<MusicItem>? = null

    val currentItem: MusicItem?
        get() = synchronized(lock) {
            if (currentIndex in currentQueue.indices) {
                currentQueue[currentIndex]
            } else {
                null
            }
        }
    
    fun addItem(item: MusicItem) {
        synchronized(lock) {
            currentQueue.add(item)
            preShuffleQueue = preShuffleQueue?.plus(item)
            notifyQueueStructureChangedLocked()
        }
    }
    
    fun addItemAt(index: Int, item: MusicItem) {
        synchronized(lock) {
            val safeIndex = index.coerceIn(0, currentQueue.size)
            currentQueue.add(safeIndex, item)
            preShuffleQueue = preShuffleQueue?.plus(item)
            notifyQueueStructureChangedLocked()
        }
    }

    val size: Int
        get() = currentQueue.size

    fun indexOf(item: MusicItem): Int {
        return synchronized(lock) {
            currentQueue.indexOfFirst { it.url == item.url }
        }
    }

    val nextItem: MusicItem?
        get() = synchronized(lock) {
            val nextIndex = currentIndex + 1
            if (nextIndex in currentQueue.indices) {
                currentQueue[nextIndex]
            } else {
                null
            }
        }

    fun setCurrentIndexSafe(index: Int) {
        synchronized(lock) {
            val safeIndex = index.coerceIn(-1, currentQueue.size - 1)
            if (currentIndex != safeIndex) {
                currentIndex = safeIndex
            } else {
                _currentPlayingItem.value = currentItem
            }
        }
    }

    fun getQueueSnapshot(): List<MusicItem> {
        return synchronized(lock) {
            currentQueue.toList().distinctBy { it.url }
        }
    }

    fun getUpcomingSnapshot(): List<MusicItem> {
        return synchronized(lock) {
            val nextIndex = currentIndex + 1
            if (nextIndex in currentQueue.indices) {
                currentQueue.subList(nextIndex, currentQueue.size).toList().distinctBy { it.url }
            } else {
                emptyList()
            }
        }
    }

    /** Recently played entries from this queue, newest first; excludes the playhead. */
    fun getPlayedSnapshot(): List<MusicItem> {
        return synchronized(lock) {
            val playedEndExclusive = currentIndex.coerceIn(0, currentQueue.size)
            if (playedEndExclusive == 0) return@synchronized emptyList()
            currentQueue.subList(0, playedEndExclusive).distinctBy { it.url }.asReversed()
        }
    }

    fun setCurrentPlayingItemDirectly(item: MusicItem) {
        synchronized(lock) {
            val idx = currentQueue.indexOfFirst { it.url == item.url }
            if (idx >= 0) {
                currentIndex = idx
            } else {
                // The item is audible but missing from the queue (e.g. local-file
                // playback). Insert it at the playhead so currentItem,
                // currentPlayingItem and getUpcomingSnapshot() stay consistent —
                // otherwise Up Next renders entries relative to a stale index.
                val insertAt = (currentIndex + 1).coerceIn(0, currentQueue.size)
                currentQueue.add(insertAt, item)
                preShuffleQueue = preShuffleQueue?.let { original ->
                    val preIdx = original.indexOfFirst { it.url == item.url }
                    if (preIdx >= 0) original else original.plus(item)
                }
                currentIndex = insertAt
                notifyQueueStructureChangedLocked()
            }
        }
    }
    
    fun replaceQueue(items: List<MusicItem>, startIndex: Int = 0) {
        synchronized(lock) {
            currentQueue.clear()
            val start = startIndex.coerceIn(-1, items.size - 1)
            if (isShuffleEnabled) {
                preShuffleQueue = items.toList()
                val current = items.getOrNull(start)
                val rest = items.filterIndexed { i, _ -> i != start }.shuffled()
                if (current != null) {
                    currentQueue.add(current)
                    currentQueue.addAll(rest)
                    currentIndex = 0
                } else {
                    currentQueue.addAll(rest)
                    currentIndex = -1
                }
            } else {
                preShuffleQueue = null
                currentQueue.addAll(items)
                currentIndex = start
            }
            notifyQueueStructureChangedLocked()
        }
    }

    fun replaceUpcomingItems(items: List<MusicItem>) {
        synchronized(lock) {
            val keepCount = (currentIndex + 1).coerceAtLeast(0)
            val kept = currentQueue.take(keepCount)
            val keptUrls = kept.map { it.url }.toSet()
            val distinctUpcoming = items.distinctBy { it.url }.filter { !keptUrls.contains(it.url) }
            currentQueue.clear()
            currentQueue.addAll(kept)
            currentQueue.addAll(distinctUpcoming)
            if (isShuffleEnabled) {
                val preKept = preShuffleQueue?.take(keepCount) ?: kept
                val preUrls = preKept.map { it.url }.toSet()
                preShuffleQueue = preKept + items.filter { !preUrls.contains(it.url) }
            }
            notifyQueueStructureChangedLocked()
        }
    }

    fun appendDistinct(items: List<MusicItem>): List<MusicItem> {
        return synchronized(lock) {
            val existingUrls = currentQueue.map { it.url }.toMutableSet()
            val added = items.filter { existingUrls.add(it.url) }
            if (added.isNotEmpty()) {
                currentQueue.addAll(added)
                if (isShuffleEnabled) {
                    preShuffleQueue = preShuffleQueue?.plus(added)
                }
                notifyQueueStructureChangedLocked()
            }
            added
        }
    }

    /**
     * Enables/disables shuffle by physically reordering the queue so that the
     * existing sequential next/prev/lookahead logic plays in shuffled order.
     * The current item stays at the playhead; original order is restored on disable.
     */
    fun setShuffleEnabled(enabled: Boolean) {
        synchronized(lock) {
            if (enabled == isShuffleEnabled) return
            isShuffleEnabled = enabled
            if (enabled) {
                preShuffleQueue = currentQueue.toList()
                val currentIdx = currentIndex
                val current = currentQueue.getOrNull(currentIdx)
                val rest = currentQueue.filterIndexed { i, _ -> i != currentIdx }.shuffled()
                currentQueue.clear()
                if (current != null) {
                    currentQueue.add(current)
                    currentQueue.addAll(rest)
                    currentIndex = 0
                } else {
                    currentQueue.addAll(rest)
                    currentIndex = currentIndex.coerceIn(-1, currentQueue.size - 1)
                }
            } else {
                val currentUrl = currentItem?.url
                preShuffleQueue?.let { original ->
                    currentQueue.clear()
                    currentQueue.addAll(original)
                    val restored = currentUrl?.let { u -> currentQueue.indexOfFirst { it.url == u } } ?: -1
                    currentIndex = if (restored >= 0) restored else currentIndex.coerceIn(-1, currentQueue.size - 1)
                }
                preShuffleQueue = null
            }
            notifyQueueStructureChangedLocked()
        }
    }

    private fun notifyQueueStructureChangedLocked() {
        _currentPlayingItem.value = currentItem
        _queueVersion.value = _queueVersion.value + 1
    }

    // -------------------------------------------------------------------------
    // User-facing queue edits (Up Next tab).
    //
    // All indices here are 0-based RELATIVE TO THE PLAYHEAD, so the UI never has
    // to know about the played prefix that sits behind the current index.
    //
    // Edits are restricted to the upcoming region: removing or moving the item
    // that is currently audible would require a jump-cut mid-track, which the
    // ExoPlayer lookahead cannot represent without desyncing.
    //
    // Any manual edit discards the pre-shuffle snapshot (preShuffleQueue = null).
    // That is deliberate: once the user starts hand-ordering the queue, turning
    // shuffle off must keep the order they chose rather than resurrecting the
    // pre-shuffle one. setShuffleEnabled(false) already handles a null snapshot
    // by leaving the current order untouched.
    // -------------------------------------------------------------------------

    /** Maps an upcoming-relative index onto an absolute queue index. */
    private fun absoluteIndexLocked(upcomingIndex: Int): Int = currentIndex + 1 + upcomingIndex

    /** Removes the upcoming track at [upcomingIndex]; returns it for undo, or null if out of range. */
    fun removeUpcomingAt(upcomingIndex: Int): MusicItem? = synchronized(lock) {
        val absolute = absoluteIndexLocked(upcomingIndex)
        if (upcomingIndex < 0 || absolute !in currentQueue.indices) return null
        val removed = currentQueue.removeAt(absolute)
        preShuffleQueue = null
        notifyQueueStructureChangedLocked()
        removed
    }

    /**
     * Re-inserts previously removed upcoming tracks at [upcomingIndex] (used by undo).
     * Order within [items] is preserved.
     */
    fun insertUpcomingAt(upcomingIndex: Int, items: List<MusicItem>): Boolean = synchronized(lock) {
        if (items.isEmpty()) return false
        val absolute = absoluteIndexLocked(upcomingIndex).coerceIn(currentIndex + 1, currentQueue.size)
        val knownUrls = currentQueue.mapTo(mutableSetOf()) { it.url }
        val fresh = items.filter { knownUrls.add(it.url) }
        if (fresh.isEmpty()) return false
        currentQueue.addAll(absolute, fresh)
        preShuffleQueue = null
        notifyQueueStructureChangedLocked()
        true
    }

    /** Moves the upcoming track from [from] to [to] (both upcoming-relative). */
    fun moveUpcoming(from: Int, to: Int): Boolean = synchronized(lock) {
        val fromAbsolute = absoluteIndexLocked(from)
        val toAbsolute = absoluteIndexLocked(to)
        if (from < 0 || to < 0) return false
        if (fromAbsolute !in currentQueue.indices || toAbsolute !in currentQueue.indices) return false
        if (from == to) return false
        val moved = currentQueue.removeAt(fromAbsolute)
        currentQueue.add(toAbsolute, moved)
        preShuffleQueue = null
        notifyQueueStructureChangedLocked()
        true
    }

    /** Moves the upcoming track at [upcomingIndex] by [delta] positions (e.g. -1 = up, +1 = down). */
    fun moveUpcomingBy(upcomingIndex: Int, delta: Int): Boolean =
        moveUpcoming(upcomingIndex, upcomingIndex + delta)

    /**
     * Removes every upcoming track, keeping the current one playing.
     * Returns the removed tracks in their original order (for undo).
     */
    fun clearUpcoming(): List<MusicItem> = synchronized(lock) {
        val firstUpcoming = currentIndex + 1
        if (firstUpcoming !in 0..currentQueue.size) return emptyList()
        val removed = currentQueue.subList(firstUpcoming, currentQueue.size).toList()
        if (removed.isEmpty()) return emptyList()
        currentQueue.subList(firstUpcoming, currentQueue.size).clear()
        preShuffleQueue = null
        notifyQueueStructureChangedLocked()
        removed
    }

    /** Absolute 0-based index of [url] within the upcoming region, or -1. */
    fun upcomingIndexOf(url: String): Int = synchronized(lock) {
        val firstUpcoming = currentIndex + 1
        if (firstUpcoming > currentQueue.size) return -1
        val offset = currentQueue.subList(firstUpcoming, currentQueue.size)
            .indexOfFirst { it.url == url }
        if (offset < 0) -1 else offset
    }
}

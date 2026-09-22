package com.vyllo.music.data.manager

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
    
    fun addAll(items: List<MusicItem>) {
        synchronized(lock) {
            currentQueue.addAll(items)
            preShuffleQueue = preShuffleQueue?.plus(items)
            notifyQueueStructureChangedLocked()
        }
    }
    
    fun removeItem(item: MusicItem): Boolean {
        return synchronized(lock) {
            val removed = currentQueue.remove(item)
            if (removed && currentIndex >= currentQueue.size) {
                currentIndex = (currentQueue.size - 1).coerceAtLeast(0)
            }
            if (removed) {
                notifyQueueStructureChangedLocked()
            }
            removed
        }
    }
    
    fun removeItemAt(index: Int): MusicItem? {
        return synchronized(lock) {
            if (index in currentQueue.indices) {
                val removed = currentQueue.removeAt(index)
                if (index < currentIndex) {
                    currentIndex--
                } else if (index == currentIndex) {
                    currentIndex = (currentQueue.size - 1).coerceAtLeast(-1)
                }
                notifyQueueStructureChangedLocked()
                removed
            } else {
                null
            }
        }
    }
    
    fun clear() {
        synchronized(lock) {
            currentQueue.clear()
            currentIndex = -1
            notifyQueueStructureChangedLocked()
        }
    }
    
    val size: Int
        get() = currentQueue.size
    
    fun getItemAt(index: Int): MusicItem? {
        return synchronized(lock) {
            if (index in currentQueue.indices) {
                currentQueue[index]
            } else {
                null
            }
        }
    }
    
    val isEmpty: Boolean
        get() = currentQueue.isEmpty()
    
    val nextItem: MusicItem?
        get() = synchronized(lock) {
            val nextIndex = currentIndex + 1
            if (nextIndex in currentQueue.indices) {
                currentQueue[nextIndex]
            } else {
                null
            }
        }
    
    val previousItem: MusicItem?
        get() = synchronized(lock) {
            val prevIndex = currentIndex - 1
            if (prevIndex in currentQueue.indices) {
                currentQueue[prevIndex]
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
    
    fun indexOf(item: MusicItem): Int {
        return synchronized(lock) {
            currentQueue.indexOfFirst { it.url == item.url }
        }
    }
    
    fun contains(item: MusicItem): Boolean {
        return synchronized(lock) {
            currentQueue.contains(item)
        }
    }
    
    fun getQueueSnapshot(): List<MusicItem> {
        return synchronized(lock) {
            currentQueue.toList()
        }
    }

    fun getUpcomingSnapshot(): List<MusicItem> {
        return synchronized(lock) {
            val nextIndex = currentIndex + 1
            if (nextIndex in currentQueue.indices) {
                currentQueue.subList(nextIndex, currentQueue.size).toList()
            } else {
                emptyList()
            }
        }
    }

    fun setCurrentPlayingItemDirectly(item: MusicItem) {
        synchronized(lock) {
            val idx = currentQueue.indexOfFirst { it.url == item.url }
            if (idx >= 0) {
                currentIndex = idx
            } else {
                _currentPlayingItem.value = item
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
            val distinctUpcoming = items.filter { !keptUrls.contains(it.url) }
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
}

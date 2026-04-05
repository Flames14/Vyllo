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
            notifyQueueStructureChangedLocked()
        }
    }
    
    fun addItemAt(index: Int, item: MusicItem) {
        synchronized(lock) {
            val safeIndex = index.coerceIn(0, currentQueue.size)
            currentQueue.add(safeIndex, item)
            notifyQueueStructureChangedLocked()
        }
    }
    
    fun addAll(items: List<MusicItem>) {
        synchronized(lock) {
            currentQueue.addAll(items)
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
            currentQueue.indexOf(item)
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
    
    fun replaceQueue(items: List<MusicItem>, startIndex: Int = 0) {
        synchronized(lock) {
            currentQueue.clear()
            currentQueue.addAll(items)
            currentIndex = startIndex.coerceIn(-1, items.size - 1)
            notifyQueueStructureChangedLocked()
        }
    }

    fun replaceUpcomingItems(items: List<MusicItem>) {
        synchronized(lock) {
            val keepCount = (currentIndex + 1).coerceAtLeast(0)
            val kept = currentQueue.take(keepCount)
            currentQueue.clear()
            currentQueue.addAll(kept)
            currentQueue.addAll(items)
            notifyQueueStructureChangedLocked()
        }
    }

    fun appendDistinct(items: List<MusicItem>): List<MusicItem> {
        return synchronized(lock) {
            val existingUrls = currentQueue.map { it.url }.toMutableSet()
            val added = items.filter { existingUrls.add(it.url) }
            if (added.isNotEmpty()) {
                currentQueue.addAll(added)
                notifyQueueStructureChangedLocked()
            }
            added
        }
    }

    private fun notifyQueueStructureChangedLocked() {
        _currentPlayingItem.value = currentItem
        _queueVersion.value = _queueVersion.value + 1
    }
}

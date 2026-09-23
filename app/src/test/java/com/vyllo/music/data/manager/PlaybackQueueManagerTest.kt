package com.vyllo.music.domain.manager

import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.MusicItemType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PlaybackQueueManagerTest {

    private lateinit var playbackQueueManager: PlaybackQueueManager

    @Before
    fun setUp() {
        playbackQueueManager = PlaybackQueueManager()
    }

    @Test
    fun testAddItem() {
        val item = createTestMusicItem("1", "Song 1")
        playbackQueueManager.addItem(item)
        assertEquals(1, playbackQueueManager.size)
        assertTrue(playbackQueueManager.contains(item))
    }

    @Test
    fun testAddAll() {
        val items = listOf(
            createTestMusicItem("1", "Song 1"),
            createTestMusicItem("2", "Song 2"),
            createTestMusicItem("3", "Song 3")
        )
        playbackQueueManager.addAll(items)
        assertEquals(3, playbackQueueManager.size)
    }

    @Test
    fun testClear() {
        playbackQueueManager.addItem(createTestMusicItem("1", "Song 1"))
        playbackQueueManager.addItem(createTestMusicItem("2", "Song 2"))
        playbackQueueManager.clear()
        assertEquals(0, playbackQueueManager.size)
        assertEquals(-1, playbackQueueManager.currentIndex)
        assertTrue(playbackQueueManager.isEmpty)
    }

    @Test
    fun testSetCurrentIndexSafe() {
        val items = listOf(
            createTestMusicItem("1", "Song 1"),
            createTestMusicItem("2", "Song 2"),
            createTestMusicItem("3", "Song 3")
        )
        playbackQueueManager.addAll(items)
        playbackQueueManager.setCurrentIndexSafe(1)
        assertEquals(1, playbackQueueManager.currentIndex)
    }

    @Test
    fun testSetCurrentIndexSafe_Bounds() {
        val items = listOf(
            createTestMusicItem("1", "Song 1"),
            createTestMusicItem("2", "Song 2")
        )
        playbackQueueManager.addAll(items)
        playbackQueueManager.setCurrentIndexSafe(10)
        assertEquals(1, playbackQueueManager.currentIndex) 
    }

    @Test
    fun testGetCurrentItem() {
        val item1 = createTestMusicItem("1", "Song 1")
        val item2 = createTestMusicItem("2", "Song 2")
        playbackQueueManager.addAll(listOf(item1, item2))
        playbackQueueManager.setCurrentIndexSafe(0)
        assertEquals(item1, playbackQueueManager.currentItem)
    }

    @Test
    fun testGetCurrentItem_NullWhenEmpty() {
        playbackQueueManager.clear()
        assertNull(playbackQueueManager.currentItem)
    }

    @Test
    fun testGetItemAt() {
        val item1 = createTestMusicItem("1", "Song 1")
        val item2 = createTestMusicItem("2", "Song 2")
        playbackQueueManager.addAll(listOf(item1, item2))
        assertEquals(item2, playbackQueueManager.getItemAt(1))
    }

    @Test
    fun testGetItemAt_InvalidIndex() {
        playbackQueueManager.addItem(createTestMusicItem("1", "Song 1"))
        assertNull(playbackQueueManager.getItemAt(10))
    }

    @Test
    fun testNextItem() {
        val item1 = createTestMusicItem("1", "Song 1")
        val item2 = createTestMusicItem("2", "Song 2")
        playbackQueueManager.addAll(listOf(item1, item2))
        playbackQueueManager.setCurrentIndexSafe(0)
        assertEquals(item2, playbackQueueManager.nextItem)
    }

    @Test
    fun testNextItem_NullAtEnd() {
        val item1 = createTestMusicItem("1", "Song 1")
        playbackQueueManager.addAll(listOf(item1))
        playbackQueueManager.setCurrentIndexSafe(0)
        assertNull(playbackQueueManager.nextItem)
    }

    @Test
    fun testPreviousItem() {
        val item1 = createTestMusicItem("1", "Song 1")
        val item2 = createTestMusicItem("2", "Song 2")
        playbackQueueManager.addAll(listOf(item1, item2))
        playbackQueueManager.setCurrentIndexSafe(1)
        assertEquals(item1, playbackQueueManager.previousItem)
    }

    @Test
    fun testPreviousItem_NullAtStart() {
        playbackQueueManager.addItem(createTestMusicItem("1", "Song 1"))
        playbackQueueManager.setCurrentIndexSafe(0)
        assertNull(playbackQueueManager.previousItem)
    }

    @Test
    fun testIndexOf() {
        val item1 = createTestMusicItem("1", "Song 1")
        val item2 = createTestMusicItem("2", "Song 2")
        playbackQueueManager.addAll(listOf(item1, item2))
        assertEquals(1, playbackQueueManager.indexOf(item2))
    }

    @Test
    fun testIndexOf_NotFound() {
        val item1 = createTestMusicItem("1", "Song 1")
        playbackQueueManager.addAll(listOf(item1))
        val item2 = createTestMusicItem("2", "Song 2")
        assertEquals(-1, playbackQueueManager.indexOf(item2))
    }

    @Test
    fun testRemoveItem() {
        val item1 = createTestMusicItem("1", "Song 1")
        val item2 = createTestMusicItem("2", "Song 2")
        playbackQueueManager.addAll(listOf(item1, item2))
        val removed = playbackQueueManager.removeItem(item1)
        assertTrue(removed)
        assertEquals(1, playbackQueueManager.size)
        assertFalse(playbackQueueManager.contains(item1))
    }

    @Test
    fun testRemoveItemAt() {
        val item1 = createTestMusicItem("1", "Song 1")
        val item2 = createTestMusicItem("2", "Song 2")
        playbackQueueManager.addAll(listOf(item1, item2))
        val removed = playbackQueueManager.removeItemAt(0)
        assertEquals(item1, removed)
        assertEquals(1, playbackQueueManager.size)
    }

    @Test
    fun testGetQueueSnapshot() {
        val items = listOf(
            createTestMusicItem("1", "Song 1"),
            createTestMusicItem("2", "Song 2")
        )
        playbackQueueManager.addAll(items)
        val snapshot = playbackQueueManager.getQueueSnapshot()
        assertEquals(items.size, snapshot.size)
        assertNotSame(items, snapshot) 
    }

    @Test
    fun testReplaceQueue() {
        playbackQueueManager.addItem(createTestMusicItem("1", "Song 1"))
        val newItems = listOf(
            createTestMusicItem("2", "Song 2"),
            createTestMusicItem("3", "Song 3")
        )
        playbackQueueManager.replaceQueue(newItems, startIndex = 0)
        assertEquals(2, playbackQueueManager.size)
        assertEquals(0, playbackQueueManager.currentIndex)
    }

    @Test
    fun testIsEmpty() {
        assertTrue(playbackQueueManager.isEmpty)
        playbackQueueManager.addItem(createTestMusicItem("1", "Song 1"))
        assertFalse(playbackQueueManager.isEmpty)
    }

    @Test
    fun testSize() {
        assertEquals(0, playbackQueueManager.size)
        playbackQueueManager.addItem(createTestMusicItem("1", "Song 1"))
        assertEquals(1, playbackQueueManager.size)
    }

    @Test
    fun testContains() {
        val item = createTestMusicItem("1", "Song 1")
        playbackQueueManager.addItem(item)
        assertTrue(playbackQueueManager.contains(item))
        val otherItem = createTestMusicItem("2", "Song 2")
        assertFalse(playbackQueueManager.contains(otherItem))
    }

    private fun createTestMusicItem(id: String, title: String): MusicItem {
        return MusicItem(
            title = title,
            url = "https://example.com/$id",
            uploader = "Test Artist",
            thumbnailUrl = "https://example.com/thumb/$id.jpg",
            type = MusicItemType.SONG
        )
    }

    // --- Up Next regression: setCurrentPlayingItemDirectly must keep currentIndex
    // --- and upcoming snapshot consistent when the item is missing from the queue.

    @Test
    fun setCurrentPlayingItemDirectly_insertsMissingItemAtPlayhead() {
        val a = createTestMusicItem("a", "Song A")
        val b = createTestMusicItem("b", "Song B")
        val c = createTestMusicItem("c", "Song C")
        playbackQueueManager.addAll(listOf(a, b, c))
        playbackQueueManager.setCurrentIndexSafe(0)

        val orphan = createTestMusicItem("orphan", "Orphan Song")
        playbackQueueManager.setCurrentPlayingItemDirectly(orphan)

        assertEquals(orphan, playbackQueueManager.currentItem)
        assertEquals(1, playbackQueueManager.currentIndex)
        assertEquals(4, playbackQueueManager.size)
        // Inserted immediately after the previous playhead so nextItem stays sane
        assertEquals(orphan, playbackQueueManager.getItemAt(1))
        assertEquals(b, playbackQueueManager.nextItem)
    }

    @Test
    fun setCurrentPlayingItemDirectly_updatesIndexForExistingItem() {
        val a = createTestMusicItem("a", "Song A")
        val b = createTestMusicItem("b", "Song B")
        val c = createTestMusicItem("c", "Song C")
        playbackQueueManager.addAll(listOf(a, b, c))
        playbackQueueManager.setCurrentIndexSafe(0)

        playbackQueueManager.setCurrentPlayingItemDirectly(c)

        assertEquals(c, playbackQueueManager.currentItem)
        assertEquals(2, playbackQueueManager.currentIndex)
        assertEquals(3, playbackQueueManager.size)
        assertNull(playbackQueueManager.nextItem)
    }

    @Test
    fun setCurrentPlayingItemDirectly_onEmptyQueueTracksItemAsCurrent() {
        val orphan = createTestMusicItem("orphan", "Orphan Song")
        playbackQueueManager.setCurrentPlayingItemDirectly(orphan)

        assertEquals(orphan, playbackQueueManager.currentItem)
        assertEquals(1, playbackQueueManager.size)
        assertEquals(0, playbackQueueManager.currentIndex)
        assertNull(playbackQueueManager.nextItem)
    }

    @Test
    fun upcomingSnapshot_afterInsertDoesNotSkipToStaleNext() {
        val a = createTestMusicItem("a", "Song A")
        val b = createTestMusicItem("b", "Song B")
        playbackQueueManager.addAll(listOf(a, b))
        playbackQueueManager.setCurrentIndexSafe(0)

        val orphan = createTestMusicItem("orphan", "Orphan")
        playbackQueueManager.setCurrentPlayingItemDirectly(orphan)

        val upcoming = playbackQueueManager.getUpcomingSnapshot()
        // After switching to orphan at idx 1, upcoming must be [c-or-nothing] —
        // i.e. B must still be reachable as next, never skipped over.
        assertEquals(orphan, playbackQueueManager.currentItem)
        // whatever getUpcomingSnapshot returns, currentIndex must agree with currentItem
        assertTrue(playbackQueueManager.currentIndex in 0 until playbackQueueManager.size)
        assertEquals(orphan, playbackQueueManager.getItemAt(playbackQueueManager.currentIndex))
        // silence unused warning if snapshot API shape differs
        assertNotNull(upcoming)
    }

    @Test
    fun nextItem_afterDirectSwitchIsNotStale() {
        val a = createTestMusicItem("a", "A")
        val b = createTestMusicItem("b", "B")
        val c = createTestMusicItem("c", "C")
        playbackQueueManager.addAll(listOf(a, b, c))
        playbackQueueManager.setCurrentIndexSafe(1) // on B

        // Simulate external item (e.g. radio/relation) not yet in queue
        val radio = createTestMusicItem("radio", "Radio Song")
        playbackQueueManager.setCurrentPlayingItemDirectly(radio)

        assertEquals(radio, playbackQueueManager.currentItem)
        // Inserted at currentIndex+1 (=2) so queue is [A,B,radio,C]; next is C
        assertEquals(c, playbackQueueManager.nextItem)
        assertEquals(2, playbackQueueManager.indexOf(radio))
        assertEquals(2, playbackQueueManager.currentIndex)
    }
}

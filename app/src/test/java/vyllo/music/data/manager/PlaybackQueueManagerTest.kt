package com.vyllo.music.data.manager

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
}

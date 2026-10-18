package com.vyllo.music.ui.home

import com.vyllo.music.HomeUiState
import com.vyllo.music.data.manager.HomeFeedSnapshot
import com.vyllo.music.domain.model.MusicItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the home feed's row layout and cache-snapshot contract.
 *
 * These rules were previously inline in HomeViewModel and untested, so a change
 * to a chunk size or a section key could silently corrupt the persisted cache.
 */
class HomeFeedShaperTest {

    private fun items(count: Int): List<MusicItem> =
        (1..count).map { MusicItem(title = "Song $it", url = "https://youtu.be/$it") }

    @Test
    fun `inline trending is capped at eight`() {
        assertEquals(8, HomeFeedShaper.inlineTrending(items(20)).size)
    }

    @Test
    fun `extra trending rows are rows of four capped at sixteen`() {
        val rows = HomeFeedShaper.extraTrendingRows(items(30))
        assertEquals(4, rows.size)
        assertTrue(rows.all { it.size == 4 })
    }

    @Test
    fun `quick picks grid is four rows of four`() {
        val rows = HomeFeedShaper.quickPicksGrid(items(30))
        assertEquals(4, rows.size)
        assertTrue(rows.all { it.size == 4 })
    }

    @Test
    fun `mixed for you takes eight after the grid`() {
        val result = HomeFeedShaper.mixedForYou(items(30))
        assertEquals(8, result.size)
        assertEquals("Song 17", result.first().title)
    }

    @Test
    fun `more quick picks takes eight after grid and mixed row`() {
        val result = HomeFeedShaper.moreQuickPicks(items(40))
        assertEquals(8, result.size)
        assertEquals("Song 25", result.first().title)
    }

    @Test
    fun `remaining quick picks keeps everything after grid and mixed row`() {
        assertEquals(16, HomeFeedShaper.remainingQuickPicks(items(40)).size)
    }

    @Test
    fun `snapshot is null when no section has been seen`() {
        assertNull(HomeFeedShaper.buildSnapshot(HomeUiState(), emptySet()))
    }

    @Test
    fun `snapshot only serialises seen sections`() {
        val state = HomeUiState(
            trendingNowItems = items(2),
            quickPicksRows = listOf(items(2)),
            chillItems = items(5)
        )
        val snapshot = HomeFeedShaper.buildSnapshot(
            state,
            setOf(HomeFeedSnapshot.KEY_TRENDING, HomeFeedSnapshot.KEY_QUICK_PICKS)
        )
        assertTrue(snapshot!!.section(HomeFeedSnapshot.KEY_TRENDING).isNotEmpty())
        assertTrue(snapshot.section(HomeFeedSnapshot.KEY_QUICK_PICKS).isNotEmpty())
        assertTrue(snapshot.section(HomeFeedSnapshot.KEY_CHILL).isEmpty())
    }

    @Test
    fun `apply snapshot restores rows and stops the loader`() {
        val snapshot = HomeFeedSnapshot(
            rows = mapOf(
                HomeFeedSnapshot.KEY_TRENDING to items(10),
                HomeFeedSnapshot.KEY_CHILL to items(3)
            ),
            cachedAtMs = 0L
        )
        val restored = HomeFeedShaper.applySnapshot(HomeUiState(), snapshot)
        assertEquals(8, restored.trendingNowItems.size)
        assertEquals(3, restored.chillItems.size)
        assertFalse(restored.isLoading)
    }

    @Test
    fun `snapshot seen keys only mark non-empty sections`() {
        val snapshot = HomeFeedSnapshot(
            rows = mapOf(HomeFeedSnapshot.KEY_TRENDING to items(3)),
            cachedAtMs = 0L
        )
        val seen = HomeFeedShaper.snapshotSeenKeys(snapshot)
        assertEquals(setOf(HomeFeedSnapshot.KEY_TRENDING), seen)
    }

    @Test
    fun `home title flips once there is history`() {
        assertEquals("Recommended For You", HomeFeedShaper.homeTitle(hasNoHistory = true))
        assertEquals("Trending Now", HomeFeedShaper.homeTitle(hasNoHistory = false))
    }
}

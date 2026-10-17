package com.vyllo.music.data.download

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vyllo.music.domain.model.PlaylistEntity
import com.vyllo.music.domain.model.PlaylistSongEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression: playlist songs were returned newest-first (imports appeared
 * reversed after restart) and were truncated to 500 rows.
 */
@RunWith(AndroidJUnit4::class)
class PlaylistDaoOrderingTest {

    private lateinit var database: DownloadDatabase
    private lateinit var dao: PlaylistDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, DownloadDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.playlistDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun song(playlistId: Long, index: Int) = PlaylistSongEntity(
        playlistId = playlistId,
        url = "https://music.youtube.com/watch?v=song$index",
        title = "Track $index",
        uploader = "Artist",
        thumbnailUrl = "https://img.youtube.com/song$index.jpg",
        addedAt = 1_700_000_000_000L + index
    )

    @Test
    fun songsAreReturnedInInsertionOrder() = runBlocking {
        val playlistId = dao.insertPlaylist(PlaylistEntity(name = "Ordered"))
        dao.insertSongsToPlaylist((0 until 10).map { song(playlistId, it) })

        val loaded = dao.getSongsByPlaylistList(playlistId)

        assertEquals((0 until 10).map { "Track $it" }, loaded.map { it.title })
    }

    @Test
    fun playlistsLargerThanFiveHundredAreNotTruncated() = runBlocking {
        val playlistId = dao.insertPlaylist(PlaylistEntity(name = "Big"))
        dao.insertSongsToPlaylist((0 until 600).map { song(playlistId, it) })

        val loaded = dao.getSongsByPlaylistList(playlistId)

        assertEquals(600, loaded.size)
        assertEquals("Track 0", loaded.first().title)
        assertEquals("Track 599", loaded.last().title)
    }
}

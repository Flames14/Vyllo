package com.vyllo.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmartPlaylistTest {

    @Test
    fun `maps stored keys to entries`() {
        assertEquals(SmartPlaylist.LIKED, SmartPlaylist.from("liked"))
        assertEquals(SmartPlaylist.OFFLINE, SmartPlaylist.from("offline"))
        assertEquals(SmartPlaylist.TOP, SmartPlaylist.from("top"))
    }

    @Test
    fun `rejects unknown and missing keys`() {
        assertNull(SmartPlaylist.from("uploads"))
        assertNull(SmartPlaylist.from(null))
        assertNull(SmartPlaylist.from(""))
    }

    @Test
    fun `keys are unique`() {
        assertEquals(SmartPlaylist.entries.size, SmartPlaylist.entries.map { it.key }.distinct().size)
    }
}

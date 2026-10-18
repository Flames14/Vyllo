package com.vyllo.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VylloDeepLinksTest {

    @Test
    fun `search link maps to the search route`() {
        assertEquals(
            VylloRoutes.SEARCH,
            VylloDeepLinks.routeFor(VylloDeepLinks.SCHEME, VylloDeepLinks.HOST_OPEN, "search")
        )
    }

    @Test
    fun `library link maps to the library route`() {
        assertEquals(
            VylloRoutes.LIBRARY,
            VylloDeepLinks.routeFor(VylloDeepLinks.SCHEME, VylloDeepLinks.HOST_OPEN, "library")
        )
    }

    @Test
    fun `recognition link maps to the recognition route`() {
        assertEquals(
            VylloRoutes.RECOGNITION,
            VylloDeepLinks.routeFor(VylloDeepLinks.SCHEME, VylloDeepLinks.HOST_OPEN, "recognition")
        )
    }

    @Test
    fun `unknown destination is ignored`() {
        assertNull(
            VylloDeepLinks.routeFor(VylloDeepLinks.SCHEME, VylloDeepLinks.HOST_OPEN, "settings")
        )
        assertNull(
            VylloDeepLinks.routeFor(VylloDeepLinks.SCHEME, VylloDeepLinks.HOST_OPEN, null)
        )
    }

    @Test
    fun `foreign scheme or host is ignored`() {
        assertNull(VylloDeepLinks.routeFor("https", VylloDeepLinks.HOST_OPEN, "search"))
        assertNull(VylloDeepLinks.routeFor(VylloDeepLinks.SCHEME, "oauth2redirect", "search"))
        assertNull(VylloDeepLinks.routeFor(null, null, "search"))
    }

    @Test
    fun `watch links accept youtube host variants and extra query parameters`() {
        val id = "dQw4w9WgXcQ"
        listOf(
            "https://www.youtube.com/watch?v=$id",
            "https://m.youtube.com/watch?feature=share&v=$id",
            "https://music.youtube.com/watch?v=$id&si=abc",
            "https://youtube.com/watch?v=$id"
        ).forEach { assertEquals(id, VylloDeepLinks.videoIdForLink(it)) }
    }

    @Test
    fun `short and shorts links resolve video ids`() {
        val id = "dQw4w9WgXcQ"
        assertEquals(id, VylloDeepLinks.videoIdForLink("https://youtu.be/$id?t=42"))
        assertEquals(id, VylloDeepLinks.videoIdForLink("https://www.youtube.com/shorts/$id"))
    }

    @Test
    fun `shared text extracts first supported video link and ignores trailing punctuation`() {
        val id = "dQw4w9WgXcQ"
        assertEquals(id, VylloDeepLinks.videoIdInText("Listen to this: https://youtu.be/$id. Great!"))
        assertNull(VylloDeepLinks.videoIdInText("Just a song title, no link"))
    }

    @Test
    fun `unsupported hosts and playlist ids are rejected`() {
        assertNull(VylloDeepLinks.videoIdForLink("https://evil.example/watch?v=dQw4w9WgXcQ"))
        assertNull(VylloDeepLinks.videoIdForLink("https://evil.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertNull(VylloDeepLinks.videoIdForLink("https://youtube.com.evil.example/watch?v=dQw4w9WgXcQ"))
        assertNull(VylloDeepLinks.videoIdForLink("https://youtube.com/watchlater?v=dQw4w9WgXcQ"))
        assertNull(VylloDeepLinks.videoIdForLink("https://youtube.com/watch?v=PL12345678901234567890"))
    }

    @Test
    fun `our own track deep links require the exact route and a video id`() {
        val id = "dQw4w9WgXcQ"
        assertEquals(
            id,
            VylloDeepLinks.trackIdFor(VylloDeepLinks.SCHEME, VylloDeepLinks.HOST_OPEN, listOf("track", id))
        )
        assertNull(VylloDeepLinks.trackIdFor(VylloDeepLinks.SCHEME, VylloDeepLinks.HOST_OPEN, listOf("library", id)))
        assertNull(VylloDeepLinks.trackIdFor("https", "open", listOf("track", id)))
    }

    @Test
    fun `canonical watch URL round trips through the parser`() {
        val id = "dQw4w9WgXcQ"
        assertEquals(id, VylloDeepLinks.videoIdForLink(VylloDeepLinks.watchUrlFor(id)))
    }
}

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
}

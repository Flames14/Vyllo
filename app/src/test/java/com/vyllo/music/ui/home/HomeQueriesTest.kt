package com.vyllo.music.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the search terms behind the fixed home rows and Explore cards.
 *
 * These strings are product-visible copy; a silent edit changes what the feed
 * fetches, so pin the ones users actually tap.
 */
class HomeQueriesTest {

    @Test
    fun `known explore categories map to their curated query`() {
        assertEquals("top music charts global official", HomeQueries.exploreQuery("Charts"))
        assertEquals("sleep relaxing music", HomeQueries.exploreQuery("Sleep"))
    }

    @Test
    fun `unknown explore category falls back to a playlist search`() {
        assertEquals("Jazz playlist", HomeQueries.exploreQuery("Jazz"))
    }

    @Test
    fun `known chips map to their curated query`() {
        assertEquals("focus concentration study music", HomeQueries.chipQuery("Focus"))
        assertEquals("driving road trip music", HomeQueries.chipQuery("Commute"))
    }

    @Test
    fun `unknown chip falls back to trending`() {
        assertEquals("trending music", HomeQueries.chipQuery("SomethingElse"))
    }
}

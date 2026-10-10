package com.vyllo.music.data.manager

import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.MusicItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Home feed cache codec tests.
 *
 * Pure JVM (no Robolectric) because the parsing rules are what matter: a corrupt or
 * partial cache must degrade to a refetch, never block launch.
 */
class HomeFeedCodecTest {

    private fun item(
        title: String,
        uploader: String = "Artist",
        url: String = "https://x/$title",
        durationSecs: Long = 200L
    ) = MusicItem(
        title = title,
        url = url,
        uploader = uploader,
        thumbnailUrl = "https://t/$title",
        type = MusicItemType.SONG,
        durationSecs = durationSecs
    )

    private fun encode(rows: Map<String, List<MusicItem>>, cachedAtMs: Long = System.currentTimeMillis()): String =
        HomeFeedCodec.encode(rows, cachedAtMs)!!

    // -----------------------------------------------------------------
    // Round trip
    // -----------------------------------------------------------------

    @Test
    fun `round trips every cached section`() {
        val rows = mapOf(
            HomeFeedSnapshot.KEY_TRENDING to listOf(item("Trend1"), item("Trend2")),
            HomeFeedSnapshot.KEY_QUICK_PICKS to listOf(item("Quick1")),
            HomeFeedSnapshot.KEY_CHILL to listOf(item("Chill1"))
        )

        val decoded = HomeFeedCodec.decode(encode(rows))!!
        assertEquals(listOf("Trend1", "Trend2"), decoded.section(HomeFeedSnapshot.KEY_TRENDING).map { it.title })
        assertEquals(listOf("Quick1"), decoded.section(HomeFeedSnapshot.KEY_QUICK_PICKS).map { it.title })
        assertEquals(listOf("Chill1"), decoded.section(HomeFeedSnapshot.KEY_CHILL).map { it.title })
    }

    @Test
    fun `preserves all item fields`() {
        val source = item("Song", uploader = "U", url = "https://x/s", durationSecs = 321L)
        val decoded = HomeFeedCodec.decode(
            encode(mapOf(HomeFeedSnapshot.KEY_TRENDING to listOf(source)))
        )!!.section(HomeFeedSnapshot.KEY_TRENDING).single()

        assertEquals(source.url, decoded.url)
        assertEquals(source.uploader, decoded.uploader)
        assertEquals(source.thumbnailUrl, decoded.thumbnailUrl)
        assertEquals(source.durationSecs, decoded.durationSecs)
        assertEquals(MusicItemType.SONG, decoded.type)
    }

    @Test
    fun `playlist type survives the round trip`() {
        val playlist = MusicItem(
            title = "PL",
            url = "https://x/pl",
            uploader = "U",
            type = MusicItemType.PLAYLIST
        )
        val decoded = HomeFeedCodec.decode(
            encode(mapOf(HomeFeedSnapshot.KEY_TRENDING to listOf(playlist)))
        )!!.section(HomeFeedSnapshot.KEY_TRENDING).single()

        assertEquals(MusicItemType.PLAYLIST, decoded.type)
    }

    @Test
    fun `unknown type string falls back to SONG`() {
        val payload = """
            {"cachedAt":${System.currentTimeMillis()},"sections":{
              "trending":[{"title":"T","url":"https://x/t","type":"WEIRD"}]
            }}
        """.trimIndent()

        assertEquals(MusicItemType.SONG, HomeFeedCodec.decode(payload)!!.section("trending").single().type)
    }

    @Test
    fun `per section item cap is enforced`() {
        val rows = mapOf(
            HomeFeedSnapshot.KEY_TRENDING to (1..80).map { item("T$it", url = "https://x/$it") }
        )
        assertEquals(HomeFeedCodec.MAX_ITEMS_PER_SECTION, HomeFeedCodec.decode(encode(rows))!!.section("trending").size)
    }

    // -----------------------------------------------------------------
    // Malformed input
    // -----------------------------------------------------------------

    @Test
    fun `corrupt json decodes to null rather than throwing`() {
        assertNull(HomeFeedCodec.decode("{ this is not json "))
        assertNull(HomeFeedCodec.decode(""))
        assertNull(HomeFeedCodec.decode("[]"))
    }

    @Test
    fun `missing timestamp is invalid`() {
        assertNull(HomeFeedCodec.decode("""{"sections":{"trending":[{"title":"T","url":"https://x/t"}]}}"""))
        assertNull(HomeFeedCodec.decode("""{"cachedAt":0,"sections":{"trending":[]}}"""))
    }

    @Test
    fun `missing sections object is invalid`() {
        val now = System.currentTimeMillis()
        assertNull(HomeFeedCodec.decode("""{"cachedAt":$now}"""))
    }

    @Test
    fun `all sections empty decodes to null`() {
        val now = System.currentTimeMillis()
        assertNull(HomeFeedCodec.decode("""{"cachedAt":$now,"sections":{"trending":[]}}"""))
    }

    @Test
    fun `blank url and title rows are dropped on decode`() {
        // A row without a URL cannot be de-duped and would break the LazyColumn key.
        val now = System.currentTimeMillis()
        val payload = """
            {"cachedAt":$now,"sections":{"trending":[
              {"title":"Good","url":"https://x/good"},
              {"title":"","url":"https://x/notitle"},
              {"title":"NoUrl","url":""}
            ]}}
        """.trimIndent()

        assertEquals(listOf("Good"), HomeFeedCodec.decode(payload)!!.section("trending").map { it.title })
    }

    @Test
    fun `unknown section keys are ignored`() {
        val now = System.currentTimeMillis()
        val payload = """
            {"cachedAt":$now,"sections":{
              "trending":[{"title":"T","url":"https://x/t"}],
              "not_a_real_section":[{"title":"X","url":"https://x/x"}]
            }}
        """.trimIndent()

        val decoded = HomeFeedCodec.decode(payload)!!
        // Unknown keys are simply absent, so a lookup yields nothing rather than failing.
        assertTrue(decoded.section("not_a_real_section").isEmpty())
        assertEquals(1, decoded.section("trending").size)
    }

    @Test
    fun `non object entries in a section are skipped`() {
        val now = System.currentTimeMillis()
        val payload = """
            {"cachedAt":$now,"sections":{"trending":[
              "garbage",
              {"title":"Good","url":"https://x/good"}
            ]}}
        """.trimIndent()

        assertEquals(listOf("Good"), HomeFeedCodec.decode(payload)!!.section("trending").map { it.title })
    }

    @Test
    fun `encoding empty rows returns null`() {
        assertNull(HomeFeedCodec.encode(emptyMap(), System.currentTimeMillis()))
        assertNull(HomeFeedCodec.encode(mapOf("trending" to emptyList()), System.currentTimeMillis()))
    }

    // -----------------------------------------------------------------
    // Freshness
    // -----------------------------------------------------------------

    @Test
    fun `freshness honours the ttl window`() {
        val now = System.currentTimeMillis()
        assertTrue(HomeFeedCodec.isFresh(now))
        assertTrue("inside the window", HomeFeedCodec.isFresh(now - HomeFeedCache.TTL_MS + 30_000L))
        assertFalse("past the window", HomeFeedCodec.isFresh(now - HomeFeedCache.TTL_MS - 60_000L))
        assertFalse(HomeFeedCodec.isFresh(0L))
    }

    @Test
    fun `snapshot isEmpty reflects all empty sections`() {
        assertTrue(HomeFeedSnapshot(rows = mapOf("trending" to emptyList()), cachedAtMs = 0L).isEmpty)
        assertFalse(HomeFeedSnapshot(rows = mapOf("trending" to listOf(item("x"))), cachedAtMs = 0L).isEmpty)
    }

    @Test
    fun `section lookup on a missing key is empty not null`() {
        val snapshot = HomeFeedSnapshot(rows = mapOf("trending" to listOf(item("x"))), cachedAtMs = 1L)
        assertNotNull(snapshot)
        assertTrue(snapshot.section("nope").isEmpty())
    }
}
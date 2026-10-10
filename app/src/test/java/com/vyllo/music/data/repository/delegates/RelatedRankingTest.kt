package com.vyllo.music.data.repository.delegates

import com.vyllo.music.domain.model.MusicItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the ranking rules.
 *
 * The scoring inversion fixed here was the single largest quality problem: taste was
 * worth up to +5.0 against a same-artist bonus of +4.0, so a heavily played but
 * unrelated artist outranked the current artist's own songs in Up Next.
 */
class RelatedRankingTest {

    private fun item(
        title: String,
        uploader: String,
        url: String = "https://www.youtube.com/watch?v=${title.hashCode().toUInt()}",
        durationSecs: Long = 200L
    ) = MusicItem(
        title = title,
        url = url,
        uploader = uploader,
        durationSecs = durationSecs
    )

    private fun anchor(
        artist: String = "Arijit Singh - Topic",
        language: String? = null,
        movie: String? = null,
        durationSecs: Long = 200L
    ) = RecommendationAnchor(
        title = "Some Song",
        uploader = artist,
        durationSecs = durationSecs,
        language = language,
        movie = movie
    )

    // -----------------------------------------------------------------
    // Scoring inversion
    // -----------------------------------------------------------------

    @Test
    fun `same artist outranks a 20x played unrelated artist`() {
        val a = anchor()
        val sameArtist = item("Song A", "Arijit Singh - Topic")
        val unrelatedButLoved = item("Song B", "Some Other Band")

        val sameScore = RelatedRanking.score(sameArtist, a, null, tasteCount = 0)
        val lovedScore = RelatedRanking.score(unrelatedButLoved, a, null, tasteCount = 20)

        assertTrue(
            "same-artist ($sameScore) must beat maxed-taste unrelated ($lovedScore)",
            sameScore > lovedScore
        )
    }

    @Test
    fun `taste bonus can never exceed the weakest artist match`() {
        val a = anchor()
        // Same 200s duration as the anchor so the duration term contributes equally to
        // both sides and cannot mask the taste/relevance relationship under test.
        val titleMatch = item("Arijit Singh Anthem", "Random Channel", durationSecs = 200L)
        val lovedUnrelated = item("Nope", "Some Other Band", durationSecs = 200L)

        // Title match is the weakest artist signal (+2.0); a maxed taste bonus (+1.0)
        // must still lose to it.
        val titleScore = RelatedRanking.score(titleMatch, a, null, tasteCount = 0)
        val lovedScore = RelatedRanking.score(lovedUnrelated, a, null, tasteCount = 20)

        assertTrue("titleMatch=$titleScore must beat loved=$lovedScore", titleScore > lovedScore)
        assertEquals(1.0, RelatedRanking.TASTE_BONUS_CAP, 0.0001)
    }

    @Test
    fun `taste bonus is monotonic and capped`() {
        val a = anchor()
        val candidate = item("Song", "Some Band")
        val base = RelatedRanking.score(candidate, a, null, tasteCount = 0)
        val at20 = RelatedRanking.score(candidate, a, null, tasteCount = 20)
        val at100 = RelatedRanking.score(candidate, a, null, tasteCount = 100)

        assertTrue(at20 > base)
        assertEquals(at20, at100, 0.0001) // capped
    }

    // -----------------------------------------------------------------
    // Language / film affinity
    // -----------------------------------------------------------------

    @Test
    fun `matching language scores above unknown language`() {
        val a = anchor(language = "tamil")
        val matching = item("Song", "Artist A")
        val unknown = item("Song", "Artist B")

        assertTrue(
            RelatedRanking.score(matching, a, "tamil", 0) >
                RelatedRanking.score(unknown, a, null, 0)
        )
    }

    @Test
    fun `mismatched language is penalised below unknown`() {
        val a = anchor(language = "tamil")
        val mismatch = item("Song", "Artist A")
        val unknown = item("Song", "Artist B")

        assertTrue(
            RelatedRanking.score(mismatch, a, "hindi", 0) <
                RelatedRanking.score(unknown, a, null, 0)
        )
    }

    @Test
    fun `film title match outranks a plain different-artist candidate`() {
        val a = anchor(movie = "Jawan")
        val soundtrack = item("Jawan Title Track", "Various")
        val unrelated = item("Random Song", "Other Band")

        assertTrue(
            RelatedRanking.score(soundtrack, a, null, 0) >
                RelatedRanking.score(unrelated, a, null, 0)
        )
    }

    // -----------------------------------------------------------------
    // Variant demotion
    // -----------------------------------------------------------------

    @Test
    fun `variant uploads are demoted relative to a clean upload`() {
        val a = anchor()
        val clean = item("Song Title", "Some Band")
        val lyricVideo = item("Song Title Lyrics", "Some Band")

        assertTrue(
            RelatedRanking.score(clean, a, null, 0) >
                RelatedRanking.score(lyricVideo, a, null, 0)
        )
    }

    @Test
    fun `variant pattern recognises common upload suffixes`() {
        listOf(
            "Song (Lyrics)",
            "Song Live",
            "Song Cover",
            "Song Remix",
            "Song Sped Up",
            "Song Slowed",
            "Song Karaoke",
            "Song Instrumental"
        ).forEach { title ->
            assertTrue("expected variant match for: $title", RelatedRanking.isVariantUpload(title))
        }
        assertFalse(RelatedRanking.isVariantUpload("Ordinary Song Title"))
    }

    // -----------------------------------------------------------------
    // Acceptance gate
    // -----------------------------------------------------------------

    @Test
    fun `shorts and long form are rejected`() {
        assertTrue(RelatedRanking.isShortsUrl("https://www.youtube.com/shorts/abc123"))
        assertTrue(RelatedRanking.isShortsUrl("https://www.youtube.com/embed/abc123"))
        assertFalse(RelatedRanking.isShortsUrl("https://www.youtube.com/watch?v=abc"))

        assertTrue(RelatedRanking.isAcceptableDuration(240L))
        assertFalse(RelatedRanking.isAcceptableDuration(20L))    // too short
        assertFalse(RelatedRanking.isAcceptableDuration(3600L))  // podcast / livestream
        assertTrue(RelatedRanking.isAcceptableDuration(0L))      // unknown duration is kept
    }

    @Test
    fun `anchor itself and shorts are not acceptable candidates`() {
        val anchorUrl = "https://www.youtube.com/watch?v=anchor"
        val a = anchor()

        val self = item("Some Song", "Arijit Singh - Topic", url = anchorUrl)
        val shorts = item(
            "Some Song",
            "Arijit Singh - Topic",
            url = "https://www.youtube.com/shorts/xyz",
            durationSecs = 0L
        )
        val longForm = item("Podcast", "Arijit Singh - Topic", durationSecs = 4000L)

        assertFalse(RelatedRanking.isAcceptable(self, a.title, a.uploader, anchorUrl))
        assertFalse(RelatedRanking.isAcceptable(shorts, a.title, a.uploader, anchorUrl))
        assertFalse(RelatedRanking.isAcceptable(longForm, a.title, a.uploader, anchorUrl))
    }

    // -----------------------------------------------------------------
    // Normalization
    // -----------------------------------------------------------------

    @Test
    fun `topic suffix and whitespace collapse to one artist key`() {
        assertEquals(
            RelatedRanking.sameArtistKey("Arijit  Singh - Topic"),
            RelatedRanking.sameArtistKey("Arijit Singh")
        )
    }

    @Test
    fun `normalized track key collapses variant uploads of the same song`() {
        val official = item("Some Song (Official Audio)", "Artist - Topic")
        val lyricVideo = item("Some Song Lyrics", "Artist - Topic")

        assertEquals(
            RelatedRanking.normalizedTrackKey(official),
            RelatedRanking.normalizedTrackKey(lyricVideo)
        )
    }

    @Test
    fun `normalized track key overload accepts raw fields`() {
        assertEquals(
            RelatedRanking.normalizedTrackKey("Song", "Artist - Topic"),
            RelatedRanking.normalizedTrackKey("Song", "Artist")
        )
    }

    // -----------------------------------------------------------------
    // Tiering
    // -----------------------------------------------------------------

    @Test
    fun `same artist tiers as SAME`() {
        val a = anchor()
        val candidate = item("Song", "Arijit Singh - Topic")
        val scored = RelatedRanking.toCandidate(candidate, a, null, 0)
        assertEquals(AffinityTier.SAME, scored.tier)
    }

    @Test
    fun `language match tiers as SIMILAR not DISCOVERY`() {
        val a = anchor(language = "tamil")
        val candidate = item("Song", "Different Artist")
        val scored = RelatedRanking.toCandidate(candidate, a, "tamil", 0)
        assertEquals(AffinityTier.SIMILAR, scored.tier)
    }

    @Test
    fun `unrelated unknown candidate tiers as DISCOVERY`() {
        val a = anchor()
        val candidate = item("Song", "Totally Different")
        val scored = RelatedRanking.toCandidate(candidate, a, null, 0)
        assertEquals(AffinityTier.DISCOVERY, scored.tier)
    }

    // -----------------------------------------------------------------
    // Ordering
    // -----------------------------------------------------------------

    @Test
    fun `continuity ordering opens with a primary track`() {
        val primary = listOf(item("P1", "Anchor"), item("P2", "Anchor"))
        val discovery = listOf(item("D1", "Other"), item("D2", "Other"), item("D3", "Other"))

        val ordered = RelatedRanking.interleave(
            primary = primary,
            discovery = discovery,
            primaryRun = RelatedPoolManager.UPNE_PRIMARY_RUN,
            discoveryRun = RelatedPoolManager.UPNE_DISCOVERY_RUN,
            limit = 10
        )

        assertEquals("P1", ordered.first().title)
    }

    @Test
    fun `continuity ordering is primary heavy and discovery ordering is discovery heavy`() {
        val primary = (1..20).map { item("P$it", "Anchor") }
        val discovery = (1..20).map { item("D$it", "Other") }

        fun primaryShare(primaryRun: Int, discoveryRun: Int): Float {
            val out = RelatedRanking.interleave(
                primary = primary,
                discovery = discovery,
                primaryRun = primaryRun,
                discoveryRun = discoveryRun,
                limit = 20
            )
            return out.count { it.title.startsWith("P") }.toFloat() / out.size
        }

        val upnext = primaryShare(RelatedPoolManager.UPNE_PRIMARY_RUN, RelatedPoolManager.UPNE_DISCOVERY_RUN)
        val related = primaryShare(RelatedPoolManager.RELATED_PRIMARY_RUN, RelatedPoolManager.RELATED_DISCOVERY_RUN)

        assertTrue("Up Next should be primary heavy, got $upnext", upnext > 0.5)
        assertTrue("Related should be discovery leaning, got $related", related < 0.5)
        assertTrue("Up Next must be heavier than Related: $upnext vs $related", upnext > related)
    }

    @Test
    fun `interleave honours the limit and drains when one side empties`() {
        val primary = listOf(item("P1", "Anchor"))
        val discovery = (1..5).map { item("D$it", "Other") }

        val limited = RelatedRanking.interleave(primary, discovery, 2, 1, limit = 3)
        assertEquals(3, limited.size)

        val drained = RelatedRanking.interleave(primary, discovery, 2, 1, limit = 20)
        assertEquals(6, drained.size)
        assertEquals(6, drained.map { it.title }.toSet().size)
    }

    @Test
    fun `interleave with empty primary still returns discovery`() {
        val discovery = (1..4).map { item("D$it", "Other") }
        val out = RelatedRanking.interleave(emptyList(), discovery, 2, 1, limit = 10)
        assertEquals(4, out.size)
    }

    // -----------------------------------------------------------------
    // Sorted merge (the O(n^2) regression)
    // -----------------------------------------------------------------

    @Test
    fun `merge keeps the pool sorted descending`() {
        val merged = RelatedRanking.mergeSortedDesc(
            existing = listOf(9.0, 7.0, 5.0),
            incoming = listOf(8.0, 6.0),
            limit = 10,
            scoreOf = { score -> score }
        )
        assertEquals(listOf(9.0, 8.0, 7.0, 6.0, 5.0), merged)
    }

    @Test
    fun `merge respects the limit`() {
        val merged = RelatedRanking.mergeSortedDesc(
            existing = listOf(9.0, 7.0),
            incoming = listOf(8.0, 6.0),
            limit = 3,
            scoreOf = { score -> score }
        )
        assertEquals(listOf(9.0, 8.0, 7.0), merged)
    }

    @Test
    fun `expanding a full pool stays linear and correctly ordered`() {
        val size = RELATED_MAX_ITEMS_TEST
        val existing = (0 until size).map { it.toDouble() }.shuffled().sortedDescending()
        val incoming = (0 until 50).map { 150.0 + it }

        val start = System.nanoTime()
        val merged = RelatedRanking.mergeSortedDesc(existing, incoming, limit = size, scoreOf = { score -> score })
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0

        assertEquals(size, merged.size)
        assertEquals(merged.sortedDescending(), merged)

        // The previous implementation rescored every pool item per incoming item,
        // with a regex per comparison. 1s is generous for a linear merge.
        assertTrue("merge took ${elapsedMs}ms", elapsedMs < 1000)
    }

    @Test
    fun `merge with empty sides short-circuits`() {
        assertEquals(
            listOf(2.0),
            RelatedRanking.mergeSortedDesc(emptyList(), listOf(2.0), 10) { score -> score }
        )
        assertEquals(
            listOf(2.0),
            RelatedRanking.mergeSortedDesc(listOf(2.0), emptyList(), 10) { score -> score }
        )
        assertTrue(
            RelatedRanking.mergeSortedDesc(listOf(2.0), listOf(1.0), limit = 0) { score -> score }.isEmpty()
        )
    }

    // -----------------------------------------------------------------
    // Per-artist cap
    // -----------------------------------------------------------------

    @Test
    fun `per artist cap limits one artist and keeps others`() {
        val items = listOf(
            item("A1", "Artist A"), item("A2", "Artist A"), item("A3", "Artist A"),
            item("B1", "Artist B"), item("B2", "Artist B")
        )
        val capped = RelatedRanking.capPerArtist(items, maxPerArtist = 2)

        assertEquals(4, capped.size)
        assertEquals(2, capped.count { it.uploader.startsWith("Artist A") })
        assertEquals(2, capped.count { it.uploader.startsWith("Artist B") })
    }

    // -----------------------------------------------------------------
    // Query ladder
    // -----------------------------------------------------------------

    @Test
    fun `query ladder is deterministic and highest weighted first`() {
        val first = RelatedRanking.buildQueryLadder(artist = "Arijit Singh", language = "tamil")
        val second = RelatedRanking.buildQueryLadder(artist = "Arijit Singh", language = "tamil")

        assertEquals("ladders must not shuffle between identical calls", first, second)
        assertEquals(first.map { it.weight }.sortedDescending(), first.map { it.weight })
        assertNotNull(first.firstOrNull())
    }

    @Test
    fun `artist query ladder never seeds a songs-like query with a title`() {
        // "songs like <artist>" is fine; "songs like <title>" is not — YouTube's
        // songs-like semantic resolves artist names, and seeding it with a title
        // returned covers and compilation uploads.
        val ladder = RelatedRanking.buildQueryLadder(artist = "Some Artist")
        ladder.forEach { query ->
            assertFalse(
                "unexpected title-seeded query: ${query.query}",
                query.query.startsWith("songs like Some Song")
            )
        }
        // The artist-anchored form is still present and expected.
        assertTrue(ladder.any { it.query == "songs like Some Artist" })
    }

    @Test
    fun `film anchor prefers soundtrack queries`() {
        val ladder = RelatedRanking.buildQueryLadder(
            artist = "Arijit Singh",
            movie = "Jawan",
            isSoundtrack = true
        )
        assertTrue(ladder.first().query.contains("Jawan"))
        assertTrue(ladder.first().query.contains("soundtrack"))
        // Soundtrack queries must outrank the generic "<artist> radio" template.
        val artistRadioWeight = ladder.first { it.query.endsWith("radio") }.weight
        assertTrue(
            "soundtrack (${ladder.first().weight}) should outrank artist radio ($artistRadioWeight)",
            ladder.first().weight > artistRadioWeight
        )
    }

    @Test
    fun `language fragment narrows artist queries`() {
        val ladder = RelatedRanking.buildQueryLadder(
            artist = "Artist",
            language = "tamil",
            languageFragment = "tamil"
        )
        assertTrue(ladder.any { it.query.contains("tamil") })
        // The canonical tag rides along for scoring even though it shaped the query.
        assertTrue(ladder.all { it.impliedLanguage == "tamil" })
    }

    @Test
    fun `language tag alone does not appear in query text`() {
        // language is the scoring tag; only languageFragment shapes the query.
        val ladder = RelatedRanking.buildQueryLadder(
            artist = "Artist",
            language = "tamil",
            languageFragment = null
        )
        ladder.forEach { assertFalse("unexpected tamil in: ${it.query}", it.query.contains("tamil")) }
        assertTrue(ladder.all { it.impliedLanguage == "tamil" })
    }

    @Test
    fun `english is never injected into a query`() {
        // "english" is the YouTube default; adding it narrows results for no gain.
        // The implied-language *tag* is still attached so scoring can use it.
        val ladder = RelatedRanking.buildQueryLadder(artist = "Artist", language = "english")
        ladder.forEach { assertFalse("unexpected english in: ${it.query}", it.query.contains("english")) }
        // ...and the tag survives for the scorer.
        assertTrue(ladder.all { it.impliedLanguage == "english" })
    }

    @Test
    fun `similar artists ladder drops both broken templates`() {
        val ladder = RelatedRanking.buildSimilarArtistsLadder(
            title = "Kesariya",
            artist = "Arijit Singh"
        )
        assertFalse(ladder.any { it.query == "songs like Kesariya" })
        assertFalse(ladder.any { it.query.contains("trending music similar") })
        assertTrue(ladder.any { it.query.startsWith("artists similar to") })
    }

    @Test
    fun `similar artists ladder with a blank artist falls back to the title`() {
        val ladder = RelatedRanking.buildSimilarArtistsLadder(title = "Some Song", artist = "")
        assertTrue(ladder.isNotEmpty())
        assertEquals("Some Song", ladder.first().query)
    }

    @Test
    fun `expansion ladder walks the tail then exhausts`() {
        val ladder = RelatedRanking.buildQueryLadder(artist = "Artist")

        val round0 = RelatedRanking.buildExpansionLadder(ladder, round = 0)
        val round1 = RelatedRanking.buildExpansionLadder(ladder, round = 1)
        assertEquals(2, round0.size)
        assertEquals(2, round1.size)
        assertTrue(round0.map { it.query }.intersect(round1.map { it.query }.toSet()).isEmpty())

        // Far past the end must be empty, not crash or repeat.
        assertTrue(RelatedRanking.buildExpansionLadder(ladder, round = 999).isEmpty())
        assertTrue(RelatedRanking.buildExpansionLadder(emptyList(), round = 0).isEmpty())
    }

    private companion object {
        const val RELATED_MAX_ITEMS_TEST = 200
    }
}
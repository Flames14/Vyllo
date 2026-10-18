package com.vyllo.music.ui.home

import com.vyllo.music.HomeUiState
import com.vyllo.music.data.manager.HomeFeedSnapshot
import com.vyllo.music.domain.model.MusicItem

/**
 * Everything that knows the layout of the home feed's rows and sections.
 *
 * Extracted from [com.vyllo.music.HomeViewModel] so the row sizing, the
 * cache snapshot contract and the feed title all live in one place that can be
 * reasoned about (and tested) without a ViewModel. Pure — no state, no I/O.
 */
object HomeFeedShaper {

    /** Cards are laid out 4-per-row on the Home grid. */
    private const val HOME_ROW_SIZE = 4

    private const val QUICK_PICKS_CARD_COUNT = 16
    private const val MIXED_FOR_YOU_COUNT = 8
    private const val QUICK_PICKS_COUNT = 8
    private const val TRENDING_INLINE_COUNT = 8
    private const val TRENDING_ROW_COUNT = 16
    private const val NEW_RELEASES_COUNT = 8
    private const val MOOD_COUNT = 8

    /** Title above the feed: a fresh user sees the recommendation pitch. */
    fun homeTitle(hasNoHistory: Boolean): String =
        if (hasNoHistory) "Recommended For You" else "Trending Now"

    fun inlineTrending(items: List<MusicItem>): List<MusicItem> =
        items.take(TRENDING_INLINE_COUNT)

    fun extraTrendingRows(items: List<MusicItem>): List<List<MusicItem>> =
        items.drop(TRENDING_INLINE_COUNT).take(TRENDING_ROW_COUNT).chunked(HOME_ROW_SIZE)

    fun quickPicksGrid(items: List<MusicItem>): List<List<MusicItem>> =
        items.take(QUICK_PICKS_CARD_COUNT).chunked(HOME_ROW_SIZE)

    fun mixedForYou(items: List<MusicItem>): List<MusicItem> =
        items.drop(QUICK_PICKS_CARD_COUNT).take(MIXED_FOR_YOU_COUNT)

    fun moreQuickPicks(items: List<MusicItem>): List<MusicItem> =
        items.drop(QUICK_PICKS_CARD_COUNT + MIXED_FOR_YOU_COUNT).take(QUICK_PICKS_COUNT)

    /** Everything left after the grid and the mixed row — used by refresh. */
    fun remainingQuickPicks(items: List<MusicItem>): List<MusicItem> =
        items.drop(QUICK_PICKS_CARD_COUNT + MIXED_FOR_YOU_COUNT)

    fun topNewReleases(items: List<MusicItem>): List<MusicItem> =
        items.take(NEW_RELEASES_COUNT)

    fun topMoods(items: List<MusicItem>): List<MusicItem> =
        items.take(MOOD_COUNT)

    /**
     * Serialises only the sections a network pass actually filled, so the cache
     * never persists an empty placeholder. Returns null when nothing is seen.
     */
    fun buildSnapshot(state: HomeUiState, seenSections: Set<String>): HomeFeedSnapshot? {
        val rows = HashMap<String, List<MusicItem>>()
        if (HomeFeedSnapshot.KEY_TRENDING in seenSections) {
            rows[HomeFeedSnapshot.KEY_TRENDING] = state.trendingNowItems + state.trendingNowRows.flatten()
        }
        if (HomeFeedSnapshot.KEY_QUICK_PICKS in seenSections) {
            rows[HomeFeedSnapshot.KEY_QUICK_PICKS] = state.quickPicksRows.flatten()
        }
        if (HomeFeedSnapshot.KEY_MIXED_FOR_YOU in seenSections) {
            rows[HomeFeedSnapshot.KEY_MIXED_FOR_YOU] = state.mixedForYouItems
        }
        if (HomeFeedSnapshot.KEY_NEW_RELEASES in seenSections) {
            rows[HomeFeedSnapshot.KEY_NEW_RELEASES] = state.newReleasesItems
        }
        if (HomeFeedSnapshot.KEY_CHILL in seenSections) rows[HomeFeedSnapshot.KEY_CHILL] = state.chillItems
        if (HomeFeedSnapshot.KEY_WORKOUT in seenSections) rows[HomeFeedSnapshot.KEY_WORKOUT] = state.workoutItems
        if (HomeFeedSnapshot.KEY_FOCUS in seenSections) rows[HomeFeedSnapshot.KEY_FOCUS] = state.focusItems
        if (rows.isEmpty()) return null
        return HomeFeedSnapshot(rows = rows, cachedAtMs = System.currentTimeMillis())
    }

    /** Paints a cached snapshot into the UI so the feed is visible immediately. */
    fun applySnapshot(state: HomeUiState, snapshot: HomeFeedSnapshot): HomeUiState {
        val trending = snapshot.section(HomeFeedSnapshot.KEY_TRENDING)
        val quickPicks = snapshot.section(HomeFeedSnapshot.KEY_QUICK_PICKS)
        val mixedForYou = snapshot.section(HomeFeedSnapshot.KEY_MIXED_FOR_YOU)
        return state.copy(
            homeTitle = homeTitle(state.recentlyPlayed.isEmpty()),
            trendingNowItems = inlineTrending(trending),
            trendingNowRows = extraTrendingRows(trending),
            quickPicksRows = quickPicksGrid(quickPicks),
            mixedForYouItems = mixedForYou,
            newReleasesItems = snapshot.section(HomeFeedSnapshot.KEY_NEW_RELEASES),
            chillItems = snapshot.section(HomeFeedSnapshot.KEY_CHILL),
            workoutItems = snapshot.section(HomeFeedSnapshot.KEY_WORKOUT),
            focusItems = snapshot.section(HomeFeedSnapshot.KEY_FOCUS),
            isLoading = false
        )
    }

    /**
     * The sections a just-painted snapshot carries real content for.
     *
     * buildSnapshot only persists keys [sectionsSeen] knows about, so an offline
     * launch must mark what the cache handed back or the next save would drop it.
     */
    fun snapshotSeenKeys(snapshot: HomeFeedSnapshot): Set<String> {
        val seen = mutableSetOf<String>()
        if (snapshot.section(HomeFeedSnapshot.KEY_TRENDING).isNotEmpty()) seen += HomeFeedSnapshot.KEY_TRENDING
        if (snapshot.section(HomeFeedSnapshot.KEY_QUICK_PICKS).isNotEmpty()) seen += HomeFeedSnapshot.KEY_QUICK_PICKS
        if (snapshot.section(HomeFeedSnapshot.KEY_MIXED_FOR_YOU).isNotEmpty()) seen += HomeFeedSnapshot.KEY_MIXED_FOR_YOU
        if (snapshot.section(HomeFeedSnapshot.KEY_NEW_RELEASES).isNotEmpty()) seen += HomeFeedSnapshot.KEY_NEW_RELEASES
        if (snapshot.section(HomeFeedSnapshot.KEY_CHILL).isNotEmpty()) seen += HomeFeedSnapshot.KEY_CHILL
        if (snapshot.section(HomeFeedSnapshot.KEY_WORKOUT).isNotEmpty()) seen += HomeFeedSnapshot.KEY_WORKOUT
        if (snapshot.section(HomeFeedSnapshot.KEY_FOCUS).isNotEmpty()) seen += HomeFeedSnapshot.KEY_FOCUS
        return seen
    }
}

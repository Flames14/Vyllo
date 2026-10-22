package com.vyllo.music.domain.model

/**
 * Providers the lyrics engine can query, in the order the user wants them
 * tried.
 *
 * Order is a real preference, not a cosmetic one: a source that returns plain
 * text ends the chain, so putting a text-only provider first can hide a synced
 * version from a provider further down. Unknown stored keys are dropped and
 * newly added providers are appended, so an upgrade never silently disables a
 * source the user had not seen yet.
 */
enum class LyricsSource(val key: String, val needsArtist: Boolean) {
    PAXSENIX("paxsenix", true),
    BETTER_LYRICS("better_lyrics", true),
    LRCLIB_EXACT("lrclib_exact", true),
    LRCLIB_SEARCH("lrclib_search", true),
    LYRICS_PLUS("lyrics_plus", true),
    KUGOU("kugou", true),
    NETEASE("netease", false),
    LYRICS_OVH("lyrics_ovh", true),
    LRCLIB_TITLE_ONLY("lrclib_title_only", false);

    companion object {
        val DEFAULT_ORDER: List<LyricsSource> = entries.toList()

        fun parse(stored: String?): List<LyricsSource> {
            val requested = stored
                ?.split(',')
                ?.mapNotNull { token -> entries.firstOrNull { it.key == token.trim() } }
                ?.distinct()
                .orEmpty()
            return requested + DEFAULT_ORDER.filterNot { it in requested }
        }

        fun encode(order: List<LyricsSource>): String = order.joinToString(",") { it.key }
    }
}

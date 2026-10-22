package com.vyllo.music.domain.model

/**
 * Queue seeding for a radio station.
 *
 * Kept pure so the ordering guarantees (seed first, no duplicates, bounded
 * size) are testable without a player.
 */
object RadioStation {

    const val DEFAULT_LIMIT = 30

    /**
     * Seed track first, then its related pool in the order the resolver ranked
     * it. Entries without any identifier are dropped because the service could
     * never resolve them, and duplicate urls are collapsed: the related pool
     * regularly echoes the seed back.
     */
    fun build(seed: MusicItem, related: List<MusicItem>, limit: Int = DEFAULT_LIMIT): List<MusicItem> {
        if (limit <= 0) return emptyList()
        return (listOf(seed) + related)
            .filter { it.url.isNotBlank() || it.id.isNotBlank() }
            .distinctBy { it.url }
            .take(limit)
    }
}

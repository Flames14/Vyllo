package com.vyllo.music.data.manager

import android.content.Context
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.model.MusicItem
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-disk snapshot of the Home feed.
 *
 * Cold start used to wait on six-plus sequential network round trips before a single
 * row appeared. With this cache the feed renders from disk immediately and refreshes
 * in the background, so launch time stops depending on network conditions.
 *
 * Deliberately a plain JSON file in `cacheDir` rather than a Room table: the OS can
 * reclaim it under pressure without data loss, and it avoids a database migration
 * plus a schema export for a purely derived, re-fetchable value.
 */
data class HomeFeedSnapshot(
    val rows: Map<String, List<MusicItem>>,
    val cachedAtMs: Long
) {
    fun section(key: String): List<MusicItem> = rows[key].orEmpty()

    val isEmpty: Boolean get() = rows.values.all { it.isEmpty() }

    companion object {
        const val KEY_QUICK_PICKS = "quickPicks"
        const val KEY_MIXED_FOR_YOU = "mixedForYou"
        const val KEY_TRENDING = "trending"
        const val KEY_NEW_RELEASES = "newReleases"
        const val KEY_CHILL = "chill"
        const val KEY_WORKOUT = "workout"
        const val KEY_FOCUS = "focus"

        /** Sections worth caching — everything the user sees on Home. */
        val CACHED_KEYS = listOf(
            KEY_QUICK_PICKS,
            KEY_MIXED_FOR_YOU,
            KEY_TRENDING,
            KEY_NEW_RELEASES,
            KEY_CHILL,
            KEY_WORKOUT,
            KEY_FOCUS
        )
    }
}

@Singleton
class HomeFeedCache @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        /**
         * Feed freshness window.
         *
         * Long enough that opening the app a few times in a day never waits on the
         * network, short enough that "Trending Now" is not badly out of date when a
         * refresh does happen.
         */
        const val TTL_MS = 6 * 60 * 60 * 1000L

        private const val FILE_NAME = "home_feed.json"
    }

    private val lock = Any()

    private fun file(): File = File(context.cacheDir, FILE_NAME)

    private fun tempFile(): File = File(context.cacheDir, "$FILE_NAME.tmp")

    fun save(snapshot: HomeFeedSnapshot) {
        if (snapshot.isEmpty) return
        val payload = HomeFeedCodec.encode(snapshot.rows, snapshot.cachedAtMs) ?: return
        synchronized(lock) {
            try {
                // Write to a temp file and rename: a process kill mid-write must not
                // leave a truncated snapshot that fails to parse on next launch.
                tempFile().writeText(payload)
                val target = file()
                if (!tempFile().renameTo(target)) {
                    target.writeText(payload)
                    tempFile().delete()
                }
            } catch (e: Exception) {
                SecureLogger.w("HomeFeedCache", "save failed", e)
            }
        }
    }

    /** Returns a fresh snapshot, or null when absent, stale or unreadable. */
    fun loadIfFresh(): HomeFeedSnapshot? = read()?.takeIf { HomeFeedCodec.isFresh(it.cachedAtMs) }

    /** Returns a snapshot regardless of age — used for offline launches. */
    fun loadStale(): HomeFeedSnapshot? = read()

    private fun read(): HomeFeedSnapshot? = synchronized(lock) {
        try {
            val target = file()
            if (!target.exists()) return null
            val decoded = HomeFeedCodec.decode(target.readText())
            if (decoded == null) {
                // Corrupt: drop it so the next save starts clean.
                SecureLogger.d("HomeFeedCache") { "discarding unreadable cache" }
                target.delete()
            }
            decoded
        } catch (e: Exception) {
            SecureLogger.w("HomeFeedCache", "load failed", e)
            null
        }
    }

    fun clear() {
        synchronized(lock) {
            runCatching {
                file().delete()
                tempFile().delete()
            }
        }
    }
}
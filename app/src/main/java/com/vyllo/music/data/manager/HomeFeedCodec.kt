package com.vyllo.music.data.manager

import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.MusicItemType
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON codec for the Home feed snapshot.
 *
 * Split out from [HomeFeedCache] so it can be unit tested without Robolectric or a
 * device — the file/lifecycle behaviour lives in the cache, the parsing rules here.
 */
object HomeFeedCodec {

    const val MAX_ITEMS_PER_SECTION = 40

    /** Serialises only the given sections. Returns null when nothing is serialisable. */
    fun encode(rows: Map<String, List<MusicItem>>, cachedAtMs: Long): String? {
        val sections = JSONObject()
        rows.forEach { (key, items) ->
            if (items.isEmpty()) return@forEach
            sections.put(key, encodeItems(items))
        }
        if (sections.length() == 0) return null
        return JSONObject()
            .put("cachedAt", cachedAtMs)
            .put("sections", sections)
            .toString()
    }

    /**
     * Parses a snapshot, reading only [HomeFeedSnapshot.CACHED_KEYS].
     *
     * Returns null for anything malformed rather than throwing: a corrupt cache must
     * never be able to block launch.
     */
    fun decode(raw: String): HomeFeedSnapshot? {
        return try {
            val parsed = JSONObject(raw)
            val cachedAt = parsed.optLong("cachedAt", 0L)
            if (cachedAt <= 0L) return null
            val sections = parsed.optJSONObject("sections") ?: return null

            val rows = HashMap<String, List<MusicItem>>()
            HomeFeedSnapshot.CACHED_KEYS.forEach { key ->
                val array = sections.optJSONArray(key) ?: return@forEach
                decodeItems(array)?.let { rows[key] = it }
            }
            if (rows.isEmpty()) return null
            HomeFeedSnapshot(rows = rows, cachedAtMs = cachedAt)
        } catch (e: Exception) {
            null
        }
    }

    fun isFresh(cachedAtMs: Long, ttlMs: Long = HomeFeedCache.TTL_MS): Boolean =
        cachedAtMs > 0L && System.currentTimeMillis() - cachedAtMs <= ttlMs

    private fun encodeItems(items: List<MusicItem>): JSONArray {
        val array = JSONArray()
        items.take(MAX_ITEMS_PER_SECTION).forEach { item ->
            array.put(
                JSONObject()
                    .put("title", item.title)
                    .put("url", item.url)
                    .put("uploader", item.uploader)
                    .put("thumbnailUrl", item.thumbnailUrl)
                    .put("type", item.type.name)
                    .put("durationSecs", item.durationSecs)
            )
        }
        return array
    }

    private fun decodeItems(array: JSONArray): List<MusicItem>? {
        val out = ArrayList<MusicItem>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val url = obj.optString("url")
            val title = obj.optString("title")
            // A row without a URL could not be de-duped on reload and would break the
            // LazyColumn key invariant, so drop it here rather than on render.
            if (url.isBlank() || title.isBlank()) continue
            val type = runCatching { MusicItemType.valueOf(obj.optString("type", "SONG")) }
                .getOrDefault(MusicItemType.SONG)
            out.add(
                MusicItem(
                    title = title,
                    url = url,
                    uploader = obj.optString("uploader"),
                    thumbnailUrl = obj.optString("thumbnailUrl"),
                    type = type,
                    durationSecs = obj.optLong("durationSecs")
                )
            )
        }
        return out.ifEmpty { null }
    }
}
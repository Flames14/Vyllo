package com.vyllo.music.data.manager

import androidx.core.content.edit

import android.content.SharedPreferences
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.MusicItemType
import org.json.JSONArray
import org.json.JSONObject

object QueuePersistence {
    private const val KEY_QUEUE = "persisted_queue_json_v1"
    private const val KEY_INDEX = "persisted_queue_index_v1"
    private const val MAX_ITEMS = 200

    fun save(prefs: SharedPreferences, items: List<MusicItem>, index: Int) {
        try {
            val array = JSONArray()
            items.take(MAX_ITEMS).forEach { item ->
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
            prefs.edit {
                    putString(KEY_QUEUE, array.toString())
                    .putInt(KEY_INDEX, index)
                }
        } catch (e: Exception) {
            SecureLogger.w("QueuePersistence", "save failed", e)
        }
    }

    fun restore(prefs: SharedPreferences): Pair<List<MusicItem>, Int>? {
        return try {
            val raw = prefs.getString(KEY_QUEUE, null) ?: return null
            val array = JSONArray(raw)
            if (array.length() == 0) return null
            val items = ArrayList<MusicItem>(array.length())
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val url = o.optString("url")
                val title = o.optString("title")
                if (url.isBlank() || title.isBlank()) continue
                val type = runCatching { MusicItemType.valueOf(o.optString("type", "SONG")) }
                    .getOrDefault(MusicItemType.SONG)
                items.add(
                    MusicItem(
                        title = title,
                        url = url,
                        uploader = o.optString("uploader"),
                        thumbnailUrl = o.optString("thumbnailUrl"),
                        type = type,
                        durationSecs = o.optLong("durationSecs")
                    )
                )
            }
            if (items.isEmpty()) return null
            val index = prefs.getInt(KEY_INDEX, 0).coerceIn(0, items.size - 1)
            items to index
        } catch (e: Exception) {
            SecureLogger.w("QueuePersistence", "restore failed", e)
            null
        }
    }

    fun clear(prefs: SharedPreferences) {
        prefs.edit {remove(KEY_QUEUE).remove(KEY_INDEX)}
    }
}

package com.vyllo.music.recognition.data

import android.content.Context
import androidx.core.content.edit
import com.vyllo.music.recognition.domain.model.RecognitionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class RecognitionHistoryEntry(
    val result: RecognitionResult,
    val recognizedAt: Long
) {
    val key: String get() = "${result.trackId}:$recognizedAt"
}

/** Small, bounded local recognition history; entries never leave the device. */
@Singleton
class RecognitionHistoryRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val lock = Any()
    private val _entries = MutableStateFlow(readEntries())
    val entries: StateFlow<List<RecognitionHistoryEntry>> = _entries.asStateFlow()

    fun add(result: RecognitionResult, recognizedAt: Long = System.currentTimeMillis()) = synchronized(lock) {
        val updated = (listOf(RecognitionHistoryEntry(result, recognizedAt)) + _entries.value)
            .take(MAX_ENTRIES)
        persist(updated)
        _entries.value = updated
    }

    fun remove(key: String) = synchronized(lock) {
        val updated = _entries.value.filterNot { it.key == key }
        persist(updated)
        _entries.value = updated
    }

    fun clear() = synchronized(lock) {
        preferences.edit { remove(KEY_ENTRIES) }
        _entries.value = emptyList()
    }

    private fun persist(entries: List<RecognitionHistoryEntry>) {
        preferences.edit { putString(KEY_ENTRIES, encode(entries)) }
    }

    private fun readEntries(): List<RecognitionHistoryEntry> =
        decode(preferences.getString(KEY_ENTRIES, null))

    companion object {
        private const val PREFERENCES = "recognition_history"
        private const val KEY_ENTRIES = "entries"
        const val MAX_ENTRIES = 50

        internal fun encode(entries: List<RecognitionHistoryEntry>): String {
            val array = JSONArray()
            entries.forEach { entry ->
                val result = entry.result
                array.put(
                    JSONObject()
                        .put("recognizedAt", entry.recognizedAt)
                        .put("trackId", result.trackId)
                        .put("title", result.title)
                        .put("artist", result.artist)
                        .put("album", result.album)
                        .put("coverArtUrl", result.coverArtUrl)
                        .put("coverArtHqUrl", result.coverArtHqUrl)
                        .put("genre", result.genre)
                        .put("releaseDate", result.releaseDate)
                        .put("label", result.label)
                        .put("lyrics", result.lyrics)
                        .put("shazamUrl", result.shazamUrl)
                        .put("appleMusicUrl", result.appleMusicUrl)
                        .put("spotifyUrl", result.spotifyUrl)
                        .put("isrc", result.isrc)
                        .put("youtubeVideoId", result.youtubeVideoId)
                )
            }
            return array.toString()
        }

        internal fun decode(raw: String?): List<RecognitionHistoryEntry> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(raw)
                (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    val trackId = item.optString("trackId").takeIf(String::isNotBlank)
                        ?: return@mapNotNull null
                    val title = item.optString("title").takeIf(String::isNotBlank)
                        ?: return@mapNotNull null
                    val artist = item.optString("artist")
                    val result = RecognitionResult(
                        trackId = trackId,
                        title = title,
                        artist = artist,
                        album = item.nullableString("album"),
                        coverArtUrl = item.nullableString("coverArtUrl"),
                        coverArtHqUrl = item.nullableString("coverArtHqUrl"),
                        genre = item.nullableString("genre"),
                        releaseDate = item.nullableString("releaseDate"),
                        label = item.nullableString("label"),
                        lyrics = item.nullableString("lyrics"),
                        shazamUrl = item.nullableString("shazamUrl"),
                        appleMusicUrl = item.nullableString("appleMusicUrl"),
                        spotifyUrl = item.nullableString("spotifyUrl"),
                        isrc = item.nullableString("isrc"),
                        youtubeVideoId = item.nullableString("youtubeVideoId")
                    )
                    RecognitionHistoryEntry(result, item.optLong("recognizedAt"))
                }.take(MAX_ENTRIES)
            }.getOrDefault(emptyList())
        }

        private fun JSONObject.nullableString(key: String): String? =
            optString(key).takeUnless { it.isEmpty() || it == "null" }
    }
}

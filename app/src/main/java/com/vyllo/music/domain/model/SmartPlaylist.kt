package com.vyllo.music.domain.model

/**
 * Library collections that are computed rather than stored as playlist rows.
 * They behave like playlists (open, play, show a count) but never write user
 * edits, so they cannot drift out of sync with the data they summarise.
 */
enum class SmartPlaylist(val key: String) {
    LIKED("liked"),
    OFFLINE("offline"),
    TOP("top");

    companion object {
        fun from(key: String?): SmartPlaylist? = entries.firstOrNull { it.key == key }
    }
}

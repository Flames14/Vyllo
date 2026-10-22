package com.vyllo.music.data.repository

import com.vyllo.music.domain.model.PlaylistSongEntity

/** Pure stable ordering operations for locally saved playlist songs. */
internal object PlaylistSongOrdering {

    /** Inserts [song] at a zero-based index and normalizes order keys. */
    fun insert(
        songs: List<PlaylistSongEntity>,
        song: PlaylistSongEntity,
        position: Int,
        nowMillis: Long
    ): List<PlaylistSongEntity> {
        val reordered = songs.toMutableList().also { rows ->
            rows.add(position.coerceIn(0, rows.size), song)
        }
        val previousMax = songs.maxOfOrNull { it.addedAt } ?: Long.MIN_VALUE
        val nextAfterExisting = if (previousMax == Long.MAX_VALUE) Long.MIN_VALUE else previousMax + 1
        val firstOrderKey = maxOf(nowMillis, nextAfterExisting)
        return reordered.mapIndexed { index, row -> row.copy(addedAt = firstOrderKey + index) }
    }

    /**
     * Moves one row and assigns strictly increasing order keys. A null result
     * means the requested indices are invalid or already equal.
     */
    fun move(
        songs: List<PlaylistSongEntity>,
        fromIndex: Int,
        toIndex: Int,
        nowMillis: Long
    ): List<PlaylistSongEntity>? {
        if (fromIndex !in songs.indices || toIndex !in songs.indices || fromIndex == toIndex) return null

        val reordered = songs.toMutableList().also { rows ->
            rows.add(toIndex, rows.removeAt(fromIndex))
        }
        val previousMax = songs.maxOfOrNull { it.addedAt } ?: Long.MIN_VALUE
        val nextAfterExisting = if (previousMax == Long.MAX_VALUE) Long.MIN_VALUE else previousMax + 1
        val firstOrderKey = maxOf(nowMillis, nextAfterExisting)

        return reordered.mapIndexed { index, song ->
            song.copy(addedAt = firstOrderKey + index)
        }
    }
}

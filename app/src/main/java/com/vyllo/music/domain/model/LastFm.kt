package com.vyllo.music.domain.model

/**
 * Last.fm API request signing.
 *
 * The spec requires parameters sorted by name, concatenated as `key + value`,
 * followed by the shared secret and hashed with MD5. `format` and `callback`
 * are never part of the signature.
 */
object LastFmSignature {

    fun sign(params: Map<String, String>, secret: String): String {
        val payload = params
            .filterKeys { !it.equals("format", ignoreCase = true) && !it.equals("callback", ignoreCase = true) }
            .toSortedMap()
            .entries
            .joinToString(separator = "") { (key, value) -> key + value } + secret
        return md5Hex(payload)
    }

    internal fun md5Hex(value: String): String {
        val digest = java.security.MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }
}

/**
 * Last.fm's scrobble threshold: a track counts once it has played for at least
 * half its length, or for four minutes — whichever comes first. Tracks shorter
 * than 30 seconds are never scrobbled.
 */
object LastFmScrobbleRules {

    const val MIN_DURATION_MS: Long = 30_000L
    const val LONG_TRACK_MS: Long = 240_000L

    fun shouldScrobble(positionMs: Long, durationMs: Long): Boolean {
        if (positionMs <= 0L) return false
        if (durationMs in 1 until MIN_DURATION_MS) return false
        if (positionMs >= LONG_TRACK_MS) return true
        return durationMs > 0L && positionMs * 2L >= durationMs
    }
}

data class LastFmSession(
    val username: String,
    val sessionKey: String
)

data class LastFmTrack(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationSecs: Long = 0L
)

/** A scrobble that has not reached Last.fm yet, kept for offline retry. */
data class PendingScrobble(
    val track: LastFmTrack,
    val timestampSecs: Long
)

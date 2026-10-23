package com.vyllo.music.data.lastfm

import com.vyllo.music.core.security.SecurePreferenceManager
import com.vyllo.music.domain.model.LastFmPendingCodec
import com.vyllo.music.domain.model.LastFmSession
import com.vyllo.music.domain.model.PendingScrobble
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the user's Last.fm credentials and the offline scrobble queue.
 *
 * Credentials live in [SecurePreferenceManager] (Keystore-backed encryption).
 * The queue is capped so a permanently offline device cannot grow it forever.
 */
@Singleton
class LastFmStore @Inject constructor(
    private val secure: SecurePreferenceManager
) {
    private val pendingLock = Any()

    var enabled: Boolean
        get() = secure.getBoolean(KEY_ENABLED, false)
        set(value) = secure.saveBoolean(KEY_ENABLED, value)

    var apiKey: String
        get() = secure.getString(KEY_API_KEY).orEmpty()
        set(value) = secure.saveString(KEY_API_KEY, value)

    var apiSecret: String
        get() = secure.getString(KEY_API_SECRET).orEmpty()
        set(value) = secure.saveString(KEY_API_SECRET, value)

    fun session(): LastFmSession? {
        val key = secure.getString(KEY_SESSION_KEY).orEmpty()
        val username = secure.getString(KEY_USERNAME).orEmpty()
        if (key.isBlank() || username.isBlank()) return null
        return LastFmSession(username = username, sessionKey = key)
    }

    fun saveSession(session: LastFmSession) {
        secure.saveString(KEY_SESSION_KEY, session.sessionKey)
        secure.saveString(KEY_USERNAME, session.username)
    }

    fun saveCredentials(apiKey: String, apiSecret: String) {
        this.apiKey = apiKey
        this.apiSecret = apiSecret
    }

    fun isConfigured(): Boolean =
        apiKey.isNotBlank() && apiSecret.isNotBlank() && session() != null

    fun clearSession() {
        secure.saveString(KEY_SESSION_KEY, "")
        secure.saveString(KEY_USERNAME, "")
    }

    fun pendingScrobbles(): List<PendingScrobble> = synchronized(pendingLock) {
        LastFmPendingCodec.decode(secure.getString(KEY_PENDING))
    }

    fun replacePending(scrobbles: List<PendingScrobble>) = synchronized(pendingLock) {
        val capped = scrobbles.takeLast(LastFmApi.MAX_PENDING_SCROBBLES)
        secure.saveString(KEY_PENDING, LastFmPendingCodec.encode(capped))
    }

    fun addPending(scrobble: PendingScrobble) = synchronized(pendingLock) {
        val pending = LastFmPendingCodec.decode(secure.getString(KEY_PENDING))
        val capped = (pending + scrobble).takeLast(LastFmApi.MAX_PENDING_SCROBBLES)
        secure.saveString(KEY_PENDING, LastFmPendingCodec.encode(capped))
    }

    private companion object {
        const val KEY_ENABLED = "lastfm_enabled"
        const val KEY_API_KEY = "lastfm_api_key"
        const val KEY_API_SECRET = "lastfm_api_secret"
        const val KEY_SESSION_KEY = "lastfm_session_key"
        const val KEY_USERNAME = "lastfm_username"
        const val KEY_PENDING = "lastfm_pending_scrobbles"
    }
}

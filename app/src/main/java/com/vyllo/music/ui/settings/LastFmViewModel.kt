package com.vyllo.music.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vyllo.music.data.lastfm.LastFmApi
import com.vyllo.music.data.lastfm.LastFmApiException
import com.vyllo.music.data.lastfm.LastFmStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LastFmAuthState {
    data object Idle : LastFmAuthState
    data object Connecting : LastFmAuthState
    data class AwaitingAuthorization(val url: String) : LastFmAuthState
    data class Connected(val username: String) : LastFmAuthState
    data class Failed(val reason: LastFmFailure) : LastFmAuthState
}

enum class LastFmFailure {
    MISSING_CREDENTIALS,
    INVALID_CREDENTIALS,
    NETWORK,
    AUTHORIZATION_TIMEOUT
}

/**
 * Drives the Last.fm credential + authorization flow.
 *
 * Last.fm's mobile flow needs no redirect URI: request a token, send the user to
 * the approval page, then poll `auth.getSession` until the user approves.
 */
@HiltViewModel
class LastFmViewModel @Inject constructor(
    private val store: LastFmStore,
    private val api: LastFmApi
) : ViewModel() {

    var apiKey by mutableStateOf(store.apiKey)
        private set
    var apiSecret by mutableStateOf(store.apiSecret)
        private set
    var username by mutableStateOf(store.session()?.username.orEmpty())
        private set
    var enabled by mutableStateOf(store.enabled)
        private set
    var authState by mutableStateOf<LastFmAuthState>(
        store.session()?.let { LastFmAuthState.Connected(it.username) } ?: LastFmAuthState.Idle
    )
        private set

    private var pollJob: Job? = null

    fun updateApiKey(value: String) {
        apiKey = value
    }

    fun updateApiSecret(value: String) {
        apiSecret = value
    }

    fun updateEnabled(value: Boolean) {
        enabled = value
        store.enabled = value
    }

    fun connect() {
        val key = apiKey.trim()
        val secret = apiSecret.trim()
        if (key.isBlank() || secret.isBlank()) {
            authState = LastFmAuthState.Failed(LastFmFailure.MISSING_CREDENTIALS)
            return
        }
        store.saveCredentials(key, secret)
        pollJob?.cancel()
        authState = LastFmAuthState.Connecting
        pollJob = viewModelScope.launch {
            val token = try {
                api.getToken(key, secret)
            } catch (_: LastFmApiException) {
                authState = LastFmAuthState.Failed(LastFmFailure.INVALID_CREDENTIALS)
                return@launch
            } catch (_: Exception) {
                authState = LastFmAuthState.Failed(LastFmFailure.NETWORK)
                return@launch
            }

            authState = LastFmAuthState.AwaitingAuthorization(api.authUrl(key, token))

            repeat(POLL_ATTEMPTS) {
                delay(POLL_INTERVAL_MS)
                try {
                    val session = api.getSession(key, secret, token)
                    store.saveSession(session)
                    username = session.username
                    store.enabled = true
                    enabled = true
                    authState = LastFmAuthState.Connected(session.username)
                    return@launch
                } catch (error: LastFmApiException) {
                    if (error.code != TOKEN_NOT_AUTHORIZED) {
                        authState = LastFmAuthState.Failed(LastFmFailure.INVALID_CREDENTIALS)
                        return@launch
                    }
                } catch (_: Exception) {
                    authState = LastFmAuthState.Failed(LastFmFailure.NETWORK)
                    return@launch
                }
            }
            authState = LastFmAuthState.Failed(LastFmFailure.AUTHORIZATION_TIMEOUT)
        }
    }

    fun disconnect() {
        pollJob?.cancel()
        store.clearSession()
        store.enabled = false
        enabled = false
        username = ""
        authState = LastFmAuthState.Idle
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val POLL_ATTEMPTS = 24
        const val POLL_INTERVAL_MS = 5_000L
        const val TOKEN_NOT_AUTHORIZED = 14
    }
}

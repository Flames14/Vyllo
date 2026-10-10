package com.vyllo.music.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Connectivity as the UI needs to know it: "can we reach the internet right now".
 *
 * Deliberately reads `NET_CAPABILITY_VALIDATED`, not just `INTERNET`. A device that
 * is associated to a Wi-Fi access point but has no route (captive portal, airplane
 * mode handoff, flaky emulator networking) reports `INTERNET` while every request
 * still fails — which would hide the offline banner exactly when it matters.
 */

private fun NetworkCapabilities.hasInternetPath(): Boolean =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

fun ConnectivityManager.isCurrentlyOnline(): Boolean {
    val caps = getNetworkCapabilities(activeNetwork) ?: return false
    return caps.hasInternetPath()
}

/** Hot-free cold flow of connectivity, emitting the current state on collection. */
fun ConnectivityManager.onlineFlow(): Flow<Boolean> = callbackFlow {
    val manager = this@onlineFlow

    // Seed immediately so collectors never sit on "unknown" until the first callback.
    trySend(manager.isCurrentlyOnline())

    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val caps = manager.getNetworkCapabilities(network)
            trySend(caps != null && caps.hasInternetPath())
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            trySend(capabilities.hasInternetPath())
        }

        override fun onLost(network: Network) {
            // Another network may still be carrying traffic (Wi-Fi -> cellular).
            trySend(manager.isCurrentlyOnline())
        }

        override fun onUnavailable() {
            trySend(false)
        }
    }

    registerDefaultNetworkCallback(callback)
    awaitClose { runCatching { unregisterNetworkCallback(callback) } }
}.distinctUntilChanged()

/**
 * Observes connectivity for the lifetime of the composition.
 *
 * Offline is the *starting* assumption only until the first callback lands;
 * [fallbackWhenUnknown] is true so a device that cannot answer at all (no
 * CONNECTIVITY_SERVICE, e.g. in a Robolectric-ish host) does not render the app
 * as permanently offline.
 */
@Composable
fun rememberIsOnline(fallbackWhenUnknown: Boolean = true): Boolean {
    val context = LocalContext.current
    val connectivityManager = remember(context) {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }
    var online by remember(connectivityManager) {
        mutableStateOf(
            connectivityManager?.isCurrentlyOnline() ?: fallbackWhenUnknown
        )
    }

    LaunchedEffect(connectivityManager) {
        val manager = connectivityManager ?: return@LaunchedEffect
        manager.onlineFlow().collect { online = it }
    }

    return online
}

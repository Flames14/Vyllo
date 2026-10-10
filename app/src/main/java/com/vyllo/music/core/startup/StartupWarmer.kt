package com.vyllo.music.core.startup

import android.content.Context
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.data.network.YouTubeDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the cold-start work that the *first user action* will need, but that the
 * *first rendered frame* must never wait for.
 *
 * Everything here is fire-and-forget on [Dispatchers.IO]: the Application's
 * onCreate stays non-blocking so app launch, the splash hand-off and the first
 * Compose frame are untouched. Failures are logged and swallowed — the
 * on-demand paths (extractor init, poToken generation) remain authoritative.
 */
@Singleton
class StartupWarmer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var started = false

    /**
     * Boots NewPipe's static state and pre-warms the poToken WebView generator.
     *
     * NewPipe static init is pure CPU/IO, and the poToken generator is a
     * multi-second WebView dance on first use; both used to happen while the
     * user waited for their first track. Doing them once here removes that from
     * the first-play critical path.
     */
    fun warmUpAsync() {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
        }
        scope.launch {
            try {
                YouTubeDataSource.warmUpPoToken(context)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                SecureLogger.w("StartupWarmer", "Background warm-up failed: ${t.message}")
            }
        }
    }
}

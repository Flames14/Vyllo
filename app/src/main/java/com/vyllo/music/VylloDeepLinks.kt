package com.vyllo.music

import android.content.Intent
import android.net.Uri

/**
 * Single place that maps inbound Intents to in-app routes.
 *
 * Owns the `com.vyllo.music://open/<destination>` scheme used by the launcher
 * shortcuts and the quick-settings tile (and, later, shared content links).
 * Keeping the parsing here means [MainActivity] stays a thin host instead of
 * growing one branch per launch source, and the URI shapes can be unit-tested.
 */
object VylloDeepLinks {
    const val SCHEME = "com.vyllo.music"
    const val HOST_OPEN = "open"
    const val HOST_OAUTH = "oauth2redirect"

    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val RECOGNITION = "recognition"

    /** Builds an `open` link, e.g. `open("search")` -> com.vyllo.music://open/search. */
    fun open(vararg segments: String): Uri =
        Uri.parse("$SCHEME://$HOST_OPEN/${segments.joinToString("/")}")

    /** True when the intent is the Google OAuth redirect this app registered for. */
    fun isOAuthRedirect(intent: Intent?): Boolean {
        val data = intent?.data ?: return false
        return data.scheme == SCHEME && data.host == HOST_OAUTH
    }

    /** In-app route for an `open` link, or null when the intent is not one we own. */
    fun routeFor(intent: Intent?): String? {
        val data = intent?.data ?: return null
        return routeFor(data.scheme, data.host, data.pathSegments.firstOrNull())
    }

    /**
     * Pure matcher over the URI parts. Kept free of Android types so the matching
     * rules can be unit-tested on the JVM (Uri/Intent are not real under the local
     * test classpath).
     */
    fun routeFor(scheme: String?, host: String?, firstSegment: String?): String? {
        if (scheme != SCHEME || host != HOST_OPEN) return null
        return when (firstSegment) {
            SEARCH -> VylloRoutes.SEARCH
            LIBRARY -> VylloRoutes.LIBRARY
            RECOGNITION -> VylloRoutes.RECOGNITION
            else -> null
        }
    }
}

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
    const val PATH_TRACK = "track"

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

    /**
     * Video id carried by an inbound intent: either our own
     * `open/track/<id>` link or one of the shared/pasted YouTube URL shapes
     * (WATCH, youtu.be, SHORTS) that the share target and the browser handle
     * deliver. Null when the intent carries no usable content link.
     */
    fun videoIdFor(intent: Intent?): String? = when (intent?.action) {
        Intent.ACTION_VIEW -> videoIdInUri(intent)
        Intent.ACTION_SEND -> videoIdInText(intent.getStringExtra(Intent.EXTRA_TEXT))
        else -> null
    }

    private fun videoIdInUri(intent: Intent): String? {
        val uri = intent.data ?: return null
        // Our own track links carry the raw id; everything else is a YouTube URL.
        trackIdFor(uri.scheme, uri.host, uri.pathSegments)?.let { return it }
        return videoIdForLink(uri.toString())
    }

    /** Id inside our own `com.vyllo.music://open/track/<id>` shape. */
    fun trackIdFor(scheme: String?, host: String?, segments: List<String>?): String? {
        if (scheme != SCHEME || host != HOST_OPEN) return null
        val id = segments?.takeIf { it.size >= 2 && it[0] == PATH_TRACK }?.get(1) ?: return null
        return id.takeIf { isLikelyVideoId(it) }
    }

    /** First YouTube watch/shorts link inside arbitrary share text, if any. */
    fun videoIdInText(text: String?): String? {
        val first = URL_IN_TEXT.find(text ?: "")?.value
            ?.trimEnd('.', ',', '!', '?', ')', ']', '}', '\'')
            ?: return null
        return videoIdForLink(first)
    }

    /** Video id from a YouTube watch / youtu.be / shorts link, else null. */
    fun videoIdForLink(link: String): String? {
        val url = URL_IN_TEXT.find(link)?.value
            ?.trimEnd('.', ',', '!', '?', ')', ']', '}', '\'')
            ?: return null
        val rest = url.substringAfter("//", "")
        if (rest.isEmpty()) return null

        val hostPart = rest.substringBefore('/').lowercase().substringBefore(':')
        val splitAt = rest.indexOf('/')
        val pathAndQuery = if (splitAt >= 0) rest.substring(splitAt) else ""
        val path = pathAndQuery.substringBefore('?')
        val query = pathAndQuery.substringAfter('?', "")
        val vParam = query.split('&')
            .map { it.substringBefore('=') to it.substringAfter('=', missingDelimiterValue = "") }
            .firstOrNull { it.first == "v" }?.second
        val segments = path.split('/').filter { it.isNotBlank() }

        val candidate = when {
            hostPart == "youtu.be" -> segments.firstOrNull()
            hostPart in YOUTUBE_HOSTS -> when (segments.firstOrNull()) {
                "watch" -> vParam
                "shorts" -> segments.getOrNull(1)
                else -> null
            }
            else -> null
        }
        return candidate?.takeIf { isLikelyVideoId(it) }
    }

    /** Canonical watch URL the stream layer resolves (and caches) for an id. */
    fun watchUrlFor(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"

    private const val ID_LENGTH = 11

    /** Real video ids are exactly 11 chars; this also rejects playlist ids. */
    internal fun isLikelyVideoId(candidate: String?): Boolean =
        candidate != null &&
            candidate.length == ID_LENGTH &&
            candidate.all { it.isLetterOrDigit() || it == '-' || it == '_' }

    private val YOUTUBE_HOSTS = setOf(
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "music.youtube.com"
    )

    private val URL_IN_TEXT = Regex("https?://[\\S]+")
}

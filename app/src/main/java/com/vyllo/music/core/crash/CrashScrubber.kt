package com.vyllo.music.core.crash

/**
 * Redacts anything secret-looking before a stack trace is written to disk.
 *
 * Crash messages routinely carry values the app never intends to persist:
 * stream URLs with tokens, OAuth fragments, email addresses, authorization
 * headers surfaced by an OkHttp exception. Reports live in app-private storage
 * and are shared by the user, so anything privacy-relevant is stripped first.
 *
 * Deliberately conservative and allocation-cheap: it runs on the crashing
 * thread while the process is dying.
 */
internal object CrashScrubber {

    private val bearer = Regex("""(?i)\b(bearer\s+)[A-Za-z0-9._\-+/=]{8,}""")
    private val kvSecrets = Regex(
        """(?i)\b(token|access_token|refresh_token|apikey|api_key|key|secret|password|passwd|auth|authorization|signature|sig|pode|po_token)\b\s*[=:]\s*["']?([^\s"',;&}]{6,})["']?"""
    )
    private val jwt = Regex("""\beyJ[A-Za-z0-9_\-]{4,}\.[A-Za-z0-9_\-]{4,}\.[A-Za-z0-9_\-]{4,}\b""")
    private val urlCredentials = Regex("""(?i)://([^:/@\s]+):([^@\s]+)@""")
    private val urlQuerySecrets = Regex(
        """(?i)([?&](?:token|access_token|sig|signature|key|api_key|po_token|expire)=)[^&\s"']+"""
    )
    private val email = Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b""")
    private val ipv4 = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")

    fun scrub(input: String): String {
        var out = input
        out = bearer.replace(out, "$1<redacted>")
        out = urlCredentials.replace(out, "://<redacted>:<redacted>@")
        out = urlQuerySecrets.replace(out, "$1<redacted>")
        out = jwt.replace(out, "<redacted-jwt>")
        out = kvSecrets.replace(out) { m -> "${m.groupValues[1]}=<redacted>" }
        out = email.replace(out, "<redacted-email>")
        out = ipv4.replace(out, "<redacted-ip>")
        return out
    }
}

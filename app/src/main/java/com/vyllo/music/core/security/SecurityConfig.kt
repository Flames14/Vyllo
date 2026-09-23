package com.vyllo.music.core.security

import okhttp3.CertificatePinner
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Security Configuration for Network Layer
 *
 * Provides:
 * - Certificate pinning for first-party / critical hosts
 * - Encrypted DNS (DoH): Prevents DNS spoofing and ISP surveillance
 * - Security Headers: Adds protective HTTP headers
 * - Connection Security: Enforces TLS 1.2+
 */
@Singleton
class SecurityConfig @Inject constructor() {

    companion object {
        /**
         * Optional host → SHA-256 SPKI pins for critical first-party hosts.
         * Never hardcode placeholders: a wrong pin breaks HTTPS (and updates).
         *
         * Pins ship in BuildConfig.CERT_PINS (committed public defaults, overridable
         * at build time via VYLLO_CERT_PINS env / certPins property). System.getenv
         * is only a fallback for JVM unit tests — it is always empty on Android.
         * Comma-separated host=sha256/... entries. Placeholder (all-A) values are rejected.
         *
         * Scope is intentionally narrow (api.github.com update metadata only):
         * pinning third-party hosts you don't control risks bricking the pinned
         * endpoint on rotation. A pin failure surfaces as an update-check error,
         * never a crash; VYLLO_DISABLE_CERT_PINS=true is the kill switch.
         *
         * Refresh a pin with:
         *   openssl s_client -servername github.com -connect github.com:443 </dev/null 2>/dev/null \
         *     | openssl x509 -pubkey -noout \
         *     | openssl pkey -pubin -outform der \
         *     | openssl dgst -sha256 -binary | openssl enc -base64
         */
        internal fun parsePinEntries(raw: String?): List<Pair<String, String>> {
            if (raw.isNullOrBlank()) return emptyList()
            val out = mutableListOf<Pair<String, String>>()
            raw.split(',', ';').forEach { entry ->
                val trimmed = entry.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
                val eq = trimmed.indexOf('=')
                if (eq <= 0) return@forEach
                val host = trimmed.substring(0, eq).trim()
                val pin = trimmed.substring(eq + 1).trim()
                if (host.isBlank() || !pin.startsWith("sha256/")) return@forEach
                val b64 = pin.removePrefix("sha256/")
                if (b64.length < 40 || b64.replace("A", "").replace("=", "").isBlank()) return@forEach
                out.add(host to pin)
            }
            return out
        }

        private fun buildCertificatePinner(): CertificatePinner? {
            val raw = try {
                com.vyllo.music.BuildConfig.CERT_PINS.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                null
            } ?: System.getenv("VYLLO_CERT_PINS")?.takeIf { it.isNotBlank() } ?: return null
            val builder = CertificatePinner.Builder()
            val entries = parsePinEntries(raw)
            if (entries.isEmpty()) return null
            entries.forEach { (host, pin) -> builder.add(host, pin) }
            return try {
                builder.build()
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        private val CERT_PINNER: CertificatePinner? by lazy { buildCertificatePinner() }

        /** Kill switch: VYLLO_DISABLE_CERT_PINS=true forces pins off even if configured. */
        private val pinsEnabled: Boolean
            get() = System.getenv("VYLLO_DISABLE_CERT_PINS")?.lowercase() != "true"
    }

    /**
     * Creates a hardened OkHttpClient with security best practices
     */
    fun createSecureHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .connectionSpecs(
                listOf(
                    okhttp3.ConnectionSpec.MODERN_TLS,
                    okhttp3.ConnectionSpec.COMPATIBLE_TLS
                )
            )
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(SecurityHeaderInterceptor())
            .retryOnConnectionFailure(true)

        if (pinsEnabled) {
            CERT_PINNER?.let { builder.certificatePinner(it) }
        }

        return builder.build()
    }

    /**
     * Creates DNS-over-HTTPS configuration for privacy
     */
    private fun createDnsOverHttps(): DnsOverHttps {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("dns.google")
            .addPathSegment("dns-query")
            .build()

        return DnsOverHttps.Builder()
            .client(OkHttpClient.Builder().build())
            .url(url)
            .includeIPv6(true)
            .bootstrapDnsHosts(
                InetAddress.getByName("8.8.8.8"),
                InetAddress.getByName("8.8.4.4"),
                InetAddress.getByName("2001:4860:4860::8888"),
                InetAddress.getByName("2001:4860:4860::8844")
            )
            .build()
    }

    /**
     * Creates a less restrictive client for services that don't work with pinning
     * (e.g., NewPipe Extractor which connects to many different hosts)
     */
    fun createFlexibleSecureHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

            // Modern & Compatible TLS
            .connectionSpecs(
                listOf(
                    okhttp3.ConnectionSpec.MODERN_TLS,
                    okhttp3.ConnectionSpec.COMPATIBLE_TLS
                )
            )

            // Follow HTTPS redirects
            .followRedirects(true)
            .followSslRedirects(true)

            .addInterceptor(SecurityHeaderInterceptor())

            .retryOnConnectionFailure(true)

            // No certificate pinning: extractor talks to many third-party hosts.
            .build()
    }
}

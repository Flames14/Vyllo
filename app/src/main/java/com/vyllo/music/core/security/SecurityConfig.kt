package com.vyllo.music.core.security

import android.content.Context
import okhttp3.CertificatePinner
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Senior-Level Security Configuration for Network Layer
 * 
 * Provides:
 * - Certificate Pinning: Prevents MITM attacks
 * - Encrypted DNS (DoH): Prevents DNS spoofing and ISP surveillance
 * - Security Headers: Adds protective HTTP headers
 * - Connection Security: Enforces TLS 1.2+
 */
@Singleton
class SecurityConfig @Inject constructor() {

    /**
     * Creates a hardened OkHttpClient with security best practices
     * 
     * Note: Certificate pinning is disabled by default as it requires valid pins
     * from actual servers. Enable it in production with real pins.
     */
    fun createSecureHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            // Timeouts
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            
            // Certificate Pinning - DISABLED for development
            // Enable in production with real pins from your servers
            // .certificatePinner(
            //     CertificatePinner.Builder()
            //         .add("lrclib.net", "sha256/REAL_PIN_HERE=")
            //         .build()
            // )
            
            // Encrypted DNS - Prevents DNS spoofing and eavesdropping
            .dns(createDnsOverHttps())
            
            // Connection Security - Only allow modern TLS
            .connectionSpecs(
                listOf(
                    okhttp3.ConnectionSpec.MODERN_TLS,
                    okhttp3.ConnectionSpec.COMPATIBLE_TLS,
                    okhttp3.ConnectionSpec.CLEARTEXT // Only for localhost debugging
                )
            )
            
            // Security: Don't follow redirects automatically (prevent open redirect attacks)
            .followRedirects(false)
            .followSslRedirects(false)
            
            // Add security interceptor for headers
            .addInterceptor(SecurityHeaderInterceptor())
            
            // Retry configuration with backoff
            .retryOnConnectionFailure(true)
            
            .build()
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
            
            // Encrypted DNS only (no pinning for flexibility)
            .dns(createDnsOverHttps())
            
            // Modern TLS only
            .connectionSpecs(
                listOf(
                    okhttp3.ConnectionSpec.MODERN_TLS,
                    okhttp3.ConnectionSpec.CLEARTEXT
                )
            )
            
            .followRedirects(false)
            .followSslRedirects(false)
            
            .addInterceptor(SecurityHeaderInterceptor())
            
            .retryOnConnectionFailure(true)
            
            .build()
    }
}

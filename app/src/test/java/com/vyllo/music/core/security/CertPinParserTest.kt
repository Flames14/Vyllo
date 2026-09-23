package com.vyllo.music.core.security

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the cert-pin entry parser.
 * Pins are public key hashes; the fixtures below are synthetic but
 * structurally valid (44-char base64 SHA-256).
 */
class CertPinParserTest {

    private val goodPin = "sha256/S2LUIbq4yUg5w+MYbj5LZOWAZAzaeNGJ9rTTc4GjvBQ="

    @Test
    fun parsesMultiplePinsForSameHost() {
        val entries = SecurityConfig.parsePinEntries(
            "api.github.com=$goodPin,api.github.com=sha256/ZSagvDzjltLkewXEBuDxIzpW/dpVw1Juvvmd0hhkzdY="
        )
        assertEquals(2, entries.size)
        assertTrue(entries.all { it.first == "api.github.com" })
    }

    @Test
    fun rejectsPlaceholdersAndGarbage() {
        val entries = SecurityConfig.parsePinEntries(
            "api.github.com=sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=," +
                "bad-entry,=sha256/xyz,example.com=http://not-a-pin,# comment, ; "
        )
        assertTrue(entries.isEmpty())
    }

    @Test
    fun emptyInputYieldsNoEntries() {
        assertTrue(SecurityConfig.parsePinEntries(null).isEmpty())
        assertTrue(SecurityConfig.parsePinEntries("  ").isEmpty())
    }

    @Test
    fun shippedDefaultPinsParse() {
        val raw = com.vyllo.music.BuildConfig.CERT_PINS
        val entries = SecurityConfig.parsePinEntries(raw)
        assertEquals(3, entries.size)
        assertTrue(entries.all { it.first == "api.github.com" })
    }
}

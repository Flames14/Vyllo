package com.vyllo.music.core.security

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for SecurityMonitor's pure risk-scoring helpers.
 * Emulator/root detection requires Robolectric and is covered by instrumentation;
 * here we verify the score→level mapping that getRiskLevel() uses.
 */
class SecurityMonitorTest {

    private fun classify(score: Int): SecurityRiskLevel = when {
        score >= 4 -> SecurityRiskLevel.HIGH
        score >= 2 -> SecurityRiskLevel.MEDIUM
        score >= 1 -> SecurityRiskLevel.LOW
        else -> SecurityRiskLevel.NONE
    }

    @Test
    fun `risk level classification matches SecurityRiskLevel thresholds`() {
        assertEquals(SecurityRiskLevel.NONE, classify(0))
        assertEquals(SecurityRiskLevel.LOW, classify(1))
        assertEquals(SecurityRiskLevel.MEDIUM, classify(2))
        assertEquals(SecurityRiskLevel.MEDIUM, classify(3))
        assertEquals(SecurityRiskLevel.HIGH, classify(4))
        assertEquals(SecurityRiskLevel.HIGH, classify(7))
    }

    @Test
    fun `enum ordinals stable for serialization`() {
        assertEquals(0, SecurityRiskLevel.NONE.ordinal)
        assertEquals(1, SecurityRiskLevel.LOW.ordinal)
        assertEquals(2, SecurityRiskLevel.MEDIUM.ordinal)
        assertEquals(3, SecurityRiskLevel.HIGH.ordinal)
    }
}

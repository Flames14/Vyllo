package com.vyllo.music.data.network.potoken

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PoTokenJsValidationTest {

    @Test
    fun `accepts the machine-generated Uint8Array literal`() {
        assertTrue(isValidUint8ArrayLiteral("new Uint8Array([0,1,2,255])"))
        assertTrue(isValidUint8ArrayLiteral("new Uint8Array([ 7 , 008 ])"))
    }

    @Test
    fun `rejects payloads that break out of the literal`() {
        assertFalse(isValidUint8ArrayLiteral("new Uint8Array([1]);evil()"))
        assertFalse(isValidUint8ArrayLiteral("new Uint8Array([1]),evil([2])"))
        assertFalse(isValidUint8ArrayLiteral("evil(new Uint8Array([1])"))
        assertFalse(isValidUint8ArrayLiteral("alert(1)"))
    }

    @Test
    fun `rejects out-of-range and non-integer elements`() {
        assertFalse(isValidUint8ArrayLiteral("new Uint8Array([256])"))
        assertFalse(isValidUint8ArrayLiteral("new Uint8Array([1e5])"))
        assertFalse(isValidUint8ArrayLiteral("new Uint8Array([-1])"))
        assertFalse(isValidUint8ArrayLiteral("new Uint8Array([])"))
    }

    @Test
    fun `bounds oversized element lists`() {
        val atLimit = "new Uint8Array([" + List(MAX_UINT8_ARRAY_ELEMENTS) { "0" }.joinToString(",") + "])"
        val overLimit = "new Uint8Array([" + List(MAX_UINT8_ARRAY_ELEMENTS + 1) { "0" }.joinToString(",") + "])"
        assertTrue(isValidUint8ArrayLiteral(atLimit))
        assertFalse(isValidUint8ArrayLiteral(overLimit))
    }

    @Test
    fun `challenge value must be a single non-empty json object or array`() {
        assertTrue(isValidJsonValue("""{"challenge":"abc"}"""))
        assertTrue(isValidJsonValue("""[1,2,3]"""))
        assertFalse(isValidJsonValue("{}"))
        assertFalse(isValidJsonValue("[]"))
        assertFalse(isValidJsonValue("plain text"))
        assertFalse(isValidJsonValue("{"))
    }

    @Test
    fun `rejects trailing code after an otherwise valid json value`() {
        assertFalse(isValidJsonValue("""{"a":1};evil()"""))
        assertFalse(isValidJsonValue("""["a"]alert(2)"""))
        assertFalse(isValidJsonValue("""{"a":1}{"b":2}"""))
    }
}

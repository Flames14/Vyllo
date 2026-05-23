package com.vyllo.music.data.network.potoken

import org.json.JSONArray
import org.json.JSONObject
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import java.nio.charset.StandardCharsets

/**
 * Parses the raw challenge data obtained from the Create endpoint and returns an object that can be
 * embedded in a JavaScript snippet.
 */
fun parseChallengeData(rawChallengeData: String): String {
    val scrambled = JSONArray(rawChallengeData)

    val optVal1 = scrambled.opt(1)
    val challengeData = if (scrambled.length() > 1 && optVal1 is String) {
        val descrambled = descramble(optVal1)
        JSONArray(descrambled)
    } else {
        scrambled.getJSONArray(0)
    }

    val messageId = challengeData.getString(0)
    val interpreterHash = challengeData.getString(3)
    val program = challengeData.getString(4)
    val globalName = challengeData.getString(5)
    val clientExperimentsStateBlob = challengeData.getString(7)

    val optArray1 = challengeData.optJSONArray(1)
    var privateDoNotAccessOrElseSafeScriptWrappedValue: String? = null
    if (optArray1 != null) {
        for (i in 0 until optArray1.length()) {
            val item = optArray1.opt(i)
            if (item is String) {
                privateDoNotAccessOrElseSafeScriptWrappedValue = item
                break
            }
        }
    }

    val optArray2 = challengeData.optJSONArray(2)
    var privateDoNotAccessOrElseTrustedResourceUrlWrappedValue: String? = null
    if (optArray2 != null) {
        for (i in 0 until optArray2.length()) {
            val item = optArray2.opt(i)
            if (item is String) {
                privateDoNotAccessOrElseTrustedResourceUrlWrappedValue = item
                break
            }
        }
    }

    val interpreterJavascript = JSONObject().apply {
        put("privateDoNotAccessOrElseSafeScriptWrappedValue", privateDoNotAccessOrElseSafeScriptWrappedValue ?: JSONObject.NULL)
        put("privateDoNotAccessOrElseTrustedResourceUrlWrappedValue", privateDoNotAccessOrElseTrustedResourceUrlWrappedValue ?: JSONObject.NULL)
    }

    return JSONObject().apply {
        put("messageId", messageId)
        put("interpreterJavascript", interpreterJavascript)
        put("interpreterHash", interpreterHash)
        put("program", program)
        put("globalName", globalName)
        put("clientExperimentsStateBlob", clientExperimentsStateBlob)
    }.toString()
}

/**
 * Parses the raw integrity token data obtained from the GenerateIT endpoint to a JavaScript
 * `Uint8Array` that can be embedded directly in JavaScript code, and a [Long] representing the
 * duration of this token in seconds.
 */
fun parseIntegrityTokenData(rawIntegrityTokenData: String): Pair<String, Long> {
    val integrityTokenData = JSONArray(rawIntegrityTokenData)
    return base64ToU8(integrityTokenData.getString(0)) to integrityTokenData.getLong(1)
}

/**
 * Converts a string (usually the identifier used as input to `obtainPoToken`) to a JavaScript
 * `Uint8Array` that can be embedded directly in JavaScript code.
 */
fun stringToU8(identifier: String): String {
    return newUint8Array(identifier.toByteArray(StandardCharsets.UTF_8))
}

/**
 * Takes a poToken encoded as a sequence of bytes represented as integers separated by commas
 * (e.g. "97,98,99" would be "abc"), which is the output of `Uint8Array::toString()` in JavaScript,
 * and converts it to the specific base64 representation for poTokens.
 */
fun u8ToBase64(poToken: String): String {
    return poToken.split(",")
        .map { it.toUByte().toByte() }
        .toByteArray()
        .toByteString()
        .base64()
        .replace("+", "-")
        .replace("/", "_")
}

/**
 * Takes the scrambled challenge, decodes it from base64, adds 97 to each byte.
 */
private fun descramble(scrambledChallenge: String): String {
    return base64ToByteString(scrambledChallenge)
        .map { (it + 97).toByte() }
        .toByteArray()
        .decodeToString()
}

/**
 * Decodes a base64 string encoded in the specific base64 representation used by YouTube, and
 * returns a JavaScript `Uint8Array` that can be embedded directly in JavaScript code.
 */
private fun base64ToU8(base64: String): String {
    return newUint8Array(base64ToByteString(base64))
}

private fun newUint8Array(contents: ByteArray): String {
    return "new Uint8Array([" + contents.joinToString(separator = ",") { it.toUByte().toString() } + "])"
}

/**
 * Decodes a base64 string encoded in the specific base64 representation used by YouTube.
 */
private fun base64ToByteString(base64: String): ByteArray {
    val base64Mod = base64
        .replace('-', '+')
        .replace('_', '/')
        .replace('.', '=')

    return (base64Mod.decodeBase64() ?: throw PoTokenException("Cannot base64 decode"))
        .toByteArray()
}

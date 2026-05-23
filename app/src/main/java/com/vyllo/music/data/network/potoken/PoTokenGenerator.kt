package com.vyllo.music.data.network.potoken

import android.content.Context
import java.io.Closeable

/**
 * Interface to generate poTokens. Modified to support Kotlin Coroutines.
 */
interface PoTokenGenerator : Closeable {
    /**
     * Generates a poToken for the provided identifier.
     */
    suspend fun generatePoToken(identifier: String): String

    /**
     * @return whether the token is expired.
     */
    fun isExpired(): Boolean

    interface Factory {
        /**
         * Asynchronously creates and initializes a PoTokenGenerator.
         */
        suspend fun newPoTokenGenerator(context: Context): PoTokenGenerator
    }
}

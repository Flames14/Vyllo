package com.vyllo.music.domain.model

/**
 * Result of a playback operation.
 */
sealed interface PlayResult {
    object Success : PlayResult
    data class Failure(val message: String) : PlayResult
}

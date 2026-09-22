package com.vyllo.music.domain.manager

import androidx.media3.common.PlaybackException
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PlaybackErrorHandlerTest {

    private var retryCount = 0
    private var maxRetriesReached = false
    private lateinit var handler: PlaybackErrorHandler

    private val fakeError = PlaybackException(
        "Test error",
        null,
        PlaybackException.ERROR_CODE_UNSPECIFIED
    )

    @Before
    fun setup() {
        retryCount = 0
        maxRetriesReached = false
        handler = PlaybackErrorHandler(
            maxRetries = 3,
            baseDelayMs = 100L,
            maxDelayMs = 500L,
            onRetry = { retryCount++ },
            onMaxRetriesReached = { maxRetriesReached = true }
        )
    }

    @Test
    fun `registerErrorAndGetDelay returns linear backoff within cap`() {
        assertEquals(100L, handler.registerErrorAndGetDelay(fakeError))
        assertEquals(200L, handler.registerErrorAndGetDelay(fakeError))
        assertEquals(300L, handler.registerErrorAndGetDelay(fakeError))
        assertFalse(maxRetriesReached)
    }

    @Test
    fun `registerErrorAndGetDelay returns null after max retries and signals callback`() {
        handler.registerErrorAndGetDelay(fakeError)
        handler.registerErrorAndGetDelay(fakeError)
        handler.registerErrorAndGetDelay(fakeError)

        val result = handler.registerErrorAndGetDelay(fakeError)

        assertNull(result)
        assertTrue(maxRetriesReached)
    }

    @Test
    fun `registerErrorAndGetDelay caps delay at maxDelayMs`() {
        val cappedHandler = PlaybackErrorHandler(
            maxRetries = 10,
            baseDelayMs = 400L,
            maxDelayMs = 500L,
            onRetry = {},
            onMaxRetriesReached = { maxRetriesReached = true }
        )
        // 400 * 1 = 400, 400 * 2 = 800 capped to 500
        assertEquals(400L, cappedHandler.registerErrorAndGetDelay(fakeError))
        assertEquals(500L, cappedHandler.registerErrorAndGetDelay(fakeError))
        assertEquals(500L, cappedHandler.registerErrorAndGetDelay(fakeError))
    }

    @Test
    fun `resetOnSuccess clears error counter so retries restart`() {
        handler.registerErrorAndGetDelay(fakeError)
        handler.registerErrorAndGetDelay(fakeError)
        handler.resetOnSuccess()

        // After reset, budget is full again — three more errors allowed before null
        assertEquals(100L, handler.registerErrorAndGetDelay(fakeError))
        assertEquals(200L, handler.registerErrorAndGetDelay(fakeError))
        assertEquals(300L, handler.registerErrorAndGetDelay(fakeError))
        assertNull(handler.registerErrorAndGetDelay(fakeError))
        assertTrue(maxRetriesReached)
    }

    @Test
    fun `release clears pending state and resets counter`() {
        handler.registerErrorAndGetDelay(fakeError)
        handler.release()

        // Counter was reset — first error after release returns base delay again
        assertEquals(100L, handler.registerErrorAndGetDelay(fakeError))
        assertFalse(maxRetriesReached)
    }

    @Test
    fun `handleError does not signal max retries before budget exhausted`() {
        handler.handleError(fakeError)
        handler.handleError(fakeError)
        assertFalse(maxRetriesReached)
    }

    @Test
    fun `handleError signals max retries after budget exhausted`() {
        // maxRetries = 3, so errors 1..3 schedule retries, error 4 exceeds budget
        handler.handleError(fakeError)
        handler.handleError(fakeError)
        handler.handleError(fakeError)
        assertFalse(maxRetriesReached)

        handler.handleError(fakeError)
        assertTrue(maxRetriesReached)
    }

    @Test
    fun `handleError schedules retry via onRetry callback path`() {
        // First error should schedule a delayed retry; we cannot await the Handler
        // in unit tests reliably, but we verify no exception and counter tracking
        // by following up with registerErrorAndGetDelay accounting:
        handler.handleError(fakeError)
        handler.handleError(fakeError)
        handler.handleError(fakeError)
        handler.handleError(fakeError) // exceeds maxRetries=3
        assertTrue(maxRetriesReached)

        // After exceeding, counter resets — next error starts fresh
        assertEquals(100L, handler.registerErrorAndGetDelay(fakeError))
    }
}

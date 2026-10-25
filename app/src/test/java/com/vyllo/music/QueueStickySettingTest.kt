package com.vyllo.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueStickySettingTest {
    @Test
    fun `new track follows sticky queue preference`() {
        assertTrue(shouldKeepQueue(fromQueue = false, stickyQueueEnabled = true))
        assertFalse(shouldKeepQueue(fromQueue = false, stickyQueueEnabled = false))
    }

    @Test
    fun `playing an existing queue item always preserves the queue`() {
        assertTrue(shouldKeepQueue(fromQueue = true, stickyQueueEnabled = false))
        assertTrue(shouldKeepQueue(fromQueue = true, stickyQueueEnabled = true))
    }
}

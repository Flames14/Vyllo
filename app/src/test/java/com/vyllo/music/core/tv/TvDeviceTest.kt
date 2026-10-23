package com.vyllo.music.core.tv

import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvDeviceTest {

    @Test
    fun `television ui mode uses the tv layout`() {
        assertTrue(TvDevice.shouldUseTvUi(Configuration.UI_MODE_TYPE_TELEVISION, false))
    }

    @Test
    fun `leanback feature uses the tv layout even on a phone ui mode`() {
        assertTrue(TvDevice.shouldUseTvUi(Configuration.UI_MODE_TYPE_NORMAL, true))
    }

    @Test
    fun `phone without leanback keeps the phone layout`() {
        assertFalse(TvDevice.shouldUseTvUi(Configuration.UI_MODE_TYPE_NORMAL, false))
    }

    @Test
    fun `tablet without leanback keeps the phone layout`() {
        assertFalse(TvDevice.shouldUseTvUi(Configuration.UI_MODE_TYPE_DESK, false))
    }
}

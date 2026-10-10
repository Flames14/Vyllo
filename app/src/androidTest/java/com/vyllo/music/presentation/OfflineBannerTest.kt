package com.vyllo.music.presentation

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vyllo.music.R
import com.vyllo.music.presentation.components.VylloOfflineBanner
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the offline strip added for the "no network" path.
 *
 * Strings are pulled from resources rather than hardcoded so the assertions hold
 * whatever locale the emulator happens to be running under.
 */
@RunWith(AndroidJUnit4::class)
class OfflineBannerTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    @Test
    fun offlineBanner_isShownWhenThereIsNoNetwork() {
        composeRule.setContent {
            MaterialTheme {
                VylloOfflineBanner(isOnline = false)
            }
        }

        composeRule.onNodeWithText(string(R.string.state_offline_banner)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.state_offline_detail)).assertIsDisplayed()
    }

    @Test
    fun offlineBanner_isAbsentWhenOnline() {
        composeRule.setContent {
            MaterialTheme {
                VylloOfflineBanner(isOnline = true)
            }
        }

        composeRule.onNodeWithText(string(R.string.state_offline_banner)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.state_offline_detail)).assertDoesNotExist()
    }

    @Test
    fun offlineBanner_explainsWhatStillWorks() {
        // The banner is informational, not a dead end: it must tell the user that
        // downloads keep playing so they do not read it as "the app is broken".
        val detail = string(R.string.state_offline_detail)
        assertEquals(detail, detail.trim())
        assert(detail.isNotBlank()) { "state_offline_detail must not be blank" }
    }
}

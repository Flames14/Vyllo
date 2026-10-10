package com.vyllo.music.presentation.components.common

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vyllo.music.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The bottom bar is the app's primary navigation, and its labels were the last
 * piece of chrome still hardcoded in English. These tests fail if either the
 * labels stop coming from resources or the tab indices drift.
 */
@RunWith(AndroidJUnit4::class)
class BottomNavTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    @Test
    fun labelsComeFromResources() {
        composeRule.setContent {
            MaterialTheme {
                YTMBottomNavBar(selectedTab = 0, onTabSelected = {})
            }
        }

        composeRule.onNodeWithText(string(R.string.nav_home)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.nav_search)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.nav_explore)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.nav_library)).assertIsDisplayed()
    }

    @Test
    fun tappingATabReportsItsStableIndex() {
        val clicked = mutableListOf<Int>()

        composeRule.setContent {
            MaterialTheme {
                YTMBottomNavBar(selectedTab = 0, onTabSelected = { clicked.add(it) })
            }
        }

        composeRule.onNodeWithText(string(R.string.nav_search)).performClick()
        composeRule.onNodeWithText(string(R.string.nav_library)).performClick()
        composeRule.onNodeWithText(string(R.string.nav_home)).performClick()

        // Index — not label — is the navigation contract, so reordering localized
        // labels must never reorder the callbacks.
        assertEquals(listOf(1, 3, 0), clicked)
    }
}

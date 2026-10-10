package com.vyllo.music.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app's Baseline Profile by exercising the real critical user
 * journeys on device. The profile AOT-compiles hot paths (startup, home feed
 * scroll, search, library, player open) so first runs don't JIT-jank —
 * the single biggest "iPhone-smooth" lever on Android.
 *
 * Every step is best-effort: a stale UI handle must degrade that step, never
 * abort collection (a thrown test emits no profile at all).
 *
 * Run: ./gradlew :baseline-profile:connectedNonMinifiedReleaseAndroidTest
 * then: ./gradlew :baseline-profile:collectNonMinifiedReleaseBaselineProfile
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "com.vyllo.music",
        includeInStartupProfile = true
    ) {
        step("cold start") {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.text("Library")), 15_000)
        }

        // Deep Home scroll: slow drags (not flings) so below-the-fold sections,
        // grid columns, large cards and the 200-item recommended list all compose
        // while content loads. Flings outrun network and skip coverage of item types
        // that only appear deeper (the obfuscated row/card paths seen JIT-compiling
        // in scroll traces). No idle-wait: composition is synchronous per scroll.
        step("home deep scroll down") {
            repeat(25) {
                device.findObject(By.scrollable(true))
                    ?.scroll(Direction.DOWN, 0.8f, 60)
            }
        }
        step("home deep scroll up") {
            repeat(10) {
                device.findObject(By.scrollable(true))
                    ?.scroll(Direction.UP, 0.8f, 60)
            }
        }

        // Player paths (mini-player, queue, related): best-effort. Taps a song-sized
        // clickable to start playback, expands the player, scrolls the queue.
        // Any failure degrades silently — Home coverage above is the critical part.
        step("play a song") {
            device.findObjects(By.clickable(true))
                .firstOrNull { it.visibleBounds.height() in 100..400 }
                ?.click()
            device.waitForIdle(8_000)
        }
        step("expand player and scroll queue") {
            // Mini-player sits above the bottom nav; tap its region to expand.
            device.click(device.displayWidth / 2, device.displayHeight - 220)
            device.waitForIdle(5_000)
            repeat(3) {
                device.findObject(By.scrollable(true))?.fling(Direction.DOWN)
                device.waitForIdle(2_000)
            }
            device.pressBack()
            device.waitForIdle(2_000)
        }

        // Search tab warm-up.
        step("search tab") {
            device.findObject(By.text("Search"))?.click()
            device.waitForIdle()
        }

        // Library tab warm-up.
        step("library tab") {
            device.findObject(By.text("Library"))?.click()
            device.waitForIdle()
        }

        step("back home") { pressHome() }
    }

    private inline fun step(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            android.util.Log.w("BaselineProfile", "step '$name' degraded: ${e.message}")
        }
    }
}

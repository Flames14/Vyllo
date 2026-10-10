package com.vyllo.music.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Home feed scroll jank benchmark.
 *
 * Two separate gestures because they fail for different reasons:
 *
 *  - [homeSlowScroll] reproduces the reported bug directly: a slow drag, where every
 *    frame must land inside the 16.67 ms budget or the eye reads it as jitter. Slow
 *    scrolling is *harder* than flinging on a 60 Hz budget, because a fling amortises
 *    its cost over the deceleration curve while a drag pays full cost every frame.
 *
 *  - [homeFlingScroll] exercises the composition/decode bursts: sections entering the
 *    viewport mid-motion, nested LazyRow materialisation, and image pre-fetch churn.
 *
 * Compilation mode: `Partial(UseIfAvailable)` is deliberately chosen so a single test
 * is a valid A/B for the baseline profile.
 *
 * TEMPORARY (device gate): this phone (MIUI) currently denies every `adb install`
 * with INSTALL_FAILED_USER_RESTRICTED, and `Partial` reinstalls the APK every
 * iteration — so it cannot run here yet. Until "Install via USB" actually sticks,
 * this uses `None`, which measures the already-installed APK as-is (no reinstall,
 * no ART reset). That is *more* representative of real-world warm use, just noisier.
 * Switch back to `Partial(BaselineProfileMode.UseIfAvailable)` once adb installs work;
 * it will then automatically A/B the generated profile.
 *
 *   - Today (no profile is wired into `:app`) this compiles an empty speed-profile,
 *     which is exactly what users get from the current release APK.
 *   - Once Phase 4.1 lands, the same test automatically applies the generated profile.
 *
 * Because `Partial` reinstalls the APK every iteration, both sides of that comparison
 * start from identical ART state — so the only variable is the profile itself.
 *
 * Run (device must be authorized for adb):
 *   ./gradlew :baseline-profile:connectedBenchmarkAndroidTest
 *
 * Results land in baseline-profile/build/outputs/connected_android_test_additional_output/
 */
@RunWith(AndroidJUnit4::class)
class ScrollHomeBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun homeSlowScroll() = measureHomeScroll {
        // High step count = slow gesture. This is the motion the user reports as jitter.
        // The scrollable is re-found before every gesture: if content reloads
        // (infinite-scroll append) or the activity recreates mid-iteration, a held
        // UiObject2 goes stale and the run aborts. Re-finding is cheap; redoing a
        // 12-minute run is not.
        repeat(SLOW_SCROLL_PASSES) {
            scrollSafely(Direction.DOWN)
            device.waitForIdle(IDLE_TIMEOUT_MS)
            scrollSafely(Direction.UP)
            device.waitForIdle(IDLE_TIMEOUT_MS)
        }
    }

    @Test
    fun homeFlingScroll() = measureHomeScroll {
        repeat(FLING_PASSES) {
            flingSafely(Direction.DOWN)
            device.waitForIdle(IDLE_TIMEOUT_MS)
            flingSafely(Direction.UP)
            device.waitForIdle(IDLE_TIMEOUT_MS)
        }
    }

    private fun measureHomeScroll(
        gesture: MacrobenchmarkScope.() -> Unit
    ) {
        rule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.None(), // TEMP: Partial needs adb install; see class doc.
            startupMode = StartupMode.WARM,
            iterations = ITERATIONS,
            setupBlock = {
                device.wakeUp()
                pressHome()
                startActivityAndWait()
                // Let first-frame work and the below-the-fold Home load settle before the
                // measured window opens, so startup frames are not scored as scroll frames.
                device.wait(Until.hasObject(By.scrollable(true)), CONTENT_TIMEOUT_MS)
                device.waitForIdle(IDLE_TIMEOUT_MS)
            },
            measureBlock = {
                gesture()
            }
        )
    }

    /** Scrolls, re-finding the feed and retrying once if the handle went stale. */
    private fun MacrobenchmarkScope.scrollSafely(direction: Direction) {
        try {
            tallestScrollable().scroll(direction, SCROLL_PERCENT, SLOW_SCROLL_STEPS)
        } catch (e: StaleObjectException) {
            tallestScrollable().scroll(direction, SCROLL_PERCENT, SLOW_SCROLL_STEPS)
        }
    }

    /** Flings, re-finding the feed and retrying once if the handle went stale. */
    private fun MacrobenchmarkScope.flingSafely(direction: Direction) {
        try {
            tallestScrollable().fling(direction)
        } catch (e: StaleObjectException) {
            tallestScrollable().fling(direction)
        }
    }

    /**
     * Picks the vertical feed rather than one of the seven nested horizontal carousels.
     *
     * `By.scrollable(true)` matches LazyColumn *and* every LazyRow inside it, so taking
     * the first match would sometimes benchmark a single 5-row carousel and report
     * meaningless numbers. The feed is always the tallest scrollable on screen.
     */
    private fun MacrobenchmarkScope.tallestScrollable(): UiObject2 {
        val candidates = device.findObjects(By.scrollable(true))
        return candidates.maxByOrNull { it.visibleBounds.height() }
            ?: candidates.firstOrNull()
            ?: error("No scrollable found — Home content did not load")
    }

    private companion object {
        const val PACKAGE_NAME = "com.vyllo.music"

        const val ITERATIONS = 5
        const val SLOW_SCROLL_PASSES = 4
        const val FLING_PASSES = 6

        /** Fraction of the viewport moved per gesture. */
        const val SCROLL_PERCENT = 0.6f

        /** ~100 steps keeps a 60%-viewport gesture well under finger-flick speed. */
        const val SLOW_SCROLL_STEPS = 100

        const val CONTENT_TIMEOUT_MS = 15_000L
        const val IDLE_TIMEOUT_MS = 2_000L
    }
}

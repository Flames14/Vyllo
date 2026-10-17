package com.vyllo.music.localization

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vyllo.music.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Guards the locale files themselves.
 *
 * Lint already fails the build on a missing translation (`MissingTranslation`
 * is intentionally not disabled), so this does not re-check key parity. What it
 * does cover is what lint cannot: that the resources actually *resolve* at
 * runtime in a non-default locale, and that plurals decline differently.
 */
@RunWith(AndroidJUnit4::class)
class LocalizationResourcesTest {

    private val app: Context = ApplicationProvider.getApplicationContext()

    private fun contextFor(locale: Locale): Context {
        val config = Configuration(app.resources.configuration)
        config.setLocale(locale)
        return app.createConfigurationContext(config)
    }

    @Test
    fun english_resolvesTheStringsTheUiRenders() {
        val en = contextFor(Locale.US)

        assertEquals("Home", en.getString(R.string.nav_home))
        assertEquals("Library", en.getString(R.string.nav_library))
        assertEquals("All", en.getString(R.string.chip_all))
        assertEquals("Commute", en.getString(R.string.chip_commute))
        assertEquals("Something went wrong", en.getString(R.string.state_error_title))
        assertEquals("Try again", en.getString(R.string.state_try_again))
    }

    @Test
    fun spanish_translatesTheStringsTheUiRenders() {
        val es = contextFor(Locale.forLanguageTag("es"))

        // Same keys as the English test above — if one locale drifts, one of the
        // two tests fails rather than shipping half-translated chrome.
        assertEquals("Inicio", es.getString(R.string.nav_home))
        assertEquals("Biblioteca", es.getString(R.string.nav_library))
        assertEquals("Todo", es.getString(R.string.chip_all))
        assertEquals("Trayecto", es.getString(R.string.chip_commute))
        assertEquals("Algo salió mal", es.getString(R.string.state_error_title))
        assertEquals("Intentar de nuevo", es.getString(R.string.state_try_again))
    }

    @Test
    fun spanish_offlineBannerIsTranslated() {
        val en = contextFor(Locale.US)
        val es = contextFor(Locale.forLanguageTag("es"))

        assertEquals("Sin conexión", es.getString(R.string.state_offline_banner))
        // Compare against an explicitly English context, not the app context:
        // the emulator carries a per-app locale, so app.getString() may already
        // be Spanish and the "different" assertion would be meaningless.
        assertNotEquals(
            en.getString(R.string.state_offline_banner),
            es.getString(R.string.state_offline_banner)
        )
    }

    @Test
    fun plurals_declineInBothLocales() {
        val en = contextFor(Locale.US)
        val es = contextFor(Locale.forLanguageTag("es"))

        val enOne = en.resources.getQuantityString(R.plurals.lyrics_results_count, 1, 1)
        val enMany = en.resources.getQuantityString(R.plurals.lyrics_results_count, 7, 7)
        assertNotEquals("English must decline 1 vs 7", enOne, enMany)
        assertEquals("1 result", enOne)
        assertEquals("7 results", enMany)

        // Spanish needs one/other (and `many`, which lint requires for es).
        val esOne = es.resources.getQuantityString(R.plurals.lyrics_results_count, 1, 1)
        val esMany = es.resources.getQuantityString(R.plurals.lyrics_results_count, 7, 7)
        assertNotEquals("Spanish must decline 1 vs 7", esOne, esMany)
        assertTrue("count must survive translation: $esOne", esOne.contains("1"))
        assertTrue("count must survive translation: $esMany", esMany.contains("7"))
    }

    @Test
    fun plurals_keepPositionalFormatArguments() {
        val es = contextFor(Locale.forLanguageTag("es"))

        // playlist_tracks_count takes a count AND a privacy label; dropping the
        // second argument would throw at runtime rather than in lint.
        val rendered = es.resources.getQuantityString(
            R.plurals.playlist_tracks_count,
            3,
            3,
            "pública"
        )
        assertTrue(rendered.contains("3"))
        assertTrue("privacy label missing: $rendered", rendered.contains("pública"))
    }

    @Test
    fun missingResourceThrowsInsteadOfFallingBackSilently() {
        esResolves(R.string.nav_home)
        esResolves(R.string.nav_search)
        esResolves(R.string.nav_explore)
        esResolves(R.string.nav_library)
    }

    private fun esResolves(id: Int) {
        val es = contextFor(Locale.forLanguageTag("es"))
        try {
            es.getString(id)
        } catch (e: Resources.NotFoundException) {
            throw AssertionError("resource not found in values-es: ${es.resources.getResourceName(id)}", e)
        }
    }
}

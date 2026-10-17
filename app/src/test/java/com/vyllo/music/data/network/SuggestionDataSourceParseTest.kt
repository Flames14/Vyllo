package com.vyllo.music.data.network

import okhttp3.OkHttpClient
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionDataSourceParseTest {

    private val dataSource = SuggestionDataSource(OkHttpClient())

    @Test
    fun `parses firefox-style response with plain string completions`() {
        val json = """["dreams",["dreams fleetwood mac","dreams tonite"]]"""
        assertEquals(
            listOf("dreams fleetwood mac", "dreams tonite"),
            dataSource.parseSuggestions(json)
        )
    }

    @Test
    fun `parses completions embedded as objects with an s field`() {
        val json = """["q",[{"s":"first suggestion"},{"s":"second suggestion"}]]"""
        assertEquals(
            listOf("first suggestion", "second suggestion"),
            dataSource.parseSuggestions(json)
        )
    }

    @Test
    fun `survives suggestions containing quotes and brackets`() {
        val json = """["x",["track [feat. Someone] \"remix\"","line ] break"]]"""
        assertEquals(
            listOf("track [feat. Someone] \"remix\"", "line ] break"),
            dataSource.parseSuggestions(json)
        )
    }

    @Test
    fun `drops blank and duplicate completions`() {
        val json = """["q",["  ","same","same","kept"]]"""
        assertEquals(listOf("same", "kept"), dataSource.parseSuggestions(json))
    }

    @Test
    fun `missing completions array yields empty list`() {
        assertTrue(dataSource.parseSuggestions("""["only query"]""").isEmpty())
        assertTrue(dataSource.parseSuggestions("""[]""").isEmpty())
    }

    @Test
    fun `malformed payload throws instead of returning guessed suggestions`() {
        // getSuggestions wraps this in try/catch upstream; at this layer a
        // malformed body must surface rather than fabricate values.
        assertThrows(JSONException::class.java) {
            dataSource.parseSuggestions("""{"not":"an array"}""")
        }
    }
}

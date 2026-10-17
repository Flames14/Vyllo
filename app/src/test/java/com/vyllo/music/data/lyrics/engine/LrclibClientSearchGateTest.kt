package com.vyllo.music.data.lyrics.engine

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LrclibClientSearchGateTest {

    private fun clientReturning(json: String): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(json.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

    @Test
    fun `synced candidate without any title overlap is rejected`() {
        val body = """
            [{"id":1,"trackName":"Bohemian Rhapsody","artistName":"Queen","duration":215,
              "syncedLyrics":"[00:12.00] Is this the real life"}]
        """.trimIndent()
        val result = LrclibClient.tryLrclibSearch(clientReturning(body), "Anti Hero", "Artist", durationSecs = 215)
        assertNull("duration + synced bonus must not override zero title overlap", result)
    }

    @Test
    fun `matching title with synced lyrics is accepted`() {
        val body = """
            [{"id":2,"trackName":"Anti Hero","artistName":"Artist","duration":215,
              "syncedLyrics":"[00:01.00] Hi"}]
        """.trimIndent()
        val result = LrclibClient.tryLrclibSearch(clientReturning(body), "Anti Hero", "Artist", durationSecs = 215)
        assertNotNull(result)
    }

    @Test
    fun `bracketed variant still passes the title gate`() {
        val body = """
            [{"id":3,"trackName":"Anti Hero (Remastered)","artistName":"Artist","duration":215,
              "syncedLyrics":"[00:01.00] Hi"}]
        """.trimIndent()
        val result = LrclibClient.tryLrclibSearch(clientReturning(body), "Anti Hero", "Artist", durationSecs = 215)
        assertNotNull(result)
    }
}

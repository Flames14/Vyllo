package com.vyllo.music.data.lyrics.engine

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsOvhClientUrlTest {

    private var recordedUrl: String? = null

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            recordedUrl = chain.request().url.toString()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"lyrics":"Don't stop believin"}""".toResponseBody("application/json".toMediaType()))
                .build()
        }
        .build()

    @Test
    fun `multi-word path segments are percent-encoded instead of using plus`() {
        val result = LyricsOvhClient.tryLyricsOvh(client, "Dreams", "Fleetwood Mac")

        assertNotNull(result)
        assertEquals("Dreams", result?.trackName)
        val url = requireNotNull(recordedUrl)
        assertTrue(url, url.contains("/Fleetwood%20Mac/Dreams"))
        assertFalse(url, url.contains("Fleetwood+Mac"))
    }
}

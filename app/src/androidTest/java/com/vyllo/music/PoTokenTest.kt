package com.vyllo.music

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vyllo.music.data.network.YouTubeDataSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import okhttp3.OkHttpClient
import okhttp3.Request
import android.util.Log

@RunWith(AndroidJUnit4::class)
class PoTokenTest {

    @Test
    fun testMultipleContentPoTokenStreamUrls() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val dataSource = YouTubeDataSource(appContext)

        // Allow iOS client fallback
        YoutubeStreamExtractor.setFetchIosClient(true)

        val videoUrls = listOf(
            "https://www.youtube.com/watch?v=kJQP7kiw5Fk",
            "https://www.youtube.com/watch?v=9bZkp7q19f0",
            "https://www.youtube.com/watch?v=OPf0YbXqDm0"
        )

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url.toString()
                if (url.contains("googlevideo.com") && url.contains("/videoplayback")) {
                    val userAgent = when {
                        url.contains("c=ANDROID", ignoreCase = true) -> {
                            "com.google.android.youtube/21.03.36 (Linux; U; Android 15; US) gzip"
                        }
                        url.contains("c=IOS", ignoreCase = true) -> {
                            "com.google.ios.youtube/21.03.2(iPhone16,2; U; CPU iOS 18_7_2 like Mac OS X; US)"
                        }
                        url.contains("c=WEB", ignoreCase = true) -> {
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"
                        }
                        else -> request.header("User-Agent")
                    }
                    if (userAgent != null) {
                        return@addInterceptor chain.proceed(
                            request.newBuilder()
                                .header("User-Agent", userAgent)
                                .build()
                        )
                    }
                }
                chain.proceed(request)
            }
            .build()

        for (url in videoUrls) {
            Log.d("PoTokenTest", "Fetching stream info for: $url")
            val streamInfo = try {
                dataSource.getOrFetchStreamInfo(url, force = true)
            } catch (e: Exception) {
                Log.e("PoTokenTest", "Failed to fetch stream info for $url", e)
                throw e
            }

            assertNotNull("StreamInfo must not be null for $url", streamInfo)
            
            val audioStreams = streamInfo.audioStreams
            assertNotNull("Audio streams must not be null", audioStreams)
            assertFalse("Audio streams must not be empty", audioStreams.isEmpty())

            val firstAudioStream = audioStreams.first()
            val streamUrl = firstAudioStream.content
            assertNotNull("Audio stream URL must not be null", streamUrl)
            assertTrue("Audio stream URL must not be empty", streamUrl.isNotEmpty())

            Log.d("PoTokenTest", "Validating stream URL: $streamUrl")

            // Make a HTTP request to verify the stream URL doesn't return 403 Forbidden
            val request = Request.Builder()
                .url(streamUrl)
                .head()
                .build()

            val response = client.newCall(request).execute()
            Log.d("PoTokenTest", "Response code for $url: ${response.code}")

            assertTrue(
                "Stream URL for $url returned HTTP ${response.code} (expected 200 or similar, but definitely NOT 403)",
                response.code != 403
            )
            response.close()
        }
    }
}

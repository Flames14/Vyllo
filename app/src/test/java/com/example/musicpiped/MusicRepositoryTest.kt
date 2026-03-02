package com.example.musicpiped

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.musicpiped.data.MusicRepository
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MusicRepositoryTest {

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        MusicRepository.init(context)
    }

    @Test
    fun testGetTrendingMusic() = runBlocking {
        println("Fetching trending music via MusicRepository...")
        val items = MusicRepository.getTrendingMusic()
        
        println("Fetched ${items.size} trending items.")
        assert(items.isNotEmpty()) { "Trending list should not be empty" }
        
        items.take(5).forEach { item ->
            println("- ${item.title} by ${item.uploader}")
            assert(item.title.isNotEmpty()) { "Item title should not be empty" }
            assert(item.url.isNotEmpty()) { "Item URL should not be empty" }
        }
    }
}

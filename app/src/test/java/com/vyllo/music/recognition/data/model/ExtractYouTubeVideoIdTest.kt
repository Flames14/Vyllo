package com.vyllo.music.recognition.data.model

import com.vyllo.music.recognition.domain.extractYouTubeVideoId
import org.junit.Assert.*
import org.junit.Test

class ExtractYouTubeVideoIdTest {

    @Test
    fun watchUrl() {
        assertEquals("jA0gY6P-qmw", extractYouTubeVideoId("https://www.youtube.com/watch?v=jA0gY6P-qmw"))
    }

    @Test
    fun watchUrlWithExtraParams() {
        assertEquals(
            "jA0gY6P-qmw",
            extractYouTubeVideoId("https://www.youtube.com/watch?v=jA0gY6P-qmw&t=30s&list=RDjA0gY6P-qmw")
        )
    }

    @Test
    fun shortUrl() {
        assertEquals("jA0gY6P-qmw", extractYouTubeVideoId("https://youtu.be/jA0gY6P-qmw?t=15"))
    }

    @Test
    fun shortsAndEmbed() {
        assertEquals("jA0gY6P-qmw", extractYouTubeVideoId("https://www.youtube.com/shorts/jA0gY6P-qmw"))
        assertEquals("jA0gY6P-qmw", extractYouTubeVideoId("https://www.youtube.com/embed/jA0gY6P-qmw"))
    }

    @Test
    fun rawId() {
        assertEquals("jA0gY6P-qmw", extractYouTubeVideoId("jA0gY6P-qmw"))
    }

    @Test
    fun garbageReturnsNull() {
        assertNull(extractYouTubeVideoId(""))
        assertNull(extractYouTubeVideoId("https://www.youtube.com/channel/UCxxxx"))
        assertNull(extractYouTubeVideoId("https://www.youtube.com/watch?v=too-short"))
        assertNull(extractYouTubeVideoId("not a url at all ***"))
    }
}

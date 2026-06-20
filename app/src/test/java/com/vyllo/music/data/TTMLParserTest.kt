package com.vyllo.music.data

import com.vyllo.music.data.lyrics.TTMLParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TTMLParserTest {

    @Test
    fun testParseTTMLAndConvertToLrc() {
        val ttmlInput = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" xmlns:ttp="http://www.w3.org/ns/ttml#parameter">
              <head>
                <metadata>
                  <audio lyricOffset="0.5"/>
                </metadata>
              </head>
              <body>
                <div>
                  <p begin="00:01.00" end="00:05.00" ttm:agent="v1">
                    <span begin="00:01.00" end="00:02.00">Hello</span>
                    <span begin="00:02.00" end="00:03.00">World</span>
                  </p>
                  <p begin="00:06.00" end="00:10.00" ttm:agent="v2">
                    <span begin="00:06.00" end="00:08.00">How</span>
                    <span begin="00:08.00" end="00:10.00">are you?</span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val parsed = TTMLParser.parseTTML(ttmlInput)
        
        // Offset is 0.5, so startTime should be shifted by +0.5s
        assertEquals(2, parsed.size)
        
        val line1 = parsed[0]
        assertEquals("Hello World", line1.text)
        assertEquals(1.5, line1.startTime, 0.001)
        assertEquals("v1", line1.agent)
        
        val line2 = parsed[1]
        assertEquals("How are you?", line2.text)
        assertEquals(6.5, line2.startTime, 0.001)
        assertEquals("v2", line2.agent)

        // Convert to LRC
        val lrc = TTMLParser.toLRC(parsed).trim()
        
        // Verify formatted LRC has agent tags and timestamps correctly set
        // Global offset 0.5 shifted 1.0s -> 1.5s -> [00:01.50]
        // Global offset 0.5 shifted 6.0s -> 6.5s -> [00:06.50]
        assertTrue(lrc.contains("[00:01.50]{agent:v1}Hello World"))
        assertTrue(lrc.contains("[00:06.50]{agent:v2}How are you?"))
    }
}

package com.vyllo.music.update

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression: GitHub marks releases without notes as "body": null and some
 * responses carry "published_at": null; both used to crash decoding with the
 * default kotlinx-serialization config.
 */
class GithubReleaseParseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `release with null body and published date decodes`() {
        val payload = """{"tag_name":"v2.9.0","published_at":null,"body":null,"assets":[]}"""

        val release = json.decodeFromString<GithubRelease>(payload)

        assertEquals("v2.9.0", release.tagName)
        assertNull(release.publishedAt)
        assertNull(release.body)
        assertTrue(release.assets.isEmpty())
    }

    @Test
    fun `missing optional fields fall back to defaults`() {
        val release = json.decodeFromString<GithubRelease>("""{"tag_name":"v2.9.0"}""")

        assertEquals("v2.9.0", release.tagName)
        assertNull(release.publishedAt)
        assertNull(release.body)
        assertTrue(release.assets.isEmpty())
    }

    @Test
    fun `populated release decodes fully`() {
        val payload = """
            {
              "tag_name": "v2.9.0",
              "published_at": "2026-02-01T10:00:00Z",
              "body": "Release notes",
              "assets": [
                {
                  "name": "app-release.apk",
                  "browser_download_url": "https://github.com/Flames14/Vyllo/releases/download/v2.9.0/app-release.apk"
                }
              ]
            }
        """.trimIndent()

        val release = json.decodeFromString<GithubRelease>(payload)

        assertEquals("2026-02-01T10:00:00Z", release.publishedAt)
        assertEquals("Release notes", release.body)
        assertEquals(1, release.assets.size)
        assertEquals("app-release.apk", release.assets[0].name)
        assertTrue(release.assets[0].downloadUrl.endsWith(".apk"))
    }
}

package com.vyllo.music.data

import com.vyllo.music.data.network.YouTubeRemotePlaylist
import com.vyllo.music.data.network.YouTubeRemoteTrack
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class YouTubeSyncApiServiceTest {

    @Test
    fun testParsePlaylistsJsonResponse() {
        val sampleJson = """
            {
                "kind": "youtube#playlistListResponse",
                "nextPageToken": "CAUQAA",
                "items": [
                    {
                        "id": "PL1234567890",
                        "snippet": {
                            "title": "Chill Lo-Fi Beats",
                            "description": "Best lo-fi beats to relax/study to",
                            "thumbnails": {
                                "high": {
                                    "url": "https://i.ytimg.com/vi/sample1/hqdefault.jpg"
                                }
                            }
                        },
                        "status": {
                            "privacyStatus": "public"
                        },
                        "contentDetails": {
                            "itemCount": 42
                        }
                    },
                    {
                        "id": "PL0987654321",
                        "snippet": {
                            "title": "Private Favorites",
                            "description": "My secret favorites",
                            "thumbnails": {
                                "default": {
                                    "url": "https://i.ytimg.com/vi/sample2/default.jpg"
                                }
                            }
                        },
                        "status": {
                            "privacyStatus": "private"
                        },
                        "contentDetails": {
                            "itemCount": 15
                        }
                    }
                ]
            }
        """.trimIndent()

        val json = JSONObject(sampleJson)
        val items = json.getJSONArray("items")
        val playlists = mutableListOf<YouTubeRemotePlaylist>()

        for (i in 0 until items.length()) {
            val itemObj = items.getJSONObject(i)
            val id = itemObj.getString("id")
            val snippet = itemObj.getJSONObject("snippet")
            val title = snippet.getString("title")
            val description = snippet.optString("description", "")
            val contentDetails = itemObj.optJSONObject("contentDetails")
            val itemCount = contentDetails?.optInt("itemCount", 0) ?: 0
            val status = itemObj.optJSONObject("status")
            val privacy = status?.optString("privacyStatus", "private") ?: "private"
            val thumbnails = snippet.optJSONObject("thumbnails")
            val thumbUrl = thumbnails?.optJSONObject("high")?.optString("url")
                ?: thumbnails?.optJSONObject("default")?.optString("url") ?: ""

            playlists.add(
                YouTubeRemotePlaylist(
                    id = id,
                    title = title,
                    description = description,
                    thumbnailUrl = thumbUrl,
                    trackCount = itemCount,
                    privacyStatus = privacy
                )
            )
        }

        assertEquals(2, playlists.size)
        assertEquals("PL1234567890", playlists[0].id)
        assertEquals("Chill Lo-Fi Beats", playlists[0].title)
        assertEquals(42, playlists[0].trackCount)
        assertEquals("public", playlists[0].privacyStatus)
        assertEquals("https://i.ytimg.com/vi/sample1/hqdefault.jpg", playlists[0].thumbnailUrl)

        assertEquals("PL0987654321", playlists[1].id)
        assertEquals("Private Favorites", playlists[1].title)
        assertEquals(15, playlists[1].trackCount)
        assertEquals("private", playlists[1].privacyStatus)
    }

    @Test
    fun testParsePlaylistTracksAndSkipPlaceholders() {
        val sampleTracksJson = """
            {
                "items": [
                    {
                        "snippet": {
                            "title": "Midnight City",
                            "videoOwnerChannelTitle": "M83",
                            "resourceId": {
                                "videoId": "dX3k_QDnzHE"
                            },
                            "thumbnails": {
                                "high": {
                                    "url": "https://i.ytimg.com/vi/dX3k_QDnzHE/hqdefault.jpg"
                                }
                            }
                        }
                    },
                    {
                        "snippet": {
                            "title": "Deleted video",
                            "videoOwnerChannelTitle": "Unknown",
                            "resourceId": {
                                "videoId": "deleted123"
                            }
                        }
                    },
                    {
                        "snippet": {
                            "title": "Starboy",
                            "channelTitle": "The Weeknd",
                            "resourceId": {
                                "videoId": "34Na4j8AVgA"
                            },
                            "thumbnails": {
                                "high": {
                                    "url": "https://i.ytimg.com/vi/34Na4j8AVgA/hqdefault.jpg"
                                }
                            }
                        }
                    },
                    {
                        "snippet": {
                            "title": "Private video",
                            "channelTitle": "Private",
                            "resourceId": {
                                "videoId": "priv456"
                            }
                        }
                    }
                ]
            }
        """.trimIndent()

        val json = JSONObject(sampleTracksJson)
        val items = json.getJSONArray("items")
        val tracks = mutableListOf<YouTubeRemoteTrack>()

        var pos = 0
        for (i in 0 until items.length()) {
            val itemObj = items.getJSONObject(i)
            val snippet = itemObj.getJSONObject("snippet")
            val title = snippet.optString("title", "")

            if (title.equals("Deleted video", ignoreCase = true) ||
                title.equals("Private video", ignoreCase = true)) {
                continue
            }

            val channelTitle = snippet.optString("videoOwnerChannelTitle", snippet.optString("channelTitle", "Unknown Artist"))
            val resourceId = snippet.optJSONObject("resourceId")
            val videoId = resourceId?.optString("videoId") ?: continue
            val thumbnails = snippet.optJSONObject("thumbnails")
            val thumbUrl = thumbnails?.optJSONObject("high")?.optString("url") ?: ""

            tracks.add(
                YouTubeRemoteTrack(
                    videoId = videoId,
                    title = title,
                    channelTitle = channelTitle,
                    thumbnailUrl = thumbUrl,
                    position = pos++
                )
            )
        }

        assertEquals(2, tracks.size)
        assertEquals("Midnight City", tracks[0].title)
        assertEquals("M83", tracks[0].channelTitle)
        assertEquals("https://www.youtube.com/watch?v=dX3k_QDnzHE", tracks[0].fullUrl)
        assertEquals(0, tracks[0].position)

        assertEquals("Starboy", tracks[1].title)
        assertEquals("The Weeknd", tracks[1].channelTitle)
        assertEquals("https://www.youtube.com/watch?v=34Na4j8AVgA", tracks[1].fullUrl)
        assertEquals(1, tracks[1].position)
    }

    @Test
    fun testCleanPlaylistUrl() {
        fun cleanUrl(input: String): String {
            val trimmed = input.trim()
            val listId = if (trimmed.contains("list=")) {
                trimmed.substringAfter("list=").substringBefore("&").substringBefore("#").trim()
            } else if (trimmed.startsWith("PL") || trimmed.startsWith("RD") || trimmed.startsWith("OLAK") || trimmed.startsWith("CLAK") || trimmed.startsWith("FL") || trimmed.startsWith("UU")) {
                trimmed
            } else if (trimmed.contains("/playlist/")) {
                trimmed.substringAfter("/playlist/").substringBefore("?").substringBefore("&").trim()
            } else {
                trimmed
            }
            return if (listId.startsWith("http")) listId else "https://www.youtube.com/playlist?list=$listId"
        }

        assertEquals("https://www.youtube.com/playlist?list=PL12345", cleanUrl("https://music.youtube.com/playlist?list=PL12345&si=abc1234"))
        assertEquals("https://www.youtube.com/playlist?list=PL12345", cleanUrl("https://www.youtube.com/watch?v=xyz&list=PL12345"))
        assertEquals("https://www.youtube.com/playlist?list=PL12345", cleanUrl("PL12345"))
        assertEquals("https://www.youtube.com/playlist?list=OLAK5uy_k1234", cleanUrl("OLAK5uy_k1234"))
    }
}

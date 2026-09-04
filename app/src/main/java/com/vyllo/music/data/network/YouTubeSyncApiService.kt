package com.vyllo.music.data.network

import android.content.Context
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.data.network.potoken.PoTokenProviderImpl
import com.vyllo.music.network.OkHttpDownloader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton

data class YouTubeRemotePlaylist(
    val id: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String,
    val trackCount: Int,
    val privacyStatus: String = "private"
)

data class YouTubeRemoteTrack(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String,
    val position: Int
) {
    val fullUrl: String
        get() = "https://www.youtube.com/watch?v=$videoId"
}

@Singleton
class YouTubeSyncApiService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "YouTubeSyncApiService"
        private const val BASE_URL = "https://www.googleapis.com/youtube/v3"
        private const val INNERTUBE_URL = "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false"
    }

    private fun ensureNewPipeInitialized() {
        try {
            if (NewPipe.getDownloader() == null) {
                NewPipe.init(OkHttpDownloader(), Localization.DEFAULT, ContentCountry.DEFAULT)
                YoutubeStreamExtractor.setFetchIosClient(false)
                PoTokenProviderImpl.init(context)
                YoutubeStreamExtractor.setPoTokenProvider(PoTokenProviderImpl)
            }
        } catch (e: Exception) {
            // Already initialized
        }
    }

    fun extractPlaylistId(input: String): String {
        val trimmed = input.trim()
        val listId = if (trimmed.contains("list=")) {
            trimmed.substringAfter("list=").substringBefore("&").substringBefore("#").substringBefore(" ").trim()
        } else if (trimmed.startsWith("PL") || trimmed.startsWith("RD") || trimmed.startsWith("OLAK") || trimmed.startsWith("CLAK") || trimmed.startsWith("FL") || trimmed.startsWith("UU")) {
            trimmed
        } else if (trimmed.contains("/playlist/")) {
            trimmed.substringAfter("/playlist/").substringBefore("?").substringBefore("&").substringBefore("#").trim()
        } else {
            trimmed
        }
        return listId
    }

    fun cleanPlaylistUrl(input: String): String {
        val listId = extractPlaylistId(input)
        return if (listId.startsWith("http")) listId else "https://www.youtube.com/playlist?list=$listId"
    }

    /**
     * High-speed direct Innertube extraction supporting lockupViewModel (2025/2026 YouTube layout),
     * playlistVideoRenderer, and YouTube Music formats with 0 login required.
     */
    private fun fetchPlaylistViaInnertube(playlistId: String): Result<Pair<YouTubeRemotePlaylist, List<YouTubeRemoteTrack>>> {
        try {
            val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
            val jsonBody = """
                {
                    "context": {
                        "client": {
                            "clientName": "WEB",
                            "clientVersion": "2.20240101.01.00",
                            "hl": "en",
                            "gl": "US"
                        }
                    },
                    "browseId": "$browseId"
                }
            """.trimIndent()

            val request = Request.Builder()
                .url(INNERTUBE_URL)
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful || body.isNullOrBlank()) {
                return Result.failure(Exception("Innertube returned HTTP ${response.code}"))
            }

            val json = JSONObject(body)
            val header = json.optJSONObject("header")
            val plHeader = header?.optJSONObject("playlistHeaderRenderer")
                ?: header?.optJSONObject("musicResponsiveHeaderRenderer")

            val title = plHeader?.optJSONObject("title")?.optString("simpleText")
                ?: plHeader?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                ?: json.optJSONObject("metadata")?.optJSONObject("playlistMetadataRenderer")?.optString("title")
                ?: json.optJSONObject("microformat")?.optJSONObject("microformatDataRenderer")?.optString("title")
                ?: "Imported Playlist"

            val desc = plHeader?.optJSONObject("descriptionText")?.optString("simpleText")
                ?: plHeader?.optJSONObject("descriptionText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                ?: json.optJSONObject("metadata")?.optJSONObject("playlistMetadataRenderer")?.optString("description")
                ?: ""

            val headerThumb = plHeader?.optJSONObject("playlistHeaderBanner")?.optJSONObject("image")?.optJSONArray("sources")?.optJSONObject(0)?.optString("url")
                ?: plHeader?.optJSONObject("thumbnailRenderer")?.optJSONObject("thumbnailRenderer")?.optJSONArray("thumbnails")?.optJSONObject(0)?.optString("url")
                ?: ""

            val tracks = mutableListOf<YouTubeRemoteTrack>()
            var positionCounter = 0

            val twoCol = json.optJSONObject("contents")?.optJSONObject("twoColumnBrowseResultsRenderer")
            val tabs = twoCol?.optJSONArray("tabs")
            val tab0 = tabs?.optJSONObject(0)?.optJSONObject("tabRenderer")?.optJSONObject("content")
            val sectionList = tab0?.optJSONObject("sectionListRenderer")
            val sectionContents = sectionList?.optJSONArray("contents")

            if (sectionContents != null) {
                for (sIdx in 0 until sectionContents.length()) {
                    val sectionObj = sectionContents.optJSONObject(sIdx) ?: continue
                    val itemSection = sectionObj.optJSONObject("itemSectionRenderer") ?: continue
                    val isContents = itemSection.optJSONArray("contents") ?: continue

                    for (i in 0 until isContents.length()) {
                        val itemObj = isContents.optJSONObject(i) ?: continue

                        // 1. Check lockupViewModel (2025/2026 YouTube UI)
                        val lockup = itemObj.optJSONObject("lockupViewModel")
                        if (lockup != null) {
                            val videoId = lockup.optString("contentId")
                            if (videoId.isNotBlank()) {
                                val meta = lockup.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
                                val trackTitle = meta?.optJSONObject("title")?.optString("content") ?: "Unknown Track"
                                val metadataRows = meta?.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")
                                val artistName = metadataRows?.optJSONObject(0)?.optJSONArray("elements")?.optJSONObject(0)?.optJSONObject("formattedString")?.optString("content") ?: "YouTube Artist"
                                val thumbSources = lockup.optJSONObject("contentImage")?.optJSONObject("thumbnailViewModel")?.optJSONObject("image")?.optJSONArray("sources")
                                val thumbUrl = thumbSources?.optJSONObject(thumbSources.length() - 1)?.optString("url")
                                    ?: "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

                                tracks.add(
                                    YouTubeRemoteTrack(
                                        videoId = videoId,
                                        title = trackTitle,
                                        channelTitle = artistName,
                                        thumbnailUrl = thumbUrl,
                                        position = positionCounter++
                                    )
                                )
                            }
                            continue
                        }

                        // 2. Check playlistVideoRenderer (Classic format)
                        val pvr = itemObj.optJSONObject("playlistVideoRenderer")
                        if (pvr != null) {
                            val videoId = pvr.optString("videoId")
                            if (videoId.isNotBlank()) {
                                val trackTitle = pvr.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                                    ?: pvr.optJSONObject("title")?.optString("simpleText") ?: "Unknown Track"
                                val artistName = pvr.optJSONObject("shortBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "YouTube Artist"
                                val thumbList = pvr.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                                val thumbUrl = thumbList?.optJSONObject(thumbList.length() - 1)?.optString("url")
                                    ?: "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

                                tracks.add(
                                    YouTubeRemoteTrack(
                                        videoId = videoId,
                                        title = trackTitle,
                                        channelTitle = artistName,
                                        thumbnailUrl = thumbUrl,
                                        position = positionCounter++
                                    )
                                )
                            }
                            continue
                        }

                        // 3. Check playlistVideoListRenderer
                        val pvl = itemObj.optJSONObject("playlistVideoListRenderer")
                        val pvlContents = pvl?.optJSONArray("contents")
                        if (pvlContents != null) {
                            for (j in 0 until pvlContents.length()) {
                                val innerPvr = pvlContents.optJSONObject(j)?.optJSONObject("playlistVideoRenderer") ?: continue
                                val videoId = innerPvr.optString("videoId")
                                if (videoId.isNotBlank()) {
                                    val trackTitle = innerPvr.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                                        ?: innerPvr.optJSONObject("title")?.optString("simpleText") ?: "Unknown Track"
                                    val artistName = innerPvr.optJSONObject("shortBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "YouTube Artist"
                                    val thumbList = innerPvr.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                                    val thumbUrl = thumbList?.optJSONObject(thumbList.length() - 1)?.optString("url")
                                        ?: "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

                                    tracks.add(
                                        YouTubeRemoteTrack(
                                            videoId = videoId,
                                            title = trackTitle,
                                            channelTitle = artistName,
                                            thumbnailUrl = thumbUrl,
                                            position = positionCounter++
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (tracks.isNotEmpty()) {
                val finalThumb = headerThumb.takeIf { it.isNotBlank() } ?: tracks.first().thumbnailUrl
                val playlist = YouTubeRemotePlaylist(
                    id = playlistId,
                    title = title,
                    description = desc,
                    thumbnailUrl = finalThumb,
                    trackCount = tracks.size,
                    privacyStatus = "public"
                )
                return Result.success(Pair(playlist, tracks))
            } else {
                return Result.failure(Exception("No tracks parsed from Innertube"))
            }
        } catch (e: Exception) {
            SecureLogger.w(TAG, "Innertube extraction fallback triggered: ${e.message}")
            return Result.failure(e)
        }
    }

    /**
     * Fetches all playlists owned by the authenticated Google account with automatic pagination.
     */
    suspend fun fetchMyPlaylists(accessToken: String): Result<List<YouTubeRemotePlaylist>> = withContext(Dispatchers.IO) {
        val allPlaylists = mutableListOf<YouTubeRemotePlaylist>()
        var nextPageToken: String? = null

        try {
            do {
                val urlBuilder = StringBuilder("$BASE_URL/playlists?part=snippet,contentDetails,status&mine=true&maxResults=50")
                if (!nextPageToken.isNullOrBlank()) {
                    urlBuilder.append("&pageToken=").append(nextPageToken)
                }

                val request = Request.Builder()
                    .url(urlBuilder.toString())
                    .header("Authorization", "Bearer $accessToken")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val body = response.body?.string()

                if (!response.isSuccessful || body.isNullOrBlank()) {
                    SecureLogger.e(TAG, "Failed to fetch playlists: HTTP ${response.code}, body=$body")
                    return@withContext Result.failure(Exception("YouTube API Error (${response.code})"))
                }

                val json = JSONObject(body)
                val items = json.optJSONArray("items")
                if (items != null) {
                    for (i in 0 until items.length()) {
                        val itemObj = items.getJSONObject(i)
                        val id = itemObj.getString("id")
                        val snippet = itemObj.getJSONObject("snippet")
                        val title = snippet.optString("title", "Untitled Playlist")
                        val description = snippet.optString("description", "")
                        val contentDetails = itemObj.optJSONObject("contentDetails")
                        val itemCount = contentDetails?.optInt("itemCount", 0) ?: 0
                        val status = itemObj.optJSONObject("status")
                        val privacy = status?.optString("privacyStatus", "private") ?: "private"

                        val thumbnails = snippet.optJSONObject("thumbnails")
                        val high = thumbnails?.optJSONObject("high")?.optString("url")
                        val med = thumbnails?.optJSONObject("medium")?.optString("url")
                        val def = thumbnails?.optJSONObject("default")?.optString("url")
                        val thumbUrl = high ?: med ?: def ?: "https://img.youtube.com/vi/$id/hqdefault.jpg"

                        allPlaylists.add(
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
                }

                nextPageToken = json.optString("nextPageToken", null)
            } while (!nextPageToken.isNullOrBlank())

            SecureLogger.d(TAG, "Fetched ${allPlaylists.size} total playlists from YouTube account")
            return@withContext Result.success(allPlaylists)
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Error fetching YouTube playlists", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Fetches all tracks for a specific playlist with automatic pagination.
     */
    suspend fun fetchPlaylistTracks(
        playlistId: String,
        accessToken: String?
    ): Result<List<YouTubeRemoteTrack>> = withContext(Dispatchers.IO) {
        val allTracks = mutableListOf<YouTubeRemoteTrack>()
        var nextPageToken: String? = null

        try {
            var positionCounter = 0
            do {
                val urlBuilder = StringBuilder("$BASE_URL/playlistItems?part=snippet,contentDetails&playlistId=$playlistId&maxResults=50")
                if (!nextPageToken.isNullOrBlank()) {
                    urlBuilder.append("&pageToken=").append(nextPageToken)
                }

                val reqBuilder = Request.Builder().url(urlBuilder.toString())
                if (!accessToken.isNullOrBlank()) {
                    reqBuilder.header("Authorization", "Bearer $accessToken")
                }

                val response = okHttpClient.newCall(reqBuilder.build()).execute()
                val body = response.body?.string()

                if (!response.isSuccessful || body.isNullOrBlank()) {
                    SecureLogger.e(TAG, "Failed to fetch tracks for playlist $playlistId: HTTP ${response.code}")
                    return@withContext Result.failure(Exception("YouTube API Error (${response.code})"))
                }

                val json = JSONObject(body)
                val items = json.optJSONArray("items")
                if (items != null) {
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
                        val high = thumbnails?.optJSONObject("high")?.optString("url")
                        val med = thumbnails?.optJSONObject("medium")?.optString("url")
                        val def = thumbnails?.optJSONObject("default")?.optString("url")
                        val thumbUrl = high ?: med ?: def ?: "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

                        allTracks.add(
                            YouTubeRemoteTrack(
                                videoId = videoId,
                                title = title,
                                channelTitle = channelTitle,
                                thumbnailUrl = thumbUrl,
                                position = positionCounter++
                            )
                        )
                    }
                }

                nextPageToken = json.optString("nextPageToken", null)
            } while (!nextPageToken.isNullOrBlank())

            SecureLogger.d(TAG, "Fetched ${allTracks.size} tracks for playlist $playlistId")
            return@withContext Result.success(allTracks)
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Error fetching tracks for playlist $playlistId", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Fallback zero-login extractor using Innertube API and NewPipeExtractor for public/unlisted playlist URLs or IDs.
     */
    suspend fun extractPlaylistFromUrl(urlOrId: String): Result<Pair<YouTubeRemotePlaylist, List<YouTubeRemoteTrack>>> = withContext(Dispatchers.IO) {
        try {
            ensureNewPipeInitialized()
            val listId = extractPlaylistId(urlOrId)
            val playlistUrl = "https://www.youtube.com/playlist?list=$listId"
            SecureLogger.d(TAG, "Extracting playlist from cleaned ID: $listId")

            // 1. Direct Innertube Extraction (Fastest, handles 2025/2026 lockupViewModel & all layouts)
            val innertubeResult = fetchPlaylistViaInnertube(listId)
            if (innertubeResult.isSuccess && innertubeResult.getOrNull()?.second?.isNotEmpty() == true) {
                val (pl, trks) = innertubeResult.getOrThrow()
                SecureLogger.d(TAG, "Extracted ${trks.size} tracks via Innertube for '${pl.title}'")
                return@withContext Result.success(Pair(pl, trks))
            }

            // 2. Fallback to NewPipe Extractor
            val service = ServiceList.YouTube
            val extractor = service.getPlaylistExtractor(playlistUrl)
            extractor.fetchPage()

            val bestThumb = extractor.thumbnails?.maxByOrNull { it.width * it.height }?.url
                ?: extractor.thumbnails?.firstOrNull()?.url
                ?: "https://img.youtube.com/vi/$listId/hqdefault.jpg"

            val title = extractor.name?.takeIf { it.isNotBlank() } ?: "Imported Playlist"
            val trackCount = try { extractor.streamCount.toInt() } catch (e: Exception) { 0 }

            val remotePlaylist = YouTubeRemotePlaylist(
                id = extractor.id ?: listId,
                title = title,
                description = extractor.description?.content ?: "",
                thumbnailUrl = bestThumb,
                trackCount = trackCount,
                privacyStatus = "public"
            )

            val tracks = mutableListOf<YouTubeRemoteTrack>()
            var positionCounter = 0
            var page = extractor.initialPage
            var pageCount = 0

            while (page != null && pageCount < 15) {
                pageCount++
                page.items.forEach { item ->
                    if (item is StreamInfoItem) {
                        val videoId = item.url?.substringAfter("v=")?.substringBefore("&") ?: ""
                        if (videoId.isNotBlank()) {
                            val itemThumb = item.thumbnails?.maxByOrNull { it.width * it.height }?.url
                                ?: item.thumbnails?.firstOrNull()?.url
                                ?: "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                            tracks.add(
                                YouTubeRemoteTrack(
                                    videoId = videoId,
                                    title = item.name?.takeIf { it.isNotBlank() } ?: "Unknown Song",
                                    channelTitle = item.uploaderName?.takeIf { it.isNotBlank() } ?: "Unknown Artist",
                                    thumbnailUrl = itemThumb,
                                    position = positionCounter++
                                )
                            )
                        }
                    }
                }

                val nextPage = page.nextPage
                if (nextPage != null && (nextPage.url != null || nextPage.ids != null)) {
                    try {
                        page = extractor.getPage(nextPage)
                    } catch (e: Exception) {
                        break
                    }
                } else {
                    break
                }
            }

            if (tracks.isNotEmpty()) {
                SecureLogger.d(TAG, "Extracted ${tracks.size} tracks successfully for playlist '$title'")
                return@withContext Result.success(Pair(remotePlaylist, tracks))
            } else {
                return@withContext Result.failure(Exception("No playable tracks found in playlist"))
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Error extracting playlist from URL: $urlOrId", e)
            return@withContext Result.failure(Exception("Could not load playlist: ${e.localizedMessage ?: "Invalid URL or playlist is private"}"))
        }
    }
}

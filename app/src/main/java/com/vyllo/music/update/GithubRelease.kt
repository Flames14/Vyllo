package com.vyllo.music.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("published_at") val publishedAt: String,
    @SerialName("body") val body: String,
    @SerialName("assets") val assets: List<GithubAsset>
)

@Serializable
data class GithubAsset(
    @SerialName("name") val name: String,
    @SerialName("browser_download_url") val downloadUrl: String
)

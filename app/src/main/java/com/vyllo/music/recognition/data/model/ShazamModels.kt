package com.vyllo.music.recognition.data.model

import com.vyllo.music.recognition.domain.model.RecognitionResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ShazamRequestJson(
    @SerialName("geolocation")
    val geolocation: Geolocation,
    @SerialName("signature")
    val signature: Signature,
    @SerialName("timestamp")
    val timestamp: Long,
    @SerialName("timezone")
    val timezone: String
) {
    @Serializable
    data class Geolocation(
        @SerialName("altitude")
        val altitude: Double,
        @SerialName("latitude")
        val latitude: Double,
        @SerialName("longitude")
        val longitude: Double
    )

    @Serializable
    data class Signature(
        @SerialName("samplems")
        val samplems: Long,
        @SerialName("timestamp")
        val timestamp: Long,
        @SerialName("uri")
        val uri: String
    )
}

@Serializable
data class ShazamResponseJson(
    @SerialName("matches")
    val matches: List<Match?>? = null,
    @SerialName("track")
    val track: Track? = null,
    @SerialName("tagid")
    val tagid: String? = null
) {
    @Serializable
    data class Match(
        @SerialName("id")
        val id: String? = null
    )

    @Serializable
    data class Track(
        @SerialName("key")
        val key: String? = null,
        @SerialName("title")
        val title: String? = null,
        @SerialName("subtitle")
        val subtitle: String? = null,
        @SerialName("images")
        val images: Images? = null,
        @SerialName("hub")
        val hub: Hub? = null,
        @SerialName("sections")
        val sections: List<Section?>? = null,
        @SerialName("url")
        val url: String? = null,
        @SerialName("isrc")
        val isrc: String? = null,
        @SerialName("genres")
        val genres: Genres? = null
    ) {
        @Serializable
        data class Images(
            @SerialName("coverart")
            val coverart: String? = null,
            @SerialName("coverarthq")
            val coverarthq: String? = null
        )

        @Serializable
        data class Hub(
            @SerialName("options")
            val options: List<Option?>? = null,
            @SerialName("providers")
            val providers: List<Provider?>? = null
        ) {
            @Serializable
            data class Option(
                @SerialName("actions")
                val actions: List<OptionAction?>? = null,
                @SerialName("type")
                val type: String? = null,
                @SerialName("providername")
                val providername: String? = null
            ) {
                @Serializable
                data class OptionAction(
                    @SerialName("uri")
                    val uri: String? = null
                )
            }

            @Serializable
            data class Provider(
                @SerialName("caption")
                val caption: String? = null,
                @SerialName("actions")
                val actions: List<ProviderAction?>? = null
            ) {
                @Serializable
                data class ProviderAction(
                    @SerialName("uri")
                    val uri: String? = null
                )
            }
        }

        @Serializable
        data class Section(
            @SerialName("type")
            val type: String? = null,
            @SerialName("metadata")
            val metadata: List<Metadata?>? = null,
            @SerialName("text")
            val text: List<String>? = null
        ) {
            @Serializable
            data class Metadata(
                @SerialName("title")
                val title: String? = null,
                @SerialName("text")
                val text: String? = null
            )
        }

        @Serializable
        data class Genres(
            @SerialName("primary")
            val primary: String? = null
        )
    }

    fun toDomain(): RecognitionResult? {
        val track = this.track ?: return null

        val songSection = track.sections?.find { it?.type == "SONG" }
        val metadata = songSection?.metadata
        val album = metadata?.find { it?.title == "Album" }?.text
        val label = metadata?.find { it?.title == "Label" }?.text
        val releaseDate = metadata?.find { it?.title == "Released" }?.text

        val lyricsSection = track.sections?.find { it?.type == "LYRICS" }
        val lyrics = lyricsSection?.text?.joinToString("\n")

        val appleAction = track.hub?.options?.firstOrNull {
            it?.providername?.contains("apple", ignoreCase = true) == true
        }?.actions?.firstOrNull()
        
        val spotifyProvider = track.hub?.providers?.find {
            it?.caption?.contains("spotify", ignoreCase = true) == true
        }

        val youtubeAction = track.hub?.options?.find {
            it?.type?.contains("video", ignoreCase = true) == true
        }?.actions?.firstOrNull()
        
        val youtubeVideoId = youtubeAction?.uri?.let { uri ->
            uri.substringAfterLast("v=", "").takeIf { it.isNotEmpty() }
                ?: uri.substringAfterLast("/", "").takeIf { it.isNotEmpty() && it.length == 11 }
        }

        return RecognitionResult(
            trackId = track.key ?: tagid ?: "",
            title = track.title ?: "",
            artist = track.subtitle ?: "",
            album = album,
            coverArtUrl = track.images?.coverart,
            coverArtHqUrl = track.images?.coverarthq,
            genre = track.genres?.primary,
            releaseDate = releaseDate,
            label = label,
            lyrics = lyrics,
            shazamUrl = track.url,
            appleMusicUrl = appleAction?.uri,
            spotifyUrl = spotifyProvider?.actions?.firstOrNull()?.uri,
            isrc = track.isrc,
            youtubeVideoId = youtubeVideoId
        )
    }
}

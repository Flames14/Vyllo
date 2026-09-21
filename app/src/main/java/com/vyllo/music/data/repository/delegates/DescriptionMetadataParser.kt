package com.vyllo.music.data.repository.delegates

import javax.inject.Inject
import javax.inject.Singleton

data class DescriptionMetadata(
    val title: String?,
    val singers: String?,
    val music: String?,
    val movie: String?,
    val language: String?
)

@Singleton
class DescriptionMetadataParser @Inject constructor() {

    fun parseProvidedToYouTubeDescription(description: String): DescriptionMetadata? {
        val lines = description.lines().map { it.trim() }
        val startIndex = lines.indexOfFirst { it.contains("Provided to YouTube by", ignoreCase = true) }
        if (startIndex == -1) return null

        var songLineIndex = -1
        for (i in (startIndex + 1) until lines.size) {
            if (lines[i].isNotBlank()) {
                songLineIndex = i
                break
            }
        }
        if (songLineIndex == -1) return null

        val songLine = lines[songLineIndex]
        val songParts = songLine.split(Regex("[·•]")).map { it.trim() }
        if (songParts.isEmpty()) return null

        val title = songParts[0]
        val singers = if (songParts.size > 1) {
            songParts.subList(1, songParts.size).joinToString(", ")
        } else {
            null
        }

        var albumLineIndex = -1
        for (i in (songLineIndex + 1) until lines.size) {
            if (lines[i].isNotBlank()) {
                albumLineIndex = i
                break
            }
        }
        val album = if (albumLineIndex != -1) {
            val candidate = lines[albumLineIndex]
            if (candidate.startsWith("℗") || candidate.startsWith("Released on:", ignoreCase = true) || candidate.contains("Auto-generated", ignoreCase = true)) {
                null
            } else {
                candidate
            }
        } else {
            null
        }

        return DescriptionMetadata(
            title = title,
            singers = singers,
            music = null,
            movie = album,
            language = null
        )
    }

    fun parseDescriptionMetadata(description: String): DescriptionMetadata {
        var title: String? = null
        var singers: String? = null
        var music: String? = null
        var movie: String? = null
        var language: String? = null

        description.lines().forEach { line ->
            val trimmed = line.trim()
            if (title == null) {
                val match = Regex("(?i)^(?:\\s*[-–—•*]*\\s*)?(?:Song|Track|Title)\\s*[:\\-–—]\\s*(.+)").find(trimmed)
                if (match != null) title = match.groupValues[1].trim()
            }
            if (singers == null) {
                val match = Regex("(?i)^(?:\\s*[-–—•*]*\\s*)?(?:Singer|Singers|Artist|Artists|Vocals|Sung by|Singers/Vocals|Singer/Vocals)\\s*[:\\-–—]\\s*(.+)").find(trimmed)
                if (match != null) singers = match.groupValues[1].trim()
            }
            if (music == null) {
                val match = Regex("(?i)^(?:\\s*[-–—•*]*\\s*)?(?:Music|Music Director|Composer|Composed by|Music Composer)\\s*[:\\-–—]\\s*(.+)").find(trimmed)
                if (match != null) music = match.groupValues[1].trim()
            }
            if (movie == null) {
                val match = Regex("(?i)^(?:\\s*[-–—•*]*\\s*)?(?:Movie|Album|Film|Movie Name|Film Name)\\s*[:\\-–—]\\s*(.+)").find(trimmed)
                if (match != null) movie = match.groupValues[1].trim()
            }
            if (language == null) {
                val match = Regex("(?i)^(?:\\s*[-–—•*]*\\s*)?(?:Language|Lang)\\s*[:\\-–—]\\s*(.+)").find(trimmed)
                if (match != null) language = match.groupValues[1].trim()
            }
        }

        return DescriptionMetadata(title, singers, music, movie, language)
    }

    fun detectLanguageFromTagsOrTitle(title: String, tags: List<String>): String? {
        val languages = listOf("Tamil", "Telugu", "Hindi", "Malayalam", "Kannada", "Punjabi", "Bengali", "English", "Spanish")
        for (lang in languages) {
            if (title.contains(lang, ignoreCase = true)) return lang
            if (tags.any { it.contains(lang, ignoreCase = true) }) return lang
        }
        return null
    }

    fun detectLanguageFromText(text: String): String? {
        val languages = listOf("Tamil", "Telugu", "Hindi", "Malayalam", "Kannada", "Punjabi", "Bengali", "English", "Spanish")
        for (lang in languages) {
            if (text.contains(lang, ignoreCase = true)) return lang
        }
        return null
    }
}

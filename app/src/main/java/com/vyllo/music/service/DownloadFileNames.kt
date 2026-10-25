package com.vyllo.music.service

/** Naming policy for the public in-progress item and the final downloaded song. */
internal object DownloadFileNames {
    fun base(title: String, timestampMillis: Long): String {
        val safeTitle = title.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(100)
            .ifBlank { "Unknown_Song" }
        return "$safeTitle-$timestampMillis"
    }

    fun pending(base: String, extension: String): String = "$base.$extension.download"

    fun completed(base: String, extension: String): String = "$base.$extension"
}

package com.vyllo.music.data.manager

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/** Thin SAF wrapper used by playlist import/export. All IO is UTF-8. */
@Singleton
class PlaylistFileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun read(uri: Uri): Result<String> = runCatching {
        val stream = context.contentResolver.openInputStream(uri)
            ?: error("Cannot open $uri")
        stream.use { it.readBytes().toString(StandardCharsets.UTF_8) }
    }

    fun write(uri: Uri, contents: String): Result<Unit> = runCatching {
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: error("Cannot write $uri")
        stream.use { it.write(contents.toByteArray(StandardCharsets.UTF_8)) }
    }

    fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
            }
    }.getOrNull()
}

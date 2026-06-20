package com.vyllo.music.presentation.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Picture
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun rememberPicture(): Picture {
    return remember { Picture() }
}

fun Modifier.captureToPicture(picture: Picture): Modifier = this.drawWithCache {
    val width = this.size.width.toInt()
    val height = this.size.height.toInt()
    onDrawWithContent {
        val pictureCanvas = androidx.compose.ui.graphics.Canvas(picture.beginRecording(width, height))
        draw(this, this.layoutDirection, pictureCanvas, this.size) {
            this@onDrawWithContent.drawContent()
        }
        picture.endRecording()
        
        drawIntoCanvas { canvas -> canvas.nativeCanvas.drawPicture(picture) }
    }
}

suspend fun createBitmapFromPicture(picture: Picture): Bitmap? {
    return withContext(Dispatchers.Default) {
        if (picture.width <= 0 || picture.height <= 0) return@withContext null
        val bitmap = Bitmap.createBitmap(
            picture.width,
            picture.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.BLACK)
        canvas.drawPicture(picture)
        bitmap
    }
}

suspend fun saveBitmapToFile(context: Context, bitmap: Bitmap): File? {
    return withContext(Dispatchers.IO) {
        try {
            val cachePath = File(context.cacheDir, "shared_images")
            cachePath.mkdirs()
            val file = File(cachePath, "story_share_${System.currentTimeMillis()}.png")
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
            outputStream.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

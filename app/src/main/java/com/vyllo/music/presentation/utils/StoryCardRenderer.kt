package com.vyllo.music.presentation.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.vyllo.music.R
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.model.MusicItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * Renders the story-share card into a real bitmap instead of snapshotting the
 * Compose preview with [android.graphics.Picture].
 *
 * The old capture ran synchronously on tap, so when Coil had not decoded the
 * artwork yet (cold network, streaming in progress, blank thumbnail URL) the
 * exported image contained only the gradient and the song title. Here the
 * artwork is resolved through the same fallback ladder as the player, decoded
 * before drawing, and the card is rasterized at the full 1080x1920 story size.
 */
object StoryCardRenderer {

    const val WIDTH = 1080
    const val HEIGHT = 1920

    private const val CACHE_DIR = "shared_images"
    private const val MAX_CACHE_AGE_MS = 24L * 60L * 60L * 1000L

    private const val PAD_H = 116f
    private const val TOP = 200f
    private const val COVER_SIZE = 850f
    private const val COVER_TOP = 430f
    private const val TITLE_TOP = 1360f
    private const val FOOTER_TOP = 1700f

    /**
     * Decodes the first reachable artwork URL. Each candidate is cached by Coil,
     * so after the player has shown the same track this resolves from
     * memory/disk instead of the network.
     */
    suspend fun loadArtwork(context: Context, candidates: List<String>): Bitmap? {
        if (candidates.isEmpty()) return null
        val loader = context.imageLoader

        for (url in candidates) {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(WIDTH)
                .crossfade(false)
                .allowHardware(false)
                .build()

            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    when (val result = withTimeoutOrNull(ARTWORK_TIMEOUT_MS) { loader.execute(request) }) {
                        is SuccessResult -> result.drawable.toSoftwareBitmap()
                        else -> null
                    }
                }.getOrNull()
            }
            if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) return bitmap
        }
        return null
    }

    /** Rasterizes the card at [WIDTH]x[HEIGHT]. Safe to call off the main thread. */
    suspend fun renderCard(context: Context, item: MusicItem, artwork: Bitmap?): Bitmap =
        withContext(Dispatchers.Default) {
            val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawBackground(canvas)
            if (artwork != null) drawGlow(canvas, artwork)
            drawBrandPill(canvas, context.getString(R.string.story_now_playing))
            drawCover(canvas, artwork)
            val footerTop = drawTextBlock(
                canvas = canvas,
                title = item.title.ifBlank { "Unknown title" },
                artist = item.uploader.ifBlank { "Unknown artist" }
            )
            drawFooter(canvas, context.getString(R.string.story_shared_via), footerTop)
            bitmap
        }

    /** Writes the card to the FileProvider-whitelisted cache dir. */
    suspend fun saveCard(context: Context, bitmap: Bitmap): File? = withContext(Dispatchers.IO) {
        try {
            val directory = File(context.cacheDir, CACHE_DIR)
            if (!directory.exists() && !directory.mkdirs()) return@withContext null
            pruneOldCards(directory)
            val file = File(directory, "story_share_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            file.takeIf { it.length() > 0L }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to save story card", e)
            null
        }
    }

    private fun drawBackground(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, HEIGHT.toFloat(),
                intArrayOf(0xFF1B1030.toInt(), 0xFF0B0B10.toInt(), Color.BLACK),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
    }

    /**
     * Cheap three-pass box blur: downscale, upscale with bilinear filtering,
     * repeat. RenderEffect blur does not survive a software canvas, which is
     * why the old glow disappeared from the shared image.
     */
    private fun drawGlow(canvas: Canvas, artwork: Bitmap) {
        var small = Bitmap.createScaledBitmap(artwork, 32, 32, true)
        repeat(2) {
            val up = Bitmap.createScaledBitmap(small, 160, 160, true)
            val next = Bitmap.createScaledBitmap(up, 32, 32, true)
            up.recycle()
            small.recycle()
            small = next
        }
        val glow = Bitmap.createScaledBitmap(small, 270, 480, true)
        small.recycle()

        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply { alpha = 0x73 }
        canvas.drawBitmap(glow, null, RectF(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat()), paint)
        glow.recycle()

        val scrim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, HEIGHT * 0.35f, 0f, HEIGHT.toFloat(),
                intArrayOf(Color.TRANSPARENT, 0xCC000000.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, HEIGHT * 0.35f, WIDTH.toFloat(), HEIGHT.toFloat(), scrim)
    }

    private fun drawBrandPill(canvas: Canvas, label: String) {
        val textPaint = textPaint(28f, Typeface.BOLD, Color.WHITE).apply { letterSpacing = 0.08f }
        val textWidth = textPaint.measureText(label)
        val iconWidth = 34f
        val gap = 18f
        val padH = 44f
        val pillHeight = 74f
        val pillWidth = padH * 2 + iconWidth + gap + textWidth
        val left = (WIDTH - pillWidth) / 2f
        val top = TOP

        canvas.drawRoundRect(
            RectF(left, top, left + pillWidth, top + pillHeight),
            pillHeight / 2f, pillHeight / 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1FFFFFFF }
        )

        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val barWidth = 6f
        val barGap = 6f
        val heights = floatArrayOf(16f, 30f, 22f, 34f)
        var barLeft = left + padH
        val barCenterY = top + pillHeight / 2f
        for (height in heights) {
            canvas.drawRoundRect(
                RectF(barLeft, barCenterY - height / 2f, barLeft + barWidth, barCenterY + height / 2f),
                barWidth / 2f, barWidth / 2f,
                barPaint
            )
            barLeft += barWidth + barGap
        }

        val textBaseline = barCenterY - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, left + padH + iconWidth + gap, textBaseline, textPaint)
    }

    private fun drawCover(canvas: Canvas, artwork: Bitmap?) {
        val left = (WIDTH - COVER_SIZE) / 2f
        val top = COVER_TOP
        val rect = RectF(left, top, left + COVER_SIZE, top + COVER_SIZE)
        val radius = 64f

        canvas.drawRoundRect(
            RectF(rect.left, rect.top + 26f, rect.right, rect.bottom + 26f),
            radius, radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xAA000000.toInt()
                maskFilter = BlurMaskFilter(70f, BlurMaskFilter.Blur.NORMAL)
            }
        )

        if (artwork == null) {
            canvas.drawRoundRect(
                rect, radius, radius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1FFFFFFF }
            )
            return
        }

        val shader = BitmapShader(artwork, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val scale = max(rect.width() / artwork.width, rect.height() / artwork.height)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                rect.left - (artwork.width * scale - rect.width()) / 2f,
                rect.top - (artwork.height * scale - rect.height()) / 2f
            )
        }
        shader.setLocalMatrix(matrix)

        canvas.drawRoundRect(
            rect, radius, radius,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader }
        )
    }

    /** Draws title + artist; returns the y position where the footer should start. */
    private fun drawTextBlock(canvas: Canvas, title: String, artist: String): Float {
        val titlePaint = textPaint(84f, Typeface.BOLD, Color.WHITE)
        val titleLayout = multiline(title, titlePaint, (WIDTH - PAD_H * 2).toInt(), 2)

        canvas.save()
        canvas.translate(PAD_H, TITLE_TOP)
        titleLayout.draw(canvas)
        canvas.restore()

        val artistTop = TITLE_TOP + titleLayout.height + 18f
        val artistLayout = multiline(artist, textPaint(46f, Typeface.NORMAL, 0xB3FFFFFF.toInt()), (WIDTH - PAD_H * 2).toInt(), 1)
        canvas.save()
        canvas.translate(PAD_H, artistTop)
        artistLayout.draw(canvas)
        canvas.restore()

        return artistTop + artistLayout.height + 56f
    }

    private fun drawFooter(canvas: Canvas, brandLabel: String, preferredTop: Float) {
        val labelPaint = textPaint(36f, Typeface.NORMAL, 0x8CFFFFFF.toInt()).apply { letterSpacing = 0.05f }
        val circle = 62f
        val gap = 20f
        val contentWidth = circle + gap + labelPaint.measureText(brandLabel)
        val left = (WIDTH - contentWidth) / 2f
        val top = minOf(max(preferredTop, FOOTER_TOP), HEIGHT - 180f - circle)

        val circleRect = RectF(left, top, left + circle, top + circle)
        canvas.drawOval(
            circleRect,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    circleRect.left, circleRect.top, circleRect.right, circleRect.bottom,
                    intArrayOf(0xFFE1306C.toInt(), 0xFF7B2FF7.toInt()),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
        )
        val vPaint = textPaint(32f, Typeface.BOLD, Color.WHITE)
        canvas.drawText(
            "V",
            circleRect.centerX() - vPaint.measureText("V") / 2f,
            circleRect.centerY() - (vPaint.ascent() + vPaint.descent()) / 2f,
            vPaint
        )

        val baseline = circleRect.centerY() - (labelPaint.descent() + labelPaint.ascent()) / 2f
        canvas.drawText(brandLabel, left + circle + gap, baseline, labelPaint)
    }

    private fun multiline(text: String, paint: TextPaint, maxWidth: Int, maxLines: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()

    private fun textPaint(size: Float, style: Int, color: Int): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = size
            typeface = Typeface.create("sans-serif", style)
            this.color = color
        }

    private fun android.graphics.drawable.Drawable.toSoftwareBitmap(): Bitmap? {
        val bitmap = (this as? BitmapDrawable)?.bitmap ?: runCatching { toBitmap() }.getOrNull()
        if (bitmap == null) return null
        // HARDWARE bitmaps cannot be read back, so a Canvas draw silently paints
        // nothing. Only API 26+ can even produce them (minSdk is 24).
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            bitmap.config == Bitmap.Config.HARDWARE
        ) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
    }

    private fun pruneOldCards(directory: File) {
        val cutoff = System.currentTimeMillis() - MAX_CACHE_AGE_MS
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) {
                runCatching { file.delete() }
            }
        }
    }

    private const val TAG = "StoryCardRenderer"
    private const val ARTWORK_TIMEOUT_MS = 6_000L
}

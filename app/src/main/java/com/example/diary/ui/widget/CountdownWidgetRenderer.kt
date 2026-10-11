package com.example.diary.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import com.example.diary.data.countdown.TextureLibrary
import com.example.diary.data.image.BackgroundImageStore
import com.example.diary.data.image.EventImageStore
import com.example.diary.data.local.CountdownEvent
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

object CountdownWidgetRenderer {

    const val PHOTO_MAX_PX = 192
    const val TEXTURE_MAX_PX = 192
    const val FLAT_MAX_PX = 96
    private const val PHOTO_DECODE_MAX = 512
    private const val CORNER_DP = 28f
    private const val INK_ALPHA = 77

    suspend fun render(
        context: Context,
        event: CountdownEvent,
        accentArgb: Int,
        widthDp: Int,
        heightDp: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        val density = context.resources.displayMetrics.density
        val w = max((widthDp * density).roundToInt(), 1)
        val h = max((heightDp * density).roundToInt(), 1)
        val photo = loadPhoto(context, event)
        val cap = when {
            photo != null -> PHOTO_MAX_PX
            event.textureIndex >= 0 -> TEXTURE_MAX_PX
            else -> FLAT_MAX_PX
        }
        val k = minOf(1f, cap.toFloat() / max(w, h))
        val bw = max(1, (w * k).roundToInt())
        val bh = max(1, (h * k).roundToInt())
        val corner = CORNER_DP * density * k

        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.RGB_565)
        val canvas = Canvas(bmp)
        val bwF = bw.toFloat()
        val bhF = bh.toFloat()

        val gradient = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, bhF,
                shade(accentArgb, 0.72f), lighten(accentArgb, 0.10f), Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, bwF, bhF, gradient)

        val source = photo
        if (source != null) {
            drawPhoto(canvas, source, bwF, bhF, event)
        } else if (event.textureIndex >= 0) {
            drawTexture(canvas, event.textureIndex, bwF, bhF)
        }

        val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawRoundRect(0f, 0f, bwF, bhF, corner, corner, mask)
        bmp
    }

    private suspend fun loadPhoto(context: Context, event: CountdownEvent): Bitmap? {
        val file = EventImageStore.file(context, event.id)
        if (!file.exists()) return null
        return BackgroundImageStore.decode(context, file.absolutePath, PHOTO_DECODE_MAX)?.asAndroidBitmap()
    }

    private fun drawPhoto(canvas: Canvas, photo: Bitmap, w: Float, h: Float, event: CountdownEvent) {
        val base = max(w / photo.width, h / photo.height)
        val zoom = event.photoScale.coerceAtLeast(1f)
        val scale = base * zoom
        val drawW = photo.width * scale
        val drawH = photo.height * scale
        val left = (w - drawW) / 2f
        val top = (h - drawH) / 2f + event.photoOffsetY.coerceIn(-1f, 1f) * h
        canvas.drawBitmap(photo, null, RectF(left, top, left + drawW, top + drawH), Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.drawRect(
            0f, 0f, w, h,
            Paint().apply { color = Color.argb((255 * 0.26f).toInt(), 0, 0, 0) }
        )
    }

    private fun drawTexture(canvas: Canvas, textureIndex: Int, w: Float, h: Float) {
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(INK_ALPHA, 255, 255, 255) }
        val cell = minOf(w, h) / 5f
        when (textureIndex % TextureLibrary.TEXTURE_COUNT) {
            0 -> {
                var y = cell / 2f
                while (y < h + cell) {
                    var x = cell / 2f
                    while (x < w + cell) {
                        canvas.drawCircle(x, y, cell * 0.11f, ink)
                        x += cell
                    }
                    y += cell
                }
            }
            1 -> {
                val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(INK_ALPHA, 255, 255, 255)
                    strokeWidth = max(1f, cell * 0.055f)
                }
                var x = 0f
                while (x < w + cell) {
                    canvas.drawLine(x, 0f, x, h, line)
                    x += cell
                }
                var y = 0f
                while (y < h + cell) {
                    canvas.drawLine(0f, y, w, y, line)
                    y += cell
                }
            }
            2 -> {
                val band = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(INK_ALPHA, 255, 255, 255)
                    strokeWidth = cell * 0.3f
                }
                val gap = cell * 1.7f
                var d = -h
                while (d < w + h + gap) {
                    canvas.drawLine(d, 0f, d + h, h, band)
                    d += gap
                }
            }
            else -> {
                val wave = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(INK_ALPHA, 255, 255, 255)
                    style = Paint.Style.STROKE
                    strokeWidth = max(1f, cell * 0.09f)
                }
                val wl = cell * 1.25f
                val amp = cell * 0.32f
                val path = android.graphics.Path()
                var y = wl
                while (y < h + wl) {
                    path.reset()
                    var x = 0f
                    path.moveTo(x, y)
                    while (x < w + wl) {
                        path.quadTo(x + wl / 4f, y - amp, x + wl / 2f, y)
                        path.quadTo(x + wl * 3f / 4f, y + amp, x + wl, y)
                        x += wl
                    }
                    canvas.drawPath(path, wave)
                    y += cell
                }
            }
        }
    }

    private fun shade(argb: Int, factor: Float): Int = Color.rgb(
        (Color.red(argb) * factor).toInt().coerceIn(0, 255),
        (Color.green(argb) * factor).toInt().coerceIn(0, 255),
        (Color.blue(argb) * factor).toInt().coerceIn(0, 255)
    )

    private fun lighten(argb: Int, amount: Float): Int = Color.rgb(
        (Color.red(argb) + (255 - Color.red(argb)) * amount).toInt().coerceIn(0, 255),
        (Color.green(argb) + (255 - Color.green(argb)) * amount).toInt().coerceIn(0, 255),
        (Color.blue(argb) + (255 - Color.blue(argb)) * amount).toInt().coerceIn(0, 255)
    )
}

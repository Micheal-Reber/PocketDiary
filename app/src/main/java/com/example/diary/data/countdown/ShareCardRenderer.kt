package com.example.diary.data.countdown

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import com.example.diary.data.image.BackgroundImageStore
import com.example.diary.data.image.EventImageStore
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 日历风分享卡片离屏渲染（1080×1440）：
 * 事件色头部条（名称+还有/已经文案）→ 超大数字 → 虚线分隔 → 日期脚注。
 * 纯 android.graphics，不依赖 View/Compose——后台线程可靠出图。
 */
object ShareCardRenderer {

    const val WIDTH = 1080
    const val HEIGHT = 1440
    private const val HEADER_H = 380f

    // 照片卡分享图尺寸（3:2 横向）
    const val PHOTO_CARD_WIDTH = 1080
    const val PHOTO_CARD_HEIGHT = 720

    /**
     * @param name        事件名
     * @param accentArgb  主色（事件色/自动蓝橙）
     * @param headline    头部副标题（如「还有 3 天」）
     * @param bigNumber   大数字字符串（0..9999+；Today 态传「今」由调用方定）
     * @param unit        数字下方单位（「天」或空串）
     * @param footLines   脚注行（目标日+星期 / 结束日 / 时间）
     * @param fontDark    文字色：false=白字(深色背景), true=黑字(浅色背景) —— CLASSIC 风格用
     */
    fun render(
        name: String,
        accentArgb: Int,
        headline: String,
        bigNumber: String,
        unit: String,
        footLines: List<String>,
        fontDark: Boolean = false
    ): Bitmap {
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // 背景色：fontDark=true 时浅色，否则深色
        val bgColor = if (fontDark) Color.WHITE else Color.BLACK
        val textColor = if (fontDark) Color.BLACK else Color.WHITE
        val subTextColor = if (fontDark) Color.argb(180, 0, 0, 0) else Color.argb(220, 255, 255, 255)
        val numberColor = if (fontDark) accentArgb else accentArgb
        val unitColor = if (fontDark) Color.argb(180, 0, 0, 0) else Color.argb(150, 255, 255, 255)
        val dashColor = if (fontDark) Color.argb(70, 0, 0, 0) else Color.argb(70, 255, 255, 255)
        val footColor = if (fontDark) Color.argb(190, 0, 0, 0) else Color.argb(190, 255, 255, 255)

        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), bg)

        // ── 色头 ──
        val header = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentArgb }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEADER_H, header)

        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = 76f
            typeface = TypefaceCompat.bold()
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(name.ellipsize(12), WIDTH / 2f, HEADER_H / 2f - 10f, namePaint)

        val headSub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subTextColor
            textSize = 44f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(headline, WIDTH / 2f, HEADER_H / 2f + 80f, headSub)

        // ── 大数字 ──
        val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = numberColor
            textSize = 430f
            typeface = TypefaceCompat.bold()
            textAlign = Paint.Align.CENTER
        }
        val scaled = fitText(bigNumber, numberPaint, WIDTH - 160f)
        numberPaint.textSize = scaled
        canvas.drawText(bigNumber, WIDTH / 2f, 900f, numberPaint)

        if (unit.isNotEmpty()) {
            val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = unitColor
                textSize = 56f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(unit, WIDTH / 2f, 1010f, unitPaint)
        }

        // ── 虚线分隔 ──
        val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dashColor
            strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(24f, 20f), 0f)
        }
        canvas.drawLine(120f, 1105f, WIDTH - 120f, 1105f, dash)

        // ── 脚注 ──
        val foot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = footColor
            textSize = 46f
            textAlign = Paint.Align.CENTER
        }
        var y = 1215f
        for (line in footLines.take(4)) {
            if (line.isBlank()) continue
            canvas.drawText(line, WIDTH / 2f, y, foot)
            y += 66f
        }
        return bmp
    }

    /**
     * 照片卡风格分享卡片（1080×720）：镜像详情页照片卡外观。
     * 背景：用户照片 + 模糊 + scrim → 圆角内嵌卡（白/黑字）+ 大数字 + 脚注。
     */
    suspend fun renderPhotoCard(
        context: android.content.Context,
        eventId: Long,
        eventName: String,
        accentArgb: Int,
        headline: String,
        bigNumber: String,
        unit: String,
        footLines: List<String>,
        blurRadius: Int,
        fontDark: Boolean
    ): Bitmap = withContext(Dispatchers.IO) {
        // 1. 解码原图（不降采样，用全分辨率以保证分享图质量）
        val srcFile = EventImageStore.file(context, eventId)
        val photoBmp = if (srcFile.exists()) {
            BackgroundImageStore.decode(srcFile.absolutePath, maxDim = PHOTO_CARD_WIDTH)?.asAndroidBitmap()
        } else null

        val bmp = Bitmap.createBitmap(PHOTO_CARD_WIDTH, PHOTO_CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // 背景色（兜底）
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawRect(0f, 0f, PHOTO_CARD_WIDTH.toFloat(), PHOTO_CARD_HEIGHT.toFloat(), bgPaint)

        // 2. 绘制照片背景（如果有）
        if (photoBmp != null) {
            val dest = RectF(0f, 0f, PHOTO_CARD_WIDTH.toFloat(), PHOTO_CARD_HEIGHT.toFloat())
            val src = Rect(0, 0, photoBmp.width, photoBmp.height)
            // Crop 居中绘制
            val scale = maxOf(
                PHOTO_CARD_WIDTH.toFloat() / src.width(),
                PHOTO_CARD_HEIGHT.toFloat() / src.height()
            )
            val scaledW = src.width() * scale
            val scaledH = src.height() * scale
            val left = (PHOTO_CARD_WIDTH - scaledW) / 2f
            val top = (PHOTO_CARD_HEIGHT - scaledH) / 2f
            val destCrop = RectF(left, top, left + scaledW, top + scaledH)
            canvas.drawBitmap(photoBmp, src, destCrop, null)

            // 模糊（软件栈模糊，离线）
            if (blurRadius > 0) {
                val blurred = stackBlurForShare(photoBmp, blurRadius * 2)
                canvas.drawBitmap(blurred, src, destCrop, null)
                blurred.recycle()
            }

            // Scrim 提升文字对比度
            val scrimAlpha = if (fontDark) 0.15f else 0.25f
            val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((255 * scrimAlpha).toInt(), 0, 0, 0)
            }
            canvas.drawRect(0f, 0f, PHOTO_CARD_WIDTH.toFloat(), PHOTO_CARD_HEIGHT.toFloat(), scrimPaint)
        } else {
            // 无照片兜底：纯色渐变
            val grad = LinearGradient(
                0f, 0f, 0f, PHOTO_CARD_HEIGHT.toFloat(),
                intArrayOf(accentArgb, Color.WHITE),
                null,
                Shader.TileMode.CLAMP
            )
            val gradPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = grad }
            canvas.drawRect(0f, 0f, PHOTO_CARD_WIDTH.toFloat(), PHOTO_CARD_HEIGHT.toFloat(), gradPaint)
        }

        // 3. 文字（直接在全画布上画）
        val textColor = if (fontDark) Color.BLACK else Color.WHITE

        // 事件名
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = 64f
            typeface = TypefaceCompat.bold()
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(eventName.ellipsize(16), PHOTO_CARD_WIDTH / 2f, 160f, namePaint)

        // 状态副标题
        val headSub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, Color.red(textColor), Color.green(textColor), Color.blue(textColor))
            textSize = 36f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(headline, PHOTO_CARD_WIDTH / 2f, 220f, headSub)

        // 大数字
        val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = 280f
            typeface = TypefaceCompat.bold()
            textAlign = Paint.Align.CENTER
        }
        val scaled = fitText(bigNumber, numberPaint, PHOTO_CARD_WIDTH - 160f)
        numberPaint.textSize = scaled
        canvas.drawText(bigNumber, PHOTO_CARD_WIDTH / 2f, 420f, numberPaint)

        if (unit.isNotEmpty()) {
            val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(180, Color.red(textColor), Color.green(textColor), Color.blue(textColor))
                textSize = 44f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(unit, PHOTO_CARD_WIDTH / 2f, 490f, unitPaint)
        }

        // 虚线分隔
        val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(80, Color.red(textColor), Color.green(textColor), Color.blue(textColor))
            strokeWidth = 2f
            pathEffect = DashPathEffect(floatArrayOf(20f, 16f), 0f)
        }
        canvas.drawLine(100f, 530f, PHOTO_CARD_WIDTH - 100f, 530f, dash)

        // 脚注
        val foot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, Color.red(textColor), Color.green(textColor), Color.blue(textColor))
            textSize = 38f
            textAlign = Paint.Align.CENTER
        }
        var y = 570f
        for (line in footLines.take(3)) {
            if (line.isBlank()) continue
            canvas.drawText(line, PHOTO_CARD_WIDTH / 2f, y, foot)
            y += 55f
        }

        return@withContext bmp
    }

    /** 超长数字缩字号到可用宽度内。 */
    private fun fitText(text: String, probe: Paint, maxWidth: Float): Float {
        var size = probe.textSize
        probe.textSize = size
        while (probe.measureText(text) > maxWidth && size > 120f) {
            size -= 24f
            probe.textSize = size
        }
        return size
    }

    private fun String.ellipsize(max: Int): String =
        if (length <= max) this else take(max - 1) + "…"

    /** 栈模糊用于分享图离线渲染（同 BlurCache.stackBlur 逻辑）。 */
    private fun stackBlurForShare(src: Bitmap, radiusPx: Int): Bitmap {
        if (radiusPx <= 0) return src.copy(src.config ?: Bitmap.Config.ARGB_8888, false)
        val w = src.width
        val h = src.height
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val r = radiusPx.coerceAtMost(w.coerceAtLeast(h) / 2).coerceAtLeast(1)
        val div = r + r + 1

        val pixels = IntArray(w * h)
        out.getPixels(pixels, 0, w, 0, 0, w, h)

        var temp = IntArray(w * h)
        var outPx = IntArray(w * h)
        val divSum = (div + 1) shr 1

        repeat(2) { pass ->
            if (pass == 0) {
                boxBlurH(pixels, temp, w, h, r, divSum)
                boxBlurV(temp, outPx, w, h, r, divSum)
            } else {
                boxBlurH(outPx, temp, w, h, r, divSum)
                boxBlurV(temp, outPx, w, h, r, divSum)
            }
        }

        out.setPixels(outPx, 0, w, 0, 0, w, h)
        temp = IntArray(0)
        outPx = IntArray(0)
        return out
    }

    private fun boxBlurH(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, divSum: Int) {
        for (y in 0 until h) {
            val rowStart = y * w
            var aSum = 0; var rSum = 0; var gSum = 0; var bSum = 0
            for (i in -r..r) {
                val p = src[rowStart + i.coerceIn(0, w - 1)]
                aSum += p ushr 24 and 0xFF
                rSum += p ushr 16 and 0xFF
                gSum += p ushr 8 and 0xFF
                bSum += p and 0xFF
            }
            for (x in 0 until w) {
                dst[rowStart + x] = (aSum / divSum shl 24) or
                    (rSum / divSum shl 16) or
                    (gSum / divSum shl 8) or
                    (bSum / divSum)
                val pAdd = src[rowStart + (x + r + 1).coerceIn(0, w - 1)]
                val pSub = src[rowStart + (x - r).coerceIn(0, w - 1)]
                aSum += (pAdd ushr 24 and 0xFF) - (pSub ushr 24 and 0xFF)
                rSum += (pAdd ushr 16 and 0xFF) - (pSub ushr 16 and 0xFF)
                gSum += (pAdd ushr 8 and 0xFF) - (pSub ushr 8 and 0xFF)
                bSum += (pAdd and 0xFF) - (pSub and 0xFF)
            }
        }
    }

    private fun boxBlurV(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, divSum: Int) {
        for (x in 0 until w) {
            var aSum = 0; var rSum = 0; var gSum = 0; var bSum = 0
            for (i in -r..r) {
                val p = src[i.coerceIn(0, h - 1) * w + x]
                aSum += p ushr 24 and 0xFF
                rSum += p ushr 16 and 0xFF
                gSum += p ushr 8 and 0xFF
                bSum += p and 0xFF
            }
            for (y in 0 until h) {
                dst[y * w + x] = (aSum / divSum shl 24) or
                    (rSum / divSum shl 16) or
                    (gSum / divSum shl 8) or
                    (bSum / divSum)
                val pAdd = src[(y + r + 1).coerceIn(0, h - 1) * w + x]
                val pSub = src[(y - r).coerceIn(0, h - 1) * w + x]
                aSum += (pAdd ushr 24 and 0xFF) - (pSub ushr 24 and 0xFF)
                rSum += (pAdd ushr 16 and 0xFF) - (pSub ushr 16 and 0xFF)
                gSum += (pAdd ushr 8 and 0xFF) - (pSub ushr 8 and 0xFF)
                bSum += (pAdd and 0xFF) - (pSub and 0xFF)
            }
        }
    }

    /** 隔离 Typeface 读取，便于将来替换自定义字体。 */
    private object TypefaceCompat {
        fun bold(): android.graphics.Typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD
        )
    }
}
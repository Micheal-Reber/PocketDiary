package com.example.diary.data.countdown

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint

/**
 * 日历风分享卡片离屏渲染（1080×1440）：
 * 事件色头部条（名称+还有/已经文案）→ 超大数字 → 虚线分隔 → 日期脚注。
 * 纯 android.graphics，不依赖 View/Compose——后台线程可靠出图。
 */
object ShareCardRenderer {

    const val WIDTH = 1080
    const val HEIGHT = 1440
    private const val HEADER_H = 380f

    /**
     * @param name        事件名
     * @param accentArgb  主色（事件色/自动蓝橙）
     * @param headline    头部副标题（如「还有 3 天」）
     * @param bigNumber   大数字字符串（0..9999+；Today 态传「今」由调用方定）
     * @param unit        数字下方单位（「天」或空串）
     * @param footLines   脚注行（目标日+星期 / 结束日 / 时间）
     */
    fun render(
        name: String,
        accentArgb: Int,
        headline: String,
        bigNumber: String,
        unit: String,
        footLines: List<String>
    ): Bitmap {
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), bg)

        // ── 色头 ──
        val header = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentArgb }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEADER_H, header)

        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 76f
            typeface = TypefaceCompat.bold()
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(name.ellipsize(12), WIDTH / 2f, HEADER_H / 2f - 10f, namePaint)

        val headSub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 255, 255, 255)
            textSize = 44f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(headline, WIDTH / 2f, HEADER_H / 2f + 80f, headSub)

        // ── 大数字 ──
        val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentArgb
            textSize = 430f
            typeface = TypefaceCompat.bold()
            textAlign = Paint.Align.CENTER
        }
        val scaled = fitText(bigNumber, numberPaint, WIDTH - 160f)
        numberPaint.textSize = scaled
        canvas.drawText(bigNumber, WIDTH / 2f, 900f, numberPaint)

        if (unit.isNotEmpty()) {
            val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(150, 0, 0, 0)
                textSize = 56f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(unit, WIDTH / 2f, 1010f, unitPaint)
        }

        // ── 虚线分隔 ──
        val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 0, 0, 0)
            strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(24f, 20f), 0f)
        }
        canvas.drawLine(120f, 1105f, WIDTH - 120f, 1105f, dash)

        // ── 脚注 ──
        val foot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 0, 0, 0)
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
}

/** 隔离 Typeface 读取，便于将来替换自定义字体。 */
private object TypefaceCompat {
    fun bold(): android.graphics.Typeface = android.graphics.Typeface.create(
        android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD
    )
}

package com.example.diary.data.countdown

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * TextureLibrary: 4 procedural textures — 1.3 版 Canvas 手绘方案，亮/暗模式均清晰。
 *
 * 参考 1.3 CountdownUi.TextureBackdrop：base + accent 淡层 + onSurface 0.32 高对比 ink，
 * 零二进制资源，主题自适应（onSurface 在亮色为黑、暗色为白）。
 * 旧数据 textureIndex 4..11 通过 % TEXTURE_COUNT 自动兼容。
 */

object TextureLibrary {

    const val TEXTURE_COUNT = 4

    private val names: List<String> = listOf("Dots", "Grid", "Diagonal", "Wave")

    fun getTextureName(index: Int): String = names[index % TEXTURE_COUNT]

    /** 全屏纹理背景——Canvas 手绘，亮/暗模式均清晰。 */
    @Composable
    fun TextureBackdrop(
        textureIndex: Int,
        accent: Color,
        modifier: Modifier = Modifier
    ) {
        val base = MaterialTheme.colorScheme.background
        val ink = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
        Canvas(modifier = modifier.fillMaxSize()) {
            drawRect(base)
            drawRect(accent.copy(alpha = 0.20f))
            val cell = 56.dp.toPx()
            when (textureIndex % TEXTURE_COUNT) {
                0 -> { // 大圆点阵
                    var y = cell / 2
                    while (y < size.height + cell) {
                        var x = cell / 2
                        while (x < size.width + cell) {
                            drawCircle(ink, radius = 6.dp.toPx(), center = Offset(x, y))
                            x += cell
                        }
                        y += cell
                    }
                }
                1 -> { // 方格线
                    var x = 0f
                    while (x < size.width + cell) {
                        drawLine(ink, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3.dp.toPx())
                        x += cell
                    }
                    var y = 0f
                    while (y < size.height + cell) {
                        drawLine(ink, Offset(0f, y), Offset(size.width, y), strokeWidth = 3.dp.toPx())
                        y += cell
                    }
                }
                2 -> { // 宽斜纹带
                    val span = size.width + size.height
                    var d = -size.height
                    val gap = cell * 1.7f
                    while (d < span + gap) {
                        drawLine(
                            ink, Offset(d, 0f), Offset(d + size.height, size.height),
                            strokeWidth = 16.dp.toPx(), cap = StrokeCap.Butt
                        )
                        d += gap
                    }
                }
                else -> { // 粗波浪
                    val wl = cell * 1.25f
                    var y = wl
                    while (y < size.height + wl) {
                        val path = Path()
                        var x = 0f
                        path.moveTo(x, y)
                        while (x < size.width + wl) {
                            path.quadraticTo(x + wl / 4, y - 18.dp.toPx(), x + wl / 2, y)
                            path.quadraticTo(x + wl * 3 / 4, y + 18.dp.toPx(), x + wl, y)
                            x += wl
                        }
                        drawPath(path, ink, style = Stroke(width = 5.dp.toPx()))
                        y += cell
                    }
                }
            }
        }
    }

    /** 缩略图预览（64dp 方块）：与全屏同款绘制，cell 等比缩小以保证预览可辨。 */
    @Composable
    fun TexturePreviewThumb(
        textureIndex: Int,
        accent: Color,
        modifier: Modifier = Modifier
    ) {
        val base = MaterialTheme.colorScheme.surfaceContainerHigh
        val ink = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
        Canvas(modifier = modifier) {
            drawRect(base)
            drawRect(accent.copy(alpha = 0.18f))
            // 预览 cell 缩小约 0.45 倍，使 64dp 内可见 2~3 个单元
            val cell = 56.dp.toPx() * 0.45f
            when (textureIndex % TEXTURE_COUNT) {
                0 -> {
                    var y = cell / 2
                    while (y < size.height + cell) {
                        var x = cell / 2
                        while (x < size.width + cell) {
                            drawCircle(ink, radius = 3.dp.toPx(), center = Offset(x, y))
                            x += cell
                        }
                        y += cell
                    }
                }
                1 -> {
                    var x = 0f
                    while (x < size.width + cell) {
                        drawLine(ink, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5.dp.toPx())
                        x += cell
                    }
                    var y = 0f
                    while (y < size.height + cell) {
                        drawLine(ink, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx())
                        y += cell
                    }
                }
                2 -> {
                    val span = size.width + size.height
                    var d = -size.height
                    val gap = cell * 1.7f
                    while (d < span + gap) {
                        drawLine(ink, Offset(d, 0f), Offset(d + size.height, size.height), strokeWidth = 7.dp.toPx(), cap = StrokeCap.Butt)
                        d += gap
                    }
                }
                else -> {
                    val wl = cell * 1.25f
                    var y = wl
                    while (y < size.height + wl) {
                        val path = Path()
                        var x = 0f
                        path.moveTo(x, y)
                        while (x < size.width + wl) {
                            path.quadraticTo(x + wl / 4, y - 8.dp.toPx(), x + wl / 2, y)
                            path.quadraticTo(x + wl * 3 / 4, y + 8.dp.toPx(), x + wl, y)
                            x += wl
                        }
                        drawPath(path, ink, style = Stroke(width = 2.dp.toPx()))
                        y += cell
                    }
                }
            }
        }
    }

    // 兼容：旧 Brush API 已移除，预览请用 TexturePreviewThumb
    @Deprecated("改用 TexturePreviewThumb Canvas 预览", ReplaceWith("TexturePreviewThumb(index, accent, modifier)"))
    fun getTextureBrush(@Suppress("UNUSED_PARAMETER") index: Int): androidx.compose.ui.graphics.Brush {
        return androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
    }
}

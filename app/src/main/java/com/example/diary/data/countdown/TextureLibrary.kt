package com.example.diary.data.countdown

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode

/**
 * TextureLibrary: 4 procedural textures using Compose Brush.
 * Shared by CLASSIC and PHOTO_CARD countdown card styles.
 *
 * Brush 注意：所有 Brush 以固定 100×100 名义尺寸定义，
 * 配合 TileMode.Repeated 自动平铺到任意画布尺寸，
 * 无需在 drawBehind/Canvas 中获取 runtime size。
 * 旧数据 textureIndex 4..11 通过 % TEXTURE_COUNT 自动兼容。
 */

// ── 4 种纹理 Brush（private top-level，声明顺序在前，避免前向引用）──────────

private val dotsBrush: Brush = Brush.radialGradient(
    center = Offset(50f, 50f),
    radius = 100f / 24f,
    colors = listOf(Color(0xFF000000).copy(alpha = 0.12f), Color.Transparent),
    tileMode = TileMode.Repeated
)

private val gridBrush: Brush = Brush.linearGradient(
    colors = listOf(
        Color(0xFF000000).copy(alpha = 0.08f),
        Color.Transparent,
        Color(0xFF000000).copy(alpha = 0.08f)
    ),
    start = Offset(0f, 0f),
    end = Offset(100f / 8f, 100f / 8f),
    tileMode = TileMode.Repeated
)

private val diagonalBrush: Brush = Brush.linearGradient(
    colors = listOf(Color.Transparent, Color(0xFF000000).copy(alpha = 0.10f), Color.Transparent),
    start = Offset(0f, 100f),
    end = Offset(100f, 0f),
    tileMode = TileMode.Repeated
)

private val waveBrush: Brush = Brush.linearGradient(
    colors = List(8) { i -> if (i % 2 == 0) Color.Transparent else Color(0xFF000000).copy(alpha = 0.12f) },
    start = Offset(0f, 0f),
    end = Offset(0f, 100f / 4f),
    tileMode = TileMode.Repeated
)



// ── TextureLibrary 对象 ─────────────────────────────────────────────────────

object TextureLibrary {

    const val TEXTURE_COUNT = 4

    private val brushes: List<Brush> = listOf(
        dotsBrush, gridBrush, diagonalBrush, waveBrush
    )

    private val names: List<String> = listOf(
        "Dots", "Grid", "Diagonal", "Wave"
    )

    fun getTextureName(index: Int): String = names[index % TEXTURE_COUNT]

    fun getTextureBrush(index: Int): Brush = brushes[index % TEXTURE_COUNT]

    /** 全屏纹理背景——直接渲染，caller 放入 Box 即可叠加其他内容。 */
    @Composable
    fun TextureBackdrop(
        textureIndex: Int,
        accent: Color,
        modifier: Modifier = Modifier
    ) {
        val brush = brushes[textureIndex % TEXTURE_COUNT]
        Box(
            modifier = modifier
                .fillMaxSize()
                .drawBehind { drawRect(brush) }
        )
    }

    // TexturePreview 已迁至 ui/countdown/TexturePicker.kt 统一处理
    // 保留兼容：旧调用方逐步迁移至 TexturePickerRow
}

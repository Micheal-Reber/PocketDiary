package com.example.diary.data.countdown

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * TextureLibrary: 12 procedural textures using Compose Brush.
 * Shared by CLASSIC and PHOTO_CARD countdown card styles.
 *
 * Brush 注意：所有 Brush 以固定 100×100 名义尺寸定义，
 * 配合 TileMode.Repeated 自动平铺到任意画布尺寸，
 * 无需在 drawBehind/Canvas 中获取 runtime size。
 */

// ── 12 种纹理 Brush（private top-level，声明顺序在前，避免前向引用）──────────

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

private val noiseBrush: Brush = Brush.linearGradient(
    colors = List(16) { i -> if (i % 3 == 0) Color(0xFF000000).copy(alpha = 0.06f) else Color.Transparent },
    start = Offset(0f, 0f),
    end = Offset(100f / 16f, 100f / 16f),
    tileMode = TileMode.Repeated
)

private val diagonalGridBrush: Brush = Brush.linearGradient(
    colors = listOf(
        Color.Transparent, Color(0xFF000000).copy(alpha = 0.08f),
        Color.Transparent, Color(0xFF000000).copy(alpha = 0.08f), Color.Transparent
    ),
    start = Offset(0f, 100f),
    end = Offset(100f, 0f),
    tileMode = TileMode.Repeated
)

private val hexagonBrush: Brush = Brush.linearGradient(
    colors = List(12) { i -> if (i % 4 == 0) Color(0xFF000000).copy(alpha = 0.07f) else Color.Transparent },
    start = Offset(0f, 0f),
    end = Offset(100f / 6f, 100f / 6f),
    tileMode = TileMode.Repeated
)

private val radialWaveBrush: Brush = Brush.radialGradient(
    center = Offset(50f, 50f),
    radius = 50f,
    colors = List(10) { i -> if (i % 2 == 0) Color.Transparent else Color(0xFF000000).copy(alpha = 0.10f) },
    tileMode = TileMode.Repeated
)

private val dualDiagonalBrush: Brush = Brush.linearGradient(
    colors = listOf(
        Color(0xFF000000).copy(alpha = 0.10f), Color.Transparent,
        Color.White.copy(alpha = 0.05f), Color.Transparent
    ),
    start = Offset(0f, 100f),
    end = Offset(100f, 0f),
    tileMode = TileMode.Repeated
)

private val gridDotsBrush: Brush = Brush.linearGradient(
    colors = List(16) { i ->
        when (i % 5) {
            0 -> Color(0xFF000000).copy(alpha = 0.08f)
            3 -> Color(0xFF000000).copy(alpha = 0.12f)
            else -> Color.Transparent
        }
    },
    start = Offset(0f, 0f),
    end = Offset(100f / 8f, 100f / 8f),
    tileMode = TileMode.Repeated
)

private val thinLinesBrush: Brush = Brush.linearGradient(
    colors = List(20) { i -> if (i % 4 == 0) Color(0xFF000000).copy(alpha = 0.06f) else Color.Transparent },
    start = Offset(0f, 0f),
    end = Offset(0f, 100f / 20f),
    tileMode = TileMode.Repeated
)

private val gradientOverlayBrush: Brush = Brush.radialGradient(
    center = Offset(50f, 50f),
    radius = 70f,
    colors = listOf(
        Color(0xFF000000).copy(alpha = 0.15f),
        Color(0xFF000000).copy(alpha = 0.05f),
        Color.Transparent
    ),
    tileMode = TileMode.Clamp
)

// ── TextureLibrary 对象 ─────────────────────────────────────────────────────

object TextureLibrary {

    const val TEXTURE_COUNT = 12

    private val brushes: List<Brush> = listOf(
        dotsBrush, gridBrush, diagonalBrush, waveBrush,
        noiseBrush, diagonalGridBrush, hexagonBrush, radialWaveBrush,
        dualDiagonalBrush, gridDotsBrush, thinLinesBrush, gradientOverlayBrush
    )

    private val names: List<String> = listOf(
        "Dots", "Grid", "Diagonal", "Wave",
        "Noise", "DiagGrid", "Hexagon", "RadialWave",
        "DualDiag", "GridDots", "ThinLines", "GradientOverlay"
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

    /** 72dp 纹理预览缩略图（纹理选择器用）。 */
    @Composable
    fun TexturePreview(
        textureIndex: Int,
        accent: Color,
        previewSize: Dp = 72.dp,
        selected: Boolean = false,
        onClick: (() -> Unit)? = null
    ) {
        val brush = brushes[textureIndex % TEXTURE_COUNT]
        val shape = RoundedCornerShape(8.dp)
        Box(
            modifier = Modifier
                .size(previewSize)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .then(if (selected) Modifier.border(2.dp, accent, shape) else Modifier)
                .clickable(enabled = onClick != null) { onClick?.invoke() },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(brush)
            }
        }
    }
}

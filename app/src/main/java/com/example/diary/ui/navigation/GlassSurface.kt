package com.example.diary.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

val FloatingBottomInset = 96.dp
val DockedBottomBarHeight = 80.dp
val LocalBottomBarInset = staticCompositionLocalOf { FloatingBottomInset }
val LocalFloatingBar = staticCompositionLocalOf { true }
val BottomBarContentInset: Dp
    @Composable get() = LocalBottomBarInset.current

private val GlassBlurRadius = 20.dp

// 与主底栏 LiquidGlassBottomBar 同一套玻璃配方，保证胶囊和底栏质感一致
private val CapsuleBlurRadius = 8.dp
private val CapsuleRefraction = 24.dp

@Composable
private fun glassContainerColor(): Color =
    MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.40f)

internal fun glassTint(dark: Boolean): Brush =
    if (dark) {
        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.60f), Color.Black.copy(alpha = 0.46f)))
    } else {
        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.05f)))
    }

internal fun glassStroke(dark: Boolean): Brush =
    if (dark) {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.10f)))
    } else {
        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.16f), Color.Black.copy(alpha = 0.08f)))
    }

@Composable
fun rememberGlassBackdrop(): LayerBackdrop = rememberLayerBackdrop { drawContent() }

@Composable
fun GlassCapsule(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    indicator: @Composable BoxScope.() -> Unit = {},
    content: @Composable RowScope.() -> Unit,
) {
    val containerColor = glassContainerColor()
    Box(
        modifier
            .height(64.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(50.dp) },
                effects = {
                    vibrancy()
                    blur(CapsuleBlurRadius.toPx())
                    lens(CapsuleRefraction.toPx(), CapsuleRefraction.toPx())
                },
                onDrawSurface = { drawRect(containerColor) }
            )
            .clip(RoundedCornerShape(50.dp))
    ) {
        indicator()
        Row(
            Modifier.fillMaxSize().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun GlassFab(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Box(
        modifier
            .size(56.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    vibrancy()
                    blur(GlassBlurRadius.toPx())
                    lens(16.dp.toPx(), 32.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(if (dark) Color.Black.copy(alpha = 0.50f) else Color.White.copy(alpha = 0.48f))
                    drawRect(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (dark) 0.20f else 0.50f),
                                Color.White.copy(alpha = 0f)
                            )
                        )
                    )
                }
            )
            .clip(CircleShape)
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

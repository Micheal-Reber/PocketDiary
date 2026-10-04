package com.example.diary.ui.navigation

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import com.example.diary.R
import com.example.diary.ui.theme.Spacing
import kotlin.math.roundToInt

/** 悬浮玻璃底栏在各 Tab 页内容底部预留的高度（下间隙 16 + 胶囊 64 + 上间隙 16）。 */
val BottomBarContentInset = 96.dp

private val BarHeight = 64.dp

private val GlassBlurRadius = 20.dp

internal fun glassTint(dark: Boolean): Brush =
    if (dark) {
        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.60f), Color.Black.copy(alpha = 0.46f)))
    } else {
        // 亮色背景近白，白叠白不可见 —— 改中性灰 tint 让玻璃片与底分离
        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.05f)))
    }

internal fun glassStroke(dark: Boolean): Brush =
    if (dark) {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.10f)))
    } else {
        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.16f), Color.Black.copy(alpha = 0.08f)))
    }

class GlassBackdropState(val layer: GraphicsLayer) {
    var active by mutableStateOf(false)
    var barBoundsInRoot by mutableStateOf(Rect.Zero)
    var contentOriginInRoot by mutableStateOf(Offset.Zero)
}

// 每屏一个「内容区录制层」给加号用：录制节点不含 FAB（FAB 在 Scaffold 的
// fab 槽），加号画这层不可能自环
class FabLayer(val layer: GraphicsLayer) {
    val originInRoot = mutableStateOf(Offset.Zero)
}

@Composable
fun rememberFabLayer(): FabLayer {
    val graphicsContext = LocalGraphicsContext.current
    val layer = remember(graphicsContext) { graphicsContext.createGraphicsLayer() }
    val radiusPx = with(LocalDensity.current) { GlassBlurRadius.toPx() }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val effect = remember(radiusPx) { BlurEffect(radiusPx, radiusPx, TileMode.Clamp) }
        SideEffect { layer.renderEffect = effect }
    }
    DisposableEffect(graphicsContext) {
        onDispose { graphicsContext.releaseGraphicsLayer(layer) }
    }
    return remember(layer) { FabLayer(layer) }
}

// 挂在屏内容根节点：每帧正常绘制后再把内容录进 fabLayer（供 GlassFab 取样）
@Composable
fun Modifier.fabRecord(fab: FabLayer): Modifier =
    this
        .onGloballyPositioned { fab.originInRoot.value = it.boundsInRoot().topLeft }
        .drawWithContent {
            val contentScope = this
            drawContent()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                size.width > 0f && size.height > 0f
            ) {
                fab.layer.record(
                    IntSize(size.width.roundToInt(), size.height.roundToInt())
                ) {
                    contentScope.drawContent()
                }
            }
        }

@Composable
fun rememberGlassBackdrop(): GlassBackdropState {
    val graphicsContext = LocalGraphicsContext.current
    val layer = remember(graphicsContext) { graphicsContext.createGraphicsLayer() }
    DisposableEffect(graphicsContext) {
        onDispose { graphicsContext.releaseGraphicsLayer(layer) }
    }
    return remember { GlassBackdropState(layer) }
}

@Composable
fun Modifier.glassBackdrop(state: GlassBackdropState): Modifier {
    val radiusPx = with(LocalDensity.current) { GlassBlurRadius.toPx() }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val effect = remember(radiusPx) { BlurEffect(radiusPx, radiusPx, TileMode.Clamp) }
        SideEffect { state.layer.renderEffect = effect }
    }
    return this.drawWithContent {
        val contentScope = this
        drawContent()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !state.active) return@drawWithContent
        val bar = state.barBoundsInRoot
        if (bar.width <= 0f || bar.height <= 0f) return@drawWithContent
        val origin = state.contentOriginInRoot
        val left = bar.left - origin.x - radiusPx
        val top = bar.top - origin.y - radiusPx
        val right = bar.right - origin.x + radiusPx
        val bottom = bar.bottom - origin.y + radiusPx
        val layerWidth = (right - left).roundToInt()
        val layerHeight = (bottom - top).roundToInt()
        if (layerWidth <= 0 || layerHeight <= 0) return@drawWithContent
        state.layer.topLeft = IntOffset(left.roundToInt(), top.roundToInt())
        state.layer.record(IntSize(layerWidth, layerHeight)) {
            clipRect(0f, 0f, layerWidth.toFloat(), layerHeight.toFloat()) {
                translate(-left, -top) { contentScope.drawContent() }
            }
        }
        val localBar = Rect(
            bar.left - origin.x,
            bar.top - origin.y,
            bar.right - origin.x,
            bar.bottom - origin.y,
        )
        val radius = localBar.height / 2f
        val capsule = Path().apply {
            addRoundRect(
                RoundRect(
                    localBar.left, localBar.top, localBar.right, localBar.bottom,
                    CornerRadius(radius), CornerRadius(radius),
                    CornerRadius(radius), CornerRadius(radius),
                )
            )
        }
        clipPath(capsule) { drawLayer(state.layer) }
    }
}

@Composable
fun GlassCapsule(
    backdrop: GlassBackdropState,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    DisposableEffect(backdrop) {
        backdrop.active = true
        onDispose {
            backdrop.active = false
            backdrop.barBoundsInRoot = Rect.Zero
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight)
            .onGloballyPositioned { backdrop.barBoundsInRoot = it.boundsInRoot() }
            .clip(CircleShape)
            .background(glassTint(dark))
            .border(1.dp, glassStroke(dark), CircleShape),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun GlassBottomBar(
    currentDestination: NavDestination?,
    onNavigate: (Screen) -> Unit,
    backdrop: GlassBackdropState,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxWidth()) {
        // 2dp 安全边距：给格子 CircleShape 在文字高度的内削留余量
        val labelAvail = maxWidth / 5f - Spacing.s * 2 - 2.dp

        GlassCapsule(backdrop) {
            bottomNavItems.forEach { screen ->
                val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                val color = when {
                    selected -> MaterialTheme.colorScheme.primary
                    dark -> Color.White.copy(alpha = 0.78f)
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                }
                val label = stringResource(screen.titleRes)
                val fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                val fontSize = remember(label, fontWeight, labelAvail, density.fontScale) {
                    val natural = measurer.measure(
                        label,
                        style = TextStyle(fontSize = 11.sp, fontWeight = fontWeight)
                    ).size.width
                    val availPx = with(density) { labelAvail.toPx() }
                    if (natural <= 0 || natural <= availPx) 11.sp
                    else (11f * (availPx / natural)).coerceAtLeast(8f).sp
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = Spacing.s, vertical = Spacing.s)
                        .clip(CircleShape)
                        .clickable { onNavigate(screen) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        if (selected) screen.selectedIcon else screen.unselectedIcon,
                        contentDescription = label,
                        tint = color,
                        modifier = Modifier.size(24.dp).offset(y = 1.dp),
                    )
                    Text(
                        label,
                        modifier = Modifier.offset(y = (-1).dp),
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        color = color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// 真液态玻璃加号：先贴一块本屏内容录制层的模糊副本（backdrop 列表），
// 再画自己的玻璃底/高光/描边；录制层节点在 Scaffold 内容槽，不含加号本身
@Composable
fun GlassFab(
    onClick: () -> Unit,
    backdrop: List<FabLayer> = emptyList(),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bounds = remember { mutableStateOf(Rect.Zero) }
    Box(
        modifier
            .size(56.dp)
            .onGloballyPositioned { bounds.value = it.boundsInRoot() }
            .clip(CircleShape)
            .drawWithContent {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val b = bounds.value
                    if (b.width > 0f && b.height > 0f) {
                        for (fab in backdrop) {
                            val o = fab.originInRoot.value
                            translate(-(b.left - o.x), -(b.top - o.y)) {
                                drawLayer(fab.layer)
                            }
                        }
                    }
                }
                drawContent()
            }
            .background(glassTint(dark), CircleShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (dark) 0.20f else 0.50f),
                        Color.White.copy(alpha = 0f)
                    )
                ),
                CircleShape
            )
            .border(1.dp, glassStroke(dark), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

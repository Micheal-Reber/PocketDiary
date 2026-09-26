package com.example.diary.ui.navigation

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import kotlin.math.roundToInt

/** 悬浮玻璃底栏在各 Tab 页内容底部预留的高度（下间隙 16 + 胶囊 64 + 上间隙 16）。 */
val BottomBarContentInset = 96.dp

private val BarHeight = 64.dp

private val GlassBlurRadius = 20.dp

class GlassBackdropState(val layer: GraphicsLayer) {
    var active by mutableStateOf(false)
    var barBoundsInRoot by mutableStateOf(Rect.Zero)
    var contentOriginInRoot by mutableStateOf(Offset.Zero)
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
fun GlassBottomBar(
    currentDestination: NavDestination?,
    onNavigate: (Screen) -> Unit,
    backdrop: GlassBackdropState,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val tint = if (dark) {
        Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = 0.60f), Color.Black.copy(alpha = 0.46f))
        )
    } else {
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.74f), Color.White.copy(alpha = 0.58f))
        )
    }
    val stroke = if (dark) {
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.10f))
        )
    } else {
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.92f), Color.White.copy(alpha = 0.64f))
        )
    }

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
            .background(tint)
            .border(1.dp, stroke, CircleShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        bottomNavItems.forEach { screen ->
            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
            val color = when {
                selected -> MaterialTheme.colorScheme.primary
                dark -> Color.White.copy(alpha = 0.78f)
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onNavigate(screen) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    if (selected) screen.selectedIcon else screen.unselectedIcon,
                    contentDescription = screen.title,
                    tint = color,
                    modifier = Modifier.size(24.dp).offset(y = 1.dp),
                )
                Text(
                    screen.title,
                    modifier = Modifier.offset(y = (-1).dp),
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = color,
                    maxLines = 1,
                )
            }
        }
    }
}

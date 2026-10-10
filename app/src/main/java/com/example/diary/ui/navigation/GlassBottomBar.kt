package com.example.diary.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import com.example.diary.R
import com.example.diary.ui.theme.Spacing
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/** 悬浮玻璃底栏模式下各 Tab 页内容底部预留的高度（下间隙 16 + 胶囊 64 + 上间隙 16）。 */
val FloatingBottomInset = 96.dp

/**
 * 底栏占位高度，由 [AppNavigation] 按底栏样式提供：
 * 悬浮玻璃胶囊 96dp；经典停靠 NavigationBar 80dp + 手势区高度。
 */
val LocalBottomBarInset = staticCompositionLocalOf { FloatingBottomInset }

val BottomBarContentInset: Dp
    @Composable get() = LocalBottomBarInset.current

private val BarHeight = 64.dp

val DockedBottomBarHeight = 80.dp

private val GlassBlurRadius = 20.dp

private fun rubberBand(overshoot: Float, limit: Float): Float {
    val pull = 1f - 1f / (abs(overshoot) * 0.55f / limit + 1f)
    return limit * pull * overshoot.sign
}

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

@Composable
fun rememberGlassBackdrop(): LayerBackdrop = rememberLayerBackdrop { drawContent() }

@Composable
fun GlassCapsule(
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    indicator: @Composable BoxScope.() -> Unit = {},
    pressProgress: Animatable<Float, AnimationVector1D> = remember { Animatable(0f) },
    content: @Composable RowScope.() -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val pressScope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressScope.launch { pressProgress.animateTo(1f, tween(90)) }
                    waitForUpOrCancellation()
                    pressScope.launch {
                        pressProgress.animateTo(0f, spring(0.5f, 300f, 0.001f))
                    }
                }
            }
            .fillMaxWidth()
            .height(BarHeight),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur(GlassBlurRadius.toPx())
                        lens(16.dp.toPx(), 32.dp.toPx())
                    },
                    layerBlock = {
                        val p = pressProgress.value
                        val maxScale = (size.width + 16.dp.toPx()) / size.width
                        val s = 1f + (maxScale - 1f) * p
                        scaleX = s
                        scaleY = s
                    },
                    onDrawSurface = { drawRect(glassTint(dark)) }
                )
                .clip(CircleShape)
                .border(1.dp, glassStroke(dark), CircleShape)
        )
        indicator()
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun GlassBottomBar(
    currentDestination: NavDestination?,
    onNavigate: (Screen) -> Unit,
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val currentIndex = bottomNavItems.indexOfFirst { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    }
    val liveIndex by rememberUpdatedState(currentIndex)
    val liveNavigate by rememberUpdatedState(onNavigate)

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val labelAvail = maxWidth / 5f - Spacing.xs * 2
        val cellDp = maxWidth / 5f
        val cellPx = with(LocalDensity.current) { cellDp.toPx() }
        val lastIndex = bottomNavItems.size - 1
        // 切 tab 的位移：温和欠阻尼弹簧（≈2% 过冲），单一 animateTo 路径避免双动画打架
        val settleSpec = spring<Float>(0.78f, 300f)
        val releaseSpec = spring<Float>(0.6f, 300f)
        val snapSpec = spring<Float>(0.5f, 300f, 0.001f)
        val stretchLimitPx = with(density) { (BarHeight * 7f / 32f).toPx() }

        // 高亮/透镜的连续位置（tab 单位），拖动 1:1 跟手；rawPos 为未钳制的手指位置（越界走橡胶带）
        var displayIndex by remember { mutableIntStateOf(currentIndex) }
        var rawPos by remember { mutableFloatStateOf(currentIndex.coerceAtLeast(0).toFloat()) }
        val pos = remember { Animatable(currentIndex.coerceAtLeast(0).toFloat()) }
        // 透镜果冻形变：拖动速度越快横向拉丝越明显，松手回弹
        val jelly = remember { Animatable(0f) }
        // 整条胶囊越界拖动的橡胶带拉伸（px），松手弹回
        val stretch = remember { Animatable(0f) }
        // 透镜默认隐藏：拖动时浮现、点按时闪现；按压放大由点按补一次闪现
        val lensAlpha = remember { Animatable(0f) }
        val pressProgress = remember { Animatable(0f) }
        var tabFlashJob by remember { mutableStateOf<Job?>(null) }
        var lastTick by remember { mutableLongStateOf(0L) }
        var fingerVel by remember { mutableFloatStateOf(0f) }
        val animScope = rememberCoroutineScope()

        LaunchedEffect(currentIndex) {
            if (currentIndex >= 0) {
                rawPos = currentIndex.toFloat()
                pos.animateTo(currentIndex.toFloat(), settleSpec)
            }
            displayIndex = currentIndex
        }

        val scrubState = rememberDraggableState { delta ->
            animScope.launch {
                val now = System.nanoTime()
                val last = lastTick
                lastTick = now
                if (last != 0L) {
                    val dt = (now - last) / 1_000_000_000f
                    if (dt in 0.001f..0.1f) {
                        val instant = delta / dt
                        fingerVel += (instant - fingerVel) * (dt * 30f).coerceIn(0f, 1f)
                    }
                }
                rawPos += delta / cellPx
                val clamped = rawPos.coerceIn(0f, lastIndex.toFloat())
                val over = rawPos - clamped
                pos.snapTo(clamped + rubberBand(over, 0.35f))
                stretch.snapTo(rubberBand(over * cellPx, stretchLimitPx))
                jelly.snapTo((abs(fingerVel) / cellPx / 8f).coerceAtMost(1f) * 0.25f)
                val crossed = rawPos.roundToInt().coerceIn(0, lastIndex)
                if (crossed != displayIndex) {
                    displayIndex = crossed
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        }

        GlassCapsule(
            backdrop = backdrop,
            modifier = Modifier
                .draggable(
                    state = scrubState,
                    orientation = Orientation.Horizontal,
                    onDragStarted = {
                        tabFlashJob?.cancel()
                        lastTick = 0L
                        fingerVel = 0f
                        rawPos = pos.value
                        lensAlpha.animateTo(1f, tween(100))
                    },
                    onDragStopped = { velocity ->
                        val lead = velocity * 0.1f / cellPx
                        val start = rawPos.roundToInt().coerceIn(0, lastIndex)
                        val target = (rawPos + lead).roundToInt()
                            .coerceIn(start - 1, start + 1)
                            .coerceIn(0, lastIndex)
                        rawPos = target.toFloat()
                        displayIndex = target
                        lastTick = 0L
                        fingerVel = 0f
                        launch { stretch.animateTo(0f, releaseSpec) }
                        launch { jelly.animateTo(0f, releaseSpec) }
                        launch { lensAlpha.animateTo(0f, tween(260)) }
                        if (target != liveIndex) {
                            // 导航触发 LaunchedEffect 统一弹簧吸附，避免双动画打架
                            liveNavigate(bottomNavItems[target])
                        } else {
                            pos.animateTo(target.toFloat(), settleSpec)
                        }
                    },
                )
                .graphicsLayer {
                    val s = stretch.value
                    translationX = s
                    scaleX = 1f + abs(s) / size.width * 0.5f
                },
            pressProgress = pressProgress,
            indicator = {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .graphicsLayer {
                            translationX = pos.value * cellPx
                            alpha = lensAlpha.value
                            val lift = 1f + 0.10f * pressProgress.value
                            val j = jelly.value
                            scaleX = lift * (1f + j)
                            scaleY = lift * (1f - j / 2f)
                        }
                        .width(cellDp)
                        .height(BarHeight - Spacing.m)
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { CircleShape },
                            effects = {
                                vibrancy()
                                blur(GlassBlurRadius.toPx())
                                lens(16.dp.toPx(), 32.dp.toPx())
                            },
                            onDrawSurface = {
                                drawRect(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.16f),
                                            Color.White.copy(alpha = 0.06f)
                                        )
                                    )
                                )
                            }
                        )
                        .clip(CircleShape)
                        .border(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.40f),
                                    Color.White.copy(alpha = 0.14f)
                                )
                            ),
                            CircleShape
                        )
                )
            },
        ) {
            bottomNavItems.forEachIndexed { index, screen ->
                BottomBarItem(
                    index = index,
                    screen = screen,
                    pos = pos,
                    pressProgress = pressProgress,
                    dark = dark,
                    labelAvail = labelAvail,
                    onClick = {
                        onNavigate(screen)
                        tabFlashJob?.cancel()
                        tabFlashJob = animScope.launch {
                            launch { lensAlpha.animateTo(1f, tween(80)) }
                            launch { pressProgress.animateTo(1f, tween(60)) }
                            delay(440)
                            launch { lensAlpha.animateTo(0f, tween(260)) }
                            pressProgress.animateTo(0f, snapSpec)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun RowScope.BottomBarItem(
    index: Int,
    screen: Screen,
    pos: Animatable<Float, AnimationVector1D>,
    pressProgress: Animatable<Float, AnimationVector1D>,
    dark: Boolean,
    labelAvail: Dp,
    onClick: () -> Unit,
) {
    val emphasis = (1f - abs(pos.value - index)).coerceIn(0f, 1f)
    val selected = emphasis >= 0.5f
    val baseColor = if (dark) {
        Color.White.copy(alpha = 0.78f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
    }
    val color = lerp(baseColor, MaterialTheme.colorScheme.primary, emphasis)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val label = stringResource(screen.titleRes)
    val fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
    val fontSize = remember(label, fontWeight, labelAvail, density.fontScale) {
        val natural = measurer.measure(
            label,
            style = TextStyle(fontSize = 10.sp, fontWeight = fontWeight)
        ).size.width
        val availPx = with(density) { labelAvail.toPx() }
        if (natural <= 0 || natural <= availPx) 10.sp
        else (10f * (availPx / natural)).coerceAtLeast(8.5f).sp
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(horizontal = Spacing.s, vertical = Spacing.s)
            .graphicsLayer {
                val lift = 1f + 0.12f * emphasis * pressProgress.value
                scaleX = lift
                scaleY = lift
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
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
            softWrap = false,
            overflow = TextOverflow.Visible,
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
                    drawRect(glassTint(dark))
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
            .border(1.dp, glassStroke(dark), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

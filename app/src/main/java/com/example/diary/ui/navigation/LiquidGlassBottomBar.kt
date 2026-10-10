package com.example.diary.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import com.example.diary.ui.navigation.liquid.DampedDragAnimation
import com.example.diary.ui.navigation.liquid.InteractiveHighlight
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun LiquidGlassBottomBar(
    currentDestination: NavDestination?,
    onNavigate: (Screen) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val currentIndex = bottomNavItems.indexOfFirst { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    }.coerceAtLeast(0)
    val animationScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val tabCount = bottomNavItems.size
    val position = remember { Animatable(currentIndex.toFloat()) }
    val drag = remember(position, animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = currentIndex.toFloat(),
            valueRange = 0f..(tabCount - 1).toFloat(),
            pressedScale = 1.18f,
            onDrag = { size, amount ->
                val tabWidth = size.width.toFloat() / tabCount
                if (tabWidth > 0f) updateValue(value + amount.x / tabWidth)
            },
            onDragStopped = {
                val target = targetValue.roundToInt().coerceIn(0, tabCount - 1)
                animateToValue(target.toFloat())
                onNavigate(bottomNavItems[target])
            },
        )
    }
    val highlight = remember(animationScope) { InteractiveHighlight(animationScope) }

    LaunchedEffect(currentIndex) {
        position.animateTo(currentIndex.toFloat(), spring(0.78f, 300f))
        drag.animateToValue(currentIndex.toFloat())
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val tabWidth = maxWidth / tabCount
        val glassTint = if (dark) {
            Color(0xFF121212).copy(alpha = 0.50f)
        } else {
            Color(0xFFFAFAFA).copy(alpha = 0.48f)
        }
        val stroke = if (dark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.14f)

        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur(8.dp.toPx())
                        lens(24.dp.toPx(), 24.dp.toPx())
                    },
                    onDrawSurface = { drawRect(glassTint) }
                )
                .clip(CircleShape)
        )
        Box(
            Modifier
                .padding(4.dp)
                .width(tabWidth)
                .height(56.dp)
                .graphicsLayer { translationX = position.value * with(density) { tabWidth.toPx() } }
                .then(highlight.modifier)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur(8.dp.toPx())
                        lens(10.dp.toPx(), 14.dp.toPx(), chromaticAberration = true)
                    },
                    onDrawSurface = {
                        drawRect(if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f))
                    }
                )
                .clip(CircleShape)
        )
        Row(
            Modifier
                .fillMaxSize()
                .then(drag.modifier),
            verticalAlignment = Alignment.CenterVertically,
            content = {
                bottomNavItems.forEachIndexed { index, screen ->
                    LiquidGlassBottomTab(
                        index = index,
                        screen = screen,
                        selectedIndex = position.value,
                        dark = dark,
                        onClick = { onNavigate(screen) },
                    )
                }
            }
        )
    }
}

@Composable
private fun RowScope.LiquidGlassBottomTab(
    index: Int,
    screen: Screen,
    selectedIndex: Float,
    dark: Boolean,
    onClick: () -> Unit,
) {
    val emphasis = (1f - kotlin.math.abs(selectedIndex - index)).coerceIn(0f, 1f)
    val color = androidx.compose.ui.graphics.lerp(
        if (dark) Color.White.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
        MaterialTheme.colorScheme.primary,
        emphasis
    )
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (emphasis >= 0.5f) screen.selectedIcon else screen.unselectedIcon,
            contentDescription = stringResource(screen.titleRes),
            tint = color,
            modifier = Modifier.size(24.dp),
        )
        Text(
            stringResource(screen.titleRes),
            color = color,
            fontSize = 10.sp,
            fontWeight = if (emphasis >= 0.5f) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
        )
    }
}

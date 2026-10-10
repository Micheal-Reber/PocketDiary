package com.example.diary.ui.navigation.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class InteractiveHighlight(
    private val animationScope: CoroutineScope,
    private val position: (Size, Offset) -> Offset = { _, offset -> offset }
) {
    private val pressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
    private var startPosition = Offset.Zero

    val pressProgress: Float get() = pressAnimation.value
    val offset: Offset get() = positionAnimation.value - startPosition

    val modifier = Modifier.drawWithContent {
        val progress = pressAnimation.value
        if (progress > 0f) {
            val point = position(size, positionAnimation.value)
            drawRect(Color.White.copy(alpha = 0.08f * progress), blendMode = BlendMode.Plus)
            drawCircle(
                color = Color.White.copy(alpha = 0.12f * progress),
                radius = size.minDimension * 0.75f,
                center = point
            )
        }
        drawContent()
    }

    val gestureModifier = Modifier.pointerInput(animationScope) {
        inspectDragGestures(
            onDragStart = { down ->
                startPosition = down.position
                animationScope.launch {
                    pressAnimation.animateTo(1f, spring(0.5f, 300f, 0.001f))
                    positionAnimation.snapTo(startPosition)
                }
            },
            onDragEnd = {
                animationScope.launch {
                    launch { pressAnimation.animateTo(0f, spring(0.5f, 300f, 0.001f)) }
                    launch { positionAnimation.animateTo(startPosition, spring(0.5f, 300f, Offset.VisibilityThreshold)) }
                }
            },
            onDragCancel = {
                animationScope.launch { pressAnimation.animateTo(0f, spring(0.5f, 300f, 0.001f)) }
            },
        ) { change, _ -> animationScope.launch { positionAnimation.snapTo(change.position) } }
    }
}

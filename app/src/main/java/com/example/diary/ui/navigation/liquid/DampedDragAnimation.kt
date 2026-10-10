package com.example.diary.ui.navigation.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
    val onDragCancelled: DampedDragAnimation.() -> Unit = {},
) {
    private val valueAnimationSpec = spring(1f, 1000f, visibilityThreshold)
    private val velocityAnimationSpec = spring(0.5f, 300f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec = spring(1f, 1000f, 0.001f)
    private val scaleXAnimationSpec = spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec = spring(0.7f, 250f, 0.001f)

    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 0.01f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)

    private var valueJob: Job? = null
    private var shapeJob: Job? = null
    private var velocityJob: Job? = null
    private var releaseJob: Job? = null
    private var animatedTarget: Float? = null

    var isPressed by mutableStateOf(false)
        private set

    var gestureActive by mutableStateOf(false)
        private set

    val value: Float get() = valueAnimation.value

    val progress: Float
        get() {
            val span = valueRange.endInclusive - valueRange.start
            return if (span > 0f) (value - valueRange.start) / span else 0f
        }

    val targetValue: Float get() = animatedTarget ?: valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(this) {
        inspectDragGestures(
            consumeChanges = true,
            onDragStart = { down ->
                gestureActive = true
                press()
                onDragStarted(down.position)
            },
            onDragEnd = {
                gestureActive = false
                onDragStopped()
                releaseAfterSettle()
            },
            onDragCancel = {
                gestureActive = false
                onDragCancelled()
                release()
            },
        ) { _, dragAmount -> onDrag(size, dragAmount) }
    }

    fun press() {
        releaseJob?.cancel()
        isPressed = true
        valueJob?.cancel()
        animatedTarget = null
        animateShape(true)
    }

    fun release() {
        releaseJob?.cancel()
        isPressed = false
        animateShape(false)
        settleVelocity()
    }

    /** 等指示器到位（或超时）再收起透镜，中途重新按下可取消等待。 */
    private fun releaseAfterSettle() {
        releaseJob?.cancel()
        releaseJob = animationScope.launch {
            withTimeoutOrNull(500L) {
                val threshold = ((valueRange.endInclusive - valueRange.start) * 0.025f)
                    .coerceAtLeast(visibilityThreshold)
                while (abs(value - targetValue) > threshold || pressProgress < 0.9f) delay(16L)
            }
            isPressed = false
            animateShape(false)
            settleVelocity()
        }
    }

    fun updateValue(value: Float) = animateValue(value.coerceIn(valueRange))

    fun animateToValue(value: Float) {
        val target = value.coerceIn(valueRange)
        if (animatedTarget == target && valueJob?.isActive == true) return
        if (!isPressed && abs(this.value - target) <= visibilityThreshold) return
        if (!isPressed && abs(this.value - target) > visibilityThreshold) press()
        animateValue(target)
        if (!gestureActive) releaseAfterSettle()
    }

    /** 每次重定向都带上当前速度继续，UNDISPATCHED 不等下一帧——跟手不僵硬的两个关键。 */
    private fun animateValue(target: Float) {
        if (animatedTarget == target && valueJob?.isActive == true) return
        animatedTarget = target
        val initialVelocity = valueAnimation.velocity
        valueJob?.cancel()
        valueJob = animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            valueAnimation.animateTo(target, valueAnimationSpec, initialVelocity) {
                val span = valueRange.endInclusive - valueRange.start
                val next = if (span > 0f) velocity / span else 0f
                velocityJob?.cancel()
                velocityJob = animationScope.launch {
                    velocityAnimation.animateTo(next, velocityAnimationSpec)
                }
            }
        }
    }

    private fun settleVelocity() {
        velocityJob?.cancel()
        velocityJob = animationScope.launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
    }

    private fun animateShape(pressed: Boolean) {
        shapeJob?.cancel()
        shapeJob = animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            launch { pressProgressAnimation.animateTo(if (pressed) 1f else 0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(if (pressed) pressedScale else initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(if (pressed) pressedScale else initialScale, scaleYAnimationSpec) }
        }
    }
}
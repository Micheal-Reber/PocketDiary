package com.example.diary.ui.navigation.liquid

import kotlin.math.abs
import kotlin.math.floor

/** 命中格与指示器中心是两套坐标：按下哪格，指示器就从哪格开始跟手。 */
internal class DockTouchState(
    private val count: Int,
    private val cellSize: Float,
    private val reverse: Boolean = false,
) {
    private var startCell = 0f
    private var currentCell = 0f
    private var startIndex = 0
    private var displacement = 0f

    val index: Int get() = cellIndex(currentCell)

    val indicatorValue: Float
        get() = (startIndex + (currentCell - startCell)).coerceIn(0f, (count - 1).coerceAtLeast(0).toFloat())

    fun start(position: Float) {
        val safeCell = cellSize.coerceAtLeast(1f)
        val extent = safeCell * count.coerceAtLeast(1)
        startCell = if (reverse) (extent - position) / safeCell else position / safeCell
        currentCell = startCell
        startIndex = cellIndex(startCell)
        displacement = 0f
    }

    fun move(delta: Float) {
        displacement += delta
        currentCell = startCell + displacement / cellSize.coerceAtLeast(1f) * if (reverse) -1f else 1f
    }

    fun hasDragged(touchSlop: Float): Boolean = abs(displacement) > touchSlop.coerceAtLeast(0f)

    private fun cellIndex(cell: Float): Int = floor(cell).toInt().coerceIn(0, (count - 1).coerceAtLeast(0))
}
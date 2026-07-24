package com.example.diary.ui.habits

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val HabitColorPalette = listOf(
    Color(0xFF4CAF50), // Green
    Color(0xFF2196F3), // Blue
    Color(0xFFFF9800), // Orange
    Color(0xFFE91E63), // Pink
    Color(0xFF9C27B0), // Purple
    Color(0xFF00BCD4), // Cyan
    Color(0xFFFF5722), // Deep Orange
    Color(0xFF607D8B), // Blue Grey
    Color(0xFF8BC34A), // Light Green
    Color(0xFF3F51B5), // Indigo
)

fun habitColor(index: Int): Color {
    // Guard against negative indices (Kotlin's % preserves sign, so -1 % 10 == -1,
    // which would throw IndexOutOfBoundsException at render time). Old data with
    // a corrupted colorIndex could otherwise crash the chart.
    val size = HabitColorPalette.size
    return HabitColorPalette[((index % size) + size) % size]
}

data class ChartLine(
    val label: String,
    val color: Color,
    val values: List<Float>  // 0..max
)

@Composable
fun LineChart(
    lines: List<ChartLine>,
    xLabels: List<String>,
    modifier: Modifier = Modifier,
    maxY: Float? = null
) {
    // Contract: every line must have one value per x label; otherwise drawing math
    // (index * stepX) silently misaligns or crashes. Fail loudly at the boundary
    // so the bug surfaces here instead of in obscure UI glitches.
    require(lines.all { it.values.size == xLabels.size }) {
        "Every ChartLine must have values.size == xLabels.size (got ${lines.map { it.values.size }} vs ${xLabels.size})"
    }
    if (lines.isEmpty() || xLabels.isEmpty()) {
        Box(modifier.height(160.dp).fillMaxWidth()) {
            Text("暂无数据", color = MaterialTheme.colorScheme.outline)
        }
        return
    }

    // `require` above already guarantees lines and xLabels are non-empty, so
    // `flatMap { it.values }.maxOrNull()` is always Some. The `?: 1f` and
    // `coerceAtLeast(1f)` are belt-and-suspenders dead code — left out so the
    // expression reads as "20% headroom over the data peak".
    val computedMax = maxY ?: (lines.flatMap { it.values }.max() * 1.2f)
    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier = modifier) {
        // Chart
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(start = 32.dp, end = 8.dp, top = 8.dp, bottom = 24.dp)
        ) {
            val chartWidth = size.width
            val chartHeight = size.height
            val stepX = if (xLabels.size > 1) chartWidth / (xLabels.size - 1) else chartWidth

            // Draw grid lines
            val yAxisPaint = android.graphics.Paint().apply {
                color = textColor.toArgb()
                textSize = 24f
                textAlign = android.graphics.Paint.Align.LEFT
            }
            for (i in 0..4) {
                val y = chartHeight * i / 4
                drawLine(gridColor.copy(alpha = 0.3f), Offset(0f, y), Offset(chartWidth, y), strokeWidth = 1f)
                // Y labels
                val label = "${(computedMax * (4 - i) / 4).toInt()}"
                drawContext.canvas.nativeCanvas.drawText(label, -28f, y + 5f, yAxisPaint)
            }

            // Draw lines
            lines.forEach { line ->
                if (line.values.isEmpty()) return@forEach
                val path = Path()
                line.values.forEachIndexed { index, value ->
                    // With a single data point the chart would degenerate to a
                    // line stuck at the left edge; center it horizontally instead.
                    val x = if (xLabels.size == 1) chartWidth / 2 else index * stepX
                    val y = chartHeight - (value / computedMax * chartHeight)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, line.color, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                // Draw dots
                line.values.forEachIndexed { index, value ->
                    val x = if (xLabels.size == 1) chartWidth / 2 else index * stepX
                    val y = chartHeight - (value / computedMax * chartHeight)
                    drawCircle(line.color, radius = 5f, center = Offset(x, y))
                }
            }
        }

        // X labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            xLabels.forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
        }

        // Legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Match the Canvas's start padding (32.dp) so legend lines up with the chart left edge.
                .padding(start = 32.dp, end = 8.dp, top = 8.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            lines.forEach { line ->
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Canvas(Modifier.size(10.dp)) {
                        drawCircle(line.color, radius = 5f, center = Offset(5f, 5f))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        line.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

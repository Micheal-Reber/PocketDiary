package com.example.diary.ui.habits

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val HabitColorPalette = listOf(
    Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFFF9800), Color(0xFFE91E63),
    Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFFFF5722), Color(0xFF607D8B),
    Color(0xFF8BC34A), Color(0xFF3F51B5),
)

fun habitColor(index: Int): Color {
    val size = HabitColorPalette.size
    return HabitColorPalette[((index % size) + size) % size]
}

data class ChartLine(val label: String, val color: Color, val values: List<Float>)

@Composable
fun LineChart(
    lines: List<ChartLine>,
    xLabels: List<String>,
    modifier: Modifier = Modifier,
    maxY: Float? = null
) {
    if (lines.isEmpty() || xLabels.isEmpty()) {
        Box(modifier.height(160.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("暂无数据", color = MaterialTheme.colorScheme.outline)
        }
        return
    }

    val computedMax = if (maxY != null && maxY > 0) maxY
    else lines.flatMap { it.values }.max().let { if (it <= 0) 1f else it * 1.2f }

    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val yLabels = (0..4).map { i -> "${(computedMax * (4 - i) / 4).toInt()}" }
    // Fixed width for Y axis label column based on typical 3-digit numbers
    val yAxisWidth = 32.dp

    Column(modifier = modifier) {
        // Chart row: Y-axis labels | Canvas
        Row(
            modifier = Modifier.fillMaxWidth().height(180.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Y-axis labels column — precisely aligned to grid lines
            Column(
                modifier = Modifier.width(yAxisWidth).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                yLabels.forEachIndexed { i, label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor,
                        fontSize = 10.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Chart canvas
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = 8.dp, top = 8.dp)
            ) {
                val cw = size.width; val ch = size.height
                val stepX = if (xLabels.size > 1) cw / (xLabels.size - 1) else cw

                // Horizontal grid lines (align with Y-axis label positions)
                for (i in 0..4) {
                    val y = ch * i / 4
                    drawLine(gridColor.copy(alpha = 0.3f), Offset(0f, y), Offset(cw, y), strokeWidth = 1f)
                }

                // Data lines
                lines.forEach { line ->
                    if (line.values.isEmpty()) return@forEach
                    val path = Path()
                    line.values.forEachIndexed { idx, v ->
                        val x = if (xLabels.size == 1) cw / 2 else idx * stepX
                        val y = ch - (v / computedMax * ch)
                        if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, line.color,
                        style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                    line.values.forEachIndexed { idx, v ->
                        val x = if (xLabels.size == 1) cw / 2 else idx * stepX
                        val y = ch - (v / computedMax * ch)
                        drawCircle(line.color, 5f, Offset(x, y))
                    }
                }
            }
        }

        // X-axis labels
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = yAxisWidth, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            xLabels.forEach { label ->
                Text(label, style = MaterialTheme.typography.labelSmall,
                    color = textColor, fontSize = 10.sp, textAlign = TextAlign.Center)
            }
        }

        // Legend
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            lines.forEach { line ->
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(10.dp)) { drawCircle(line.color, 5f, Offset(5f, 5f)) }
                    Spacer(Modifier.width(4.dp))
                    Text(line.label, style = MaterialTheme.typography.labelSmall, color = textColor)
                }
            }
        }
    }
}

package com.example.diary.ui.habits

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil

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

/**
 * Tall line chart with per-point value labels and an "∞"-topped Y axis.
 *
 * Y scale: 5 numeric steps sized to the data (step = ceil(max/5)) plus a
 * decorative "∞" band above the topmost gridline — 7 equally spaced lines in
 * total. Data maps against the numeric region only, so points never enter
 * the ∞ band.
 *
 * [highlightXIndex] marks the "current" column (本周/本月/本日) — its label is
 * drawn red and bold; no guide line or dot emphasis.
 */
@Composable
fun LineChart(
    lines: List<ChartLine>,
    xLabels: List<String>,
    modifier: Modifier = Modifier,
    maxY: Float? = null,
    highlightXIndex: Int? = null,
    // Minimum horizontal distance between adjacent X positions, in dp. When
    // labels would be packed tighter than this the chart area becomes
    // horizontally scrollable (the Y axis stays fixed).
    minStepDp: Float = 0f
) {
    if (lines.isEmpty() || xLabels.isEmpty()) {
        Box(
            modifier.height(340.dp).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text("暂无数据", color = MaterialTheme.colorScheme.outline)
        }
        return
    }

    val rawMax = maxY ?: lines.flatMap { it.values }.max().let { if (it <= 0f) 1f else it }
    val step = ceil(rawMax.coerceAtLeast(1f) / 5f).toInt().coerceAtLeast(1)
    val numericMax = (step * 5).toFloat()
    // Top→bottom: ∞, numericMax ... step, 0 — mirrors the reference design.
    val yLabels = buildList {
        add("∞")
        for (i in 5 downTo 1) add("${i * step}")
        add("0")
    }

    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = MaterialTheme.colorScheme.error
    val textStyle = TextStyle(fontSize = 9.sp)
    val textMeasurer = rememberTextMeasurer()

    // Pre-measure every point's value label once per data change. Measuring
    // inside the draw scope would re-run for all points on every frame —
    // noticeable while the month view scrolls horizontally.
    val measuredLabels = remember(lines, step) {
        lines.map { line ->
            line.values.map { v -> textMeasurer.measure("${v.toInt()}", textStyle) }
        }
    }

    val scrollState = rememberScrollState()
    val minChartWidth = if (minStepDp > 0f && xLabels.size > 1) {
        (xLabels.size * minStepDp).dp
    } else {
        0.dp
    }

    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            // Y-axis labels column — evenly spaced to line up with the gridlines
            Column(
                modifier = Modifier.width(32.dp).height(340.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                yLabels.forEach { label ->
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

            // Chart canvas + X labels scroll together so they never desync;
            // the Y axis column stays pinned.
            BoxWithConstraints(Modifier.weight(1f)) {
                val chartWidth = if (minChartWidth > maxWidth) minChartWidth else maxWidth
                Column(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState)
                ) {
                    Canvas(
                        modifier = Modifier
                            .width(chartWidth)
                            .height(320.dp)
                            .padding(end = 8.dp)
                    ) {
                        val cw = size.width
                        val ch = size.height
                        // 7 gridlines → 6 equal bands; the top band belongs to ∞
                        // and data is mapped into the lower 5 bands only.
                        val band = ch / 6f
                        for (i in 0..6) {
                            val y = ch - i * band
                            drawLine(
                                gridColor.copy(alpha = 0.35f),
                                Offset(0f, y), Offset(cw, y),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                        val plotHeight = band * 5f
                        val stepX = if (xLabels.size > 1) cw / (xLabels.size - 1) else cw

                        lines.forEachIndexed { lineIdx, line ->
                            if (line.values.isEmpty()) return@forEachIndexed
                            val path = Path()
                            line.values.forEachIndexed { idx, v ->
                                val x = if (xLabels.size == 1) cw / 2 else idx * stepX
                                val y = ch - (v / numericMax) * plotHeight
                                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(
                                path, line.color,
                                style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )

                            line.values.forEachIndexed { idx, v ->
                                val x = if (xLabels.size == 1) cw / 2 else idx * stepX
                                val y = ch - (v / numericMax) * plotHeight
                                drawCircle(line.color, 5f, Offset(x, y))
                                // Value label above the point (pre-measured)
                                val text = measuredLabels[lineIdx][idx]
                                val lx = (x - text.size.width / 2f)
                                    .coerceIn(0f, (cw - text.size.width).coerceAtLeast(0f))
                                val ly = (y - text.size.height - 4.dp.toPx()).coerceAtLeast(0f)
                                drawText(text, color = line.color, topLeft = Offset(lx, ly))
                            }
                        }
                    }

                    // X-axis labels
                    Row(
                        modifier = Modifier.width(chartWidth).padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        xLabels.forEachIndexed { i, label ->
                            val isCurrent = i == highlightXIndex
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCurrent) accentColor else MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

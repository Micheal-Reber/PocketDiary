package com.example.diary.ui.focus

import android.content.Intent
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.example.diary.R
import com.example.diary.data.focus.FocusRuntime
import com.example.diary.data.focus.FocusService
import com.example.diary.data.focus.FocusTimer
import com.example.diary.data.focus.WhiteNoise
import com.example.diary.data.preferences.FocusPreferences
import com.example.diary.data.local.FocusSession
import com.example.diary.ui.theme.Spacing

/**
 * 番茄钟页。计时真值在 FocusRecord 快照里，这里只是按 elapsedRealtime 读差值渲染，
 * 所以页面重建 / 从后台回来都不会跳数。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    focusTaskId: Long?,
    onBack: () -> Unit,
    onPickTask: () -> Unit
) {
    val context = LocalContext.current
    val record by FocusRuntime.record.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    val focusPreferences = remember { FocusPreferences(context) }
    val noiseKind by focusPreferences.noiseKind.collectAsStateWithLifecycle(initialValue = WhiteNoise.Kind.OFF.id)
    val scope = rememberCoroutineScope()

    LaunchedEffect(record?.running) {
        while (record?.running == true) {
            kotlinx.coroutines.delay(1000L)
            tick++
        }
    }

    val segments = record?.segments()
    val nowElapsed = remember(tick, record) { SystemClock.elapsedRealtime() }
    val remaining = record?.let {
        FocusTimer.remainingMs(it.toSession(), nowElapsed, it.segments())
    } ?: 0L
    val phaseLength = record?.let { FocusTimer.phaseLengthMs(it.phase, it.segments()) } ?: 0L
    val progress = FocusTimer.progress(remaining, phaseLength)
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val arcColor = phaseColor(record?.phase)
    val arcStroke = 14.dp
    val progressDegrees = 360f * progress

    fun send(action: String, taskId: Long? = record?.taskId, title: String = record?.taskTitle.orEmpty()) {
        val intent = Intent(context, FocusService::class.java).apply {
            this.action = action
            putExtra(FocusService.EXTRA_TASK_ID, taskId ?: -1L)
            putExtra(FocusService.EXTRA_TASK_TITLE, title)
        }
        runCatching { context.startForegroundService(intent) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.focus_title), fontWeight = FontWeight.Bold) },
                actions = {
                    val kind = WhiteNoise.Kind.from(noiseKind)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            noiseKindLabel(kind),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(
                            onClick = {
                                val next = WhiteNoise.Kind.entries[
                                    (WhiteNoise.Kind.entries.indexOf(kind) + 1) % WhiteNoise.Kind.entries.size
                                ]
                                scope.launch {
                                    focusPreferences.setNoiseKind(next.id)
                                    // 正在计时就把新音色推给服务，立刻换掉正在响的那个
                                    if (FocusRuntime.isRunning) {
                                        runCatching {
                                            context.startService(
                                                Intent(context, FocusService::class.java).apply {
                                                    action = FocusService.ACTION_NOISE
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(
                                if (kind == WhiteNoise.Kind.OFF) Icons.Default.VolumeOff else Icons.Default.GraphicEq,
                                contentDescription = stringResource(
                                    R.string.focus_noise_title, noiseKindLabel(kind)
                                ),
                                tint = if (kind == WhiteNoise.Kind.OFF) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                BoxWithConstraints(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val diameter = minOf(maxWidth * 0.9f, maxHeight * 0.94f)
                        .coerceIn(180.dp, 420.dp)
                    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val stroke = arcStroke.toPx()
                            val inset = stroke / 2f
                            val arcSize = Size(size.width - stroke, size.height - stroke)
                            drawArc(
                                color = trackColor,
                                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                                topLeft = Offset(inset, inset), size = arcSize,
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                            if (progressDegrees > 0f) {
                                drawArc(
                                    color = arcColor,
                                    startAngle = -90f, sweepAngle = progressDegrees, useCenter = false,
                                    topLeft = Offset(inset, inset), size = arcSize,
                                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                FocusTimer.formatDuration(remaining),
                                fontSize = 46.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                phaseLabel(record?.phase),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Text(
                record?.taskTitle?.takeIf { it.isNotBlank() } ?: stringResource(R.string.focus_task_fallback),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                stringResource(
                    R.string.focus_cycle,
                    (record?.cycleIndex ?: 0) + 1,
                    (record?.longBreakInterval ?: 4)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Spacing.l))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s)
            ) {
                if (record?.running == true) {
                    Button(
                        onClick = { send(FocusService.ACTION_PAUSE) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(Icons.Filled.Pause, contentDescription = null)
                        Spacer(Modifier.size(Spacing.xs))
                        Text(stringResource(R.string.focus_pause))
                    }
                } else {
                    Button(
                        onClick = { send(FocusService.ACTION_RESUME) },
                        enabled = record != null,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.size(Spacing.xs))
                        Text(
                            stringResource(
                                if (record == null) R.string.focus_start_generic
                                else R.string.focus_resume
                            )
                        )
                    }
                }
                OutlinedButton(
                    onClick = { send(FocusService.ACTION_SKIP) },
                    enabled = record != null,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Filled.SkipNext, contentDescription = null)
                    Spacer(Modifier.size(Spacing.xs))
                    Text(stringResource(R.string.focus_skip))
                }
            }

            Spacer(Modifier.height(Spacing.s))

            OutlinedButton(
                onClick = { send(FocusService.ACTION_STOP) },
                enabled = record != null,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Filled.Stop, contentDescription = null)
                Spacer(Modifier.size(Spacing.xs))
                Text(stringResource(R.string.focus_abandon))
            }

            Spacer(Modifier.height(Spacing.l))

            OutlinedButton(
                onClick = onPickTask,
                enabled = record?.running != true,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(stringResource(R.string.focus_pick_task))
            }

            Spacer(Modifier.height(Spacing.l))
        }
    }
}

@Composable
private fun noiseKindLabel(kind: WhiteNoise.Kind): String = stringResource(
    when (kind) {
        WhiteNoise.Kind.WHITE -> R.string.focus_noise_white
        WhiteNoise.Kind.PINK -> R.string.focus_noise_pink
        WhiteNoise.Kind.BROWN -> R.string.focus_noise_brown
        WhiteNoise.Kind.OFF -> R.string.focus_noise_off
    }
)

@Composable
private fun phaseLabel(phase: Int?): String = stringResource(
    when (phase) {
        FocusSession.PHASE_SHORT_BREAK -> R.string.focus_phase_short_break
        FocusSession.PHASE_LONG_BREAK -> R.string.focus_phase_long_break
        else -> R.string.focus_phase_focus
    }
)

@Composable
private fun phaseColor(phase: Int?) = when (phase) {
    FocusSession.PHASE_SHORT_BREAK -> MaterialTheme.colorScheme.tertiary
    FocusSession.PHASE_LONG_BREAK -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.primary
}